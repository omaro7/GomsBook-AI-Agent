/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.rag.RagException;
import kr.co.goms.gomsbook.ai.rag.RagService;
import kr.co.goms.gomsbook.ai.rag.context.RagContext;
import kr.co.goms.gomsbook.ai.rag.context.RagSource;
import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.index.ProjectIndexException;
import kr.co.goms.gomsbook.ai.rag.index.ProjectIndexResult;
import kr.co.goms.gomsbook.ai.rag.index.ProjectRagIndexer;
import kr.co.goms.gomsbook.ai.rag.retrieval.RetrievalRequest;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 현재 GomsBook 프로젝트의 RAG 인덱스에서 관련 문서를 검색하는 Tool.
 *
 * <p>사용자의 질의와 관련된 프로젝트 문서 Chunk를 검색하여
 * LLM이 사용할 수 있는 RAG Context를 반환한다.</p>
 *
 * <p>실제 검색, 순위화 및 Context 생성은 {@link RagService}에
 * 위임한다.</p>
 */
public final class SearchProjectDocumentsTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "search_project_documents";

    private static final String DESCRIPTION =
            "Searches indexed documents in the current GomsBook project "
                    + "and returns relevant RAG context.";

    private static final int DEFAULT_TOP_K = 5;
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 50;

    private final RagService ragService;

    private final CurrentProjectProvider projectProvider;

    private final ProjectRagIndexer projectRagIndexer;

    public SearchProjectDocumentsTool(
            RagService ragService,
            CurrentProjectProvider projectProvider,
            ProjectRagIndexer projectRagIndexer) {

        this.ragService =
                Objects.requireNonNull(
                        ragService,
                        "ragService must not be null"
                );

        this.projectProvider =
                Objects.requireNonNull(
                        projectProvider,
                        "projectProvider must not be null"
                );

        this.projectRagIndexer =
                Objects.requireNonNull(
                        projectRagIndexer,
                        "projectRagIndexer must not be null"
                );
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> query =
                property(
                        "string",
                        "Search query for finding relevant "
                                + "documents in the current project."
                );

        Map<String, Object> topK =
                property(
                        "integer",
                        "Maximum number of relevant document "
                                + "chunks to retrieve."
                );

        topK.put("minimum", MIN_TOP_K);
        topK.put("maximum", MAX_TOP_K);
        topK.put("default", DEFAULT_TOP_K);

        Map<String, Object> sourcePath =
                property(
                        "string",
                        "Optional project-relative source path "
                                + "used to restrict the search."
                );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "query",
                query
        );

        properties.put(
                "topK",
                topK
        );

        properties.put(
                "sourcePath",
                sourcePath
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                properties
        );

        schema.put(
                "required",
                Collections.singletonList(
                        "query"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return Collections.unmodifiableMap(
                schema
        );
    }

    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        List<ToolIssue> issues =
                new ArrayList<>();

        if (request == null) {

            issues.add(
                    error(
                            "request",
                            "Tool request must not be null."
                    )
            );

            return ToolValidationResult.invalid(
                    issues
            );
        }

        if (context == null) {

            issues.add(
                    error(
                            "context",
                            "Tool context must not be null."
                    )
            );

            return ToolValidationResult.invalid(
                    issues
            );
        }

        Map<String, Object> arguments =
                safeArguments(request);

        String query =
                readString(
                        arguments,
                        "query"
                );

        if (query == null
                || query.isBlank()) {

            issues.add(
                    error(
                            "query",
                            "query must not be blank."
                    )
            );
        }

        validateTopK(
                arguments,
                issues
        );

        validateOptionalString(
                arguments,
                "sourcePath",
                issues
        );

        if (!issues.isEmpty()) {

            return ToolValidationResult.invalid(
                    issues
            );
        }

        return ToolValidationResult.valid();
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation =
                validate(
                        request,
                        context
                );

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.FAILED
                    )
                    .message(
                            "Invalid project document search request."
                    )
                    .issues(
                            validation.getIssues()
                    )
                    .build();
        }

        Map<String, Object> arguments = safeArguments(request);

        EpubProjectContext project =
                projectProvider
                        .getCurrentProject();

        String query =
                readString(
                        arguments,
                        "query"
                ).trim();

        int topK =
                defaultInteger(
                        readInteger(
                                arguments,
                                "topK"
                        ),
                        DEFAULT_TOP_K
                );

        String sourcePath =
                normalizeOptionalText(
                        readString(
                                arguments,
                                "sourcePath"
                        )
                );

        try {

            /*
             * 검색 전에 현재 프로젝트의 RAG 인덱스를 자동 동기화합니다.
             * 변경이 없으면 Embedding 없이 SKIP 처리됩니다.
             */
            ProjectIndexResult indexResult =
                    projectRagIndexer
                            .synchronize(
                                    project
                            );

            String projectId =
                    indexResult.getProjectId();

            RetrievalRequest retrievalRequest =
                    createRetrievalRequest(
                            projectId,
                            query,
                            topK,
                            sourcePath
                    );

            /*
             * 중요:
             *
             * Retriever.retrieve(...)가 아니라
             * 상위 서비스인 RagService.buildContext(...)를 사용한다.
             *
             * RagService가 Retrieval, Ranking, Context 생성을
             * 내부에서 조율한다.
             */
            RagContext ragContext =
                    ragService.buildContext(
                            retrievalRequest
                    );

            if (ragContext == null) {

                return ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.FAILED
                        )
                        .message(
                                "RAG service returned no context."
                        )
                        .build();
            }

            Map<String, Object> output =
                    createOutput(
                            projectId,
                            query,
                            topK,
                            sourcePath,
                            indexResult,
                            ragContext
                    );

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.SUCCESS
                    )
                    .message(
                            createResultMessage(
                                    ragContext
                            )
                    )
                    .data(
                            output
                    )
                    .build();

        } catch (ProjectIndexException exception) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.FAILED
                    )
                    .message(
                            "Automatic RAG index synchronization failed: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .cause(
                            exception
                    )
                    .build();

        } catch (RagException exception) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.FAILED
                    )
                    .message(
                            "RAG document search failed: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .data(
                            createErrorOutput(
                                    query,
                                    sourcePath,
                                    exception
                            )
                    )
                    .cause(
                            exception
                    )
                    .build();

        } catch (IllegalArgumentException exception) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.FAILED
                    )
                    .message(
                            "Invalid RAG search request: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .cause(
                            exception
                    )
                    .build();

        } catch (RuntimeException exception) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.FAILED
                    )
                    .message(
                            "Unexpected project document "
                                    + "search failure: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .cause(
                            exception
                    )
                    .build();
        }
    }

    /**
     * 현재 EPUB 프로젝트의 Project Scope를 포함한
     * RetrievalRequest를 생성합니다.
     */
    private RetrievalRequest createRetrievalRequest(
    		String projectId,
            String query,
            int topK,
            String sourcePath) {

        RetrievalRequest.Builder builder =
                RetrievalRequest.builder()
		                .projectId(
		                        projectId
		                )
                        .query(
                                query
                        )
                        .topK(
                                topK
                        );

        /*
         * RetrievalRequest에 sourcePath(...)가 구현되어 있는 경우
         * 아래 코드를 사용한다.
         *
         * 현재 RetrievalRequest에 해당 메서드가 없다면
         * 이 if 블록만 삭제하면 된다.
         */
        if (sourcePath != null) {

            builder.sourcePath(
                    sourcePath
            );
        }

        return builder.build();
    }

    /**
     * RAG 검색 결과를 Tool 결과 구조로 변환한다.
     */
    private Map<String, Object> createOutput(
            String projectId,
            String query,
            int topK,
            String sourcePath,
            ProjectIndexResult indexResult,
            RagContext ragContext) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "projectId",
                projectId
        );

        output.put(
                "query",
                query
        );

        output.put(
                "topK",
                topK
        );

        output.put(
                "indexSync",
                createIndexSyncOutput(
                        indexResult
                )
        );

        if (sourcePath != null) {
            output.put(
                    "sourcePath",
                    sourcePath
            );
        }

        /*
         * RAG 검색 결과로 생성된 실제 Context 문자열
         */
        output.put(
                "context",
                ragContext.getContextText()
        );

        /*
         * 검색 결과 존재 여부
         */
        output.put(
                "empty",
                ragContext.isEmpty()
        );

        /*
         * 검색된 Source 개수
         */
        output.put(
                "sourceCount",
                ragContext.size()
        );

        /*
         * 검색된 Source 목록
         */
        output.put(
                "sources",
                ragContext.getSources()
        );
        
        /*
         * Retrieval 검증용 Top-K 상세 결과.
         */
        output.put(
                "topResults",
                createTopResults(
                        ragContext
                )
        );

        /*
         * Embedding 모델
         */
        output.put(
                "embeddingModel",
                ragContext.getEmbeddingModel()
        );

        /*
         * 검색 수행 시간
         */
        output.put(
                "retrievalDurationMillis",
                ragContext.getRetrievalDurationMillis()
        );

        /*
         * Context가 최대 길이에 의해 잘렸는지 여부
         */
        output.put(
                "truncated",
                ragContext.isTruncated()
        );

        /*
         * 실제 Context 문자 수
         */
        output.put(
                "characterCount",
                ragContext.getCharacterCount()
        );

        /*
         * 자르기 전 원본 문자 수
         */
        output.put(
                "originalCharacterCount",
                ragContext.getOriginalCharacterCount()
        );

        /*
         * 잘려서 제외된 문자 수
         */
        output.put(
                "omittedCharacterCount",
                ragContext.getOmittedCharacterCount()
        );

        /*
         * 최고 유사도 점수
         */
        output.put(
                "highestScore",
                ragContext.getHighestScore()
        );

        return Collections.unmodifiableMap(
                output
        );
    }
    
    private Map<String, Object> createIndexSyncOutput(
            ProjectIndexResult result) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "changed",
                result.isChanged()
        );

        output.put(
                "newFiles",
                result.getNewFiles()
        );

        output.put(
                "reindexedFiles",
                result.getReindexedFiles()
        );

        output.put(
                "skippedFiles",
                result.getSkippedFiles()
        );

        output.put(
                "deletedFiles",
                result.getDeletedFiles()
        );

        output.put(
                "createdEmbeddings",
                result.getCreatedEmbeddings()
        );

        output.put(
                "storedVectors",
                result.getStoredVectors()
        );

        output.put(
                "deletedVectors",
                result.getDeletedVectors()
        );

        output.put(
                "vectorStoreSize",
                result.getVectorStoreSize()
        );

        return Collections.unmodifiableMap(
                output
        );
    }

    private String createResultMessage(
            RagContext ragContext) {

        if (ragContext.isEmpty()) {

            return "No relevant project documents were found.";
        }

        int sourceCount =
                ragContext.getSources() == null
                        ? 0
                        : ragContext
                                .getSources()
                                .size();

        return "Project document search completed with "
                + sourceCount
                + " relevant source(s).";
    }

    private Map<String, Object> createErrorOutput(
            String query,
            String sourcePath,
            RagException exception) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "query",
                query
        );

        if (sourcePath != null) {

            output.put(
                    "sourcePath",
                    sourcePath
            );
        }

        output.put(
                "errorType",
                exception
                        .getClass()
                        .getSimpleName()
        );

        output.put(
                "errorMessage",
                safeMessage(
                        exception
                )
        );

        return Collections.unmodifiableMap(
                output
        );
    }
    
    private List<Map<String, Object>> createTopResults( RagContext ragContext) {

        if (ragContext == null
                || ragContext.getSources() == null
                || ragContext.getSources().isEmpty()) {

            return List.of();
        }


        List<Map<String, Object>> results = new ArrayList<>();


        int rank = 1;


        for (RagSource source : ragContext.getSources()) {

            if (source == null) {
                continue;
            }


            DocumentChunk chunk = source.getChunk();


            if (chunk == null) {
                continue;
            }


            Map<String, Object> item = new LinkedHashMap<>();


            item.put(
                    "rank",
                    rank
            );


            item.put(
                    "score",
                    source.getScore()
            );


            item.put(
                    "sourcePath",
                    chunk.getSourcePath()
            );


            item.put(
                    "chunkId",
                    chunk.getId()
            );


            item.put(
                    "heading",
                    chunk.getTitle()
            );


            item.put(
                    "type",
                    chunk.getType()
                            .name()
            );


            item.put(
                    "text",
                    chunk.getContent()
            );


            results.add(
                    Collections.unmodifiableMap(
                            item
                    )
            );

            rank++;
        }


        return List.copyOf(
                results
        );
    }

    private void validateTopK(
            Map<String, Object> arguments,
            List<ToolIssue> issues) {

        if (!arguments.containsKey(
                "topK"
        )) {

            return;
        }

        Integer topK =
                readInteger(
                        arguments,
                        "topK"
                );

        if (topK == null) {

            issues.add(
                    error(
                            "topK",
                            "topK must be an integer."
                    )
            );

            return;
        }

        if (topK < MIN_TOP_K
                || topK > MAX_TOP_K) {

            issues.add(
                    error(
                            "topK",
                            "topK must be between "
                                    + MIN_TOP_K
                                    + " and "
                                    + MAX_TOP_K
                                    + "."
                    )
            );
        }
    }

    private void validateOptionalString(
            Map<String, Object> arguments,
            String key,
            List<ToolIssue> issues) {

        if (!arguments.containsKey(
                key
        )) {

            return;
        }

        Object value =
                arguments.get(
                        key
                );

        if (value == null) {
            return;
        }

        if (!(value instanceof String)) {

            issues.add(
                    error(
                            key,
                            key
                                    + " must be a string value."
                    )
            );
        }
    }

    private Map<String, Object> property(
            String type,
            String description) {

        Map<String, Object> property =
                new LinkedHashMap<>();

        property.put(
                "type",
                type
        );

        property.put(
                "description",
                description
        );

        return property;
    }

    private Map<String, Object> safeArguments(
            ToolRequest request) {

        if (request == null
                || request.getArguments() == null) {

            return Collections.emptyMap();
        }

        return request.getArguments();
    }

    private String readString(
            Map<String, Object> arguments,
            String key) {

        Object value =
                arguments.get(
                        key
                );

        if (value == null) {
            return null;
        }

        if (value instanceof String text) {
            return text;
        }

        return String.valueOf(
                value
        );
    }

    private Integer readInteger(
            Map<String, Object> arguments,
            String key) {

        Object value =
                arguments.get(
                        key
                );

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {

            return number.intValue();
        }

        try {

            return Integer.valueOf(
                    String.valueOf(
                            value
                    ).trim()
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    private int defaultInteger(
            Integer value,
            int defaultValue) {

        return value == null
                ? defaultValue
                : value;
    }

    private String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private ToolIssue error(
            String field,
            String message) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .field(
                        field
                )
                .message(
                        message
                )
                .build();
    }

    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {

            return "Unknown error";
        }

        return throwable.getMessage();
    }
}