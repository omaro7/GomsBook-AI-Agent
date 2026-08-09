/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingException;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingModelProvider;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingPurpose;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingRequest;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingResponse;
import kr.co.goms.gomsbook.ai.rag.hash.HashException;
import kr.co.goms.gomsbook.ai.rag.hash.HashService;
import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.model.DocumentSource;
import kr.co.goms.gomsbook.ai.rag.vector.VectorRecord;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStoreException;

/**
 * EPUB 문서를 Chunk로 분할하고 임베딩한 뒤 VectorStore에 저장하는
 * 기본 {@link RagIndexer} 구현체입니다.
 *
 * <pre>
 * DocumentSource
 *      ↓
 * DocumentIndexer
 *      ↓
 * DocumentChunk
 *      ↓
 * EmbeddingClient
 *      ↓
 * VectorRecord
 *      ↓
 * VectorStore
 * </pre>
 */
public final class DefaultRagIndexer implements RagIndexer {

    private final DocumentIndexer documentIndexer;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final VectorStore vectorStore;
    private final HashService hashService;

    public DefaultRagIndexer(
        DocumentIndexer documentIndexer,
        EmbeddingClient embeddingClient,
        EmbeddingModelProvider embeddingModelProvider,
        VectorStore vectorStore,
        HashService hashService
    ) {
        this.documentIndexer = Objects.requireNonNull(
            documentIndexer,
            "documentIndexer must not be null"
        );

        this.embeddingClient = Objects.requireNonNull(
            embeddingClient,
            "embeddingClient must not be null"
        );

        this.embeddingModelProvider = Objects.requireNonNull(
            embeddingModelProvider,
            "embeddingModelProvider must not be null"
        );

        this.vectorStore = Objects.requireNonNull(
            vectorStore,
            "vectorStore must not be null"
        );

        this.hashService = Objects.requireNonNull(
            hashService,
            "hashService must not be null"
        );
    }

    @Override
    public RagIndexResult index(
        DocumentSource source
    ) throws RagIndexException {

        return index(
            source,
            RagIndexRequest.defaults()
        );
    }

    @Override
    public RagIndexResult index(
        DocumentSource source,
        RagIndexRequest request
    ) throws RagIndexException {

        validateSource(source);
        validateRequest(request);

        String model = resolveEmbeddingModel(
            source.getRelativePath()
        );

        List<DocumentChunk> chunks;

        try {
            chunks = documentIndexer.index(source);

        } catch (DocumentIndexException exception) {
            throw new RagIndexException(
                "Failed to split document into chunks",
                RagIndexOperation.CHUNK_DOCUMENT,
                source.getRelativePath(),
                "",
                model,
                exception
            );

        } catch (RuntimeException exception) {
            throw new RagIndexException(
                "Unexpected error while splitting document",
                RagIndexOperation.CHUNK_DOCUMENT,
                source.getRelativePath(),
                "",
                model,
                exception
            );
        }

        return indexChunksInternal(
            source,
            chunks,
            request,
            model
        );
    }

    @Override
    public RagIndexResult indexChunks(
        DocumentSource source,
        List<DocumentChunk> chunks,
        RagIndexRequest request
    ) throws RagIndexException {

        validateSource(source);
        validateRequest(request);
        validateChunks(source, chunks);

        String model = resolveEmbeddingModel(
            source.getRelativePath()
        );

        return indexChunksInternal(
            source,
            chunks,
            request,
            model
        );
    }

    private RagIndexResult indexChunksInternal(
        DocumentSource source,
        List<DocumentChunk> chunks,
        RagIndexRequest request,
        String model
    ) throws RagIndexException {

        long startedAt = System.nanoTime();

        String sourcePath =
            source.getRelativePath();

        List<DocumentChunk> safeChunks =
            sanitizeChunks(source, chunks);

        RagIndexState state =
            new RagIndexState(
                sourcePath,
                model,
                safeChunks.size()
            );

        try {
            if (request.isReplaceSource()) {
                state.deletedCount =
                    deleteExistingSource(
                        sourcePath,
                        model
                    );
            }

            ExistingRecordIndex existingIndex =
                request.isReplaceSource()
                    ? ExistingRecordIndex.empty()
                    : loadExistingRecords(
                        sourcePath,
                        model
                    );

            IndexPlan plan =
                createIndexPlan(
                    safeChunks,
                    request,
                    model,
                    existingIndex,
                    state
                );

            embedAndSave(
                source,
                plan.chunksToEmbed,
                plan.contentHashes,
                request,
                model,
                state
            );

            if (!request.isReplaceSource()) {
                state.deletedCount +=
                    deleteStaleRecords(
                        existingIndex,
                        plan.currentChunkIds,
                        model
                    );
            }

            state.durationNanos =
                System.nanoTime() - startedAt;

            state.success =
                state.failedCount == 0;

            return state.toResult();

        } catch (RagIndexException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new RagIndexException(
                "Unexpected error while indexing document",
                RagIndexOperation.SAVE,
                sourcePath,
                "",
                model,
                exception
            );
        }
    }

    /**
     * 기존 레코드 재사용 여부와 임베딩 대상 Chunk를 결정합니다.
     */
    private IndexPlan createIndexPlan(
        List<DocumentChunk> chunks,
        RagIndexRequest request,
        String model,
        ExistingRecordIndex existingIndex,
        RagIndexState state
    ) throws RagIndexException {

        List<DocumentChunk> chunksToEmbed =
            new ArrayList<>();

        Map<String, String> contentHashes =
            new LinkedHashMap<>();

        Set<String> currentChunkIds =
            new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            currentChunkIds.add(
                chunk.getId()
            );

            String contentHash;

            try {
                contentHash =
                    hashService.hashChunk(chunk);

            } catch (HashException exception) {
                handleChunkFailure(
                    request,
                    state,
                    chunk,
                    "Failed to create chunk content hash",
                    RagIndexOperation.CREATE_HASH,
                    model,
                    exception
                );

                continue;
            }

            Optional<VectorRecord> existing =
                existingIndex.find(
                    chunk.getId()
                );

            /*
             * 수정 사항:
             * 실행 초기에 확정한 model 값을 canReuse()에 전달합니다.
             */
            if (canReuse(
                request,
                chunk,
                contentHash,
                existing,
                model
            )) {
                state.reusedCount++;
                continue;
            }

            chunksToEmbed.add(chunk);

            contentHashes.put(
                chunk.getId(),
                contentHash
            );
        }

        return new IndexPlan(
            List.copyOf(chunksToEmbed),
            Map.copyOf(contentHashes),
            Set.copyOf(currentChunkIds)
        );
    }

    /**
     * 변경되지 않은 기존 레코드를 재사용할 수 있는지 판단합니다.
     *
     * @param request 인덱싱 요청
     * @param chunk 현재 Chunk
     * @param contentHash 현재 Chunk 콘텐츠 해시
     * @param existing 기존 벡터 레코드
     * @param model 현재 인덱싱 실행에서 확정된 임베딩 모델명
     * @return 재사용 가능 여부
     */
    private boolean canReuse(
        RagIndexRequest request,
        DocumentChunk chunk,
        String contentHash,
        Optional<VectorRecord> existing,
        String model
    ) {
        if (request.isForceReindex()
            || !request.isReuseUnchanged()
            || existing.isEmpty()) {

            return false;
        }

        VectorRecord record =
            existing.get();

        /*
         * Provider를 다시 호출하지 않고 현재 실행에서 확정한
         * model 값을 사용합니다.
         */
        if (!record.isModel(model)) {
            return false;
        }

        if (!record.hasContentHash()) {
            return false;
        }

        if (!hashService.matches(
            contentHash,
            record.getContentHash()
        )) {
            return false;
        }

        return chunk.getId().equals(
            record.getChunk().getId()
        );
    }

    private void embedAndSave(
        DocumentSource source,
        List<DocumentChunk> chunks,
        Map<String, String> contentHashes,
        RagIndexRequest request,
        String model,
        RagIndexState state
    ) throws RagIndexException {

        if (chunks.isEmpty()) {
            return;
        }

        int batchSize =
            request.getBatchSize();

        for (int start = 0;
             start < chunks.size();
             start += batchSize) {

            int end = Math.min(
                start + batchSize,
                chunks.size()
            );

            List<DocumentChunk> batch =
                chunks.subList(start, end);

            try {
                processBatch(
                    source,
                    batch,
                    contentHashes,
                    request,
                    model,
                    state
                );

            } catch (RagIndexException exception) {
                if (!request.isContinueOnError()) {
                    throw exception;
                }

                processBatchIndividually(
                    source,
                    batch,
                    contentHashes,
                    request,
                    model,
                    state,
                    exception
                );
            }
        }
    }

    private void processBatch(
        DocumentSource source,
        List<DocumentChunk> batch,
        Map<String, String> contentHashes,
        RagIndexRequest request,
        String model,
        RagIndexState state
    ) throws RagIndexException {

        List<String> inputs =
            new ArrayList<>(batch.size());

        for (DocumentChunk chunk : batch) {
            inputs.add(
                chunk.toEmbeddingText()
            );
        }

        EmbeddingResponse embeddingResponse =
            requestEmbeddings(
                source.getRelativePath(),
                batch,
                inputs,
                request,
                model
            );

        List<float[]> vectors =
            embeddingResponse.getEmbeddings();

        if (vectors.size() != batch.size()) {
            throw new RagIndexException(
                "Embedding count mismatch. expected="
                    + batch.size()
                    + ", actual="
                    + vectors.size(),
                RagIndexOperation.EMBED,
                source.getRelativePath(),
                "",
                model,
                null
            );
        }

        List<VectorRecord> records =
            new ArrayList<>(batch.size());

        for (int index = 0;
             index < batch.size();
             index++) {

            DocumentChunk chunk =
                batch.get(index);

            float[] vector =
                vectors.get(index);

            String contentHash =
                contentHashes.get(
                    chunk.getId()
                );

            if (contentHash == null
                || contentHash.isBlank()) {

                throw new RagIndexException(
                    "Chunk content hash is missing",
                    RagIndexOperation.CREATE_RECORD,
                    source.getRelativePath(),
                    chunk.getId(),
                    model,
                    null
                );
            }

            try {
                records.add(
                    createVectorRecord(
                        source,
                        chunk,
                        vector,
                        contentHash,
                        embeddingResponse,
                        request,
                        model
                    )
                );

            } catch (RuntimeException exception) {
                throw new RagIndexException(
                    "Failed to create vector record",
                    RagIndexOperation.CREATE_RECORD,
                    source.getRelativePath(),
                    chunk.getId(),
                    model,
                    exception
                );
            }
        }

        saveRecords(
            source.getRelativePath(),
            records,
            model
        );

        for (VectorRecord record : records) {
            state.indexedCount++;

            state.indexedRecordIds.add(
                record.getId()
            );
        }
    }

    private void processBatchIndividually(
        DocumentSource source,
        List<DocumentChunk> batch,
        Map<String, String> contentHashes,
        RagIndexRequest request,
        String model,
        RagIndexState state,
        RagIndexException batchException
    ) throws RagIndexException {

        state.issues.add(
            RagIndexIssue.warning(
                "Batch embedding failed; retrying chunks individually: "
                    + safeMessage(batchException)
            )
        );

        for (DocumentChunk chunk : batch) {
            try {
                processBatch(
                    source,
                    List.of(chunk),
                    contentHashes,
                    request,
                    model,
                    state
                );

            } catch (RagIndexException exception) {
                handleChunkFailure(
                    request,
                    state,
                    chunk,
                    safeMessage(exception),
                    exception.getOperation(),
                    model,
                    exception
                );
            }
        }
    }

    private EmbeddingResponse requestEmbeddings(
        String sourcePath,
        List<DocumentChunk> batch,
        List<String> inputs,
        RagIndexRequest request,
        String model
    ) throws RagIndexException {

        String requestId =
            createEmbeddingRequestId(
                sourcePath,
                batch
            );

        EmbeddingRequest embeddingRequest =
            EmbeddingRequest.builder()
                .model(model)
                .inputs(inputs)
                .purpose(
                    EmbeddingPurpose.DOCUMENT
                )
                .normalize(
                    request.isNormalize()
                )
                .truncate(
                    request.isTruncate()
                )
                .requestId(requestId)
                .build();

        try {
            EmbeddingResponse response =
                embeddingClient.embed(
                    embeddingRequest
                );

            embeddingClient.validateResponse(
                embeddingRequest,
                response
            );

            return response;

        } catch (EmbeddingException exception) {
            throw new RagIndexException(
                "Failed to create document embeddings",
                RagIndexOperation.EMBED,
                sourcePath,
                batch.size() == 1
                    ? batch.get(0).getId()
                    : "",
                model,
                exception
            );

        } catch (RuntimeException exception) {
            throw new RagIndexException(
                "Unexpected error while creating embeddings",
                RagIndexOperation.EMBED,
                sourcePath,
                batch.size() == 1
                    ? batch.get(0).getId()
                    : "",
                model,
                exception
            );
        }
    }

    private VectorRecord createVectorRecord(
        DocumentSource source,
        DocumentChunk chunk,
        float[] vector,
        String contentHash,
        EmbeddingResponse embeddingResponse,
        RagIndexRequest request,
        String model
    ) {
        return VectorRecord.builder()
            .chunk(chunk)
            .vector(vector)
            .model(model)
            .contentHash(contentHash)
            .sourceHash(
                resolveSourceHash(source)
            )
            .normalized(
                embeddingResponse.isNormalized()
            )
            .indexedAt(
                System.currentTimeMillis()
            )
            .version(
                request.getVersion()
            )
            .build();
    }

    private void saveRecords(
        String sourcePath,
        List<VectorRecord> records,
        String model
    ) throws RagIndexException {

        try {
            vectorStore.saveAll(records);

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to save vector records",
                RagIndexOperation.SAVE,
                sourcePath,
                "",
                model,
                exception
            );

        } catch (RuntimeException exception) {
            throw new RagIndexException(
                "Unexpected error while saving vector records",
                RagIndexOperation.SAVE,
                sourcePath,
                "",
                model,
                exception
            );
        }
    }

    private ExistingRecordIndex loadExistingRecords(
        String sourcePath,
        String model
    ) throws RagIndexException {

        try {
            List<VectorRecord> sourceRecords =
                vectorStore.findBySourcePath(
                    sourcePath
                );

            Map<String, VectorRecord> indexed =
                new HashMap<>();

            for (VectorRecord record : sourceRecords) {
                if (record == null
                    || !record.isModel(model)) {

                    continue;
                }

                indexed.put(
                    record.getId(),
                    record
                );
            }

            return new ExistingRecordIndex(
                indexed
            );

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to load existing vector records",
                RagIndexOperation.CHECK_EXISTING,
                sourcePath,
                "",
                model,
                exception
            );
        }
    }

    private int deleteStaleRecords(
        ExistingRecordIndex existingIndex,
        Set<String> currentChunkIds,
        String model
    ) throws RagIndexException {

        int deletedCount = 0;

        for (VectorRecord existing
            : existingIndex.records.values()) {

            if (currentChunkIds.contains(
                existing.getId()
            )) {
                continue;
            }

            try {
                if (vectorStore.delete(
                    existing.getId(),
                    model
                )) {
                    deletedCount++;
                }

            } catch (VectorStoreException exception) {
                throw new RagIndexException(
                    "Failed to delete stale vector record",
                    RagIndexOperation.DELETE,
                    existing.getChunk()
                        .getSourcePath(),
                    existing.getId(),
                    model,
                    exception
                );
            }
        }

        return deletedCount;
    }

    private int deleteExistingSource(
        String sourcePath,
        String model
    ) throws RagIndexException {

        try {
            return vectorStore.deleteBySourcePath(
                sourcePath,
                model
            );

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to remove existing source index",
                RagIndexOperation.DELETE,
                sourcePath,
                "",
                model,
                exception
            );
        }
    }

    @Override
    public int remove(
        String sourcePath
    ) throws RagIndexException {

        String normalizedPath =
            requireText(
                sourcePath,
                "sourcePath"
            );

        try {
            return vectorStore.deleteBySourcePath(
                normalizedPath
            );

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to remove source index",
                RagIndexOperation.DELETE,
                normalizedPath,
                "",
                "",
                exception
            );
        }
    }

    @Override
    public int remove(
        String sourcePath,
        String model
    ) throws RagIndexException {

        String normalizedPath =
            requireText(
                sourcePath,
                "sourcePath"
            );

        String normalizedModel =
            requireText(
                model,
                "model"
            );

        try {
            return vectorStore.deleteBySourcePath(
                normalizedPath,
                normalizedModel
            );

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to remove source index for model",
                RagIndexOperation.DELETE,
                normalizedPath,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public int removeCurrentModel()
        throws RagIndexException {

        String model =
            resolveEmbeddingModel("");

        try {
            return vectorStore.deleteByModel(
                model
            );

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to remove current model index",
                RagIndexOperation.DELETE,
                "",
                "",
                model,
                exception
            );
        }
    }

    @Override
    public void clear()
        throws RagIndexException {

        try {
            vectorStore.clear();

        } catch (VectorStoreException exception) {
            throw new RagIndexException(
                "Failed to clear vector store",
                RagIndexOperation.CLEAR,
                "",
                "",
                "",
                exception
            );
        }
    }

    @Override
    public boolean supports(
        DocumentSource source
    ) {
        return source != null
            && documentIndexer.supports(source);
    }

    @Override
    public boolean isAvailable() {
        try {
            String model =
                embeddingModelProvider.getModel();

            return model != null
                && !model.isBlank()
                && hashService.isAvailable()
                && embeddingClient.isAvailable(model)
                && vectorStore.isAvailable();

        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void validateSource(
        DocumentSource source
    ) throws RagIndexException {

        if (source == null) {
            throw new RagIndexException(
                "DocumentSource must not be null",
                RagIndexOperation.VALIDATE,
                "",
                "",
                "",
                null
            );
        }

        if (!supports(source)) {
            throw new RagIndexException(
                "Unsupported document source type: "
                    + source.getType(),
                RagIndexOperation.VALIDATE,
                source.getRelativePath(),
                "",
                "",
                null
            );
        }

        if (!source.hasContent()) {
            throw new RagIndexException(
                "Document source content is empty",
                RagIndexOperation.VALIDATE,
                source.getRelativePath(),
                "",
                "",
                null
            );
        }
    }

    private void validateRequest(
        RagIndexRequest request
    ) throws RagIndexException {

        if (request == null) {
            throw new RagIndexException(
                "RagIndexRequest must not be null",
                RagIndexOperation.VALIDATE,
                "",
                "",
                "",
                null
            );
        }

        if (request.getBatchSize() < 1) {
            throw new RagIndexException(
                "batchSize must be greater than zero",
                RagIndexOperation.VALIDATE,
                "",
                "",
                "",
                null
            );
        }

        if (request.getVersion() < 1L) {
            throw new RagIndexException(
                "version must be greater than zero",
                RagIndexOperation.VALIDATE,
                "",
                "",
                "",
                null
            );
        }
    }

    private void validateChunks(
        DocumentSource source,
        List<DocumentChunk> chunks
    ) throws RagIndexException {

        if (chunks == null) {
            throw new RagIndexException(
                "chunks must not be null",
                RagIndexOperation.VALIDATE,
                source.getRelativePath(),
                "",
                "",
                null
            );
        }
    }

    private List<DocumentChunk> sanitizeChunks(
        DocumentSource source,
        List<DocumentChunk> chunks
    ) throws RagIndexException {

        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> sanitized =
            new ArrayList<>(chunks.size());

        Set<String> ids =
            new LinkedHashSet<>();

        for (DocumentChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }

            if (!source.getRelativePath().equals(
                chunk.getSourcePath()
            )) {
                throw new RagIndexException(
                    "Chunk sourcePath does not match DocumentSource. "
                        + "expected="
                        + source.getRelativePath()
                        + ", actual="
                        + chunk.getSourcePath(),
                    RagIndexOperation.VALIDATE,
                    source.getRelativePath(),
                    chunk.getId(),
                    "",
                    null
                );
            }

            if (!ids.add(chunk.getId())) {
                throw new RagIndexException(
                    "Duplicate chunk id: "
                        + chunk.getId(),
                    RagIndexOperation.VALIDATE,
                    source.getRelativePath(),
                    chunk.getId(),
                    "",
                    null
                );
            }

            sanitized.add(chunk);
        }

        return List.copyOf(sanitized);
    }

    private void handleChunkFailure(
        RagIndexRequest request,
        RagIndexState state,
        DocumentChunk chunk,
        String message,
        RagIndexOperation operation,
        String model,
        Throwable cause
    ) throws RagIndexException {

        if (!request.isContinueOnError()) {
            throw new RagIndexException(
                message,
                operation,
                state.sourcePath,
                chunk.getId(),
                model,
                cause
            );
        }

        state.failedCount++;
        state.success = false;

        state.issues.add(
            RagIndexIssue.error(
                message,
                chunk.getId()
            )
        );
    }

    private String resolveEmbeddingModel(
        String sourcePath
    ) throws RagIndexException {

        String model;

        try {
            model =
                embeddingModelProvider.getModel();

        } catch (RuntimeException exception) {
            throw new RagIndexException(
                "Failed to resolve embedding model",
                RagIndexOperation.VALIDATE,
                sourcePath,
                "",
                "",
                exception
            );
        }

        if (model == null || model.isBlank()) {
            throw new RagIndexException(
                "Embedding model must not be blank",
                RagIndexOperation.VALIDATE,
                sourcePath,
                "",
                "",
                null
            );
        }

        return model.trim();
    }

    private String resolveSourceHash(
        DocumentSource source
    ) {
        if (source.hasContentHash()) {
            return source.getContentHash();
        }

        try {
            return hashService.hash(
                source.getContent(),
                source.getCharset()
            );

        } catch (HashException exception) {
            return "";
        }
    }

    private String createEmbeddingRequestId(
        String sourcePath,
        List<DocumentChunk> batch
    ) {
        String firstChunkId =
            batch.isEmpty()
                ? "empty"
                : batch.get(0).getId();

        return "rag-index:"
            + sourcePath
            + ":"
            + firstChunkId
            + ":"
            + batch.size()
            + ":"
            + System.nanoTime();
    }

    private static String safeMessage(
        Throwable throwable
    ) {
        if (throwable == null) {
            return "Unknown indexing error";
        }

        String message =
            throwable.getMessage();

        if (message == null
            || message.isBlank()) {

            return throwable
                .getClass()
                .getSimpleName();
        }

        return message.trim();
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        String normalized =
            value == null
                ? ""
                : value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    public DocumentIndexer getDocumentIndexer() {
        return documentIndexer;
    }

    public EmbeddingClient getEmbeddingClient() {
        return embeddingClient;
    }

    public EmbeddingModelProvider
        getEmbeddingModelProvider() {

        return embeddingModelProvider;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public HashService getHashService() {
        return hashService;
    }

    @Override
    public String toString() {
        return "DefaultRagIndexer{" +
            "documentIndexer="
                + documentIndexer.getClass().getSimpleName() +
            ", embeddingClient="
                + embeddingClient.getClass().getSimpleName() +
            ", embeddingModelProvider="
                + embeddingModelProvider.getClass().getSimpleName() +
            ", vectorStore="
                + vectorStore.getClass().getSimpleName() +
            ", hashService="
                + hashService.getClass().getSimpleName() +
            '}';
    }

    private static final class RagIndexState {

        private final String sourcePath;
        private final String model;
        private final int totalChunkCount;

        private int indexedCount;
        private int reusedCount;
        private int skippedCount;
        private int deletedCount;
        private int failedCount;
        private long durationNanos;
        private boolean success = true;

        private final List<String> indexedRecordIds =
            new ArrayList<>();

        private final List<RagIndexIssue> issues =
            new ArrayList<>();

        private RagIndexState(
            String sourcePath,
            String model,
            int totalChunkCount
        ) {
            this.sourcePath = sourcePath;
            this.model = model;
            this.totalChunkCount =
                totalChunkCount;
        }

        private RagIndexResult toResult() {
            return RagIndexResult.builder()
                .sourcePath(sourcePath)
                .model(model)
                .totalChunkCount(
                    totalChunkCount
                )
                .indexedCount(
                    indexedCount
                )
                .reusedCount(
                    reusedCount
                )
                .skippedCount(
                    skippedCount
                )
                .deletedCount(
                    deletedCount
                )
                .failedCount(
                    failedCount
                )
                .durationNanos(
                    durationNanos
                )
                .success(success)
                .indexedRecordIds(
                    indexedRecordIds
                )
                .issues(issues)
                .build();
        }
    }

    private static final class ExistingRecordIndex {

        private final Map<String, VectorRecord> records;

        private ExistingRecordIndex(
            Map<String, VectorRecord> records
        ) {
            this.records =
                Map.copyOf(records);
        }

        private static ExistingRecordIndex empty() {
            return new ExistingRecordIndex(
                Map.of()
            );
        }

        private Optional<VectorRecord> find(
            String id
        ) {
            return Optional.ofNullable(
                records.get(id)
            );
        }
    }

    private static final class IndexPlan {

        private final List<DocumentChunk> chunksToEmbed;
        private final Map<String, String> contentHashes;
        private final Set<String> currentChunkIds;

        private IndexPlan(
            List<DocumentChunk> chunksToEmbed,
            Map<String, String> contentHashes,
            Set<String> currentChunkIds
        ) {
            this.chunksToEmbed =
                chunksToEmbed;

            this.contentHashes =
                contentHashes;

            this.currentChunkIds =
                currentChunkIds;
        }
    }
}