/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;

/**
 * EPUB manifest 리소스를 실제 작업 디렉터리에 기록하는
 * 기본 구현체입니다.
 *
 * <p>다음 방식의 리소스를 지원합니다.</p>
 *
 * <ul>
 *     <li>로컬 파일 기반 리소스 복사</li>
 *     <li>메모리 byte[] 기반 리소스 기록</li>
 *     <li>제외된 리소스 건너뛰기</li>
 *     <li>허용된 원격 리소스 건너뛰기</li>
 * </ul>
 *
 * <p>현재 구현에서는 원격 리소스 다운로드를 직접 수행하지 않습니다.
 * {@code downloadRemoteResources=true}인 경우에도 별도의 다운로드
 * 구현체가 필요합니다.</p>
 */
public final class DefaultEpubResourceWriter
        implements EpubResourceWriter {

    /**
     * 단일 EPUB 리소스를 기록합니다.
     *
     * @param resource 리소스
     * @param pathConfiguration 경로 설정
     * @param options 생성 옵션
     * @return 기록 결과
     * @throws EpubGenerationException 처리 실패 시
     */
    @Override
    public EpubResourceWriteResult write(
            EpubResource resource,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(
                resource,
                pathConfiguration,
                options
        );

        if (!resource.isIncluded()) {
            return EpubResourceWriteResult.skipped(
                    resource,
                    "Resource is excluded from the EPUB package."
            );
        }

        if (resource.isRemote()) {
            return handleRemoteResource(
                    resource,
                    options
            );
        }

        Path targetPath = resolveTargetPath(
                resource,
                pathConfiguration
        );

        validateTargetPath(
                targetPath,
                pathConfiguration,
                resource
        );

        /*
         * content가 존재하면 sourcePath보다 우선합니다.
         *
         * 생성된 XHTML이나 nav.xhtml처럼 메모리에 있는 최신 데이터를
         * 파일 기반 원본보다 우선하는 것이 자연스럽습니다.
         */
        if (resource.hasContent()) {
            return writeEmbeddedContent(
                    resource,
                    targetPath,
                    options
            );
        }

        if (resource.hasSourcePath()) {
            return copySourceFile(
                    resource,
                    targetPath,
                    options
            );
        }

        if (options.isFailOnMissingResource()) {
            throw missingResourceException(resource);
        }

        return EpubResourceWriteResult.skipped(
                resource,
                "No local source or embedded content is available."
        );
    }

    /**
     * manifest의 모든 리소스를 기록합니다.
     *
     * @param manifest EPUB manifest
     * @param pathConfiguration 경로 설정
     * @param options 생성 옵션
     * @return 개별 처리 결과
     * @throws EpubGenerationException 처리 실패 시
     */
    @Override
    public List<EpubResourceWriteResult> writeAll(
            EpubManifest manifest,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(
                manifest,
                pathConfiguration,
                options
        );

        if (manifest.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubResource> resources =
                new ArrayList<>(manifest.getResources());

        /*
         * 결과 순서를 항상 동일하게 유지합니다.
         *
         * manifest 자체가 등록 순서를 유지하지만 경로 기준으로
         * 정렬하면 파일 생성 순서가 안정적이며 테스트하기 쉽습니다.
         */
        resources.sort(
                Comparator.comparing(
                        EpubResource::getHref,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        List<EpubResourceWriteResult> results =
                new ArrayList<>(resources.size());

        for (EpubResource resource : resources) {
            results.add(
                    write(
                            resource,
                            pathConfiguration,
                            options
                    )
            );
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * 메모리 byte[] 데이터를 파일로 기록합니다.
     */
    private EpubResourceWriteResult writeEmbeddedContent(
            EpubResource resource,
            Path targetPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (!options.isWriteEmbeddedResources()) {
            return EpubResourceWriteResult.skipped(
                    resource,
                    "Embedded resource writing is disabled."
            );
        }

        byte[] content = resource.getContent()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Embedded EPUB resource content "
                                        + "was unexpectedly missing: "
                                        + resource.getId()
                        )
                );

        try {
            createParentDirectory(targetPath);

            Files.write(
                    targetPath,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            long writtenBytes = Files.size(targetPath);

            if (writtenBytes != content.length) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .RESOURCE_WRITE_FAILED,
                        "EPUB resource size mismatch after writing: "
                                + resource.getId()
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .RESOURCE_PROCESSING
                        )
                        .resourceId(resource.getId())
                        .epubPath(resource.getHref())
                        .filePath(targetPath)
                        .detail(
                                "expectedBytes",
                                String.valueOf(content.length)
                        )
                        .detail(
                                "actualBytes",
                                String.valueOf(writtenBytes)
                        )
                        .build();
            }

            return EpubResourceWriteResult.written(
                    resource,
                    targetPath,
                    writtenBytes
            );

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_WRITE_FAILED,
                    "Failed to write EPUB resource: "
                            + resource.getId()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(targetPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 로컬 원본 파일을 EPUB 작업 디렉터리로 복사합니다.
     */
    private EpubResourceWriteResult copySourceFile(
            EpubResource resource,
            Path targetPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (!options.isCopyResources()) {
            return EpubResourceWriteResult.skipped(
                    resource,
                    "Local resource copying is disabled."
            );
        }

        Path sourcePath = resolveSourcePath(resource);

        validateSourceFile(
                resource,
                sourcePath,
                options
        );

        /*
         * source와 target이 동일하면 복사할 필요가 없습니다.
         */
        if (samePath(sourcePath, targetPath)) {
            try {
                long size = Files.size(sourcePath);

                return EpubResourceWriteResult.copied(
                        resource,
                        targetPath,
                        size
                );

            } catch (IOException exception) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .RESOURCE_COPY_FAILED,
                        "Failed to inspect EPUB resource: "
                                + sourcePath
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .RESOURCE_PROCESSING
                        )
                        .resourceId(resource.getId())
                        .epubPath(resource.getHref())
                        .filePath(sourcePath)
                        .cause(exception)
                        .build();
            }
        }

        try {
            createParentDirectory(targetPath);

            Files.copy(
                    sourcePath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );

            long sourceSize = Files.size(sourcePath);
            long targetSize = Files.size(targetPath);

            if (sourceSize != targetSize) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .RESOURCE_COPY_FAILED,
                        "EPUB resource size mismatch after copy: "
                                + resource.getId()
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .RESOURCE_PROCESSING
                        )
                        .resourceId(resource.getId())
                        .epubPath(resource.getHref())
                        .filePath(targetPath)
                        .detail(
                                "sourceBytes",
                                String.valueOf(sourceSize)
                        )
                        .detail(
                                "targetBytes",
                                String.valueOf(targetSize)
                        )
                        .build();
            }

            return EpubResourceWriteResult.copied(
                    resource,
                    targetPath,
                    targetSize
            );

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_COPY_FAILED,
                    "Failed to copy EPUB resource: "
                            + resource.getId()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .requestId(null)
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(sourcePath)
                    .detail(
                            "targetPath",
                            targetPath.toString()
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 원격 리소스를 처리합니다.
     *
     * <p>현재 구현에서는 네트워크 다운로드를 수행하지 않습니다.</p>
     */
    private EpubResourceWriteResult handleRemoteResource(
            EpubResource resource,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (!options.isAllowRemoteResources()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "Remote EPUB resource is not allowed: "
                            + resource.getHref()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .build();
        }

        if (options.isDownloadRemoteResources()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .UNSUPPORTED_REQUEST,
                    "Remote EPUB resource downloading is not "
                            + "implemented by DefaultEpubResourceWriter: "
                            + resource.getHref()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .detail(
                            "downloadRemoteResources",
                            "true"
                    )
                    .build();
        }

        return EpubResourceWriteResult.skipped(
                resource,
                "Remote resource remains external."
        );
    }

    /**
     * sourcePath를 로컬 Path로 변환합니다.
     */
    private Path resolveSourcePath(
            EpubResource resource
    ) throws EpubGenerationException {

        String source = resource.getSourcePath()
                .orElseThrow(() ->
                        missingResourceException(resource)
                );

        try {
            return Path.of(source)
                    .toAbsolutePath()
                    .normalize();

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "Invalid EPUB source path: " + source
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 로컬 원본 파일을 검증합니다.
     */
    private void validateSourceFile(
            EpubResource resource,
            Path sourcePath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (!Files.exists(sourcePath)) {
            if (options.isFailOnMissingResource()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .RESOURCE_NOT_FOUND,
                        "EPUB source resource does not exist: "
                                + sourcePath
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .RESOURCE_PROCESSING
                        )
                        .resourceId(resource.getId())
                        .epubPath(resource.getHref())
                        .filePath(sourcePath)
                        .build();
            }

            return;
        }

        if (!Files.isRegularFile(sourcePath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "EPUB source resource is not a regular file: "
                            + sourcePath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(sourcePath)
                    .build();
        }

        if (!Files.isReadable(sourcePath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "EPUB source resource is not readable: "
                            + sourcePath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(sourcePath)
                    .build();
        }
    }

    /**
     * 대상 경로가 작업 콘텐츠 루트 밖으로 벗어나지 않는지 검사합니다.
     */
    private void validateTargetPath(
            Path targetPath,
            EpubPathConfiguration pathConfiguration,
            EpubResource resource
    ) throws EpubGenerationException {

        Path contentRoot = pathConfiguration
                .getContentRootPath()
                .toAbsolutePath()
                .normalize();

        Path target = targetPath
                .toAbsolutePath()
                .normalize();

        if (!target.startsWith(contentRoot)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "EPUB resource target path is outside "
                            + "the content root."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(target)
                    .detail(
                            "contentRoot",
                            contentRoot.toString()
                    )
                    .build();
        }

        if (Files.exists(target)
                && Files.isDirectory(target)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_WRITE_FAILED,
                    "EPUB resource target path is a directory: "
                            + target
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .filePath(target)
                    .build();
        }
    }

    /**
     * 상위 디렉터리를 생성합니다.
     */
    private void createParentDirectory(
            Path targetPath
    ) throws IOException {

        Path parent = targetPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private EpubGenerationException missingResourceException(
            EpubResource resource
    ) {
        return EpubGenerationException.builder(
                EpubGenerationException.ErrorCode
                        .RESOURCE_NOT_FOUND,
                "EPUB resource has no available source: "
                        + resource.getId()
        )
                .stage(
                        EpubGenerationException.Stage
                                .RESOURCE_PROCESSING
                )
                .resourceId(resource.getId())
                .epubPath(resource.getHref())
                .build();
    }

    private static boolean samePath(
            Path first,
            Path second
    ) {
        if (first == null || second == null) {
            return false;
        }

        return first.toAbsolutePath()
                .normalize()
                .equals(
                        second.toAbsolutePath()
                                .normalize()
                );
    }
}