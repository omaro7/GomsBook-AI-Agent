/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.epub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.runtime.EpubRuntime;
import kr.co.goms.gomsbook.ai.epub.validation.CompositeEpubValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidator;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 생성된 EPUB 파일을 검증하는 Agent Tool입니다.
 *
 * <p>다음 검증 모드를 지원합니다.</p>
 *
 * <ul>
 *     <li>내부 EPUB 구조 검증</li>
 *     <li>접근성 검증</li>
 *     <li>EPUBCheck 검증</li>
 *     <li>전체 Composite 검증</li>
 * </ul>
 */
public final class ValidateEpubTool
        implements AgentTool {

    public static final String NAME =
            "validate_epub";

    public static final String TOOL_NAME =
            NAME;

    public static final String DESCRIPTION =
            "Validates an EPUB file using internal validation, "
                    + "accessibility validation, EPUBCheck, "
                    + "or all configured validators.";

    private static final String PROJECT_ROOT_ARGUMENT =
            "projectRoot";

    private static final String EPUB_GENERATION_REQUEST_ATTRIBUTE =
            "epubGenerationRequest";
    
    private static final String EPUB_FILE_ARGUMENT =
            "epubFile";

    private static final String VALIDATION_MODE_ARGUMENT =
            "validationMode";

    private static final String GENERATION_OPTIONS_ATTRIBUTE =
            "epubGenerationOptions";

    private final EpubRuntime epubRuntime;

    /**
     * EPUB Runtime을 주입합니다.
     */
    public ValidateEpubTool(
            EpubRuntime epubRuntime
    ) {

        this.epubRuntime =
                Objects.requireNonNull(
                        epubRuntime,
                        "EPUB runtime must not be null."
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
     * Tool 입력값을 검증합니다.
     */
    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context
    ) {

        ToolValidationResult.Builder result =
                ToolValidationResult.builder();

        if (request == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_REQUEST_NULL",
                                    "Tool request must not be null."
                            )
                    )
                    .build();
        }

        Path epubFile;

        try {

            epubFile =
                    resolveEpubFile(
                            request,
                            context
                    );

        } catch (RuntimeException exception) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_ARGUMENT_INVALID",
                                    safeMessage(exception)
                            )
                    )
                    .build();
        }

        if (epubFile == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_FILE_MISSING",
                                    "EPUB file was not provided."
                            )
                    )
                    .build();
        }

        Path normalized =
                epubFile
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_FILE_NOT_FOUND",
                                    "EPUB file does not exist: "
                                            + normalized
                            )
                    )
                    .build();
        }

        if (!Files.isRegularFile(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_NOT_FILE",
                                    "EPUB path is not a regular file: "
                                            + normalized
                            )
                    )
                    .build();
        }

        if (!Files.isReadable(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_NOT_READABLE",
                                    "EPUB file is not readable: "
                                            + normalized
                            )
                    )
                    .build();
        }

        String fileName =
                normalized.getFileName() == null
                        ? ""
                        : normalized
                                .getFileName()
                                .toString()
                                .toLowerCase(
                                        Locale.ROOT
                                );

        if (!fileName.endsWith(".epub")) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_EXTENSION_INVALID",
                                    "Validation target must use "
                                            + "the .epub extension."
                            )
                    )
                    .build();
        }

        ValidationMode mode;

        try {

            mode =
                    resolveValidationMode(
                            request,
                            context
                    );

        } catch (RuntimeException exception) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_MODE_INVALID",
                                    safeMessage(exception)
                            )
                    )
                    .build();
        }

        if (!supportsMode(mode)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_VALIDATION_MODE_UNAVAILABLE",
                                    "Validation mode is not available: "
                                            + mode
                            )
                    )
                    .build();
        }

        return result
                .valid(true)
                .build();
    }

    /**
     * EPUB 검증을 실행합니다.
     */
    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context
    ) {

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
                            ToolStatus.VALIDATION_FAILED
                    )
                    .validationResult(
                            validation
                    )
                    .message(
                            "EPUB validation request is invalid."
                    )
                    .build();
        }

        /*
         * 프로젝트 루트를 조회합니다.
         *
         * ACCESSIBILITY / ALL 검증에서는
         * 프로젝트 루트가 필요합니다.
         */
        Path projectRoot =
                resolveProjectRoot(
                        request,
                        context
                );

        
        Path epubFile =
                Objects.requireNonNull(
                        resolveEpubFile(
                                request,
                                context
                        ),
                        "EPUB file must not be null."
                )
                        .toAbsolutePath()
                        .normalize();

        ValidationMode mode =
                resolveValidationMode(
                        request,
                        context
                );

        EpubGenerationOptions options =
                resolveOptions(
                        context
                );

        try {

            EpubValidationResult validationResult =
                    executeValidation(
                    		projectRoot,
                            epubFile,
                            options,
                            mode
                    );

            return convertResult(
                    epubFile,
                    mode,
                    validationResult
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_VALIDATION_UNEXPECTED_ERROR",
                    "Unexpected EPUB validation error: "
                            + safeMessage(exception),
                    epubFile,
                    mode,
                    exception
            );
        }
    }

    /**
     * 선택된 검증 모드에 따라 Validator를 실행합니다.
     */
    private EpubValidationResult executeValidation(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options,
            ValidationMode mode
    ) {

        switch (mode) {

            case INTERNAL:

                return requireInternalValidator()
                        .validate(
                        		projectRoot,
                                epubFile,
                                options
                        );

            case ACCESSIBILITY:

                return requireAccessibilityValidator()
                        .validate(
                        		projectRoot,
                                epubFile,
                                options
                        );

            case EPUB_CHECK:

                return requireEpubCheckValidator()
                        .validate(
                        		projectRoot,
                                epubFile,
                                options
                        );

            case ALL:

                CompositeEpubValidator validator =
                        Objects.requireNonNull(
                                epubRuntime
                                        .getCompositeValidator(),
                                "Composite EPUB validator "
                                        + "is not configured."
                        );

                return validator.validate(
                		projectRoot,
                        epubFile,
                        options
                );

            default:

                throw new IllegalArgumentException(
                        "Unsupported EPUB validation mode: "
                                + mode
                );
        }
    }

    /**
     * EpubValidationResult를 ToolResult로 변환합니다.
     */
    private ToolResult convertResult(
            Path epubFile,
            ValidationMode mode,
            EpubValidationResult validationResult
    ) {

        Objects.requireNonNull(
                validationResult,
                "EPUB validation result must not be null."
        );

        ToolStatus status =
                resolveToolStatus(
                        validationResult
                );

        String message =
                validationResult
                        .getMessage()
                        .orElseGet(
                                validationResult::getSummary
                        );

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                status
                        )
                        .message(
                                message
                        )
                        .validationResult(
                                null
                        )
                        .data(
                                "validationResult",
                                validationResult
                        )
                        .data(
                                "epubFile",
                                epubFile.toString()
                        )
                        .data(
                                "validationMode",
                                mode.name()
                        )
                        .data(
                                "validationStatus",
                                validationResult
                                        .getStatus()
                                        .name()
                        )
                        .data(
                                "issueCount",
                                validationResult
                                        .getIssueCount()
                        )
                        .data(
                                "fatalCount",
                                validationResult
                                        .getFatalCount()
                        )
                        .data(
                                "errorCount",
                                validationResult
                                        .getErrorCount()
                        )
                        .data(
                                "warningCount",
                                validationResult
                                        .getWarningCount()
                        )
                        .data(
                                "infoCount",
                                validationResult
                                        .getInfoCount()
                        )
                        .data(
                                "autoFixableIssueCount",
                                validationResult
                                        .getAutoFixableIssueCount()
                        )
                        .data(
                                "durationMillis",
                                validationResult
                                        .getDurationMillis()
                        );

        validationResult
                .getValidatorName()
                .ifPresent(
                        value ->
                                builder.data(
                                        "validator",
                                        value
                                )
                );

        validationResult
                .getValidatorVersion()
                .ifPresent(
                        value ->
                                builder.data(
                                        "validatorVersion",
                                        value
                                )
                );

        for (EpubValidationIssue issue :
                validationResult.getIssues()) {

            if (issue != null) {

                builder.issue(
                        convertIssue(
                                issue
                        )
                );
            }
        }

        validationResult
                .getCause()
                .ifPresent(
                        cause -> {

                            builder.data(
                                    "exceptionType",
                                    cause.getClass()
                                            .getName()
                            );

                            builder.data(
                                    "exceptionMessage",
                                    safeMessage(
                                            cause
                                    )
                            );

                            /*
                             * SUCCESS 상태에는 cause를 직접 넣지 않습니다.
                             * ToolResult는 SUCCESS + error 정보 조합을
                             * 허용하지 않기 때문입니다.
                             */
                            if (status
                                    != ToolStatus.SUCCESS) {

                                builder.cause(
                                        cause
                                );
                            }
                        }
                );

        /*
         * 검증 자체가 실패한 상태는 VALIDATION_FAILED로 표현합니다.
         */
        if (status == ToolStatus.VALIDATION_FAILED) {

            builder.errorMessage(
                    message == null
                            || message.isBlank()
                                    ? "EPUB validation failed."
                                    : message
            );
        }

        return builder.build();
    }

    /**
     * EPUB ValidationIssue를 Framework ToolIssue로 변환합니다.
     */
    private ToolIssue convertIssue(
            EpubValidationIssue issue
    ) {

        Objects.requireNonNull(
                issue,
                "EPUB validation issue must not be null."
        );

        /*
         * 현재 ToolIssue API에 없는 detail()을 사용하지 않습니다.
         *
         * 상세 정보는 하나의 표시 메시지로 합칩니다.
         */
        StringBuilder message =
                new StringBuilder(
                        issue.getDisplayMessage()
                );

        issue.getSuggestion()
                .ifPresent(
                        suggestion ->
                                message
                                        .append(" / suggestion: ")
                                        .append(
                                                suggestion
                                        )
                );

        return ToolIssue.builder()
                .severity(
                        mapSeverity(
                                issue.getSeverity()
                        )
                )
                .code(
                        issue.getCode()
                )
                .message(
                        message.toString()
                )
                .build();
    }

    /**
     * EPUB Severity를 Tool Severity로 변환합니다.
     */
    private ToolIssueSeverity mapSeverity(
            EpubValidationIssue.Severity severity
    ) {

        if (severity == null) {
            return ToolIssueSeverity.ERROR;
        }

        switch (severity) {

            case INFO:
                return ToolIssueSeverity.INFO;

            case WARNING:
                return ToolIssueSeverity.WARNING;

            case ERROR:
            case FATAL:
                return ToolIssueSeverity.ERROR;

            default:
                return ToolIssueSeverity.ERROR;
        }
    }

    /**
     * EpubValidationResult를 ToolStatus로 변환합니다.
     *
     * <p>경고가 있는 통과도 Tool 실행 자체는 성공입니다.</p>
     */
    private ToolStatus resolveToolStatus(
            EpubValidationResult result
    ) {

        if (result == null) {
            return ToolStatus.FAILED;
        }

        switch (result.getStatus()) {

            case PASSED:
            case PASSED_WITH_WARNINGS:
            case PARTIAL:
                return ToolStatus.SUCCESS;

            case NOT_PERFORMED:
            case FAILED:
                return ToolStatus.VALIDATION_FAILED;

            default:
                return ToolStatus.VALIDATION_FAILED;
        }
    }

    /**
     * 요청한 ValidationMode를 현재 Runtime이 지원하는지 확인합니다.
     */
    private boolean supportsMode(
            ValidationMode mode
    ) {

        if (mode == null) {
            return false;
        }

        switch (mode) {

            case INTERNAL:

                return epubRuntime
                        .hasInternalValidation();

            case ACCESSIBILITY:

                return epubRuntime
                        .hasAccessibilityValidation();

            case EPUB_CHECK:

                return epubRuntime
                        .hasEpubCheck()
                        && epubRuntime
                                .isEpubCheckAvailable();

            case ALL:

                return epubRuntime
                        .getCompositeValidator()
                        != null
                        && !epubRuntime
                                .getCompositeValidator()
                                .isEmpty();

            default:

                return false;
        }
    }

    private EpubValidator requireInternalValidator() {

        EpubValidator validator =
                epubRuntime
                        .getInternalValidator();

        if (validator == null) {

            throw new IllegalStateException(
                    "Internal EPUB validator "
                            + "is not configured."
            );
        }

        return validator;
    }

    private EpubAccessibilityValidator
            requireAccessibilityValidator() {

        EpubAccessibilityValidator validator =
                epubRuntime
                        .getAccessibilityValidator();

        if (validator == null) {

            throw new IllegalStateException(
                    "EPUB accessibility validator "
                            + "is not configured."
            );
        }

        return validator;
    }

    private EpubCheckValidator
            requireEpubCheckValidator() {

        EpubCheckValidator validator =
                epubRuntime
                        .getEpubCheckValidator();

        if (validator == null) {

            throw new IllegalStateException(
                    "EPUBCheck validator "
                            + "is not configured."
            );
        }

        if (!validator.isAvailable()) {

            throw new IllegalStateException(
                    validator
                            .getAvailability()
                            .getMessage()
                            .orElse(
                                    "EPUBCheck is not available."
                            )
            );
        }

        return validator;
    }

    /**
     * ToolRequest 또는 ToolContext에서 EPUB 파일 경로를 조회합니다.
     */
    private Path resolveEpubFile(
            ToolRequest request,
            ToolContext context
    ) {

        if (request == null) {
            return null;
        }

        /*
         * 1. ToolRequest.arguments
         */
        Path result =
                resolvePathFromArguments(
                        request.getArguments()
                );

        if (result != null) {
            return result;
        }

        /*
         * 2. ToolContext.epubFile
         */
        if (context != null) {

            Object value =
                    context.getAttribute(
                            EPUB_FILE_ARGUMENT
                    );

            result =
                    toPath(
                            value
                    );

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * ToolRequest.arguments에서 EPUB 파일을 조회합니다.
     */
    private Path resolvePathFromArguments(
            Object arguments
    ) {

        if (arguments == null) {
            return null;
        }

        Path direct =
                toPath(
                        arguments
                );

        if (direct != null) {
            return direct;
        }

        if (arguments instanceof Map<?, ?> map) {

            return toPath(
                    map.get(
                            EPUB_FILE_ARGUMENT
                    )
            );
        }

        return null;
    }
    
    /**
     * EPUB 프로젝트 루트를 조회합니다.
     *
     * <p>우선순위:</p>
     *
     * <ol>
     *     <li>ToolContext.projectRoot</li>
     *     <li>ToolContext.epubGenerationRequest.projectRoot</li>
     *     <li>ToolRequest.arguments.projectRoot</li>
     * </ol>
     */
    private Path resolveProjectRoot(
            ToolRequest request,
            ToolContext context
    ) {

        /*
         * 1. ToolContext의 projectRoot
         */
        if (context != null) {

            Object value =
                    context.getAttribute(
                            PROJECT_ROOT_ARGUMENT
                    );

            Path path =
                    toPath(
                            value
                    );

            if (path != null) {

                return path
                        .toAbsolutePath()
                        .normalize();
            }
        }

        /*
         * 2. EpubGenerationRequest가 Context에 있다면
         *    해당 Request의 projectRoot 사용
         */
        if (context != null) {

            EpubGenerationRequest generationRequest =
                    context.getAttribute(
                            EPUB_GENERATION_REQUEST_ATTRIBUTE,
                            EpubGenerationRequest.class
                    );

            if (generationRequest != null
                    && generationRequest.getProjectRoot() != null) {

                return generationRequest
                        .getProjectRoot()
                        .toAbsolutePath()
                        .normalize();
            }
        }

        /*
         * 3. ToolRequest.arguments의 projectRoot
         */
        Object value =
                getArgumentValue(
                        request,
                        PROJECT_ROOT_ARGUMENT
                );

        Path path =
                toPath(
                        value
                );

        if (path != null) {

            return path
                    .toAbsolutePath()
                    .normalize();
        }

        return null;
    }
    

    /**
     * validationMode를 조회합니다.
     */
    private ValidationMode resolveValidationMode(
            ToolRequest request,
            ToolContext context
    ) {

        Object value =
                getArgumentValue(
                        request,
                        VALIDATION_MODE_ARGUMENT
                );

        if (value == null
                && context != null) {

            value =
                    context.getAttribute(
                            VALIDATION_MODE_ARGUMENT
                    );
        }

        if (value instanceof ValidationMode mode) {
            return mode;
        }

        if (value instanceof String text
                && !text.isBlank()) {

            return ValidationMode.from(
                    text
            );
        }

        return ValidationMode.ALL;
    }

    /**
     * EPUB 생성 옵션을 Context에서 조회합니다.
     *
     * <p>복잡한 EpubGenerationOptions를 LLM arguments에서
     * 직접 생성하지 않고 Runtime/Editor에서 Context로
     * 전달하는 것을 기본 정책으로 합니다.</p>
     */
    private EpubGenerationOptions resolveOptions(
            ToolContext context
    ) {

        if (context != null) {

            EpubGenerationOptions options =
                    context.getAttribute(
                            GENERATION_OPTIONS_ATTRIBUTE,
                            EpubGenerationOptions.class
                    );

            if (options != null) {
                return options;
            }
        }

        return EpubGenerationOptions
                .defaultOptions();
    }

    /**
     * arguments Map에서 특정 값을 조회합니다.
     */
    private Object getArgumentValue(
            ToolRequest request,
            String name
    ) {

        if (request == null
                || name == null
                || name.isBlank()) {

            return null;
        }

        Object arguments =
                request.getArguments();

        if (!(arguments instanceof Map<?, ?> map)) {
            return null;
        }

        return map.get(
                name
        );
    }

    private Path toPath(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Path path) {
            return path;
        }

        if (value instanceof String text) {

            String normalized =
                    text.trim();

            if (normalized.isEmpty()) {
                return null;
            }

            return Path.of(
                    normalized
            );
        }

        return null;
    }

    /**
     * Tool 입력 Schema입니다.
     */
    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                EPUB_FILE_ARGUMENT,
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Path of the EPUB file to validate."
                )
        );

        properties.put(
                VALIDATION_MODE_ARGUMENT,
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of(
                                "INTERNAL",
                                "ACCESSIBILITY",
                                "EPUB_CHECK",
                                "ALL"
                        ),
                        "description",
                        "EPUB validation mode."
                )
        );

        schema.put(
                "properties",
                Map.copyOf(
                        properties
                )
        );

        schema.put(
                "required",
                List.of(
                        EPUB_FILE_ARGUMENT
                )
        );

        return Map.copyOf(
                schema
        );
    }

    /**
     * EPUB Runtime을 반환합니다.
     */
    public EpubRuntime getEpubRuntime() {
        return epubRuntime;
    }

    /**
     * 일반 실행 실패 결과를 생성합니다.
     */
    private ToolResult failure(
            String errorCode,
            String errorMessage,
            Path epubFile,
            ValidationMode mode,
            Throwable cause
    ) {

        String code =
                errorCode == null
                        || errorCode.isBlank()
                                ? "EPUB_VALIDATION_FAILED"
                                : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                                ? "EPUB validation failed."
                                : errorMessage.trim();

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.FAILED
                        )
                        .message(
                                message
                        )
                        .errorCode(
                                code
                        )
                        .errorMessage(
                                message
                        )
                        .issue(
                                errorIssue(
                                        code,
                                        message
                                )
                        );

        if (epubFile != null) {

            builder.data(
                    "epubFile",
                    epubFile
                            .toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        if (mode != null) {

            builder.data(
                    "validationMode",
                    mode.name()
            );
        }

        if (cause != null) {

            builder.cause(
                    cause
            );

            builder.data(
                    "exceptionType",
                    cause.getClass()
                            .getName()
            );
        }

        return builder.build();
    }

    private ToolIssue errorIssue(
            String code,
            String message
    ) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .code(
                        code
                )
                .message(
                        message
                )
                .build();
    }

    private static String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown EPUB validation error.";
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

    /**
     * EPUB 검증 실행 모드입니다.
     */
    public enum ValidationMode {

        /**
         * ZIP 구조, mimetype, container.xml,
         * OPF 등의 내부 구조를 검사합니다.
         */
        INTERNAL,

        /**
         * EPUB 접근성을 검사합니다.
         */
        ACCESSIBILITY,

        /**
         * EPUBCheck를 실행합니다.
         */
        EPUB_CHECK,

        /**
         * Runtime에 등록된 모든 Validator를 실행합니다.
         */
        ALL;

        /**
         * 문자열을 ValidationMode로 변환합니다.
         */
        public static ValidationMode from(
                String value
        ) {

            if (value == null
                    || value.isBlank()) {

                return ALL;
            }

            String normalized =
                    value.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            .replace(
                                    '-',
                                    '_'
                            )
                            .replace(
                                    ' ',
                                    '_'
                            );

            switch (normalized) {

                case "INTERNAL":
                case "STRUCTURE":
                case "BASIC":
                    return INTERNAL;

                case "ACCESSIBILITY":
                case "A11Y":
                    return ACCESSIBILITY;

                case "EPUBCHECK":
                case "EPUB_CHECK":
                case "CHECK":
                    return EPUB_CHECK;

                case "ALL":
                case "FULL":
                    return ALL;

                default:

                    throw new IllegalArgumentException(
                            "Unsupported EPUB validation mode: "
                                    + value
                    );
            }
        }
    }
}