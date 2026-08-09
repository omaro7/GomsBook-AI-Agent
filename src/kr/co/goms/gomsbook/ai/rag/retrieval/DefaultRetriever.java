/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingException;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingModelProvider;
import kr.co.goms.gomsbook.ai.rag.vector.VectorSearchRequest;
import kr.co.goms.gomsbook.ai.rag.vector.VectorSearchResult;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStoreException;

/**
 * 기본 {@link Retriever} 구현체입니다.
 *
 * <p>사용자 질의를 임베딩하고 VectorStore를 검색한 후,
 * 검색 결과를 {@link RetrievalResult}로 변환합니다.</p>
 *
 * <pre>
 * query
 *   ↓
 * EmbeddingClient.embedQuery()
 *   ↓
 * VectorSearchRequest
 *   ↓
 * VectorStore.search()
 *   ↓
 * RetrievalResult
 * </pre>
 */
public final class DefaultRetriever implements Retriever {

    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final VectorStore vectorStore;

    /**
     * 기본 검색 결과 개수입니다.
     */
    private final int defaultTopK;

    /**
     * 기본 최소 유사도 점수입니다.
     */
    private final double defaultMinimumScore;

    /**
     * 기본 Retriever를 생성합니다.
     *
     * @param embeddingClient 질의 임베딩 클라이언트
     * @param embeddingModelProvider 임베딩 모델 제공자
     * @param vectorStore 벡터 저장소
     */
    public DefaultRetriever(
        EmbeddingClient embeddingClient,
        EmbeddingModelProvider embeddingModelProvider,
        VectorStore vectorStore
    ) {
        this(
            embeddingClient,
            embeddingModelProvider,
            vectorStore,
            RetrievalRequest.DEFAULT_TOP_K,
            RetrievalRequest.DEFAULT_MINIMUM_SCORE
        );
    }

    /**
     * 기본 검색 설정을 지정하여 Retriever를 생성합니다.
     *
     * @param embeddingClient 질의 임베딩 클라이언트
     * @param embeddingModelProvider 임베딩 모델 제공자
     * @param vectorStore 벡터 저장소
     * @param defaultTopK 기본 검색 결과 개수
     * @param defaultMinimumScore 기본 최소 유사도 점수
     */
    public DefaultRetriever(
        EmbeddingClient embeddingClient,
        EmbeddingModelProvider embeddingModelProvider,
        VectorStore vectorStore,
        int defaultTopK,
        double defaultMinimumScore
    ) {
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

        if (defaultTopK < 1) {
            throw new IllegalArgumentException(
                "defaultTopK must be greater than zero"
            );
        }

        if (!Double.isFinite(defaultMinimumScore)
            || defaultMinimumScore < -1.0
            || defaultMinimumScore > 1.0) {

            throw new IllegalArgumentException(
                "defaultMinimumScore must be between -1.0 and 1.0"
            );
        }

        this.defaultTopK = defaultTopK;
        this.defaultMinimumScore = defaultMinimumScore;
    }

    /**
     * 기본 검색 설정으로 관련 문서를 검색합니다.
     */
    @Override
    public RetrievalResult retrieve(
        String query
    ) throws RetrievalException {

        String normalizedQuery = validateQuery(query);

        RetrievalRequest request =
            RetrievalRequest.builder()
                .query(normalizedQuery)
                .topK(defaultTopK)
                .minimumScore(defaultMinimumScore)
                .build();

        return retrieve(request);
    }

    /**
     * 지정된 검색 조건으로 관련 문서를 검색합니다.
     */
    @Override
    public RetrievalResult retrieve(
        RetrievalRequest request
    ) throws RetrievalException {

        validateRequest(request);

        String query = request.getQuery();
        String model = resolveEmbeddingModel();

        long startedAt = System.nanoTime();

        try {
            float[] queryVector =
                embedQuery(
                    query,
                    model
                );

            VectorSearchRequest searchRequest =
                createVectorSearchRequest(
                    request,
                    queryVector,
                    model
                );

            List<VectorSearchResult> searchResults =
                searchVectorStore(
                    query,
                    model,
                    searchRequest
                );

            List<VectorSearchResult> finalResults =
                prepareResults(
                    searchResults,
                    request
                );

            return RetrievalResult.builder()
                .query(query)
                .model(model)
                .searchResults(finalResults)
                .durationNanos(
                    System.nanoTime() - startedAt
                )
                .dimensions(queryVector.length)
                .build();

        } catch (RetrievalException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                "Unexpected error while retrieving documents",
                query,
                model,
                RetrievalOperation.RESULT_MAPPING,
                exception
            );
        }
    }

    /**
     * 사용자 질의를 임베딩합니다.
     */
    private float[] embedQuery(
        String query,
        String model
    ) throws RetrievalException {

        try {
            float[] queryVector =
                embeddingClient.embedQuery(
                    model,
                    query
                );

            validateQueryVector(
                queryVector,
                query,
                model
            );

            return queryVector;

        } catch (EmbeddingException exception) {
            throw new RetrievalException(
                "Failed to embed retrieval query",
                query,
                model,
                RetrievalOperation.EMBED_QUERY,
                exception
            );

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                "Invalid query embedding result",
                query,
                model,
                RetrievalOperation.EMBED_QUERY,
                exception
            );
        }
    }

    /**
     * RetrievalRequest를 VectorSearchRequest로 변환합니다.
     */
    private VectorSearchRequest createVectorSearchRequest(
        RetrievalRequest request,
        float[] queryVector,
        String model
    ) throws RetrievalException {

        try {
            VectorSearchRequest.Builder builder =
                VectorSearchRequest.builder()
                    .queryVector(queryVector)
                    .model(model)
                    .topK(request.getTopK())
                    .minimumScore(
                        request.getMinimumScore()
                    )
                    .similarityType(
                        request.getSimilarityType()
                    )
                    .chunkTypes(
                        request.getChunkTypes()
                    )
                    .sourcePaths(
                        request.getSourcePaths()
                    )
                    .epubTypes(
                        request.getEpubTypes()
                    )
                    .languages(
                        request.getLanguages()
                    )
                    .metadataFilters(
                        request.getMetadataFilters()
                    )
                    .includeRejected(
                        request.isIncludeRejected()
                    );

            return builder.build();

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                "Failed to create vector search request",
                request.getQuery(),
                model,
                RetrievalOperation.VALIDATE,
                exception
            );
        }
    }

    /**
     * VectorStore 검색을 실행합니다.
     */
    private List<VectorSearchResult> searchVectorStore(
        String query,
        String model,
        VectorSearchRequest searchRequest
    ) throws RetrievalException {

        try {
            List<VectorSearchResult> results =
                vectorStore.search(searchRequest);

            if (results == null) {
                throw new RetrievalException(
                    "VectorStore returned null search results",
                    query,
                    model,
                    RetrievalOperation.VECTOR_SEARCH,
                    null
                );
            }

            return List.copyOf(results);

        } catch (VectorStoreException exception) {
            throw new RetrievalException(
                "Failed to search vector store",
                query,
                model,
                RetrievalOperation.VECTOR_SEARCH,
                exception
            );
        }
    }

    /**
     * 결과 순서와 rank를 최종 정리합니다.
     *
     * <p>preserveDocumentOrder가 false이면 VectorStore의 유사도 순서를
     * 유지합니다. true이면 원본 파일 경로와 문서 내 sequence를 기준으로
     * 재정렬합니다.</p>
     */
    private List<VectorSearchResult> prepareResults(
        List<VectorSearchResult> searchResults,
        RetrievalRequest request
    ) {
        if (searchResults == null
            || searchResults.isEmpty()) {

            return List.of();
        }

        List<VectorSearchResult> results =
            new ArrayList<>(searchResults.size());

        for (VectorSearchResult result : searchResults) {
            if (result != null) {
                results.add(result);
            }
        }

        if (request.isPreserveDocumentOrder()) {
        	results.sort(
    		    Comparator
    		        .comparing(
    		            (VectorSearchResult result) ->
    		                normalizePath(
    		                    result.getChunk()
    		                        .getSourcePath()
    		                )
    		        )
    		        .thenComparingInt(
    		            (VectorSearchResult result) ->
    		                result.getChunk()
    		                    .getSequence()
    		        )
    		        .thenComparing(
    		            VectorSearchResult::getId
    		        )
    		);
        } else {
            results.sort(
                Comparator
                    .comparingDouble(
                        VectorSearchResult::getScore
                    )
                    .reversed()
                    .thenComparing(
                        VectorSearchResult::getId
                    )
            );
        }

        List<VectorSearchResult> rankedResults =
            new ArrayList<>(results.size());

        for (int index = 0;
             index < results.size();
             index++) {

            rankedResults.add(
                results.get(index)
                    .withRank(index + 1)
            );
        }

        return List.copyOf(rankedResults);
    }

    /**
     * 요청을 검증합니다.
     */
    private void validateRequest(
        RetrievalRequest request
    ) throws RetrievalException {

        if (request == null) {
            throw new RetrievalException(
                "Retrieval request must not be null",
                "",
                "",
                RetrievalOperation.VALIDATE,
                null
            );
        }

        try {
            validateQuery(request.getQuery());

        } catch (IllegalArgumentException exception) {
            throw new RetrievalException(
                "Retrieval query is invalid",
                request.getQuery(),
                "",
                RetrievalOperation.VALIDATE,
                exception
            );
        }

        if (request.getTopK() < 1) {
            throw new RetrievalException(
                "Retrieval topK must be greater than zero",
                request.getQuery(),
                "",
                RetrievalOperation.VALIDATE,
                null
            );
        }

        if (!Double.isFinite(
            request.getMinimumScore()
        )) {
            throw new RetrievalException(
                "Retrieval minimumScore must be finite",
                request.getQuery(),
                "",
                RetrievalOperation.VALIDATE,
                null
            );
        }
    }

    /**
     * 현재 임베딩 모델명을 조회합니다.
     */
    private String resolveEmbeddingModel()
        throws RetrievalException {

        String model;

        try {
            model = embeddingModelProvider.getModel();

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                "Failed to resolve embedding model",
                "",
                "",
                RetrievalOperation.VALIDATE,
                exception
            );
        }

        if (model == null || model.isBlank()) {
            throw new RetrievalException(
                "Embedding model must not be blank",
                "",
                "",
                RetrievalOperation.VALIDATE,
                null
            );
        }

        return model.trim();
    }

    /**
     * 질의 벡터 값을 검증합니다.
     */
    private void validateQueryVector(
        float[] vector,
        String query,
        String model
    ) throws RetrievalException {

        if (vector == null || vector.length == 0) {
            throw new RetrievalException(
                "Query embedding vector must not be empty",
                query,
                model,
                RetrievalOperation.EMBED_QUERY,
                null
            );
        }

        for (int index = 0;
             index < vector.length;
             index++) {

            if (!Float.isFinite(vector[index])) {
                throw new RetrievalException(
                    "Query embedding contains invalid value at index "
                        + index,
                    query,
                    model,
                    RetrievalOperation.EMBED_QUERY,
                    null
                );
            }
        }
    }

    @Override
    public boolean isAvailable() {
        String model;

        try {
            model = embeddingModelProvider.getModel();

        } catch (RuntimeException exception) {
            return false;
        }

        if (model == null || model.isBlank()) {
            return false;
        }

        return embeddingClient.isAvailable(model)
            && vectorStore.isAvailable();
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

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public double getDefaultMinimumScore() {
        return defaultMinimumScore;
    }

    private static String normalizePath(
        String path
    ) {
        return path == null
            ? ""
            : path.trim().replace('\\', '/');
    }
}