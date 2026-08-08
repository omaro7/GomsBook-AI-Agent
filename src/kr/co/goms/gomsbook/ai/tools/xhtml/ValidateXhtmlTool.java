/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.xhtml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidationResult;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidator;

/**
 * XHTML 문서의 문법과 기본 구조를 검증하는 Agent Tool입니다.
 *
 * <p>이 Tool은 문서를 변경하지 않으며, 전달받은 XHTML 문자열을
 * 검증한 뒤 검증 결과와 이슈 목록을 반환합니다.</p>
 *
 * <p>LLM Tool 이름:</p>
 *
 * <pre>
 * validate_xhtml
 * </pre>
 *
 * <p>입력 예시:</p>
 *
 * <pre>
 * {
 *   "xhtml": "&lt;!DOCTYPE html&gt;...",
 *   "strict": true
 * }
 * </pre>
 */
public final class ValidateXhtmlTool implements AgentTool {

    public static final String TOOL_NAME = "validate_xhtml";

    public static final String ARG_XHTML = "xhtml";
    public static final String ARG_STRICT = "strict";

    private final XhtmlValidator xhtmlValidator;

    /**
     * XHTML 검증 Tool을 생성합니다.
     *
     * @param xhtmlValidator XHTML 검증기
     */
    public ValidateXhtmlTool(
            XhtmlValidator xhtmlValidator) {

        this.xhtmlValidator = Objects.requireNonNull(
                xhtmlValidator,
                "xhtmlValidator must not be null"
        );
    }

    /**
     * LLM과 ToolRegistry에서 사용할 Tool 이름을 반환합니다.
     */
    @Override
    public String getName() {
        return TOOL_NAME;
    }

    /**
     * LLM이 Tool 호출 여부를 판단할 때 사용하는 설명입니다.
     */
    @Override
    public String getDescription() {
        return """
                Validates an EPUB3 XHTML document.

                Use this tool when you need to check XHTML syntax,
                required document elements, namespaces, accessibility,
                duplicate identifiers, or EPUB compatibility.

                This tool does not modify the XHTML document.
                """.trim();
    }

    /**
     * LLM에 제공할 입력 JSON Schema를 반환합니다.
     */
    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                ARG_XHTML,
                Map.of(
                        "type", "string",
                        "description",
                        "The complete XHTML document to validate."
                )
        );

        properties.put(
                ARG_STRICT,
                Map.of(
                        "type", "boolean",
                        "description",
                        "Whether warnings should also be treated as validation failures.",
                        "default", false
                )
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(ARG_XHTML));
        schema.put("additionalProperties", false);

        return schema;
    }

    /**
     * Tool 요청 인자를 검증합니다.
     */
    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        if (request == null) {
            return ToolValidationResult.invalid(
                    ToolIssue.builder()
                            .code("XHTML_REQUEST_REQUIRED")
                            .severity(ToolIssueSeverity.ERROR)
                            .field("request")
                            .message(
                                    "Tool request must not be null."
                            )
                            .build()
            );
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        Object xhtmlValue =
                request.getArgument(ARG_XHTML);

        if (xhtmlValue == null) {
            issues.add(
                    ToolIssue.builder()
                            .code("XHTML_ARGUMENT_REQUIRED")
                            .severity(ToolIssueSeverity.ERROR)
                            .field(ARG_XHTML)
                            .message(
                                    "The XHTML argument is required."
                            )
                            .build()
            );

        } else if (!(xhtmlValue instanceof String)) {
            issues.add(
                    ToolIssue.builder()
                            .code("XHTML_ARGUMENT_TYPE")
                            .severity(ToolIssueSeverity.ERROR)
                            .field(ARG_XHTML)
                            .message(
                                    "The XHTML argument must be a string."
                            )
                            .detail(
                                    "actualType",
                                    xhtmlValue
                                            .getClass()
                                            .getName()
                            )
                            .build()
            );

        } else if (((String) xhtmlValue).isBlank()) {
            issues.add(
                    ToolIssue.builder()
                            .code("XHTML_ARGUMENT_BLANK")
                            .severity(ToolIssueSeverity.ERROR)
                            .field(ARG_XHTML)
                            .message(
                                    "The XHTML argument must not be blank."
                            )
                            .build()
            );
        }

        Object strictValue =
                request.getArgument(ARG_STRICT);

        if (strictValue != null
                && !(strictValue instanceof Boolean)) {

            issues.add(
                    ToolIssue.builder()
                            .code("STRICT_ARGUMENT_TYPE")
                            .severity(ToolIssueSeverity.ERROR)
                            .field(ARG_STRICT)
                            .message(
                                    "The strict argument must be boolean."
                            )
                            .detail(
                                    "actualType",
                                    strictValue
                                            .getClass()
                                            .getName()
                            )
                            .build()
            );
        }

        if (issues.isEmpty()) {
            return ToolValidationResult.valid();
        }

        return ToolValidationResult.builder()
                .valid(false)
                .message(
                        "Validate XHTML Tool arguments are invalid."
                )
                .issues(issues)
                .build();
    }

    /**
     * XHTML 검증을 실행합니다.
     */
    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        String xhtml =
                request.requireStringArgument(ARG_XHTML);

        boolean strict =
                resolveStrict(request);

        long startedNanos =
                System.nanoTime();

        XhtmlValidationResult validationResult;

        try {
            validationResult =
                    xhtmlValidator.validate(xhtml);

        } catch (RuntimeException exception) {
            return createExecutionFailure(
                    request,
                    startedNanos,
                    exception
            );
        }

        if (validationResult == null) {
            return createInvalidValidatorResult(
                    request,
                    startedNanos
            );
        }

        return createToolResult(
                request,
                validationResult,
                strict,
                startedNanos
        );
    }

    /**
     * 현재 Tool의 사용 가능 여부를 반환합니다.
     */
    @Override
    public boolean isAvailable() {
        return xhtmlValidator != null;
    }

    /**
     * XHTML 검증 결과를 ToolResult로 변환합니다.
     */
    private ToolResult createToolResult(
            ToolRequest request,
            XhtmlValidationResult validationResult,
            boolean strict,
            long startedNanos) {

        List<ToolIssue> issues =
                convertIssues(validationResult);

        boolean hasErrors =
                issues.stream()
                        .anyMatch(ToolIssue::isError);

        boolean hasWarnings =
                issues.stream()
                        .anyMatch(ToolIssue::isWarning);

        boolean valid =
                validationResult.isValid()
                        && !hasErrors
                        && (!strict || !hasWarnings);

        ToolStatus status =
                valid
                        ? ToolStatus.SUCCESS
                        : ToolStatus.VALIDATION_FAILED;

        ToolResult.Builder builder =
                ToolResult.builder()
                        .requestId(request.getRequestId())
                        .toolCallId(request.getToolCallId())
                        .toolName(getName())
                        .status(status)
                        .message(
                                createResultMessage(
                                        valid,
                                        strict,
                                        issues
                                )
                        )
                        .data("valid", valid)
                        .data(
                                "validatorValid",
                                validationResult.isValid()
                        )
                        .data("strict", strict)
                        .data(
                                "issueCount",
                                issues.size()
                        )
                        .data(
                                "errorCount",
                                countSeverity(
                                        issues,
                                        ToolIssueSeverity.ERROR,
                                        ToolIssueSeverity.FATAL
                                )
                        )
                        .data(
                                "warningCount",
                                countSeverity(
                                        issues,
                                        ToolIssueSeverity.WARNING
                                )
                        )
                        .data(
                                "durationMillis",
                                elapsedMillis(startedNanos)
                        )
                        .validationResult(
                                toToolValidationResult(
                                        valid,
                                        issues
                                )
                        );

        if (!issues.isEmpty()) {
            builder.issues(issues);
            builder.data(
                    "issues",
                    createIssueData(issues)
            );
        }

        return builder.build();
    }

    /**
     * XHTML 검증 이슈를 Tool 공통 이슈로 변환합니다.
     *
     * <p>현재 XhtmlValidationResult의 세부 API가 프로젝트마다
     * 다를 수 있으므로, 기본 구현은 유효성 여부를 기준으로 최소한의
     * 공통 이슈를 생성합니다.</p>
     *
     * <p>XhtmlValidationResult에 getIssues()가 있다면 이 메서드에서
     * 각 이슈를 ToolIssue로 세밀하게 변환하면 됩니다.</p>
     */
    private List<ToolIssue> convertIssues(
            XhtmlValidationResult validationResult) {

        List<ToolIssue> issues =
                new ArrayList<>();

        /*
         * XhtmlValidationResult가 이슈 목록을 제공한다면 다음 형태로
         * 교체하는 것을 권장합니다.
         *
         * for (XhtmlValidationIssue issue
         *         : validationResult.getIssues()) {
         *
         *     issues.add(
         *             ToolIssue.builder()
         *                     .code(issue.getCode())
         *                     .severity(
         *                             mapSeverity(
         *                                     issue.getSeverity()
         *                             )
         *                     )
         *                     .message(issue.getMessage())
         *                     .field(issue.getLocation())
         *                     .build()
         *     );
         * }
         */

        if (!validationResult.isValid()) {
            issues.add(
                    ToolIssue.builder()
                            .code("XHTML_VALIDATION_FAILED")
                            .severity(ToolIssueSeverity.ERROR)
                            .field(ARG_XHTML)
                            .message(
                                    "The XHTML document failed validation."
                            )
                            .build()
            );
        }

        return issues;
    }

    /**
     * ToolValidationResult를 생성합니다.
     */
    private ToolValidationResult toToolValidationResult(
            boolean valid,
            List<ToolIssue> issues) {

        if (valid) {
            if (issues.isEmpty()) {
                return ToolValidationResult.valid(
                        "The XHTML document is valid."
                );
            }

            return ToolValidationResult.builder()
                    .valid(true)
                    .message(
                            "The XHTML document is valid with informational issues."
                    )
                    .issues(issues)
                    .build();
        }

        return ToolValidationResult.builder()
                .valid(false)
                .message(
                        "The XHTML document is invalid."
                )
                .issues(issues)
                .build();
    }

    /**
     * ToolResult에 포함할 단순 Map 이슈 목록을 생성합니다.
     */
    private List<Map<String, Object>> createIssueData(
            List<ToolIssue> issues) {

        List<Map<String, Object>> result =
                new ArrayList<>(issues.size());

        for (ToolIssue issue : issues) {
            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put("code", issue.getCode());
            item.put(
                    "severity",
                    issue.getSeverity().name()
            );
            item.put("message", issue.getMessage());

            if (issue.hasField()) {
                item.put("field", issue.getField());
            }

            if (issue.hasDetails()) {
                item.put(
                        "details",
                        issue.getDetails()
                );
            }

            result.add(item);
        }

        return result;
    }

    /**
     * strict 옵션을 반환합니다.
     */
    private boolean resolveStrict(
            ToolRequest request) {

        Object value =
                request.getArgument(ARG_STRICT);

        if (value == null) {
            return false;
        }

        if (!(value instanceof Boolean strict)) {
            throw new IllegalArgumentException(
                    "strict argument must be boolean"
            );
        }

        return strict;
    }

    /**
     * 검증 결과 메시지를 생성합니다.
     */
    private String createResultMessage(
            boolean valid,
            boolean strict,
            List<ToolIssue> issues) {

        if (valid) {
            if (issues.isEmpty()) {
                return "XHTML validation completed successfully.";
            }

            return "XHTML validation completed with "
                    + issues.size()
                    + " non-blocking issue(s).";
        }

        if (strict) {
            return "XHTML validation failed in strict mode.";
        }

        return "XHTML validation failed with "
                + issues.size()
                + " issue(s).";
    }

    /**
     * 특정 심각도 이슈 개수를 계산합니다.
     */
    private int countSeverity(
            List<ToolIssue> issues,
            ToolIssueSeverity... severities) {

        int count = 0;

        for (ToolIssue issue : issues) {
            for (ToolIssueSeverity severity : severities) {
                if (issue.getSeverity() == severity) {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    /**
     * Validator가 null을 반환한 경우의 실패 결과를 생성합니다.
     */
    private ToolResult createInvalidValidatorResult(
            ToolRequest request,
            long startedNanos) {

        return ToolResult.failure(
                        getName(),
                        "XHTML validator returned null."
                )
                .requestId(request.getRequestId())
                .toolCallId(request.getToolCallId())
                .errorCode(
                        "XHTML_VALIDATOR_NULL_RESULT"
                )
                .data(
                        "durationMillis",
                        elapsedMillis(startedNanos)
                )
                .build();
    }

    /**
     * Validator 실행 중 예외가 발생한 경우의 결과를 생성합니다.
     */
    private ToolResult createExecutionFailure(
            ToolRequest request,
            long startedNanos,
            RuntimeException exception) {

        String errorMessage =
                exception.getMessage() == null
                        || exception.getMessage().isBlank()
                        ? "XHTML validation execution failed."
                        : exception.getMessage();

        return ToolResult.failure(
                        getName(),
                        errorMessage,
                        exception
                )
                .requestId(request.getRequestId())
                .toolCallId(request.getToolCallId())
                .errorCode(
                        "XHTML_VALIDATION_EXECUTION_FAILED"
                )
                .data(
                        "durationMillis",
                        elapsedMillis(startedNanos)
                )
                .build();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedNanos)
                        / 1_000_000L
        );
    }
}