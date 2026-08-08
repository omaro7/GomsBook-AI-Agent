/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

/**
 * LLM 응답에서 XHTML을 추출하거나 파싱하는 과정에서 발생하는 예외입니다.
 *
 * <p>대표적인 발생 사례:</p>
 * <ul>
 *     <li>LLM 응답이 비어 있는 경우</li>
 *     <li>Markdown 코드 블록에서 XHTML을 찾지 못한 경우</li>
 *     <li>&lt;html&gt; 요소를 찾지 못한 경우</li>
 *     <li>&lt;head&gt; 또는 &lt;body&gt;가 없는 경우</li>
 *     <li>응답 형식이 예상과 다른 경우</li>
 * </ul>
 */
public class XhtmlResponseParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 메시지를 포함하는 예외를 생성합니다.
     *
     * @param message 오류 메시지
     */
    public XhtmlResponseParseException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외를 포함하는 예외를 생성합니다.
     *
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public XhtmlResponseParseException(
            String message,
            Throwable cause) {

        super(message, cause);
    }

    /**
     * 원인 예외를 포함하는 예외를 생성합니다.
     *
     * @param cause 원인 예외
     */
    public XhtmlResponseParseException(Throwable cause) {
        super(cause);
    }

    /**
     * 응답이 비어 있는 경우의 예외를 생성합니다.
     *
     * @return 예외 객체
     */
    public static XhtmlResponseParseException emptyResponse() {
        return new XhtmlResponseParseException(
                "LLM response is empty."
        );
    }

    /**
     * XHTML 문서를 찾지 못한 경우의 예외를 생성합니다.
     *
     * @return 예외 객체
     */
    public static XhtmlResponseParseException htmlNotFound() {
        return new XhtmlResponseParseException(
                "No XHTML document found in the LLM response."
        );
    }

    /**
     * 필수 요소가 없는 경우의 예외를 생성합니다.
     *
     * @param elementName 요소명
     * @return 예외 객체
     */
    public static XhtmlResponseParseException missingElement(
            String elementName) {

        return new XhtmlResponseParseException(
                "Required XHTML element is missing: " + elementName
        );
    }

    /**
     * XHTML 형식이 올바르지 않은 경우의 예외를 생성합니다.
     *
     * @param message 상세 오류 메시지
     * @return 예외 객체
     */
    public static XhtmlResponseParseException invalidFormat(
            String message) {

        return new XhtmlResponseParseException(
                "Invalid XHTML format: " + message
        );
    }

    /**
     * 파싱 실패 예외를 생성합니다.
     *
     * @param cause 원인 예외
     * @return 예외 객체
     */
    public static XhtmlResponseParseException parseFailed(
            Throwable cause) {

        return new XhtmlResponseParseException(
                "Failed to parse XHTML response.",
                cause
        );
    }
}