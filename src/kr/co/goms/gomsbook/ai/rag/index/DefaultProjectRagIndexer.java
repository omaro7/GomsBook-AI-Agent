/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.constant.RagConstant;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.rag.document.DocumentLoader;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingPurpose;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingRequest;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingResponse;
import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.model.DocumentSource;
import kr.co.goms.gomsbook.ai.rag.util.RagUtil;
import kr.co.goms.gomsbook.ai.rag.vector.VectorRecord;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.util.GomsStringUtil;

/**
 * 기본 {@link ProjectRagIndexer} 구현체입니다.
 *
 * <p>
 * 현재 EPUB 프로젝트의 TEXT 디렉터리와 VectorStore를 비교하여
 * 증분 방식으로 RAG 인덱스를 동기화합니다.
 * </p>
 *
 * <p>
 * 다음 상태를 처리합니다.
 * </p>
 *
 * <ul>
 *     <li>NEW - 새 XHTML 문서를 인덱싱</li>
 *     <li>CHANGED - 변경된 XHTML 문서를 재인덱싱</li>
 *     <li>UNCHANGED - 변경되지 않은 XHTML 문서를 건너뜀</li>
 *     <li>DELETED - 삭제된 XHTML의 stale vector 제거</li>
 * </ul>
 *
 * <pre>
 * EpubProjectContext
 *      ↓
 * TEXT/*.xhtml
 *      ↓
 * DocumentLoader
 *      ↓
 * sourceHash 비교
 *      ↓
 * NEW / CHANGED / UNCHANGED / DELETED
 *      ↓
 * DocumentIndexer
 *      ↓
 * EmbeddingClient
 *      ↓
 * VectorStore
 *      ↓
 * ProjectIndexResult
 * </pre>
 */
public final class DefaultProjectRagIndexer
        implements ProjectRagIndexer {

    private final DocumentLoader documentLoader;

    private final DocumentIndexer documentIndexer;

    private final EmbeddingClient embeddingClient;

    private final VectorStore vectorStore;


    public DefaultProjectRagIndexer(
            DocumentLoader documentLoader,
            DocumentIndexer documentIndexer,
            EmbeddingClient embeddingClient,
            VectorStore vectorStore) {

        this.documentLoader =
                Objects.requireNonNull(
                        documentLoader,
                        "documentLoader must not be null"
                );

        this.documentIndexer =
                Objects.requireNonNull(
                        documentIndexer,
                        "documentIndexer must not be null"
                );

        this.embeddingClient =
                Objects.requireNonNull(
                        embeddingClient,
                        "embeddingClient must not be null"
                );

        this.vectorStore =
                Objects.requireNonNull(
                        vectorStore,
                        "vectorStore must not be null"
                );
    }


    @Override
    public ProjectIndexResult synchronize(
            EpubProjectContext project)
            throws ProjectIndexException {

        if (project == null) {

            throw new ProjectIndexException(
                    "EPUB project must not be null."
            );
        }

        try {

            String embeddingModel =
                    RagConstant.DEFAULT_EMBEDDING_MODEL;


            Path projectRoot =
                    Objects.requireNonNull(
                            project.getProjectRoot(),
                            "projectRoot must not be null"
                    )
                    .toAbsolutePath()
                    .normalize();


            Path textDirectory =
                    Objects.requireNonNull(
                            project.getTextDirectory(),
                            "textDirectory must not be null"
                    )
                    .toAbsolutePath()
                    .normalize();


            validateTextDirectory(
                    textDirectory
            );


            /*
             * Index/Search 양쪽에서 반드시 동일한 규칙을 사용해야 한다.
             */
            String projectId =
                    createProjectId(
                            projectRoot
                    );


            List<Path> xhtmlFiles =
                    findXhtmlFiles(
                            textDirectory
                    );


            int processedFiles = 0;

            int newFiles = 0;

            int reindexedFiles = 0;

            int skippedFiles = 0;

            int deletedFiles = 0;


            int createdChunks = 0;

            int createdEmbeddings = 0;

            int storedVectors = 0;


            /*
             * 통계를 분리해서 관리한다.
             *
             * staleDeletedVectors:
             * 실제 XHTML 파일 삭제로 제거된 Vector 수
             *
             * replacedVectors:
             * CHANGED 문서 재인덱싱 과정에서 교체된 기존 Vector 수
             */
            int staleDeletedVectors = 0;

            int replacedVectors = 0;


            List<String> indexedFiles =
                    new ArrayList<>();

            List<String> skippedFilePaths =
                    new ArrayList<>();

            List<String> deletedFilePaths =
                    new ArrayList<>();


            String textSourcePrefix =
                    createTextSourcePrefix(
                            projectRoot,
                            textDirectory
                    );


            Set<String> currentSourcePaths =
                    createCurrentSourcePaths(
                            projectRoot,
                            xhtmlFiles
                    );


            /*
             * 1. DELETED 처리
             *
             * 현재 Project Scope + Embedding Model에 해당하는
             * VectorRecord 중 실제 TEXT 폴더에 없는 문서를 제거한다.
             */
            List<VectorRecord> projectRecords =
                    vectorStore
                            .findByProjectAndModel(
                                    projectId,
                                    embeddingModel
                            );


            Set<String> storedSourcePaths =
                    new LinkedHashSet<>();


            for (VectorRecord record
                    : projectRecords) {

                if (record == null
                        || record.getChunk() == null) {

                    continue;
                }


                String sourcePath =
                        GomsStringUtil.normalizePath(
                                record.getChunk()
                                        .getSourcePath()
                        );


                if (sourcePath.isBlank()) {

                    continue;
                }


                /*
                 * 현재 TEXT 영역의 레코드만
                 * stale 여부를 검사한다.
                 */
                if (!sourcePath.startsWith(
                        textSourcePrefix
                )) {

                    continue;
                }


                storedSourcePaths.add(
                        sourcePath
                );
            }


            for (String storedSourcePath
                    : storedSourcePaths) {

                if (currentSourcePaths.contains(
                        storedSourcePath
                )) {

                    continue;
                }


                int deleted =
                        vectorStore
                                .deleteByProjectAndSourcePath(
                                        projectId,
                                        storedSourcePath,
                                        embeddingModel
                                );


                if (deleted <= 0) {

                    continue;
                }


                deletedFiles++;

                staleDeletedVectors +=
                        deleted;


                deletedFilePaths.add(
                        storedSourcePath
                );


                System.out.println(
                        "[RAG][DELETE] "
                                + storedSourcePath
                                + ", deletedVectors="
                                + deleted
                );
            }


            /*
             * 2. NEW / CHANGED / UNCHANGED 처리
             */
            for (Path xhtmlFile
                    : xhtmlFiles) {

                Path relativePath =
                        projectRoot.relativize(
                                xhtmlFile
                        );


                String relativeSourcePath =
                        GomsStringUtil.normalizePath(
                                relativePath.toString()
                        );


                DocumentSource source =
                        documentLoader.load(
                                projectRoot,
                                relativePath
                        );


                if (!documentIndexer.supports(
                        source
                )) {

                    System.out.println(
                            "[RAG][SKIP] Unsupported source: "
                                    + relativeSourcePath
                    );

                    continue;
                }


                String sourceHash =
                        RagUtil.sha256(
                                source.getContent()
                        );


                List<VectorRecord> existingRecords =
                        vectorStore
                                .findByProjectAndSourcePath(
                                        projectId,
                                        relativeSourcePath
                                )
                                .stream()
                                .filter(
                                        record ->
                                                record != null
                                                        && record.isModel(
                                                                embeddingModel
                                                        )
                                )
                                .toList();


                /*
                 * UNCHANGED
                 */
                if (isSourceUnchanged(
                        existingRecords,
                        sourceHash
                )) {

                    skippedFiles++;

                    processedFiles++;


                    skippedFilePaths.add(
                            relativeSourcePath
                    );


                    System.out.println(
                            "[RAG][SKIP] "
                                    + relativeSourcePath
                                    + " - source unchanged"
                    );


                    continue;
                }


                boolean reindex =
                        !existingRecords.isEmpty();


                List<DocumentChunk> chunks =
                        documentIndexer.index(
                                source
                        );


                if (chunks == null) {

                    throw new IllegalStateException(
                            "DocumentIndexer returned null. "
                                    + "source="
                                    + relativeSourcePath
                    );
                }


                createdChunks +=
                        chunks.size();


                System.out.println(
                        "[RAG] source="
                                + relativeSourcePath
                                + ", type="
                                + source.getType()
                                + ", contentLength="
                                + source.getContent().length()
                                + ", chunks="
                                + chunks.size()
                );


                /*
                 * 새 Vector를 먼저 모두 생성한다.
                 *
                 * Embedding 과정 중 실패할 경우
                 * 기존 정상 Vector를 삭제하지 않기 위함이다.
                 */
                List<VectorRecord> newRecords =
                        new ArrayList<>();


                for (DocumentChunk chunk
                        : chunks) {

                    if (chunk == null) {

                        continue;
                    }


                    String embeddingText =
                            chunk.toEmbeddingText();


                    if (embeddingText == null
                            || embeddingText.isBlank()) {

                        continue;
                    }


                    String contentHash =
                            RagUtil.sha256(
                                    embeddingText
                            );


                    EmbeddingRequest embeddingRequest =
                            EmbeddingRequest.builder()
                                    .model(
                                            embeddingModel
                                    )
                                    .input(
                                            embeddingText
                                    )
                                    .purpose(
                                            EmbeddingPurpose.DOCUMENT
                                    )
                                    .build();


                    EmbeddingResponse embeddingResponse =
                            embeddingClient.embed(
                                    embeddingRequest
                            );


                    if (embeddingResponse == null) {

                        throw new IllegalStateException(
                                "Embedding response is null. "
                                        + "chunk="
                                        + chunk.getId()
                        );
                    }


                    List<float[]> embeddings =
                            embeddingResponse
                                    .getEmbeddings();


                    if (embeddings == null
                            || embeddings.isEmpty()) {

                        throw new IllegalStateException(
                                "Embedding vector is empty. "
                                        + "chunk="
                                        + chunk.getId()
                        );
                    }


                    if (embeddings.size() != 1) {

                        throw new IllegalStateException(
                                "Unexpected embedding count. "
                                        + "chunk="
                                        + chunk.getId()
                                        + ", count="
                                        + embeddings.size()
                        );
                    }


                    float[] vector =
                            embeddings.get(0);


                    if (vector == null
                            || vector.length == 0) {

                        throw new IllegalStateException(
                                "Embedding vector is empty. "
                                        + "chunk="
                                        + chunk.getId()
                        );
                    }


                    createdEmbeddings++;


                    VectorRecord record =
                            VectorRecord.builder()
                                    .projectId(
                                            projectId
                                    )
                                    .chunk(
                                            chunk
                                    )
                                    .vector(
                                            vector
                                    )
                                    .model(
                                            embeddingModel
                                    )
                                    .contentHash(
                                            contentHash
                                    )
                                    .sourceHash(
                                            sourceHash
                                    )
                                    .normalized(
                                            embeddingResponse
                                                    .isNormalized()
                                    )
                                    .indexedAt(
                                            System.currentTimeMillis()
                                    )
                                    .version(
                                            1L
                                    )
                                    .build();


                    newRecords.add(
                            record
                    );
                }


                /*
                 * CHANGED
                 */
                if (reindex) {

                    int deleted =
                            vectorStore
                                    .deleteByProjectAndSourcePath(
                                            projectId,
                                            relativeSourcePath,
                                            embeddingModel
                                    );


                    replacedVectors +=
                            deleted;


                    reindexedFiles++;


                    System.out.println(
                            "[RAG][REINDEX] "
                                    + relativeSourcePath
                                    + ", oldVectors="
                                    + deleted
                                    + ", newVectors="
                                    + newRecords.size()
                    );

                } else {

                    /*
                     * NEW
                     */
                    newFiles++;


                    System.out.println(
                            "[RAG][NEW] "
                                    + relativeSourcePath
                                    + ", vectors="
                                    + newRecords.size()
                    );
                }


                vectorStore.saveAll(
                        newRecords
                );


                storedVectors +=
                        newRecords.size();


                processedFiles++;


                indexedFiles.add(
                        relativeSourcePath
                );
            }


            long vectorStoreSize =
                    vectorStore.count();


            return ProjectIndexResult.builder()
                    .projectId(
                            projectId
                    )
                    .projectName(
                            project.getProjectName()
                    )
                    .textDirectory(
                            textDirectory.toString()
                    )
                    .embeddingModel(
                            embeddingModel
                    )
                    .discoveredFiles(
                            xhtmlFiles.size()
                    )
                    .processedFiles(
                            processedFiles
                    )
                    .newFiles(
                            newFiles
                    )
                    .reindexedFiles(
                            reindexedFiles
                    )
                    .skippedFiles(
                            skippedFiles
                    )
                    .deletedFiles(
                            deletedFiles
                    )
                    .createdChunks(
                            createdChunks
                    )
                    .createdEmbeddings(
                            createdEmbeddings
                    )
                    .storedVectors(
                            storedVectors
                    )
                    /*
                     * 현재 ProjectIndexResult가 deletedVectors만
                     * 가지고 있다면 합산값으로 전달한다.
                     *
                     * 추후 ProjectIndexResult에
                     * staleDeletedVectors / replacedVectors를
                     * 분리 추가하는 것을 권장한다.
                     */
                    .deletedVectors(
                            staleDeletedVectors
                                    + replacedVectors
                    )
                    .vectorStoreSize(
                            vectorStoreSize
                    )
                    .indexedFiles(
                            indexedFiles
                    )
                    .skippedFilePaths(
                            skippedFilePaths
                    )
                    .deletedFilePaths(
                            deletedFilePaths
                    )
                    .build();


        } catch (Exception exception) {

            throw new ProjectIndexException(
                    "Failed to synchronize EPUB project RAG index: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    @Override
    public boolean isAvailable() {

        return embeddingClient.isAvailable(
                RagConstant.DEFAULT_EMBEDDING_MODEL
        )
                && vectorStore.isAvailable();
    }


    private String createProjectId(
            Path projectRoot) {

        return RagUtil.sha256(
                GomsStringUtil.normalizePath(
                        projectRoot
                                .toAbsolutePath()
                                .normalize()
                                .toString()
                )
        );
    }


    private String createTextSourcePrefix(
            Path projectRoot,
            Path textDirectory) {

        String prefix =
                GomsStringUtil.normalizePath(
                        projectRoot
                                .relativize(
                                        textDirectory
                                )
                                .toString()
                );


        if (!prefix.endsWith("/")) {

            prefix =
                    prefix + "/";
        }


        return prefix;
    }


    private Set<String> createCurrentSourcePaths(
            Path projectRoot,
            List<Path> xhtmlFiles) {

        Set<String> result =
                new LinkedHashSet<>();


        for (Path xhtmlFile
                : xhtmlFiles) {

            Path relativePath =
                    projectRoot.relativize(
                            xhtmlFile
                    );


            result.add(
                    GomsStringUtil.normalizePath(
                            relativePath.toString()
                    )
            );
        }


        return result;
    }


    private boolean isSourceUnchanged(
            List<VectorRecord> records,
            String sourceHash) {

        if (records == null
                || records.isEmpty()) {

            return false;
        }


        if (sourceHash == null
                || sourceHash.isBlank()) {

            return false;
        }


        for (VectorRecord record
                : records) {

            if (record == null) {

                return false;
            }


            if (!record.hasSourceHash()) {

                return false;
            }


            if (!sourceHash.equals(
                    record.getSourceHash()
            )) {

                return false;
            }
        }


        return true;
    }


    private void validateTextDirectory(
            Path textDirectory) {

        if (!Files.exists(
                textDirectory
        )) {

            throw new IllegalStateException(
                    "TEXT directory does not exist: "
                            + textDirectory
            );
        }


        if (!Files.isDirectory(
                textDirectory
        )) {

            throw new IllegalStateException(
                    "TEXT path is not a directory: "
                            + textDirectory
            );
        }
    }


    private List<Path> findXhtmlFiles(
            Path textDirectory) {

        try (Stream<Path> stream =
                Files.walk(
                        textDirectory
                )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            this::isXhtmlFile
                    )
                    .sorted(
                            Comparator.comparing(
                                    path ->
                                            path.toAbsolutePath()
                                                    .normalize()
                                                    .toString()
                            )
                    )
                    .toList();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to scan TEXT directory: "
                            + textDirectory,
                    exception
            );
        }
    }


    private boolean isXhtmlFile(
            Path path) {

        if (path == null
                || path.getFileName() == null) {

            return false;
        }


        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );


        return fileName.endsWith(
                ".xhtml"
        );
    }
}