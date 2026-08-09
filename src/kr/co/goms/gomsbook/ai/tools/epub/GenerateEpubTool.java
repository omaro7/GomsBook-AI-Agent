/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.epub;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationResult;
import kr.co.goms.gomsbook.ai.epub.runtime.EpubRuntime;
import kr.co.goms.gomsbook.ai.epub.service.EpubGenerationException;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * EPUB 파일을 생성하는 Agent Tool입니다.
 *
 * <p>EPUB 생성 요청을 {@link EpubRuntime}에 전달하고,
 * {@link EpubGenerationResult}를 공통 {@link ToolResult}로
 * 변환합니다.</p>
 *
 * <pre>
 * Agent
 *   ↓
 * GenerateEpubTool
 *   ↓
 * EpubRuntime
 *   ↓
 * DefaultEpubGenerator
 *   ↓
 * EpubGenerationResult
 *   ↓
 * ToolResult
 * </pre>
 */
public final class GenerateEpubTool
        implements AgentTool {

    /**
     * Tool 이름입니다.
     */
    public static final String NAME =
            "generate_epub";

    /**
     * 기존 Tool 계층과 이름 규칙을 맞추기 위한 별칭입니다.
     */
    public static final String TOOL_NAME =
            NAME;

    private static final String TOOL_DESCRIPTION =
            "현재 EPUB 프로젝트를 기반으로 EPUB 파일을 생성합니다.";

    /**
     * ToolContext에서 EpubGenerationRequest를 조회할 때
     * 사용하는 속성 이름입니다.
     */
    public static final String CONTEXT_GENERATION_REQUEST =
            "epubGenerationRequest";

    private final EpubRuntime epubRuntime;

    /**
     * EPUB Runtime을 주입합니다.
     *
     * @param epubRuntime EPUB Runtime
     */
    public GenerateEpubTool(
            EpubRuntime epubRuntime
    ) {

        this.epubRuntime =
                Objects.requireNonNull(
                        epubRuntime,
                        "epubRuntime must not be null"
                );
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    /**
     * EPUB 생성을 실행합니다.
     */
    @Override
    public ToolResult execute(
            ToolRequest toolRequest,
            ToolContext toolContext
    ) {

        if (toolRequest == null) {

            return failure(
                    "TOOL_REQUEST_REQUIRED",
                    "ToolRequest가 없습니다.",
                    null
            );
        }

        try {

            EpubGenerationRequest request =
                    resolveGenerationRequest(
                            toolRequest,
                            toolContext
                    );

            validateRequest(
                    request
            );

            if (!epubRuntime.supports(
                    request
            )) {

                return failure(
                        "EPUB_GENERATION_UNSUPPORTED",
                        "현재 EPUB Runtime에서 "
                                + "해당 생성 요청을 처리할 수 없습니다.",
                        null
                );
            }

            EpubGenerationResult generationResult =
                    epubRuntime.generate(
                            request
                    );

            if (generationResult == null) {

                return failure(
                        "EPUB_GENERATION_EMPTY_RESULT",
                        "EPUB 생성 결과가 없습니다.",
                        null
                );
            }

            return convertResult(
                    generationResult
            );

        } catch (EpubGenerationException exception) {

            return failure(
                    "EPUB_GENERATION_FAILED",
                    safeMessage(exception),
                    exception
            );

        } catch (IllegalArgumentException exception) {

            return failure(
                    "INVALID_EPUB_GENERATION_REQUEST",
                    safeMessage(exception),
                    exception
            );

        } catch (SecurityException exception) {

            return failure(
                    "EPUB_FILE_ACCESS_DENIED",
                    safeMessage(exception),
                    exception
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_GENERATION_UNEXPECTED_ERROR",
                    "EPUB 생성 중 예상하지 못한 오류가 발생했습니다: "
                            + safeMessage(exception),
                    exception
            );
        }
    }

    /**
     * ToolRequest 또는 ToolContext에서
     * EpubGenerationRequest를 조회합니다.
     *
     * <p>우선순위:</p>
     *
     * <ol>
     *     <li>ToolRequest.arguments</li>
     *     <li>ToolContext.epubGenerationRequest</li>
     * </ol>
     */
    private EpubGenerationRequest resolveGenerationRequest(
            ToolRequest toolRequest,
            ToolContext toolContext
    ) {

        /*
         * 1. ToolRequest arguments
         */
        Object arguments =
                toolRequest.getArguments();

        if (arguments
                instanceof EpubGenerationRequest) {

            return (EpubGenerationRequest) arguments;
        }

        /*
         * 2. ToolContext
         *
         * EPUB GenerationRequest는 Manifest, Spine,
         * Resource 등 복합 객체를 포함하므로
         * 현재 GomsBook Editor 프로젝트에서 생성하여
         * Context로 전달하는 방식을 기본으로 합니다.
         */
        if (toolContext != null) {

            EpubGenerationRequest request =
                    toolContext.getAttribute(
                            CONTEXT_GENERATION_REQUEST,
                            EpubGenerationRequest.class
                    );

            if (request != null) {
                return request;
            }
        }

        /*
         * arguments가 null이 아니지만 Map 등의 형태라면
         * 현재 Tool에서는 임의 변환하지 않습니다.
         *
         * 복합 EPUB 모델을 LLM JSON에서 직접 구성하는 것보다
         * Editor Runtime에서 EpubGenerationRequest를 생성하는
         * 방식이 안전합니다.
         */
        if (arguments != null) {

            throw new IllegalArgumentException(
                    "EPUB 생성 인자를 "
                            + "EpubGenerationRequest로 처리할 수 없습니다. "
                            + "actualType="
                            + arguments
                                    .getClass()
                                    .getName()
            );
        }

        throw new IllegalArgumentException(
                "EPUB 생성 요청이 없습니다."
        );
    }

    /**
     * EPUB 생성 요청의 기본 유효성을 확인합니다.
     */
    private void validateRequest(
            EpubGenerationRequest request
    ) {

        Objects.requireNonNull(
                request,
                "EPUB generation request must not be null"
        );

        /*
         * EpubGenerationRequest 자체 검증을 사용합니다.
         */
        request.validate();

        if (request.getProjectRoot() == null) {

            throw new IllegalArgumentException(
                    "EPUB projectRoot가 필요합니다."
            );
        }

        if (request.getEpubPackage() == null) {

            throw new IllegalArgumentException(
                    "EPUB package가 필요합니다."
            );
        }

        if (request.getOutputFile() == null) {

            throw new IllegalArgumentException(
                    "EPUB outputFile이 필요합니다."
            );
        }

        if (request.getOptions() == null) {

            throw new IllegalArgumentException(
                    "EPUB generation options가 필요합니다."
            );
        }
    }

    /**
     * EPUB 생성 결과를 ToolResult로 변환합니다.
     */
    private ToolResult convertResult(
            EpubGenerationResult result
    ) {

        Objects.requireNonNull(
                result,
                "generationResult must not be null"
        );

        if (result.isFailed()) {

            return buildFailedResult(
                    result
            );
        }

        return buildSuccessResult(
                result
        );
    }

    /**
     * 성공 결과를 생성합니다.
     *
     * <p>현재 ToolStatus에는 SUCCESS_WITH_WARNINGS가 없으므로,
     * EPUB 생성 자체가 성공한 경우 SUCCESS를 사용하고
     * 경고는 ToolIssue WARNING으로 전달합니다.</p>
     */
    private ToolResult buildSuccessResult(
            EpubGenerationResult result
    ) {

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.SUCCESS
                        )
                        .message(
                                resolveResultMessage(
                                        result,
                                        "EPUB 생성을 완료했습니다."
                                )
                        )
                        .data(
                                "generationResult",
                                result
                        )
                        .data(
                                "generatedResourceCount",
                                result.getGeneratedResourceCount()
                        )
                        .data(
                                "copiedResourceCount",
                                result.getCopiedResourceCount()
                        )
                        .data(
                                "writtenResourceCount",
                                result.getWrittenResourceCount()
                        )
                        .data(
                                "skippedResourceCount",
                                result.getSkippedResourceCount()
                        )
                        .data(
                                "generatedXhtmlCount",
                                result.getGeneratedXhtmlCount()
                        )
                        .data(
                                "generatedImageCount",
                                result.getGeneratedImageCount()
                        )
                        .data(
                                "generatedFileCount",
                                result.getGeneratedFileCount()
                        )
                        .data(
                                "outputFileSize",
                                result.getOutputFileSize()
                        )
                        .data(
                                "durationMillis",
                                result.getDurationMillis()
                        );

        result.getOutputFile()
                .ifPresent(
                        outputFile ->
                                appendOutputData(
                                        builder,
                                        outputFile
                                )
                );

        /*
         * 일반 경고를 ToolIssue로 변환합니다.
         */
        for (String warning :
                result.getWarnings()) {

            if (warning == null
                    || warning.isBlank()) {

                continue;
            }

            builder.issue(
                    warningIssue(
                            "EPUB_GENERATION_WARNING",
                            warning
                    )
            );
        }

        /*
         * 검증 요약 정보
         */
        appendValidationSummary(
                builder,
                "validation",
                result.getValidationSummary()
        );

        appendValidationSummary(
                builder,
                "accessibilityValidation",
                result
                        .getAccessibilityValidationSummary()
        );

        appendValidationSummary(
                builder,
                "epubCheck",
                result
                        .getEpubCheckValidationSummary()
        );

        return builder.build();
    }

    /**
     * 실패 결과를 생성합니다.
     */
    private ToolResult buildFailedResult(
            EpubGenerationResult result
    ) {

        String message =
                resolveResultMessage(
                        result,
                        "EPUB 생성에 실패했습니다."
                );

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
                        /*
                         * ToolResult는 FAILED 상태에서
                         * 오류 정보가 반드시 필요합니다.
                         */
                        .errorCode(
                                resolveGenerationErrorCode(
                                        result
                                )
                        )
                        .errorMessage(
                                message
                        )
                        .data(
                                "generationResult",
                                result
                        )
                        .data(
                                "generatedResourceCount",
                                result.getGeneratedResourceCount()
                        )
                        .data(
                                "generatedFileCount",
                                result.getGeneratedFileCount()
                        )
                        .data(
                                "durationMillis",
                                result.getDurationMillis()
                        );

        result.getCause()
                .ifPresent(
                        builder::cause
                );

        result.getOutputFile()
                .ifPresent(
                        outputFile ->
                                appendOutputData(
                                        builder,
                                        outputFile
                                )
                );

        for (String warning :
                result.getWarnings()) {

            if (warning == null
                    || warning.isBlank()) {

                continue;
            }

            builder.issue(
                    warningIssue(
                            "EPUB_GENERATION_WARNING",
                            warning
                    )
            );
        }

        for (String error :
                result.getErrors()) {

            if (error == null
                    || error.isBlank()) {

                continue;
            }

            builder.issue(
                    errorIssue(
                            "EPUB_GENERATION_ERROR",
                            error
                    )
            );
        }

        appendValidationSummary(
                builder,
                "validation",
                result.getValidationSummary()
        );

        appendValidationSummary(
                builder,
                "accessibilityValidation",
                result
                        .getAccessibilityValidationSummary()
        );

        appendValidationSummary(
                builder,
                "epubCheck",
                result
                        .getEpubCheckValidationSummary()
        );

        return builder.build();
    }

    /**
     * 생성된 EPUB 파일 정보를 ToolResult.data에 추가합니다.
     */
    private void appendOutputData(
            ToolResult.Builder builder,
            Path outputFile
    ) {

        if (outputFile == null) {
            return;
        }

        Path normalized =
                outputFile
                        .toAbsolutePath()
                        .normalize();

        builder.data(
                "outputFile",
                normalized.toString()
        );

        if (normalized.getFileName() != null) {

            builder.data(
                    "outputFileName",
                    normalized
                            .getFileName()
                            .toString()
            );
        }
    }

    /**
     * 검증 요약을 ToolResult.data에 추가합니다.
     */
    private void appendValidationSummary(
            ToolResult.Builder builder,
            String prefix,
            EpubGenerationResult.ValidationSummary summary
    ) {

        if (builder == null
                || prefix == null
                || prefix.isBlank()
                || summary == null) {

            return;
        }

        builder.data(
                prefix + "Status",
                summary.getStatus().name()
        );

        builder.data(
                prefix + "ErrorCount",
                summary.getErrors()
        );

        builder.data(
                prefix + "WarningCount",
                summary.getWarnings()
        );

        summary.getValidator()
                .ifPresent(
                        validator ->
                                builder.data(
                                        prefix + "Validator",
                                        validator
                                )
                );

        summary.getMessage()
                .ifPresent(
                        message ->
                                builder.data(
                                        prefix + "Message",
                                        message
                                )
                );
    }

    /**
     * EpubGenerationResult의 errorCode attribute가 있으면
     * 이를 사용하고, 없으면 기본 코드를 반환합니다.
     */
    private String resolveGenerationErrorCode(
            EpubGenerationResult result
    ) {

        if (result == null) {
            return "EPUB_GENERATION_FAILED";
        }

        return result.getAttribute(
                "errorCode"
        ).orElse(
                "EPUB_GENERATION_FAILED"
        );
    }

    /**
     * EpubGenerationResult 메시지를 조회합니다.
     */
    private String resolveResultMessage(
            EpubGenerationResult result,
            String defaultMessage
    ) {

        if (result == null) {
            return defaultMessage;
        }

        return result.getMessage()
                .filter(
                        value ->
                                !value.isBlank()
                )
                .orElse(
                        defaultMessage
                );
    }

    /**
     * 공통 실패 ToolResult를 생성합니다.
     */
    private ToolResult failure(
            String code,
            String message,
            Throwable cause
    ) {

        String normalizedCode =
                code == null
                        || code.isBlank()
                                ? "EPUB_GENERATION_FAILED"
                                : code.trim();

        String normalizedMessage =
                message == null
                        || message.isBlank()
                                ? "EPUB 생성에 실패했습니다."
                                : message.trim();

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.FAILED
                        )
                        .message(
                                normalizedMessage
                        )
                        .errorCode(
                                normalizedCode
                        )
                        .errorMessage(
                                normalizedMessage
                        )
                        .issue(
                                errorIssue(
                                        normalizedCode,
                                        normalizedMessage
                                )
                        );

        if (cause != null) {

            builder.cause(
                    cause
            );

            /*
             * attribute 대신 ToolResult.data를 사용합니다.
             */
            builder.data(
                    "exceptionType",
                    cause.getClass()
                            .getName()
            );
        }

        return builder.build();
    }

    /**
     * ERROR ToolIssue를 생성합니다.
     */
    private ToolIssue errorIssue(
            String code,
            String message
    ) {

        return ToolIssue.builder()
                .code(code)
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .message(message)
                .build();
    }

    /**
     * WARNING ToolIssue를 생성합니다.
     */
    private ToolIssue warningIssue(
            String code,
            String message
    ) {

        return ToolIssue.builder()
                .code(code)
                .severity(
                        ToolIssueSeverity.WARNING
                )
                .message(message)
                .build();
    }

    /**
     * Throwable의 사용자 표시 메시지를 반환합니다.
     */
    private String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "알 수 없는 EPUB 생성 오류가 발생했습니다.";
        }

        String message =
                throwable.getMessage();

        if (message != null
                && !message.isBlank()) {

            return message.trim();
        }

        return throwable
                .getClass()
                .getSimpleName()
                + " 오류가 발생했습니다.";
    }

    /**
     * EPUB Runtime을 반환합니다.
     */
    public EpubRuntime getEpubRuntime() {
        return epubRuntime;
    }
}