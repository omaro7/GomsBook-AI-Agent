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

import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationRequest;
import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationResponse;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidationResult;
import kr.co.goms.gomsbook.ai.xhtml.XhtmlGenerationService;

/**
 * 사용자 콘텐츠를 EPUB3 XHTML 문서로 생성하는 Agent Tool입니다.
 *
 * <p>LLM Tool 이름:</p>
 *
 * <pre>
 * generate_xhtml
 * </pre>
 *
 * <p>입력 예시:</p>
 *
 * <pre>
 * {
 *   "title": "꽃은 자신을 재촉하지 않는다",
 *   "content": "봄꽃은 피어날 때를 스스로 알고 있다.",
 *   "author": "한정훈",
 *   "chapter": "1부 1장",
 *   "style": "문학적이고 차분한 문체",
 *   "instruction": "각 문단에 p_01 형식의 id를 부여한다."
 * }
 * </pre>
 */
public final class GenerateXhtmlTool implements AgentTool {

    public static final String TOOL_NAME = "generate_xhtml";

    public static final String ARG_TITLE = "title";
    public static final String ARG_CONTENT = "content";
    public static final String ARG_AUTHOR = "author";
    public static final String ARG_CHAPTER = "chapter";
    public static final String ARG_LANGUAGE = "language";
    public static final String ARG_STYLE = "style";
    public static final String ARG_INSTRUCTION = "instruction";
    public static final String ARG_VALIDATE = "validate";

    private static final String DEFAULT_LANGUAGE = "ko";
    private static final boolean DEFAULT_VALIDATE = true;

    private final XhtmlGenerationService generationService;

    /**
     * XHTML 생성 Tool을 생성합니다.
     *
     * @param generationService XHTML 생성 서비스
     */
    public GenerateXhtmlTool(
            XhtmlGenerationService generationService) {

        this.generationService = Objects.requireNonNull(
                generationService,
                "generationService must not be null"
        );
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return """
                Generates a complete, valid EPUB3 XHTML document from
                a title and source content.

                Use this tool when a new XHTML chapter, section, essay,
                or book page must be created.

                The result includes the generated XHTML and its validation
                status. This tool does not automatically save or replace
                content in the editor.
                """.trim();
    }

    /**
     * LLM에 공개할 입력 JSON Schema를 반환합니다.
     */
    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                ARG_TITLE,
                stringProperty(
                        "The title of the XHTML document or chapter."
                )
        );

        properties.put(
                ARG_CONTENT,
                stringProperty(
                        "The source text or instructions to convert into XHTML."
                )
        );

        properties.put(
                ARG_AUTHOR,
                stringProperty(
                        "The author name. Optional."
                )
        );

        properties.put(
                ARG_CHAPTER,
                stringProperty(
                        "The chapter or section label. Optional."
                )
        );

        properties.put(
                ARG_LANGUAGE,
                Map.of(
                        "type", "string",
                        "description",
                        "The document language code.",
                        "default", DEFAULT_LANGUAGE
                )
        );

        properties.put(
                ARG_STYLE,
                stringProperty(
                        "The preferred writing or XHTML layout style. Optional."
                )
        );

        properties.put(
                ARG_INSTRUCTION,
                stringProperty(
                        "Additional XHTML generation requirements. Optional."
                )
        );

        properties.put(
                ARG_VALIDATE,
                Map.of(
                        "type", "boolean",
                        "description",
                        "Whether the generated XHTML should be validated.",
                        "default", DEFAULT_VALIDATE
                )
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put(
                "required",
                List.of(
                        ARG_TITLE,
                        ARG_CONTENT
                )
        );
        schema.put("additionalProperties", false);

        return schema;
    }

    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        if (request == null) {
            return ToolValidationResult.invalid(
                    issue(
                            "XHTML_GENERATION_REQUEST_REQUIRED",
                            "request",
                            "Tool request must not be null."
                    )
            );
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        validateRequiredString(
                request,
                ARG_TITLE,
                issues
        );

        validateRequiredString(
                request,
                ARG_CONTENT,
                issues
        );

        validateOptionalString(
                request,
                ARG_AUTHOR,
                issues
        );

        validateOptionalString(
                request,
                ARG_CHAPTER,
                issues
        );

        validateOptionalString(
                request,
                ARG_LANGUAGE,
                issues
        );

        validateOptionalString(
                request,
                ARG_STYLE,
                issues
        );

        validateOptionalString(
                request,
                ARG_INSTRUCTION,
                issues
        );

        validateOptionalBoolean(
                request,
                ARG_VALIDATE,
                issues
        );

        if (issues.isEmpty()) {
            return ToolValidationResult.valid();
        }

        return ToolValidationResult.builder()
                .valid(false)
                .message(
                        "Generate XHTML Tool arguments are invalid."
                )
                .issues(issues)
                .build();
    }

    /**
     * XHTML 생성을 실행합니다.
     */
    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        long startedNanos =
                System.nanoTime();

        try {
            XhtmlGenerationRequest generationRequest =
                    createGenerationRequest(request);

            XhtmlGenerationResponse response =
                    generationService.generate(
                            generationRequest
                    );

            if (response == null) {
                return failure(
                        request,
                        startedNanos,
                        "XHTML_GENERATION_NULL_RESPONSE",
                        "XHTML generation service returned null.",
                        null
                );
            }

            return createSuccessResult(
                    request,
                    response,
                    startedNanos
            );

        } catch (RuntimeException exception) {
            return failure(
                    request,
                    startedNanos,
                    "XHTML_GENERATION_FAILED",
                    resolveExceptionMessage(exception),
                    exception
            );
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return generationService.isAvailable();

        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * ToolRequest를 XHTML 생성 요청으로 변환합니다.
     *
     * <p>이 코드는 XhtmlGenerationRequest가 Builder를 제공한다는
     * 기준입니다. 기존 클래스가 record 또는 생성자 기반이라면
     * 이 메서드만 실제 API에 맞게 변경하면 됩니다.</p>
     */
    private XhtmlGenerationRequest createGenerationRequest(
            ToolRequest request) {

        String title =
                request.requireStringArgument(
                        ARG_TITLE
                );

        String content =
                request.requireStringArgument(
                        ARG_CONTENT
                );

        String author =
                optionalString(
                        request,
                        ARG_AUTHOR
                );

        String chapter =
                optionalString(
                        request,
                        ARG_CHAPTER
                );

        String language =
                optionalString(
                        request,
                        ARG_LANGUAGE
                );

        String style =
                optionalString(
                        request,
                        ARG_STYLE
                );

        String instruction =
                optionalString(
                        request,
                        ARG_INSTRUCTION
                );

        boolean validate =
                optionalBoolean(
                        request,
                        ARG_VALIDATE,
                        DEFAULT_VALIDATE
                );

        return XhtmlGenerationRequest.builder()
                .title(title)
                .content(content)
                .author(author)
                .chapter(chapter)
                .language(
                        language == null
                                ? DEFAULT_LANGUAGE
                                : language
                )
                .style(style)
                .instruction(instruction)
                .validationEnabled(validate)
                .build();
    }

    /**
     * XHTML 생성 응답을 ToolResult로 변환합니다.
     *
     * <p>아래 코드는 XhtmlGenerationResponse에 getXhtml(),
     * getRawContent(), isValid(), getValidationResult(), getModel()이
     * 있다고 가정합니다.</p>
     */
    private ToolResult createSuccessResult(
            ToolRequest request,
            XhtmlGenerationResponse response,
            long startedNanos) {

        String xhtml =
                normalizeRequiredResult(
                        response.getXhtml(),
                        "Generated XHTML is empty."
                );

        XhtmlValidationResult validationResult =
                response.getValidationResult();

        boolean valid =
                response.isValid();

        ToolStatus status =
                valid
                        ? ToolStatus.SUCCESS
                        : ToolStatus.VALIDATION_FAILED;

        List<ToolIssue> issues =
                createValidationIssues(
                        validationResult,
                        valid
                );

        ToolResult.Builder builder =
                ToolResult.builder()
                        .requestId(
                                request.getRequestId()
                        )
                        .toolCallId(
                                request.getToolCallId()
                        )
                        .toolName(getName())
                        .status(status)
                        .message(
                                valid
                                        ? "XHTML generation completed successfully."
                                        : "XHTML was generated but failed validation."
                        )
                        .data("xhtml", xhtml)
                        .data("valid", valid)
                        .data(
                                "durationMillis",
                                elapsedMillis(startedNanos)
                        );

        if (response.getModel() != null
                && !response.getModel().isBlank()) {

            builder.data(
                    "model",
                    response.getModel()
            );
        }

        if (response.getRawContent() != null
                && !response.getRawContent().isBlank()) {

            builder.data(
                    "rawContent",
                    response.getRawContent()
            );
        }

        if (validationResult != null) {
            builder.data(
                    "validationResult",
                    validationResult
            );
        }

        if (!issues.isEmpty()) {
            builder.issues(issues);
            builder.data(
                    "issues",
                    toIssueData(issues)
            );
        }

        builder.validationResult(
                createToolValidationResult(
                        valid,
                        issues
                )
        );

        return builder.build();
    }

    private ToolValidationResult createToolValidationResult(
            boolean valid,
            List<ToolIssue> issues) {

        if (valid) {
            return ToolValidationResult.valid(
                    "Generated XHTML is valid."
            );
        }

        return ToolValidationResult.builder()
                .valid(false)
                .message(
                        "Generated XHTML failed validation."
                )
                .issues(issues)
                .build();
    }

    /**
     * XHTML 검증 결과를 공통 ToolIssue로 변환합니다.
     *
     * <p>현재는 XhtmlValidationResult의 상세 이슈 API를 확정하지
     * 않았으므로 유효성 여부를 기준으로 최소 이슈를 생성합니다.</p>
     */
    private List<ToolIssue> createValidationIssues(
            XhtmlValidationResult validationResult,
            boolean valid) {

        if (valid) {
            return List.of();
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        issues.add(
                ToolIssue.builder()
                        .code(
                                "GENERATED_XHTML_INVALID"
                        )
                        .severity(
                                ToolIssueSeverity.ERROR
                        )
                        .field("xhtml")
                        .message(
                                "The generated XHTML document is invalid."
                        )
                        .detail(
                                "validationResultAvailable",
                                validationResult != null
                        )
                        .build()
        );

        /*
         * XhtmlValidationResult#getIssues()가 존재한다면 이곳에서
         * 실제 검증 이슈를 ToolIssue로 변환하는 것이 좋습니다.
         */

        return issues;
    }

    private ToolResult failure(
            ToolRequest request,
            long startedNanos,
            String errorCode,
            String errorMessage,
            Throwable cause) {

        ToolResult.Builder builder =
                ToolResult.failure(
                                getName(),
                                errorMessage,
                                cause
                        )
                        .requestId(
                                request.getRequestId()
                        )
                        .toolCallId(
                                request.getToolCallId()
                        )
                        .errorCode(errorCode)
                        .data(
                                "durationMillis",
                                elapsedMillis(startedNanos)
                        );

        return builder.build();
    }

    private void validateRequiredString(
            ToolRequest request,
            String argumentName,
            List<ToolIssue> issues) {

        Object value =
                request.getArgument(argumentName);

        if (value == null) {
            issues.add(
                    issue(
                            "REQUIRED_ARGUMENT_MISSING",
                            argumentName,
                            "Required argument is missing: "
                                    + argumentName
                    )
            );

            return;
        }

        if (!(value instanceof String stringValue)) {
            issues.add(
                    typeIssue(
                            argumentName,
                            "string",
                            value
                    )
            );

            return;
        }

        if (stringValue.isBlank()) {
            issues.add(
                    issue(
                            "REQUIRED_ARGUMENT_BLANK",
                            argumentName,
                            "Required argument must not be blank: "
                                    + argumentName
                    )
            );
        }
    }

    private void validateOptionalString(
            ToolRequest request,
            String argumentName,
            List<ToolIssue> issues) {

        Object value =
                request.getArgument(argumentName);

        if (value != null
                && !(value instanceof String)) {

            issues.add(
                    typeIssue(
                            argumentName,
                            "string",
                            value
                    )
            );
        }
    }

    private void validateOptionalBoolean(
            ToolRequest request,
            String argumentName,
            List<ToolIssue> issues) {

        Object value =
                request.getArgument(argumentName);

        if (value != null
                && !(value instanceof Boolean)) {

            issues.add(
                    typeIssue(
                            argumentName,
                            "boolean",
                            value
                    )
            );
        }
    }

    private ToolIssue issue(
            String code,
            String field,
            String message) {

        return ToolIssue.builder()
                .code(code)
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .field(field)
                .message(message)
                .build();
    }

    private ToolIssue typeIssue(
            String field,
            String expectedType,
            Object actualValue) {

        return ToolIssue.builder()
                .code("ARGUMENT_TYPE_MISMATCH")
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .field(field)
                .message(
                        "Argument type mismatch: "
                                + field
                )
                .detail(
                        "expectedType",
                        expectedType
                )
                .detail(
                        "actualType",
                        actualValue == null
                                ? "null"
                                : actualValue
                                    .getClass()
                                    .getName()
                )
                .build();
    }

    private String optionalString(
            ToolRequest request,
            String name) {

        Object value =
                request.getArgument(name);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(
                    name + " must be a string"
            );
        }

        return stringValue.isBlank()
                ? null
                : stringValue.trim();
    }

    private boolean optionalBoolean(
            ToolRequest request,
            String name,
            boolean defaultValue) {

        Object value =
                request.getArgument(name);

        if (value == null) {
            return defaultValue;
        }

        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException(
                    name + " must be boolean"
            );
        }

        return booleanValue;
    }

    private Map<String, Object> stringProperty(
            String description) {

        return Map.of(
                "type", "string",
                "description", description
        );
    }

    private String normalizeRequiredResult(
            String value,
            String errorMessage) {

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    errorMessage
            );
        }

        return value.trim();
    }

    private String resolveExceptionMessage(
            RuntimeException exception) {

        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {

            return "XHTML generation execution failed.";
        }

        return exception.getMessage();
    }

    private List<Map<String, Object>> toIssueData(
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
            item.put(
                    "message",
                    issue.getMessage()
            );

            if (issue.hasField()) {
                item.put(
                        "field",
                        issue.getField()
                );
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

    private long elapsedMillis(long startedNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedNanos)
                        / 1_000_000L
        );
    }
}