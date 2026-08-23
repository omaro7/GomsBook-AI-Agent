/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingException;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingModelProvider;
import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.vector.VectorRecord;
import kr.co.goms.gomsbook.ai.rag.vector.VectorSearchRequest;
import kr.co.goms.gomsbook.ai.rag.vector.VectorSearchResult;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStoreException;
import kr.co.goms.gomsbook.ai.util.GomsStringUtil;

/**
 * 기본 {@link Retriever} 구현체입니다.
 *
 * <p>사용자 질의를 임베딩하고 VectorStore를 검색한 후,
 * Hybrid Rerank를 수행하여 최종 검색 결과를 생성합니다.</p>
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
 * Candidate Top-K
 *   ↓
 * Hybrid Rerank
 *   ↓
 * Score Threshold
 *   ↓
 * Final Top-K
 *   ↓
 * RetrievalResult
 * </pre>
 */
public final class DefaultRetriever implements Retriever {

    private static final int CANDIDATE_MULTIPLIER = 10;

    private static final double EXACT_HEADING_BOOST = 0.18;
    private static final double HEADING_KEYWORD_BOOST = 0.06;
    private static final double CONTENT_KEYWORD_BOOST = 0.04;
    private static final double HEADING_CONTENT_BOOST = 0.05;
    private static final double TITLE_PATTERN_BOOST = 0.10;

    private static final double MAX_HEADING_KEYWORD_BOOST = 0.18;
    private static final double MAX_CONTENT_KEYWORD_BOOST = 0.16;

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
            VectorStore vectorStore) {

        this(
                embeddingClient,
                embeddingModelProvider,
                vectorStore,
                RetrievalRequest.DEFAULT_TOP_K,
                RetrievalRequest.DEFAULT_MINIMUM_SCORE);
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
            double defaultMinimumScore) {

        this.embeddingClient = Objects.requireNonNull(
                embeddingClient,
                "embeddingClient must not be null");

        this.embeddingModelProvider = Objects.requireNonNull(
                embeddingModelProvider,
                "embeddingModelProvider must not be null");

        this.vectorStore = Objects.requireNonNull(
                vectorStore,
                "vectorStore must not be null");

        if (defaultTopK < 1) {
            throw new IllegalArgumentException(
                    "defaultTopK must be greater than zero");
        }

        if (!Double.isFinite(defaultMinimumScore)
                || defaultMinimumScore < -1.0
                || defaultMinimumScore > 1.0) {

            throw new IllegalArgumentException(
                    "defaultMinimumScore must be between -1.0 and 1.0");
        }

        this.defaultTopK = defaultTopK;
        this.defaultMinimumScore = defaultMinimumScore;
    }

    /**
     * Project Scope가 없는 검색은 허용하지 않습니다.
     */
    @Override
    public RetrievalResult retrieve(String query) throws RetrievalException {
        String normalizedQuery = validateQuery(query);

        throw new RetrievalException(
                "Project-scoped retrieval requires RetrievalRequest with projectId",
                normalizedQuery,
                "",
                RetrievalOperation.VALIDATE,
                null);
    }

    /**
     * 지정된 검색 조건으로 관련 문서를 검색합니다.
     */
    @Override
    public RetrievalResult retrieve(RetrievalRequest request) throws RetrievalException {
        validateRequest(request);

        String query = request.getQuery();
        String model = resolveEmbeddingModel();

        long startedAt = System.nanoTime();

        try {
            float[] queryVector = embedQuery(query, model);

            VectorSearchRequest searchRequest =
                    createVectorSearchRequest(
                            request,
                            queryVector,
                            model);

            List<VectorSearchResult> searchResults =
                    searchVectorStore(
                            query,
                            model,
                            searchRequest,
                            request.getMinimumScore());

            List<VectorSearchResult> finalResults =
                    prepareResults(
                            searchResults,
                            request);

            return RetrievalResult.builder()
                    .query(query)
                    .model(model)
                    .searchResults(finalResults)
                    .durationNanos(System.nanoTime() - startedAt)
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
                    exception);
        }
    }

    /**
     * 사용자 질의를 임베딩합니다.
     */
    private float[] embedQuery(String query, String model) throws RetrievalException {
        try {
            float[] queryVector = embeddingClient.embedQuery(model, query);

            validateQueryVector(
                    queryVector,
                    query,
                    model);

            return queryVector;

        } catch (EmbeddingException exception) {
            throw new RetrievalException(
                    "Failed to embed retrieval query",
                    query,
                    model,
                    RetrievalOperation.EMBED_QUERY,
                    exception);

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                    "Invalid query embedding result",
                    query,
                    model,
                    RetrievalOperation.EMBED_QUERY,
                    exception);
        }
    }

    /**
     * RetrievalRequest를 VectorSearchRequest로 변환합니다.
     *
     * 최종 Top-K보다 넓은 후보군을 먼저 검색한 후
     * Hybrid Rerank에서 최종 순위를 계산합니다.
     */
    private VectorSearchRequest createVectorSearchRequest(
            RetrievalRequest request,
            float[] queryVector,
            String model) throws RetrievalException {

        try {
            int candidateTopK =
                    Math.max(
                            request.getTopK(),
                            request.getTopK() * CANDIDATE_MULTIPLIER);

            return VectorSearchRequest.builder()
                    .projectId(request.getProjectId())
                    .queryVector(queryVector)
                    .model(model)
                    .topK(candidateTopK)
                    .minimumScore(-1.0)
                    .similarityType(request.getSimilarityType())
                    .chunkTypes(request.getChunkTypes())
                    .sourcePaths(request.getSourcePaths())
                    .epubTypes(request.getEpubTypes())
                    .languages(request.getLanguages())
                    .metadataFilters(request.getMetadataFilters())
                    .includeRejected(true)
                    .build();

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                    "Failed to create vector search request",
                    request.getQuery(),
                    model,
                    RetrievalOperation.VALIDATE,
                    exception);
        }
    }

    /**
     * VectorStore 검색 및 Hybrid Rerank를 수행합니다.
     */
    private List<VectorSearchResult> searchVectorStore(
            String query,
            String model,
            VectorSearchRequest searchRequest,
            double minimumScore) throws RetrievalException {

        try {
            List<VectorSearchResult> searchResults =
                    vectorStore.search(searchRequest);

            if (searchResults == null) {
                throw new RetrievalException(
                        "VectorStore returned null search results",
                        query,
                        model,
                        RetrievalOperation.VECTOR_SEARCH,
                        null);
            }

            logTopKResults(
                    "[RAW] " + query,
                    searchResults,
                    -1.0);

            List<VectorSearchResult> rerankedResults =
                    rerank(
                            query,
                            searchResults);

            List<VectorSearchResult> filteredResults =
                    applyScoreThreshold(
                            rerankedResults,
                            minimumScore);

            logTopKResults(
                    "[FINAL] " + query,
                    filteredResults,
                    minimumScore);

            return List.copyOf(filteredResults);

        } catch (VectorStoreException exception) {
            throw new RetrievalException(
                    "Failed to search vector store",
                    query,
                    model,
                    RetrievalOperation.VECTOR_SEARCH,
                    exception);
        }
    }

    /**
     * 검색 결과를 최종 정렬하고 요청된 Top-K만 반환합니다.
     */
    private List<VectorSearchResult> prepareResults(
            List<VectorSearchResult> searchResults,
            RetrievalRequest request) {

        if (searchResults == null || searchResults.isEmpty()) {
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
                                                            .getSourcePath()))
                            .thenComparingInt(
                                    result ->
                                            result.getChunk()
                                                    .getSequence())
                            .thenComparing(
                                    VectorSearchResult::getId));

        } else {
            results.sort(
                    Comparator
                            .comparingDouble(
                                    VectorSearchResult::getScore)
                            .reversed()
                            .thenComparing(
                                    VectorSearchResult::getId));
        }

        int resultCount =
                Math.min(
                        request.getTopK(),
                        results.size());

        List<VectorSearchResult> rankedResults =
                new ArrayList<>(resultCount);

        for (int index = 0; index < resultCount; index++) {
            rankedResults.add(
                    results.get(index)
                            .withRank(index + 1));
        }

        return List.copyOf(rankedResults);
    }

    /**
     * Vector similarity와 lexical 정보를 결합하여
     * 검색 결과를 재정렬합니다.
     */
    private List<VectorSearchResult> rerank(String query, List<VectorSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = GomsStringUtil.normalizeForMatch(query);
        List<String> keywords = GomsStringUtil.extractKeywords(query);

        List<VectorSearchResult> reranked =
                new ArrayList<>(results.size());

        for (VectorSearchResult result : results) {
            if (result == null) {
                continue;
            }

            VectorRecord record = result.getRecord();

            if (record == null) {
                continue;
            }

            DocumentChunk chunk = record.getChunk();

            if (chunk == null) {
                continue;
            }

            double finalScore =
                    calculateRerankScore(
                            normalizedQuery,
                            keywords,
                            result.getScore(),
                            chunk);

            reranked.add(
                    VectorSearchResult.builder()
                            .record(record)
                            .score(finalScore)
                            .similarityType(result.getSimilarityType())
                            .accepted(result.isAccepted())
                            .build());
        }

        reranked.sort(
                Comparator
                        .comparingDouble(
                                VectorSearchResult::getScore)
                        .reversed()
                        .thenComparing(
                                VectorSearchResult::getId));

        List<VectorSearchResult> ranked =
                new ArrayList<>(reranked.size());

        for (int index = 0; index < reranked.size(); index++) {
            ranked.add(
                    reranked.get(index)
                            .withRank(index + 1));
        }

        return List.copyOf(ranked);
    }

    /**
     * 단일 Chunk의 Hybrid Rerank 점수를 계산합니다.
     */
    private double calculateRerankScore(
            String normalizedQuery,
            List<String> keywords,
            double vectorScore,
            DocumentChunk chunk) {

        String heading = GomsStringUtil.normalizeHeading(chunk.getTitle());
        String content = GomsStringUtil.normalizeForMatch(chunk.getContent());

        double headingBoost = 0.0;
        double contentBoost = 0.0;
        double relationBoost = 0.0;
        double patternBoost = 0.0;

        /*
         * 질문에 heading 자체가 포함되어 있으면
         * 해당 section을 강하게 우대합니다.
         *
         * 예:
         * 질문   = "예전의 저자에게 덕수궁 돌담길은..."
         * heading = "2. 덕수궁 돌담길"
         */
        if (!heading.isBlank() && heading.length() >= 2 && normalizedQuery.contains(heading)) {
            headingBoost += EXACT_HEADING_BOOST;
        }

        int headingMatches = 0;
        int contentMatches = 0;

        for (String keyword : keywords) {
            if (keyword.isBlank()) {
                continue;
            }

            if (!heading.isBlank() && heading.contains(keyword)) {
                headingMatches++;
            }

            if (!content.isBlank() && content.contains(keyword)) {
                contentMatches++;
            }
        }

        headingBoost += Math.min(
                MAX_HEADING_KEYWORD_BOOST,
                headingMatches * HEADING_KEYWORD_BOOST);

        contentBoost += Math.min(
                MAX_CONTENT_KEYWORD_BOOST,
                contentMatches * CONTENT_KEYWORD_BOOST);

        /*
         * Heading과 Content가 모두 질의와 관련 있으면
         * section + paragraph 일치로 판단하여 추가 boost합니다.
         */
        if (headingMatches > 0 && contentMatches > 0) {
            relationBoost += HEADING_CONTENT_BOOST;
        }

        /*
         * "제목"을 묻는 질문에서 실제 제목 표기 『...』가
         * 포함된 Chunk를 추가 우대합니다.
         */
        if (GomsStringUtil.containsKeyword(keywords, "제목") && GomsStringUtil.containsTitlePattern(chunk.getContent())) {
            patternBoost += TITLE_PATTERN_BOOST;
        }

        double finalScore =
                vectorScore
                        + headingBoost
                        + contentBoost
                        + relationBoost
                        + patternBoost;

        return Math.min(1.0, finalScore);
    }

    
    /**
     * Threshold 적용.
     */
    private List<VectorSearchResult> applyScoreThreshold(
            List<VectorSearchResult> results,
            double minimumScore) {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<VectorSearchResult> filtered =
                new ArrayList<>();

        for (VectorSearchResult result : results) {
            if (result == null) {
                continue;
            }

            if (result.getScore() < minimumScore) {
                continue;
            }

            filtered.add(result);
        }

        List<VectorSearchResult> ranked =
                new ArrayList<>(filtered.size());

        for (int index = 0; index < filtered.size(); index++) {
            ranked.add(
                    filtered.get(index)
                            .withRank(index + 1));
        }

        return List.copyOf(ranked);
    }

    /**
     * 요청을 검증합니다.
     */
    private void validateRequest(RetrievalRequest request) throws RetrievalException {
        if (request == null) {
            throw new RetrievalException(
                    "Retrieval request must not be null",
                    "",
                    "",
                    RetrievalOperation.VALIDATE,
                    null);
        }

        if (request.getProjectId() == null
                || request.getProjectId().isBlank()) {

            throw new RetrievalException(
                    "Retrieval projectId must not be blank",
                    request.getQuery(),
                    "",
                    RetrievalOperation.VALIDATE,
                    null);
        }

        try {
            validateQuery(request.getQuery());

        } catch (IllegalArgumentException exception) {
            throw new RetrievalException(
                    "Retrieval query is invalid",
                    request.getQuery(),
                    "",
                    RetrievalOperation.VALIDATE,
                    exception);
        }

        if (request.getTopK() < 1) {
            throw new RetrievalException(
                    "Retrieval topK must be greater than zero",
                    request.getQuery(),
                    "",
                    RetrievalOperation.VALIDATE,
                    null);
        }

        if (!Double.isFinite(request.getMinimumScore())) {
            throw new RetrievalException(
                    "Retrieval minimumScore must be finite",
                    request.getQuery(),
                    "",
                    RetrievalOperation.VALIDATE,
                    null);
        }
    }

    /**
     * 현재 임베딩 모델명을 조회합니다.
     */
    private String resolveEmbeddingModel() throws RetrievalException {
        String model;

        try {
            model = embeddingModelProvider.getModel();

        } catch (RuntimeException exception) {
            throw new RetrievalException(
                    "Failed to resolve embedding model",
                    "",
                    "",
                    RetrievalOperation.VALIDATE,
                    exception);
        }

        if (model == null || model.isBlank()) {
            throw new RetrievalException(
                    "Embedding model must not be blank",
                    "",
                    "",
                    RetrievalOperation.VALIDATE,
                    null);
        }

        return model.trim();
    }

    /**
     * 질의 벡터 값을 검증합니다.
     */
    private void validateQueryVector(
            float[] vector,
            String query,
            String model) throws RetrievalException {

        if (vector == null || vector.length == 0) {
            throw new RetrievalException(
                    "Query embedding vector must not be empty",
                    query,
                    model,
                    RetrievalOperation.EMBED_QUERY,
                    null);
        }

        for (int index = 0; index < vector.length; index++) {
            if (!Float.isFinite(vector[index])) {
                throw new RetrievalException(
                        "Query embedding contains invalid value at index "
                                + index,
                        query,
                        model,
                        RetrievalOperation.EMBED_QUERY,
                        null);
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

    /**
     * Retrieval 진단 로그.
     */
    private void logTopKResults( String query, List<VectorSearchResult> results, double threshold) {

        System.out.println("[RAG] ========================================");
        System.out.println("[RAG] Retrieval query = " + query);
        System.out.println("[RAG] Result count = " + (results == null ? 0 : results.size()));
        System.out.println("[RAG] Retrieval threshold = " + threshold);

        if (results == null || results.isEmpty()) {
            System.out.println("[RAG] No retrieval results.");
            System.out.println("[RAG] ========================================");
            return;
        }

        int rank = 1;

        for (VectorSearchResult result : results) {
            if (result == null) {
                continue;
            }

            VectorRecord record = result.getRecord();

            if (record == null) {
                continue;
            }

            DocumentChunk chunk = record.getChunk();

            if (chunk == null) {
                continue;
            }

            System.out.println();
            System.out.println("[RAG][TOP-" + rank + "]");
            System.out.println("[RAG] score      = " + String.format( java.util.Locale.ROOT, "%.6f", result.getScore()));
            System.out.println("[RAG] sourcePath = " + chunk.getSourcePath());
            System.out.println("[RAG] chunkId    = " + chunk.getId());
            System.out.println("[RAG] heading    = " + chunk.getTitle());
            System.out.println("[RAG] type       = " + chunk.getType());
            System.out.println("[RAG] sequence   = " + chunk.getSequence());
            System.out.println("[RAG] text       = " + GomsStringUtil.abbreviate( chunk.getContent(), 500));
            

            rank++;
        }

        System.out.println("[RAG] ========================================");
    }

    public EmbeddingClient getEmbeddingClient() {
        return embeddingClient;
    }

    public EmbeddingModelProvider getEmbeddingModelProvider() {
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

    private static String normalizePath(String path) {
        return path == null
                ? ""
                : path.trim().replace('\\', '/');
    }
}