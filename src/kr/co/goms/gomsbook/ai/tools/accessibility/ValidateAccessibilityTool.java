/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.accessibility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssue;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode.AccessibilityCategory;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityLocation;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleContext;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleException;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilitySeverity;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationException;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationRequest;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationResult;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;



/**
 * XHTML 문서의 접근성 문제를 검사하는 Agent Tool.
 *
 * <p>이 Tool은 요청을 {@link AccessibilityValidationRequest}로 변환하고
 * {@link AccessibilityValidator}에 검사를 위임한다.</p>
 *
 * <p>문서 파일을 수정하지 않으며, 발견된 접근성 문제와 통계만
 * 구조화된 Tool 결과로 반환한다.</p>
 */
public final class ValidateAccessibilityTool implements AgentTool {

    public static final String TOOL_NAME =
            "validate_accessibility";

    private static final String DESCRIPTION =
            "Validates EPUB XHTML accessibility using registered rules. "
                    + "Checks image alternative text, document language, "
                    + "heading structure, links, tables and ARIA attributes. "
                    + "This tool does not modify project files.";

    private static final int DEFAULT_MAX_ALT_TEXT_LENGTH = 150;
    private static final int MIN_ALT_TEXT_LENGTH = 20;
    private static final int MAX_ALT_TEXT_LENGTH = 2_000;

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(
                    "xhtml",
                    "html",
                    "htm",
                    "opf",
                    "xml",
                    "svg"
            );

    private final AccessibilityValidator validator;

    public ValidateAccessibilityTool(
            AccessibilityValidator validator) {

        this.validator = Objects.requireNonNull(
                validator,
                "validator must not be null"
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

    /**
     * LLM Tool 호출에 사용할 JSON Schema를 반환한다.
     *
     * @return 입력 스키마
     */
    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> documentPath = property(
                "string",
                "Project-relative XHTML, HTML, OPF, XML or SVG path."
        );

        Map<String, Object> strictMode = property(
                "boolean",
                "Whether accessibility recommendations should be "
                        + "evaluated using stricter severity."
        );
        strictMode.put("default", false);

        Map<String, Object> includeInformationalIssues = property(
                "boolean",
                "Whether informational accessibility recommendations "
                        + "should be included."
        );
        includeInformationalIssues.put("default", true);

        Map<String, Object> continueOnRuleError = property(
                "boolean",
                "Whether remaining accessibility rules should continue "
                        + "when one rule fails."
        );
        continueOnRuleError.put("default", true);

        Map<String, Object> maximumAltTextLength = property(
                "integer",
                "Recommended maximum alternative text length."
        );
        maximumAltTextLength.put(
                "minimum",
                MIN_ALT_TEXT_LENGTH
        );
        maximumAltTextLength.put(
                "maximum",
                MAX_ALT_TEXT_LENGTH
        );
        maximumAltTextLength.put(
                "default",
                DEFAULT_MAX_ALT_TEXT_LENGTH
        );

        Map<String, Object> enabledRules = stringArrayProperty(
                "Optional list of accessibility rule IDs to run. "
                        + "When omitted, all default-enabled rules are run."
        );

        Map<String, Object> disabledRules = stringArrayProperty(
                "Optional list of accessibility rule IDs to skip."
        );

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("type", "object");
        options.put(
                "description",
                "Optional rule-specific validation settings."
        );
        options.put(
                "additionalProperties",
                Map.of("type", "string")
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put("documentPath", documentPath);
        properties.put("strictMode", strictMode);
        properties.put(
                "includeInformationalIssues",
                includeInformationalIssues
        );
        properties.put(
                "continueOnRuleError",
                continueOnRuleError
        );
        properties.put(
                "maximumAltTextLength",
                maximumAltTextLength
        );
        properties.put("enabledRules", enabledRules);
        properties.put("disabledRules", disabledRules);
        properties.put("options", options);

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put(
                "required",
                Collections.singletonList("documentPath")
        );
        schema.put("additionalProperties", false);

        return Collections.unmodifiableMap(schema);
    }

    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        List<ToolIssue> issues = new ArrayList<>();

        if (request == null) {
            issues.add(
                    error(
                            "request",
                            "Tool request must not be null."
                    )
            );

            return ToolValidationResult.invalid(issues);
        }

        if (context == null) {
            issues.add(
                    error(
                            "context",
                            "Tool context must not be null."
                    )
            );

            return ToolValidationResult.invalid(issues);
        }

        Path projectRoot = context.getProjectRoot();

        if (projectRoot == null) {
            issues.add(
                    error(
                            "context.projectRoot",
                            "Current project root is not available."
                    )
            );

            return ToolValidationResult.invalid(issues);
        }

        Map<String, Object> arguments =
                safeArguments(request);

        String documentPathValue =
                readString(
                        arguments,
                        "documentPath"
                );

        if (documentPathValue == null
                || documentPathValue.isBlank()) {

            issues.add(
                    error(
                            "documentPath",
                            "documentPath must not be blank."
                    )
            );

        } else {
            validateDocumentPath(
                    projectRoot,
                    documentPathValue,
                    issues
            );
        }

        validateBoolean(
                arguments,
                "strictMode",
                issues
        );

        validateBoolean(
                arguments,
                "includeInformationalIssues",
                issues
        );

        validateBoolean(
                arguments,
                "continueOnRuleError",
                issues
        );

        validateMaximumAltTextLength(
                arguments,
                issues
        );

        validateRuleIds(
                arguments,
                "enabledRules",
                issues
        );

        validateRuleIds(
                arguments,
                "disabledRules",
                issues
        );

        validateRuleConflicts(
                arguments,
                issues
        );

        validateOptions(
                arguments,
                issues
        );

        if (!issues.isEmpty()) {
            return ToolValidationResult.invalid(issues);
        }

        return ToolValidationResult.valid();
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation =
                validate(request, context);

        if (!validation.isValid()) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "Invalid accessibility validation request."
                    )
                    .issues(validation.getIssues())
                    .build();
        }

        Map<String, Object> arguments =
                safeArguments(request);

        String documentPathValue =
                readString(
                        arguments,
                        "documentPath"
                ).trim();

        boolean strictMode =
                defaultBoolean(
                        readBoolean(
                                arguments,
                                "strictMode"
                        ),
                        false
                );

        boolean includeInformationalIssues =
                defaultBoolean(
                        readBoolean(
                                arguments,
                                "includeInformationalIssues"
                        ),
                        true
                );

        boolean continueOnRuleError =
                defaultBoolean(
                        readBoolean(
                                arguments,
                                "continueOnRuleError"
                        ),
                        true
                );

        int maximumAltTextLength =
                defaultInteger(
                        readInteger(
                                arguments,
                                "maximumAltTextLength"
                        ),
                        DEFAULT_MAX_ALT_TEXT_LENGTH
                );

        List<String> enabledRules =
                readStringList(
                        arguments,
                        "enabledRules"
                );

        List<String> disabledRules =
                readStringList(
                        arguments,
                        "disabledRules"
                );

        Map<String, String> options =
                readStringMap(
                        arguments,
                        "options"
                );

        Path projectRoot =
                context.getProjectRoot()
                        .toAbsolutePath()
                        .normalize();

        Path documentPath =
                resolveProjectPath(
                        projectRoot,
                        documentPathValue
                );

        try {
            AccessibilityValidationRequest validationRequest =
                    createValidationRequest(
                            projectRoot,
                            documentPath,
                            strictMode,
                            includeInformationalIssues,
                            continueOnRuleError,
                            maximumAltTextLength,
                            enabledRules,
                            disabledRules,
                            options
                    );

            AccessibilityValidationResult result =
                    validator.validate(
                            validationRequest
                    );

            Map<String, Object> output =
                    createOutput(result);

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message(
                            createResultMessage(result)
                    )
                    .data(output)
                    .build();

        } catch (AccessibilityValidationException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "Accessibility validation failed: "
                                    + safeMessage(exception)
                    )
                    .data(
                            createExceptionOutput(
                                    exception,
                                    documentPath
                            )
                    )
                    .cause(exception)
                    .build();

        } catch (RuntimeException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "Unexpected accessibility validation failure: "
                                    + safeMessage(exception)
                    )
                    .cause(exception)
                    .build();
        }
    }

    private AccessibilityValidationRequest
            createValidationRequest(
                    Path projectRoot,
                    Path documentPath,
                    boolean strictMode,
                    boolean includeInformationalIssues,
                    boolean continueOnRuleError,
                    int maximumAltTextLength,
                    List<String> enabledRules,
                    List<String> disabledRules,
                    Map<String, String> options) {

        AccessibilityValidationRequest.Builder builder =
                AccessibilityValidationRequest.builder()
                        .projectRoot(projectRoot)
                        .documentPath(documentPath)
                        .strictMode(strictMode)
                        .includeInformationalIssues(
                                includeInformationalIssues
                        )
                        .continueOnRuleError(
                                continueOnRuleError
                        )
                        .maximumAltTextLength(
                                maximumAltTextLength
                        )
                        .metadata(
                                "toolName",
                                TOOL_NAME
                        );

        for (String ruleId : enabledRules) {
            builder.enableRule(ruleId);
        }

        for (String ruleId : disabledRules) {
            builder.disableRule(ruleId);
        }

        for (Map.Entry<String, String> entry
                : options.entrySet()) {

            builder.option(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        return builder.build();
    }

    private Map<String, Object> createOutput(
            AccessibilityValidationResult result) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "documentPath",
                result.getProjectRelativePath()
        );

        output.put(
                "validationCompleted",
                result.isValidationCompleted()
        );

        output.put(
                "passed",
                result.isPassed()
        );

        output.put(
                "clean",
                result.isClean()
        );

        output.put(
                "blocksPublication",
                result.blocksPublication()
        );

        output.put(
                "totalIssueCount",
                result.getTotalIssueCount()
        );

        output.put(
                "errorCount",
                result.getErrorCount()
        );

        output.put(
                "warningCount",
                result.getWarningCount()
        );

        output.put(
                "infoCount",
                result.getInfoCount()
        );

        output.put(
                "automaticallyFixableCount",
                result.getAutomaticallyFixableCount()
        );

        output.put(
                "manualReviewCount",
                result.getManualReviewCount()
        );

        output.put(
                "validatorName",
                result.getValidatorName()
        );

        output.put(
                "durationMillis",
                result.getDuration() == null
                        ? null
                        : result.getDuration().toMillis()
        );

        output.put(
                "issues",
                createIssueOutput(result.getIssues())
        );

        output.put(
                "warnings",
                result.getWarnings()
        );

        output.put(
                "severityCounts",
                createSeverityCounts(result)
        );

        output.put(
                "categoryCounts",
                createCategoryCounts(result)
        );

        output.put(
                "metadata",
                result.getMetadata()
        );

        return output;
    }

    private List<Map<String, Object>> createIssueOutput(
            List<AccessibilityIssue> issues) {

        if (issues == null || issues.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> output =
                new ArrayList<>();

        for (AccessibilityIssue issue : issues) {

            if (issue == null) {
                continue;
            }

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "code",
                    issue.getCode().getCode()
            );

            item.put(
                    "displayName",
                    issue.getCode().getDisplayName()
            );

            item.put(
                    "category",
                    issue.getCode()
                            .getCategory()
                            .getCode()
            );

            item.put(
                    "severity",
                    issue.getSeverity().getCode()
            );

            item.put(
                    "message",
                    issue.getMessage()
            );

            item.put(
                    "description",
                    issue.getDescription()
            );

            item.put(
                    "recommendation",
                    issue.getRecommendation()
            );

            item.put(
                    "automaticallyFixable",
                    issue.isAutomaticallyFixable()
            );

            item.put(
                    "manualReviewRequired",
                    issue.isManualReviewRequired()
            );

            item.put(
                    "blocksPublication",
                    issue.blocksPublication()
            );

            item.put(
                    "ruleId",
                    issue.getRuleId()
            );

            item.put(
                    "currentValue",
                    issue.getCurrentValue()
            );

            item.put(
                    "suggestedValue",
                    issue.getSuggestedValue()
            );

            item.put(
                    "relatedValues",
                    issue.getRelatedValues()
            );

            item.put(
                    "location",
                    createLocationOutput(
                            issue.getLocation()
                    )
            );

            item.put(
                    "metadata",
                    issue.getMetadata()
            );

            output.add(item);
        }

        return Collections.unmodifiableList(output);
    }

    private Map<String, Object> createLocationOutput(
            AccessibilityLocation location) {

        if (location == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "documentPath",
                location.getProjectRelativePath()
        );

        output.put(
                "elementName",
                location.getElementName()
        );

        output.put(
                "elementId",
                location.getElementId()
        );

        output.put(
                "attributeName",
                location.getAttributeName()
        );

        output.put(
                "attributeValue",
                location.getAttributeValue()
        );

        output.put(
                "lineNumber",
                location.getLineNumber()
        );

        output.put(
                "columnNumber",
                location.getColumnNumber()
        );

        output.put(
                "xpath",
                location.getXpath()
        );

        output.put(
                "textExcerpt",
                location.getTextExcerpt()
        );

        output.put(
                "display",
                location.toDisplayString()
        );

        output.put(
                "metadata",
                location.getMetadata()
        );

        return output;
    }

    private Map<String, Integer> createSeverityCounts(
            AccessibilityValidationResult result) {

        Map<String, Integer> output =
                new LinkedHashMap<>();

        for (Map.Entry<AccessibilitySeverity, Integer> entry
                : result.getSeverityCounts().entrySet()) {

            output.put(
                    entry.getKey().getCode(),
                    entry.getValue()
            );
        }

        return Collections.unmodifiableMap(output);
    }

    private Map<String, Integer> createCategoryCounts(
            AccessibilityValidationResult result) {

        Map<String, Integer> output =
                new LinkedHashMap<>();

        for (Map.Entry<
                AccessibilityIssueCode.AccessibilityCategory,
                Integer> entry
                : result.getCategoryCounts().entrySet()) {

            output.put(
                    entry.getKey().getCode(),
                    entry.getValue()
            );
        }

        return Collections.unmodifiableMap(output);
    }

    private Map<String, Object> createExceptionOutput(
            AccessibilityValidationException exception,
            Path documentPath) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "errorCode",
                exception.getErrorCode().name()
        );

        output.put(
                "documentPath",
                exception.getDocumentPath() == null
                        ? normalizePath(documentPath)
                        : normalizePath(
                                exception.getDocumentPath()
                        )
        );

        output.put(
                "ruleId",
                exception.getRuleId()
        );

        output.put(
                "retryable",
                exception.isRetryable()
        );

        return output;
    }

    private String createResultMessage(
            AccessibilityValidationResult result) {

        if (!result.isValidationCompleted()) {
            return "Accessibility validation completed partially with "
                    + result.getTotalIssueCount()
                    + " issue(s).";
        }

        if (result.isClean()) {
            return "Accessibility validation completed with no issues.";
        }

        return "Accessibility validation found "
                + result.getTotalIssueCount()
                + " issue(s): "
                + result.getErrorCount()
                + " error(s), "
                + result.getWarningCount()
                + " warning(s), and "
                + result.getInfoCount()
                + " informational issue(s).";
    }

    private void validateDocumentPath(
            Path projectRootValue,
            String documentPathValue,
            List<ToolIssue> issues) {

        try {
            Path projectRoot =
                    projectRootValue
                            .toAbsolutePath()
                            .normalize();

            Path documentPath =
                    resolveProjectPath(
                            projectRoot,
                            documentPathValue
                    );

            if (!documentPath.startsWith(projectRoot)) {
                issues.add(
                        error(
                                "documentPath",
                                "documentPath must be inside "
                                        + "the current project."
                        )
                );

                return;
            }

            if (!java.nio.file.Files.exists(documentPath)) {
                issues.add(
                        error(
                                "documentPath",
                                "Accessibility validation document "
                                        + "does not exist."
                        )
                );

                return;
            }

            if (!java.nio.file.Files.isRegularFile(
                    documentPath)) {

                issues.add(
                        error(
                                "documentPath",
                                "documentPath is not a regular file."
                        )
                );

                return;
            }

            if (!java.nio.file.Files.isReadable(
                    documentPath)) {

                issues.add(
                        error(
                                "documentPath",
                                "Accessibility validation document "
                                        + "is not readable."
                        )
                );
            }

            String extension =
                    getExtension(documentPath);

            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                issues.add(
                        error(
                                "documentPath",
                                "Unsupported accessibility document "
                                        + "extension: "
                                        + extension
                        )
                );
            }

        } catch (RuntimeException exception) {
            issues.add(
                    error(
                            "documentPath",
                            "Invalid documentPath: "
                                    + safeMessage(exception)
                    )
            );
        }
    }

    private void validateMaximumAltTextLength(
            Map<String, Object> arguments,
            List<ToolIssue> issues) {

        if (!arguments.containsKey(
                "maximumAltTextLength")) {

            return;
        }

        Integer value =
                readInteger(
                        arguments,
                        "maximumAltTextLength"
                );

        if (value == null) {
            issues.add(
                    error(
                            "maximumAltTextLength",
                            "maximumAltTextLength must be an integer."
                    )
            );

            return;
        }

        if (value < MIN_ALT_TEXT_LENGTH
                || value > MAX_ALT_TEXT_LENGTH) {

            issues.add(
                    error(
                            "maximumAltTextLength",
                            "maximumAltTextLength must be between "
                                    + MIN_ALT_TEXT_LENGTH
                                    + " and "
                                    + MAX_ALT_TEXT_LENGTH
                                    + "."
                    )
            );
        }
    }

    private void validateRuleIds(
            Map<String, Object> arguments,
            String key,
            List<ToolIssue> issues) {

        Object value = arguments.get(key);

        if (value == null) {
            return;
        }

        if (!(value instanceof List<?> values)) {
            issues.add(
                    error(
                            key,
                            key + " must be an array of rule IDs."
                    )
            );

            return;
        }

        for (int index = 0;
                index < values.size();
                index++) {

            Object item = values.get(index);

            if (!(item instanceof String ruleId)
                    || ruleId.isBlank()) {

                issues.add(
                        error(
                                key + "[" + index + "]",
                                "Rule ID must be a non-blank string."
                        )
                );

                continue;
            }

            if (!validator.hasRule(ruleId.trim())) {
                issues.add(
                        error(
                                key + "[" + index + "]",
                                "Accessibility rule is not registered: "
                                        + ruleId
                        )
                );
            }
        }
    }

    private void validateRuleConflicts(
            Map<String, Object> arguments,
            List<ToolIssue> issues) {

        List<String> enabled =
                readStringList(
                        arguments,
                        "enabledRules"
                );

        List<String> disabled =
                readStringList(
                        arguments,
                        "disabledRules"
                );

        for (String ruleId : enabled) {
            if (disabled.contains(ruleId)) {
                issues.add(
                        error(
                                "enabledRules",
                                "Rule cannot be both enabled and disabled: "
                                        + ruleId
                        )
                );
            }
        }
    }

    private void validateOptions(
            Map<String, Object> arguments,
            List<ToolIssue> issues) {

        Object options = arguments.get("options");

        if (options == null) {
            return;
        }

        if (!(options instanceof Map<?, ?> optionMap)) {
            issues.add(
                    error(
                            "options",
                            "options must be an object."
                    )
            );

            return;
        }

        for (Map.Entry<?, ?> entry
                : optionMap.entrySet()) {

            if (!(entry.getKey() instanceof String key)
                    || key.isBlank()) {

                issues.add(
                        error(
                                "options",
                                "Option keys must be non-blank strings."
                        )
                );
            }

            if (entry.getValue() == null) {
                issues.add(
                        error(
                                "options."
                                        + String.valueOf(
                                                entry.getKey()
                                        ),
                                "Option values must not be null."
                        )
                );
            }
        }
    }

    private void validateBoolean(
            Map<String, Object> arguments,
            String key,
            List<ToolIssue> issues) {

        if (!arguments.containsKey(key)) {
            return;
        }

        if (readBoolean(arguments, key) == null) {
            issues.add(
                    error(
                            key,
                            key + " must be a boolean value."
                    )
            );
        }
    }

    private Path resolveProjectPath(
            Path projectRoot,
            String pathValue) {

        Path requestedPath =
                Path.of(pathValue);

        if (requestedPath.isAbsolute()) {
            return requestedPath
                    .toAbsolutePath()
                    .normalize();
        }

        return projectRoot.resolve(requestedPath)
                .toAbsolutePath()
                .normalize();
    }

    private String getExtension(Path path) {

        if (path == null
                || path.getFileName() == null) {

            return "";
        }

        String fileName =
                path.getFileName().toString();

        int dotIndex =
                fileName.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex + 1 >= fileName.length()) {

            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase();
    }

    private Map<String, Object> property(
            String type,
            String description) {

        Map<String, Object> property =
                new LinkedHashMap<>();

        property.put("type", type);
        property.put("description", description);

        return property;
    }

    private Map<String, Object> stringArrayProperty(
            String description) {

        Map<String, Object> property =
                property("array", description);

        property.put(
                "items",
                Map.of("type", "string")
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

        Object value = arguments.get(key);

        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private Integer readInteger(
            Map<String, Object> arguments,
            String key) {

        Object value = arguments.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(
                    String.valueOf(value).trim()
            );

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean readBoolean(
            Map<String, Object> arguments,
            String key) {

        Object value = arguments.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String text =
                String.valueOf(value).trim();

        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }

        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }

        return null;
    }

    private List<String> readStringList(
            Map<String, Object> arguments,
            String key) {

        Object value = arguments.get(key);

        if (!(value instanceof List<?> source)) {
            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<>();

        for (Object item : source) {
            if (item == null) {
                continue;
            }

            String text =
                    String.valueOf(item).trim();

            if (!text.isEmpty()
                    && !result.contains(text)) {

                result.add(text);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private Map<String, String> readStringMap(
            Map<String, Object> arguments,
            String key) {

        Object value = arguments.get(key);

        if (!(value instanceof Map<?, ?> source)) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry
                : source.entrySet()) {

            if (entry.getKey() == null
                    || entry.getValue() == null) {

                continue;
            }

            String mapKey =
                    String.valueOf(
                            entry.getKey()
                    ).trim();

            String mapValue =
                    String.valueOf(
                            entry.getValue()
                    ).trim();

            if (!mapKey.isEmpty()) {
                result.put(
                        mapKey,
                        mapValue
                );
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private boolean defaultBoolean(
            Boolean value,
            boolean defaultValue) {

        return value == null
                ? defaultValue
                : value;
    }

    private int defaultInteger(
            Integer value,
            int defaultValue) {

        return value == null
                ? defaultValue
                : value;
    }

    private String normalizePath(Path path) {

        if (path == null) {
            return null;
        }

        return path.toString()
                .replace('\\', '/');
    }

    private ToolIssue error(
            String field,
            String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.ERROR)
                .field(field)
                .message(message)
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