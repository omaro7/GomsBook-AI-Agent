/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

/**
 * LLM 응답 문자열에서 XHTML 문서를 추출하고 정규화하는 파서입니다.
 *
 * <p>LLM은 XHTML만 반환하도록 요청하더라도 다음과 같은 형태로
 * 응답할 수 있습니다.</p>
 *
 * <pre>
 * ```xhtml
 * &lt;!DOCTYPE html&gt;
 * &lt;html&gt;...&lt;/html&gt;
 * ```
 * </pre>
 *
 * <p>구현체는 Markdown 코드 블록, XHTML 앞뒤의 설명 문자열,
 * 불필요한 공백 등을 제거하여 검증 가능한 XHTML 문자열을
 * 반환해야 합니다.</p>
 */
public interface XhtmlResponseParser {

    /**
     * LLM 응답에서 XHTML 문서를 추출합니다.
     *
     * @param responseContent LLM이 반환한 원본 응답 문자열
     * @return 추출 및 정규화된 XHTML 문자열
     * @throws XhtmlResponseParseException XHTML을 추출할 수 없는 경우
     */
    String parse(String responseContent);

    /**
     * 주어진 응답에서 XHTML 문서를 추출할 수 있는지 확인합니다.
     *
     * <p>기본 구현은 {@link #parse(String)}를 호출하여 예외 발생 여부로
     * 판단합니다.</p>
     *
     * @param responseContent LLM 원본 응답 문자열
     * @return XHTML을 추출할 수 있으면 {@code true}
     */
    default boolean canParse(String responseContent) {
        if (responseContent == null
                || responseContent.isBlank()) {

            return false;
        }

        try {
            String xhtml = parse(responseContent);

            return xhtml != null
                    && !xhtml.isBlank();

        } catch (XhtmlResponseParseException exception) {
            return false;
        }
    }
}
