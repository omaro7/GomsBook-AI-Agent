/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 메모리 기반 {@link VectorStore} 구현체입니다.
 *
 * <p>모든 벡터 레코드를 메모리에 저장하고 검색 시 전체 레코드를
 * 순차적으로 비교합니다.</p>
 *
 * <p>EPUB 프로젝트 단위의 소규모 RAG 인덱스, 기능 검증,
 * 단위 테스트에 적합합니다.</p>
 */
public final class InMemoryVectorStore
    implements VectorStore {

    /**
     * Project ID, Chunk ID, Embedding Model을
     * 결합한 키로 레코드를 저장합니다.
     */
    private final Map<RecordKey, VectorRecord> records =
        new ConcurrentHashMap<>();

    /**
     * 저장소 종료 상태입니다.
     */
    private final AtomicBoolean closed =
        new AtomicBoolean(false);

    @Override
    public void save(
        VectorRecord record
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.SAVE);

        if (record == null) {
            throw new IllegalArgumentException(
                "record must not be null"
            );
        }

        try {
            RecordKey key = RecordKey.of(
            	record.getProjectId(),
                record.getId(),
                record.getModel()
            );

            records.put(key, record);

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to save vector record",
                VectorStoreOperation.SAVE,
                record.getId(),
                record.getModel(),
                exception
            );
        }
    }

    @Override
    public void saveAll(
        List<VectorRecord> records
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.SAVE_ALL);

        if (records == null || records.isEmpty()) {
            return;
        }

        try {
            for (VectorRecord record : records) {
                if (record == null) {
                    continue;
                }

                RecordKey key = RecordKey.of(
                	record.getProjectId(),
                    record.getId(),
                    record.getModel()
                );

                this.records.put(key, record);
            }

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to save vector records",
                VectorStoreOperation.SAVE_ALL,
                exception
            );
        }
    }

    @Override
    public List<VectorSearchResult> search(
        VectorSearchRequest request
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.SEARCH);

        if (request == null) {
            throw new IllegalArgumentException(
                "request must not be null"
            );
        }

        try {
            float[] queryVector =
                request.getQueryVector();

            List<VectorSearchResult> candidates =
                new ArrayList<>();

            for (VectorRecord record : records.values()) {
                if (!request.matches(record)) {
                    continue;
                }

                double score = calculateSearchScore(
                    queryVector,
                    record,
                    request.getSimilarityType()
                );

                boolean accepted =
                    request.accepts(score);

                if (!accepted
                    && !request.isIncludeRejected()) {

                    continue;
                }

                candidates.add(
                    VectorSearchResult.builder()
                        .record(record)
                        .score(score)
                        .similarityType(
                            request.getSimilarityType()
                        )
                        .accepted(accepted)
                        .build()
                );
            }

            candidates.sort(
                Comparator
                    .comparingDouble(
                        VectorSearchResult::getScore
                    )
                    .reversed()
                    .thenComparing(
                        VectorSearchResult::getId
                    )
            );

            int resultCount = Math.min(
                request.getTopK(),
                candidates.size()
            );

            List<VectorSearchResult> results =
                new ArrayList<>(resultCount);

            for (int index = 0;
                 index < resultCount;
                 index++) {

                results.add(
                    candidates
                        .get(index)
                        .withRank(index + 1)
                );
            }

            return List.copyOf(results);

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to search vector records",
                VectorStoreOperation.SEARCH,
                exception
            );
        }
    }

    /**
     * 검색 방식과 정규화 여부를 고려해 점수를 계산합니다.
     */
    private double calculateSearchScore(
        float[] queryVector,
        VectorRecord record,
        VectorSimilarityType similarityType
    ) {
        float[] recordVector =
            record.getVector();

        VectorSimilarityType resolvedType =
            Objects.requireNonNullElse(
                similarityType,
                VectorSimilarityType.COSINE
            );

        /*
         * 두 벡터가 모두 L2 정규화된 경우 내적으로
         * 코사인 유사도를 빠르게 계산할 수 있습니다.
         *
         * 현재 EmbeddingRequest.normalize=true를 사용하므로
         * queryVector도 정규화되었다고 가정할 수 있습니다.
         */
        if (resolvedType == VectorSimilarityType.COSINE
            && record.isNormalized()
            && VectorSimilarity.isNormalized(
                queryVector,
                1.0e-4
            )) {

            return VectorSimilarity.cosineNormalized(
                queryVector,
                recordVector
            );
        }

        return VectorSimilarity.score(
            queryVector,
            recordVector,
            resolvedType
        );
    }

    @Override
    public Optional<VectorRecord> findById(
        String id,
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedId =
            requireText(id, "id");

        String normalizedModel =
            requireText(model, "model");

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.getId().equals(normalizedId)
                )
                .filter(record ->
                    record.isModel(normalizedModel)
                )
                .max(
                    Comparator.comparingLong(
                        VectorRecord::getIndexedAt
                    )
                );

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector record",
                VectorStoreOperation.FIND,
                normalizedId,
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public Optional<VectorRecord> findById(
        String id
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedId =
            requireText(id, "id");

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.getId().equals(normalizedId)
                )
                .max(
                    Comparator.comparingLong(
                        VectorRecord::getIndexedAt
                    )
                );

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector record by id",
                VectorStoreOperation.FIND,
                normalizedId,
                "",
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findByIds(
        List<String> ids
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        try {
            java.util.Set<String> normalizedIds =
                new java.util.LinkedHashSet<>();

            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    normalizedIds.add(id.trim());
                }
            }

            if (normalizedIds.isEmpty()) {
                return List.of();
            }

            return records.values()
                .stream()
                .filter(record ->
                    normalizedIds.contains(
                        record.getId()
                    )
                )
                .sorted(
                    Comparator
                        .comparing(
                            VectorRecord::getId
                        )
                        .thenComparing(
                            VectorRecord::getModel
                        )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector records by ids",
                VectorStoreOperation.FIND,
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findAll()
        throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        try {
            return records.values()
                .stream()
                .sorted(
                    Comparator
                        .comparing(
                            VectorRecord::getId
                        )
                        .thenComparing(
                            VectorRecord::getModel
                        )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find all vector records",
                VectorStoreOperation.FIND,
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findByModel(
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedModel =
            requireText(model, "model");

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.isModel(normalizedModel)
                )
                .sorted(
                    Comparator.comparing(
                        VectorRecord::getId
                    )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector records by model",
                VectorStoreOperation.FIND,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findByProjectAndModel(
        String projectId,
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedProjectId =
            requireText(projectId, "projectId");

        String normalizedModel =
            requireText(model, "model");

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.isProject(normalizedProjectId)
                )
                .filter(record ->
                    record.isModel(normalizedModel)
                )
                .sorted(
                    Comparator.comparing(
                        VectorRecord::getId
                    )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector records by project and model",
                VectorStoreOperation.FIND,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findByProjectAndSourcePath(
        String projectId,
        String sourcePath
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedProjectId =
            requireText(projectId, "projectId");

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.isProject(normalizedProjectId)
                )
                .filter(record ->
                    normalizePath(
                        record.getChunk()
                            .getSourcePath()
                    ).equals(normalizedPath)
                )
                .sorted(
                    Comparator
                        .comparingInt(
                            (VectorRecord record) ->
                                record.getChunk()
                                    .getSequence()
                        )
                        .thenComparing(
                            VectorRecord::getId
                        )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector records by project and source path",
                VectorStoreOperation.FIND,
                exception
            );
        }
    }

    @Override
    public List<VectorRecord> findBySourcePath(
        String sourcePath
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.FIND);

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        try {
            return records.values()
                .stream()
                .filter(record ->
                    normalizePath(
                        record.getChunk()
                            .getSourcePath()
                    ).equals(normalizedPath)
                )
                .sorted(
                    Comparator
                        .comparingInt(
                            (VectorRecord record) ->
                                record.getChunk()
                                    .getSequence()
                        )
                        .thenComparing(
                            VectorRecord::getId
                        )
                )
                .toList();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to find vector records by source path",
                VectorStoreOperation.FIND,
                exception
            );
        }
    }

    @Override
    public boolean delete(
        String id,
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.DELETE);

        String normalizedId =
            requireText(id, "id");

        String normalizedModel =
            requireText(model, "model");

        try {
            boolean deleted = false;

            for (Map.Entry<RecordKey, VectorRecord> entry
                : records.entrySet()) {

                VectorRecord record =
                    entry.getValue();

                if (!record.getId().equals(normalizedId)) {
                    continue;
                }

                if (!record.isModel(normalizedModel)) {
                    continue;
                }

                if (records.remove(
                    entry.getKey(),
                    record
                )) {
                    deleted = true;
                }
            }

            return deleted;

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to delete vector record",
                VectorStoreOperation.DELETE,
                normalizedId,
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public int deleteBySourcePath(
        String sourcePath
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.DELETE);

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        try {
            int deletedCount = 0;

            for (Map.Entry<RecordKey, VectorRecord> entry
                : records.entrySet()) {

                VectorRecord record =
                    entry.getValue();

                String recordPath =
                    normalizePath(
                        record.getChunk().getSourcePath()
                    );

                if (!recordPath.equals(normalizedPath)) {
                    continue;
                }

                if (records.remove(
                    entry.getKey(),
                    record
                )) {
                    deletedCount++;
                }
            }

            return deletedCount;

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to delete vector records by source path",
                VectorStoreOperation.DELETE,
                exception
            );
        }
    }

    @Override
    public int deleteBySourcePath(
        String sourcePath,
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.DELETE);

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        String normalizedModel =
            requireText(model, "model");

        try {
            int deletedCount = 0;

            for (Map.Entry<RecordKey, VectorRecord> entry
                : records.entrySet()) {

                VectorRecord record =
                    entry.getValue();

                if (!record.isModel(normalizedModel)) {
                    continue;
                }

                String recordPath =
                    normalizePath(
                        record.getChunk().getSourcePath()
                    );

                if (!recordPath.equals(normalizedPath)) {
                    continue;
                }

                if (records.remove(
                    entry.getKey(),
                    record
                )) {
                    deletedCount++;
                }
            }

            return deletedCount;

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to delete vector records by source path and model",
                VectorStoreOperation.DELETE,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public int deleteByProjectAndSourcePath(
        String projectId,
        String sourcePath,
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.DELETE);

        String normalizedProjectId =
            requireText(projectId, "projectId");

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        String normalizedModel =
            requireText(model, "model");

        try {
            int deletedCount = 0;

            for (Map.Entry<RecordKey, VectorRecord> entry
                : records.entrySet()) {

                VectorRecord record =
                    entry.getValue();

                if (!record.isProject(
                    normalizedProjectId
                )) {
                    continue;
                }

                if (!record.isModel(
                    normalizedModel
                )) {
                    continue;
                }

                String recordPath =
                    normalizePath(
                        record.getChunk()
                            .getSourcePath()
                    );

                if (!recordPath.equals(
                    normalizedPath
                )) {
                    continue;
                }

                if (records.remove(
                    entry.getKey(),
                    record
                )) {
                    deletedCount++;
                }
            }

            return deletedCount;

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to delete vector records by project and source path",
                VectorStoreOperation.DELETE,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public int deleteByModel(
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.DELETE);

        String normalizedModel =
            requireText(model, "model");

        try {
            int deletedCount = 0;

            for (Map.Entry<RecordKey, VectorRecord> entry
                : records.entrySet()) {

                VectorRecord record =
                    entry.getValue();

                if (!record.isModel(normalizedModel)) {
                    continue;
                }

                if (records.remove(
                    entry.getKey(),
                    record
                )) {
                    deletedCount++;
                }
            }

            return deletedCount;

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to delete vector records by model",
                VectorStoreOperation.DELETE,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public void clear()
        throws VectorStoreException {

        ensureOpen(VectorStoreOperation.CLEAR);

        try {
            records.clear();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to clear vector store",
                VectorStoreOperation.CLEAR,
                exception
            );
        }
    }

    @Override
    public long count()
        throws VectorStoreException {

        ensureOpen(VectorStoreOperation.COUNT);

        return records.size();
    }

    @Override
    public long countByModel(
        String model
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.COUNT);

        String normalizedModel =
            requireText(model, "model");

        try {
            return records.values()
                .stream()
                .filter(record ->
                    record.isModel(normalizedModel)
                )
                .count();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to count vector records by model",
                VectorStoreOperation.COUNT,
                "",
                normalizedModel,
                exception
            );
        }
    }

    @Override
    public long countBySourcePath(
        String sourcePath
    ) throws VectorStoreException {

        ensureOpen(VectorStoreOperation.COUNT);

        String normalizedPath =
            normalizePath(
                requireText(
                    sourcePath,
                    "sourcePath"
                )
            );

        try {
            return records.values()
                .stream()
                .filter(record ->
                    normalizePath(
                        record.getChunk().getSourcePath()
                    ).equals(normalizedPath)
                )
                .count();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to count vector records by source path",
                VectorStoreOperation.COUNT,
                exception
            );
        }
    }

    @Override
    public boolean isAvailable() {
        return !closed.get();
    }

    @Override
    public void close()
        throws VectorStoreException {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            records.clear();

        } catch (RuntimeException exception) {
            throw new VectorStoreException(
                "Failed to close in-memory vector store",
                VectorStoreOperation.CLOSE,
                exception
            );
        }
    }

    private void ensureOpen(
        VectorStoreOperation operation
    ) throws VectorStoreException {

        if (closed.get()) {
            throw new VectorStoreException(
                "Vector store is already closed",
                operation
            );
        }
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

    private static String normalizePath(
        String path
    ) {
        return path == null
            ? ""
            : path
                .trim()
                .replace('\\', '/');
    }

    /**
     * 서로 다른 EPUB 프로젝트에서 동일한 Chunk ID를
     * 사용할 수 있도록 Project ID, Chunk ID, Embedding Model을
     * 복합 키로 사용합니다.
     */
    private static final class RecordKey {

        private final String projectId;
        private final String id;
        private final String model;

        private RecordKey(
            String projectId,
            String id,
            String model
        ) {
            this.projectId = requireText(projectId,"projectId");
            this.id = requireText(id, "id");
            this.model = requireText(
                model,
                "model"
            );
        }

        private static RecordKey of(
        	String projectId,
            String id,
            String model
        ) {
            return new RecordKey(projectId, id, model);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof RecordKey)) {
                return false;
            }

            RecordKey other =
                    (RecordKey) object;

            return projectId.equals(
                    other.projectId
                )
                && id.equals(
                        other.id
                )
                && model.equals(
                        other.model
                );
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    projectId,
                    id,
                    model
            );
        }

        @Override
        public String toString() {
            return projectId
                + ':'
                + id
                + '@'
                + model;
        }
    }
}