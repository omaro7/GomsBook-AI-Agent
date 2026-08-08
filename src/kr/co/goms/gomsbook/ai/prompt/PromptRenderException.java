/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

/**
 * PromptTemplate 렌더링 중 발생하는 예외입니다.
 *
 * <p>대표적인 발생 사례:</p>
 * <ul>
 *     <li>필수 템플릿 변수가 누락된 경우</li>
 *     <li>존재하지 않는 변수명을 사용하는 경우</li>
 *     <li>템플릿 형식이 올바르지 않은 경우</li>
 *     <li>Prompt 생성 중 오류가 발생한 경우</li>
 * </ul>
 */
public class PromptRenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 메시지만 포함하는 예외를 생성합니다.
     *
     * @param message 오류 메시지
     */
    public PromptRenderException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외를 포함하는 예외를 생성합니다.
     *
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public PromptRenderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 원인 예외만 포함하는 예외를 생성합니다.
     *
     * @param cause 원인 예외
     */
    public PromptRenderException(Throwable cause) {
        super(cause);
    }

    /**
     * 누락된 템플릿 변수를 위한 예외를 생성합니다.
     *
     * @param variableName 변수명
     * @return PromptRenderException
     */
    public static PromptRenderException missingVariable(String variableName) {
        return new PromptRenderException(
                "Missing required prompt variable: " + variableName
        );
    }

    /**
     * 존재하지 않는 템플릿 변수를 위한 예외를 생성합니다.
     *
     * @param variableName 변수명
     * @return PromptRenderException
     */
    public static PromptRenderException unknownVariable(String variableName) {
        return new PromptRenderException(
                "Unknown prompt variable: " + variableName
        );
    }

    /**
     * 템플릿 오류를 위한 예외를 생성합니다.
     *
     * @param message 상세 메시지
     * @return PromptRenderException
     */
    public static PromptRenderException invalidTemplate(String message) {
        return new PromptRenderException(
                "Invalid prompt template: " + message
        );
    }

    /**
     * Prompt 렌더링 실패 예외를 생성합니다.
     *
     * @param cause 원인 예외
     * @return PromptRenderException
     */
    public static PromptRenderException renderFailed(Throwable cause) {
        return new PromptRenderException(
                "Failed to render prompt.",
                cause
        );
    }
}