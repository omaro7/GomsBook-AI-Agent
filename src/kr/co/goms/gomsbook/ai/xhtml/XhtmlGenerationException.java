/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidationResult;

/**
 * XHTML 생성 과정에서 발생하는 예외입니다.
 *
 * <p>대표적인 발생 사례:</p>
 *
 * <ul>
 *     <li>XHTML 생성 요청이 올바르지 않은 경우</li>
 *     <li>Prompt 생성에 실패한 경우</li>
 *     <li>LLM 호출에 실패한 경우</li>
 *     <li>LLM 응답에서 XHTML을 추출하지 못한 경우</li>
 *     <li>생성된 XHTML 검증에 실패한 경우</li>
 * </ul>
 */
public class XhtmlGenerationException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public XhtmlGenerationException(
            String message) {

        this(
                null,
                message,
                null
        );
    }

    public XhtmlGenerationException(
            String message,
            Throwable cause) {

        this(
                null,
                message,
                cause
        );
    }

    public XhtmlGenerationException(
            String errorCode,
            String message) {

        this(
                errorCode,
                message,
                null
        );
    }

    public XhtmlGenerationException(
            String errorCode,
            String message,
            Throwable cause) {

        super(
                normalizeMessage(message),
                cause
        );

        this.errorCode =
                normalizeOptional(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean hasErrorCode() {
        return errorCode != null;
    }

    public static XhtmlGenerationException invalidRequest(
            String message) {

        return new XhtmlGenerationException(
                ErrorCodes.INVALID_REQUEST,
                message
        );
    }

    public static XhtmlGenerationException promptFailed(
            Throwable cause) {

        return new XhtmlGenerationException(
                ErrorCodes.PROMPT_FAILED,
                "Failed to create XHTML generation prompt.",
                cause
        );
    }

    public static XhtmlGenerationException llmFailed(
            Throwable cause) {

        return new XhtmlGenerationException(
                ErrorCodes.LLM_FAILED,
                "Failed to generate XHTML using LLM.",
                cause
        );
    }

    public static XhtmlGenerationException parseFailed(
            Throwable cause) {

        return new XhtmlGenerationException(
                ErrorCodes.PARSE_FAILED,
                "Failed to parse XHTML from LLM response.",
                cause
        );
    }

    public static XhtmlGenerationException validationFailed(
            String message) {

        return new XhtmlGenerationException(
                ErrorCodes.VALIDATION_FAILED,
                message
        );
    }

    public static XhtmlGenerationException validationFailed(
            XhtmlValidationResult result) {

        if (result == null) {
            return validationFailed(
                    "XHTML validation failed."
            );
        }

        String message =
                result.hasIssues()
                        ? "Generated XHTML validation failed: "
                                + String.join(
                                        "; ",
                                        result.getIssues()
                                )
                        : "Generated XHTML validation failed.";

        return validationFailed(message);
    }

    public static XhtmlGenerationException emptyResponse() {
        return new XhtmlGenerationException(
                ErrorCodes.EMPTY_RESPONSE,
                "LLM returned an empty XHTML generation response."
        );
    }

    public static XhtmlGenerationException generationFailed(
            Throwable cause) {

        return new XhtmlGenerationException(
                ErrorCodes.GENERATION_FAILED,
                "XHTML generation failed.",
                cause
        );
    }

    private static String normalizeMessage(
            String message) {

        if (message == null || message.isBlank()) {
            return "Unknown XHTML generation error.";
        }

        return message.trim();
    }

    private static String normalizeOptional(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    /**
     * XHTML 생성 오류 코드입니다.
     */
    public static final class ErrorCodes {

        public static final String INVALID_REQUEST =
                "XHTML_INVALID_REQUEST";

        public static final String PROMPT_FAILED =
                "XHTML_PROMPT_FAILED";

        public static final String LLM_FAILED =
                "XHTML_LLM_FAILED";

        public static final String PARSE_FAILED =
                "XHTML_PARSE_FAILED";

        public static final String VALIDATION_FAILED =
                "XHTML_VALIDATION_FAILED";

        public static final String EMPTY_RESPONSE =
                "XHTML_EMPTY_RESPONSE";

        public static final String GENERATION_FAILED =
                "XHTML_GENERATION_FAILED";

        private ErrorCodes() {
            throw new AssertionError(
                    "Utility class"
            );
        }
    }
}