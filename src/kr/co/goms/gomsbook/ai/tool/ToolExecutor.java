/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ToolRegistry}에 등록된 Tool을 조회하고 실행하는 공통 실행기입니다.
 *
 * <p>
 * ToolExecutor는 다음 작업을 담당합니다.
 * </p>
 *
 * <ol>
 *   <li>실행 Context와 Request 검증</li>
 *   <li>Tool Registry에서 Tool 조회</li>
 *   <li>Tool 사용 가능 여부 확인</li>
 *   <li>요청 타입 확인</li>
 *   <li>Tool 요청 데이터 검증</li>
 *   <li>Tool 실행</li>
 *   <li>예외를 표준 {@link ToolResult}로 변환</li>
 * </ol>
 *
 * <p>
 * ToolExecutor는 SWT, JFace, Workbench 등 Eclipse UI API를
 * 직접 참조하지 않습니다.
 * </p>
 * 
 * ToolRegistry registry = new ToolRegistry();

	registry.register(
	        new XhtmlGenerationTool()
	);
	
	ToolExecutor executor = new ToolExecutor(registry);
	
	ToolResult<XhtmlGenerationResponse> result =
	        executor.execute(
	                "xhtml.generate",
	                context,
	                request,
	                XhtmlGenerationResponse.class
	        );
	
	if (result.isSuccess()) {
	    String xhtml = result.response().xhtml();
	    System.out.println(xhtml);
	} else {
	    result.issues().forEach(
	            issue -> System.err.println(
	                    issue.code()
	                            + ": "
	                            + issue.message()
	            )
	    );
	}

 */
public final class ToolExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * ToolExecutor를 생성합니다.
     *
     * @param toolRegistry Tool Registry
     */
    public ToolExecutor(
            ToolRegistry toolRegistry
    ) {
        this.toolRegistry = Objects.requireNonNull(
                toolRegistry,
                "toolRegistry must not be null."
        );
    }

    /**
     * Tool 이름과 요청 객체를 사용하여 Tool을 실행합니다.
     *
     * <p>
     * Tool의 실제 Request 및 Response 타입을 컴파일 시점에 알고 있다면
     * {@link #execute(String, ToolContext, ToolRequest, Class)} 사용을
     * 권장합니다.
     * </p>
     *
     * @param toolName 실행할 Tool 이름
     * @param context 실행 Context
     * @param request Tool 요청
     * @return Tool 실행 결과
     */
    public ToolResult<? extends ToolResponse> execute(
            String toolName,
            ToolContext context,
            ToolRequest request
    ) {
        Instant startedAt = Instant.now();

        if (toolName == null || toolName.isBlank()) {
            return createFailure(
                    context,
                    "unknown",
                    "unknown",
                    ToolStatus.NOT_EXECUTED,
                    List.of(
                            issue(
                                    "TOOL_NAME_REQUIRED",
                                    ToolIssueSeverity.ERROR,
                                    "Tool 이름이 없습니다.",
                                    "실행할 Tool 이름을 입력해야 합니다.",
                                    Map.of()
                            )
                    ),
                    startedAt
            );
        }

        String normalizedToolName = toolName.trim();

        if (context == null) {
            return createFailure(
                    null,
                    normalizedToolName,
                    "unknown",
                    ToolStatus.NOT_EXECUTED,
                    List.of(
                            issue(
                                    "TOOL_CONTEXT_REQUIRED",
                                    ToolIssueSeverity.ERROR,
                                    "Tool 실행 Context가 없습니다.",
                                    "Tool 실행을 위해 ToolContext가 필요합니다.",
                                    Map.of(
                                            "toolName",
                                            normalizedToolName
                                    )
                            )
                    ),
                    startedAt
            );
        }

        AgentTool<? extends ToolRequest, ? extends ToolResponse>
                registeredTool;

        try {
            registeredTool = toolRegistry.getRequired(
                    normalizedToolName
            );
        } catch (ToolNotFoundException exception) {
            return createFailure(
                    context,
                    normalizedToolName,
                    "unknown",
                    ToolStatus.NOT_EXECUTED,
                    List.of(
                            issue(
                                    "TOOL_NOT_REGISTERED",
                                    ToolIssueSeverity.ERROR,
                                    "등록되지 않은 Tool입니다.",
                                    exception.getMessage(),
                                    Map.of(
                                            "toolName",
                                            normalizedToolName
                                    )
                            )
                    ),
                    startedAt
            );
        }

        if (!registeredTool.isAvailable()) {
            return createFailure(
                    context,
                    registeredTool.getName(),
                    registeredTool.getVersion(),
                    ToolStatus.NOT_EXECUTED,
                    List.of(
                            issue(
                                    "TOOL_UNAVAILABLE",
                                    ToolIssueSeverity.ERROR,
                                    "현재 사용할 수 없는 Tool입니다.",
                                    "Tool의 외부 서비스 또는 실행 환경을 확인해 주세요.",
                                    Map.of(
                                            "toolName",
                                            registeredTool.getName(),
                                            "toolVersion",
                                            registeredTool.getVersion()
                                    )
                            )
                    ),
                    startedAt
            );
        }

        if (request == null) {
            return createFailure(
                    context,
                    registeredTool.getName(),
                    registeredTool.getVersion(),
                    ToolStatus.VALIDATION_FAILED,
                    List.of(
                            issue(
                                    "TOOL_REQUEST_REQUIRED",
                                    ToolIssueSeverity.ERROR,
                                    "Tool 요청 데이터가 없습니다.",
                                    "Tool 실행을 위해 Request 객체가 필요합니다.",
                                    Map.of(
                                            "toolName",
                                            registeredTool.getName()
                                    )
                            )
                    ),
                    startedAt
            );
        }

        if (!registeredTool
                .getRequestType()
                .isInstance(request)) {

            return createFailure(
                    context,
                    registeredTool.getName(),
                    registeredTool.getVersion(),
                    ToolStatus.VALIDATION_FAILED,
                    List.of(
                            issue(
                                    "TOOL_REQUEST_TYPE_MISMATCH",
                                    ToolIssueSeverity.ERROR,
                                    "Tool 요청 타입이 일치하지 않습니다.",
                                    "Expected: "
                                            + registeredTool
                                                    .getRequestType()
                                                    .getName()
                                            + ", Actual: "
                                            + request
                                                    .getClass()
                                                    .getName(),
                                    Map.of(
                                            "toolName",
                                            registeredTool.getName(),
                                            "expectedType",
                                            registeredTool
                                                    .getRequestType()
                                                    .getName(),
                                            "actualType",
                                            request
                                                    .getClass()
                                                    .getName()
                                    )
                            )
                    ),
                    startedAt
            );
        }

        return executeRegisteredTool(
                registeredTool,
                context,
                request,
                startedAt
        );
    }

    /**
     * 기대하는 Response 타입을 지정하여 Tool을 실행합니다.
     *
     * <p>
     * 응답 타입이 일치하지 않으면 FAILED 결과를 반환합니다.
     * </p>
     *
     * @param toolName 실행할 Tool 이름
     * @param context 실행 Context
     * @param request Tool 요청
     * @param responseType 기대 응답 타입
     * @param <S> Tool 응답 타입
     * @return 타입이 확인된 Tool 실행 결과
     */
    public <S extends ToolResponse> ToolResult<S> execute(
            String toolName,
            ToolContext context,
            ToolRequest request,
            Class<S> responseType
    ) {
        Objects.requireNonNull(
                responseType,
                "responseType must not be null."
        );

        ToolResult<? extends ToolResponse> result =
                execute(
                        toolName,
                        context,
                        request
                );

        if (result.response() == null) {
            return copyWithoutResponse(result);
        }

        if (!responseType.isInstance(result.response())) {
            Instant completedAt = Instant.now();

            return ToolResult.failure(
                    result.requestId(),
                    result.toolName(),
                    result.toolVersion(),
                    ToolStatus.FAILED,
                    List.of(
                            issue(
                                    "TOOL_RESPONSE_TYPE_MISMATCH",
                                    ToolIssueSeverity.CRITICAL,
                                    "Tool 응답 타입이 일치하지 않습니다.",
                                    "Expected: "
                                            + responseType.getName()
                                            + ", Actual: "
                                            + result.response()
                                                    .getClass()
                                                    .getName(),
                                    Map.of(
                                            "toolName",
                                            result.toolName(),
                                            "expectedType",
                                            responseType.getName(),
                                            "actualType",
                                            result.response()
                                                    .getClass()
                                                    .getName()
                                    )
                            )
                    ),
                    result.startedAt(),
                    completedAt
            );
        }

        return castResult(
                result,
                responseType.cast(result.response())
        );
    }

    /**
     * Tool이 등록되어 있고 현재 사용 가능한지 확인합니다.
     *
     * @param toolName Tool 이름
     * @return 실행 가능하면 true
     */
    public boolean isExecutable(
            String toolName
    ) {
        return toolRegistry
                .findByName(toolName)
                .map(AgentTool::isAvailable)
                .orElse(false);
    }

    /**
     * 등록된 Tool Registry를 반환합니다.
     *
     * @return Tool Registry
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Registry에서 조회된 Tool을 실행합니다.
     */
    private ToolResult<? extends ToolResponse>
    executeRegisteredTool(
            AgentTool<? extends ToolRequest,
                    ? extends ToolResponse> tool,
            ToolContext context,
            ToolRequest request,
            Instant startedAt
    ) {
        return executeTypedTool(
                tool,
                context,
                request,
                startedAt
        );
    }

    /**
     * Wildcard Tool을 실제 Generic 타입으로 변환하여 실행합니다.
     *
     * <p>
     * 요청 타입은 호출 전에 {@code getRequestType().isInstance()}로
     * 검증되므로 이 메서드 내부의 형변환은 안전합니다.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private <R extends ToolRequest, S extends ToolResponse>
    ToolResult<S> executeTypedTool(
            AgentTool<? extends ToolRequest,
                    ? extends ToolResponse> sourceTool,
            ToolContext context,
            ToolRequest sourceRequest,
            Instant startedAt
    ) {
        AgentTool<R, S> tool =
                (AgentTool<R, S>) sourceTool;

        R request = (R) sourceRequest;

        try {
            ToolValidationResult validationResult =
                    tool.validateRequest(request);

            if (!validationResult.valid()) {
                return ToolResult.failure(
                        context.requestId(),
                        tool.getName(),
                        tool.getVersion(),
                        ToolStatus.VALIDATION_FAILED,
                        validationResult.issues(),
                        startedAt,
                        Instant.now()
                );
            }

            ToolResult<S> result =
                    tool.execute(
                            context,
                            request
                    );

            if (result == null) {
                return ToolResult.failure(
                        context.requestId(),
                        tool.getName(),
                        tool.getVersion(),
                        ToolStatus.FAILED,
                        List.of(
                                issue(
                                        "TOOL_RESULT_NULL",
                                        ToolIssueSeverity.CRITICAL,
                                        "Tool 실행 결과가 없습니다.",
                                        "AgentTool.execute()는 null을 반환할 수 없습니다.",
                                        Map.of(
                                                "toolName",
                                                tool.getName(),
                                                "toolVersion",
                                                tool.getVersion()
                                        )
                                )
                        ),
                        startedAt,
                        Instant.now()
                );
            }

            return validateToolResult(
                    tool,
                    context,
                    result,
                    startedAt
            );

        } catch (RuntimeException exception) {
            return ToolResult.failure(
                    context.requestId(),
                    tool.getName(),
                    tool.getVersion(),
                    ToolStatus.FAILED,
                    List.of(
                            createExceptionIssue(
                                    tool,
                                    exception
                            )
                    ),
                    startedAt,
                    Instant.now()
            );
        }
    }

    /**
     * Tool이 반환한 결과의 기본 무결성을 검사합니다.
     */
    private <R extends ToolRequest, S extends ToolResponse>
    ToolResult<S> validateToolResult(
            AgentTool<R, S> tool,
            ToolContext context,
            ToolResult<S> result,
            Instant executorStartedAt
    ) {
        if (result.status() == ToolStatus.SUCCESS
                && result.response() == null) {

            return ToolResult.failure(
                    context.requestId(),
                    tool.getName(),
                    tool.getVersion(),
                    ToolStatus.FAILED,
                    List.of(
                            issue(
                                    "TOOL_SUCCESS_RESPONSE_REQUIRED",
                                    ToolIssueSeverity.CRITICAL,
                                    "성공 결과에 응답 데이터가 없습니다.",
                                    "SUCCESS 상태의 ToolResult에는 Response가 필요합니다.",
                                    Map.of(
                                            "toolName",
                                            tool.getName(),
                                            "responseType",
                                            tool.getResponseType()
                                                    .getName()
                                    )
                            )
                    ),
                    executorStartedAt,
                    Instant.now()
            );
        }

        if (result.response() != null
                && !tool
                        .getResponseType()
                        .isInstance(result.response())) {

            return ToolResult.failure(
                    context.requestId(),
                    tool.getName(),
                    tool.getVersion(),
                    ToolStatus.FAILED,
                    List.of(
                            issue(
                                    "TOOL_INVALID_RESPONSE_TYPE",
                                    ToolIssueSeverity.CRITICAL,
                                    "Tool이 잘못된 응답 타입을 반환했습니다.",
                                    "Expected: "
                                            + tool.getResponseType()
                                                    .getName()
                                            + ", Actual: "
                                            + result.response()
                                                    .getClass()
                                                    .getName(),
                                    Map.of(
                                            "toolName",
                                            tool.getName(),
                                            "expectedType",
                                            tool.getResponseType()
                                                    .getName(),
                                            "actualType",
                                            result.response()
                                                    .getClass()
                                                    .getName()
                                    )
                            )
                    ),
                    executorStartedAt,
                    Instant.now()
            );
        }

        return result;
    }

    /**
     * 예외를 ToolIssue로 변환합니다.
     */
    private static ToolIssue createExceptionIssue(
            AgentTool<?, ?> tool,
            RuntimeException exception
    ) {
        String detail = exception.getMessage();

        if (detail == null || detail.isBlank()) {
            detail = exception
                    .getClass()
                    .getName();
        }

        return issue(
                "TOOL_EXECUTION_FAILED",
                ToolIssueSeverity.ERROR,
                "Tool 실행 중 오류가 발생했습니다.",
                detail,
                Map.of(
                        "toolName",
                        tool.getName(),
                        "toolVersion",
                        tool.getVersion(),
                        "exceptionType",
                        exception
                                .getClass()
                                .getName()
                )
        );
    }

    /**
     * ToolIssue를 생성합니다.
     */
    private static ToolIssue issue(
            String code,
            ToolIssueSeverity severity,
            String message,
            String detail,
            Map<String, Object> attributes
    ) {
        return new ToolIssue(
                code,
                severity,
                message,
                detail,
                null,
                null,
                null,
                attributes
        );
    }

    /**
     * 실행 실패 결과를 생성합니다.
     */
    private static ToolResult<? extends ToolResponse>
    createFailure(
            ToolContext context,
            String toolName,
            String toolVersion,
            ToolStatus status,
            List<ToolIssue> issues,
            Instant startedAt
    ) {
        String requestId =
                context == null
                        ? "UNKNOWN_REQUEST"
                        : context.requestId();

        return ToolResult.failure(
                requestId,
                toolName,
                toolVersion,
                status,
                issues,
                startedAt,
                Instant.now()
        );
    }

    /**
     * Response가 없는 결과를 지정한 Generic 타입으로 복사합니다.
     */
    private static <S extends ToolResponse>
    ToolResult<S> copyWithoutResponse(
            ToolResult<? extends ToolResponse> source
    ) {
        return new ToolResult<>(
                source.executionId(),
                source.requestId(),
                source.toolName(),
                source.toolVersion(),
                source.status(),
                null,
                source.issues(),
                source.startedAt(),
                source.completedAt(),
                source.duration(),
                source.attributes()
        );
    }

    /**
     * Response가 있는 결과를 지정한 Generic 타입으로 복사합니다.
     */
    private static <S extends ToolResponse>
    ToolResult<S> castResult(
            ToolResult<? extends ToolResponse> source,
            S response
    ) {
        return new ToolResult<>(
                source.executionId(),
                source.requestId(),
                source.toolName(),
                source.toolVersion(),
                source.status(),
                response,
                source.issues(),
                source.startedAt(),
                source.completedAt(),
                source.duration(),
                source.attributes()
        );
    }
}