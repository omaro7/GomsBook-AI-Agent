/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

/**
 * GomsBook AI Agent에서 실행 가능한 모든 Tool이 구현하는 공통 인터페이스입니다.
 *
 * <p>
 * 각 Tool은 하나의 요청 타입과 하나의 응답 타입을 가지며,
 * Tool 실행 결과는 {@link ToolResult}로 반환합니다.
 * </p>
 *
 * <p>
 * Tool 구현체는 다음 책임을 가집니다.
 * </p>
 *
 * <ul>
 *   <li>Tool 이름과 버전 정의</li>
 *   <li>지원하는 요청 및 응답 타입 정의</li>
 *   <li>요청값 검증</li>
 *   <li>Tool 실행</li>
 *   <li>표준화된 실행 결과 반환</li>
 * </ul>
 *
 * @param <R> Tool 요청 타입
 * @param <S> Tool 응답 타입
 */
public interface AgentTool<
        R extends ToolRequest,
        S extends ToolResponse> {

    /**
     * Tool을 식별하는 고유 이름을 반환합니다.
     *
     * <p>
     * 이름은 Tool Registry와 Planner에서 사용되므로
     * 프로젝트 전체에서 중복되지 않아야 합니다.
     * </p>
     *
     * <p>
     * 권장 형식:
     * </p>
     *
     * <pre>
     * xhtml.generate
     * xhtml.validate
     * epub.validate
     * accessibility.check
     * metadata.generate
     * </pre>
     *
     * @return Tool 고유 이름
     */
    String getName();

    /**
     * Tool의 기능을 설명하는 문장을 반환합니다.
     *
     * <p>
     * 이 설명은 Tool Registry, AI Planner,
     * Tool Calling Schema 및 UI에서 사용할 수 있습니다.
     * </p>
     *
     * @return Tool 설명
     */
    String getDescription();

    /**
     * Tool 버전을 반환합니다.
     *
     * <p>
     * Semantic Versioning 형식을 권장합니다.
     * </p>
     *
     * <pre>
     * 1.0.0
     * 1.1.0
     * 2.0.0
     * </pre>
     *
     * @return Tool 버전
     */
    String getVersion();

    /**
     * Tool이 처리하는 요청 타입을 반환합니다.
     *
     * @return 요청 클래스
     */
    Class<R> getRequestType();

    /**
     * Tool이 반환하는 응답 타입을 반환합니다.
     *
     * @return 응답 클래스
     */
    Class<S> getResponseType();

    /**
     * Tool이 현재 사용 가능한지 반환합니다.
     *
     * <p>
     * 기본값은 {@code true}입니다.
     * 외부 서비스, Local LLM 또는 특정 실행 환경에 의존하는 Tool은
     * 이 메서드를 재정의할 수 있습니다.
     * </p>
     *
     * @return 사용 가능하면 true
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Tool 요청을 검증합니다.
     *
     * <p>
     * 기본 구현은 다음 순서로 검증합니다.
     * </p>
     *
     * <ol>
     *   <li>요청 객체 null 여부 확인</li>
     *   <li>요청 타입 일치 여부 확인</li>
     *   <li>{@link ToolRequest#validate()} 호출</li>
     * </ol>
     *
     * @param request 검증할 요청
     * @return 검증 결과
     */
    default ToolValidationResult validateRequest(
            R request
    ) {
        if (request == null) {
            return ToolValidationResult.failure(
                    new ToolIssue(
                            "TOOL_REQUEST_REQUIRED",
                            ToolIssueSeverity.ERROR,
                            "Tool 요청 데이터가 없습니다.",
                            "Tool 실행을 위해 요청 객체가 필요합니다.",
                            null,
                            null,
                            null,
                            java.util.Map.of(
                                    "toolName",
                                    getName()
                            )
                    )
            );
        }

        if (!getRequestType().isInstance(request)) {
            return ToolValidationResult.failure(
                    new ToolIssue(
                            "TOOL_REQUEST_TYPE_MISMATCH",
                            ToolIssueSeverity.ERROR,
                            "Tool 요청 타입이 올바르지 않습니다.",
                            "Expected: "
                                    + getRequestType().getName()
                                    + ", Actual: "
                                    + request.getClass().getName(),
                            null,
                            null,
                            null,
                            java.util.Map.of(
                                    "toolName",
                                    getName(),
                                    "expectedType",
                                    getRequestType().getName(),
                                    "actualType",
                                    request.getClass().getName()
                            )
                    )
            );
        }

        ToolValidationResult validationResult =
                request.validate();

        if (validationResult == null) {
            return ToolValidationResult.failure(
                    new ToolIssue(
                            "TOOL_REQUEST_VALIDATION_NULL",
                            ToolIssueSeverity.ERROR,
                            "요청 검증 결과가 없습니다.",
                            "ToolRequest.validate()는 null을 반환할 수 없습니다.",
                            null,
                            null,
                            null,
                            java.util.Map.of(
                                    "toolName",
                                    getName(),
                                    "requestType",
                                    request.getClass().getName()
                            )
                    )
            );
        }

        return validationResult;
    }

    /**
     * Tool을 실행합니다.
     *
     * <p>
     * Tool 구현체는 이 메서드 안에서 다음 순서를 따르는 것이 좋습니다.
     * </p>
     *
     * <ol>
     *   <li>요청 검증</li>
     *   <li>Tool 실행</li>
     *   <li>응답 생성</li>
     *   <li>Issue 정리</li>
     *   <li>{@link ToolResult} 반환</li>
     * </ol>
     *
     * <p>
     * Tool 구현체는 예외를 그대로 외부에 노출하기보다,
     * 가능한 경우 표준화된 {@link ToolIssue}와
     * {@link ToolResult}로 변환해야 합니다.
     * </p>
     *
     * @param context Tool 실행 환경
     * @param request Tool 요청 데이터
     * @return Tool 실행 결과
     */
    ToolResult<S> execute(
            ToolContext context,
            R request
    );
}