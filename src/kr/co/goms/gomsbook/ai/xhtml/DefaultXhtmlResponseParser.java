/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 응답에서 XHTML 문서를 추출하는 기본 파서입니다.
 *
 * <p>다음 형태의 응답을 처리합니다.</p>
 * <ul>
 *     <li>순수 XHTML 응답</li>
 *     <li>Markdown 코드 블록으로 감싼 XHTML</li>
 *     <li>XHTML 앞뒤에 설명이 포함된 응답</li>
 *     <li>XML 선언 또는 DOCTYPE으로 시작하는 XHTML</li>
 * </ul>
 */
public final class DefaultXhtmlResponseParser
        implements XhtmlResponseParser {

    private static final Pattern XHTML_CODE_FENCE_PATTERN =
            Pattern.compile(
                    "```(?:xhtml|html|xml)?\\s*(.*?)```",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.DOTALL
            );

    private static final Pattern HTML_DOCUMENT_PATTERN =
            Pattern.compile(
                    "(?is)(<\\?xml\\s+[^>]*\\?>\\s*)?"
                            + "(<!DOCTYPE\\s+html[^>]*>\\s*)?"
                            + "(<html\\b[^>]*>.*?</html\\s*>)"
            );

    private static final Pattern HTML_ELEMENT_PATTERN =
            Pattern.compile(
                    "(?is)(<html\\b[^>]*>.*?</html\\s*>)"
            );

    private static final Pattern BODY_ELEMENT_PATTERN =
            Pattern.compile(
                    "(?is)(<body\\b[^>]*>.*?</body\\s*>)"
            );

    private static final Pattern XML_DECLARATION_PATTERN =
            Pattern.compile(
                    "(?is)^\\s*<\\?xml\\s+[^>]*\\?>"
            );

    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile(
                    "(?is)^\\s*<!DOCTYPE\\s+html[^>]*>"
            );

    private static final Pattern BYTE_ORDER_MARK_PATTERN =
            Pattern.compile("^\\uFEFF");

    /**
     * LLM 응답에서 XHTML 문서를 추출하고 정규화합니다.
     *
     * @param responseContent LLM 원본 응답
     * @return 추출된 XHTML 문자열
     * @throws XhtmlResponseParseException XHTML을 추출하지 못한 경우
     */
    @Override
    public String parse(String responseContent) {
        validateResponseContent(responseContent);

        String normalized =
                normalizeResponse(responseContent);

        String fencedContent =
                extractCodeFence(normalized);

        String candidate = fencedContent != null
                ? fencedContent
                : normalized;

        String xhtml = extractHtmlDocument(candidate);

        if (xhtml == null && fencedContent != null) {
            /*
             * 코드 블록 내부에서 찾지 못한 경우 원본 응답 전체에서
             * 다시 XHTML 문서를 검색합니다.
             */
            xhtml = extractHtmlDocument(normalized);
        }

        if (xhtml == null) {
            throw new XhtmlResponseParseException(
                    "LLM 응답에서 <html>...</html> 문서를 찾을 수 없습니다."
            );
        }

        xhtml = normalizeXhtml(xhtml);

        validateParsedXhtml(xhtml);

        return xhtml;
    }

    /**
     * 응답 문자열의 기본적인 정규화를 수행합니다.
     */
    private String normalizeResponse(String responseContent) {
        return BYTE_ORDER_MARK_PATTERN
                .matcher(responseContent)
                .replaceFirst("")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    /**
     * Markdown 코드 블록 내부 문자열을 추출합니다.
     *
     * <p>여러 코드 블록이 있으면 XHTML 문서가 포함된 첫 번째
     * 코드 블록을 우선 반환합니다.</p>
     */
    private String extractCodeFence(String value) {
        Matcher matcher =
                XHTML_CODE_FENCE_PATTERN.matcher(value);

        String firstCodeBlock = null;

        while (matcher.find()) {
            String content = matcher.group(1);

            if (content == null || content.isBlank()) {
                continue;
            }

            String normalizedContent = content.trim();

            if (firstCodeBlock == null) {
                firstCodeBlock = normalizedContent;
            }

            if (containsHtmlElement(normalizedContent)) {
                return normalizedContent;
            }
        }

        return firstCodeBlock;
    }

    /**
     * 문자열에서 완전한 HTML/XHTML 문서를 추출합니다.
     */
    private String extractHtmlDocument(String value) {
        Matcher documentMatcher =
                HTML_DOCUMENT_PATTERN.matcher(value);

        if (documentMatcher.find()) {
            String xmlDeclaration =
                    nullToEmpty(documentMatcher.group(1));

            String doctype =
                    nullToEmpty(documentMatcher.group(2));

            String html =
                    documentMatcher.group(3);

            return joinDocumentParts(
                    xmlDeclaration,
                    doctype,
                    html
            );
        }

        Matcher htmlMatcher =
                HTML_ELEMENT_PATTERN.matcher(value);

        if (htmlMatcher.find()) {
            String html = htmlMatcher.group(1);

            String prefix =
                    extractDocumentPrefix(value, htmlMatcher.start());

            return joinDocumentParts(
                    extractXmlDeclaration(prefix),
                    extractDoctype(prefix),
                    html
            );
        }

        return null;
    }

    /**
     * HTML 요소 앞에 위치한 XML 선언과 DOCTYPE 영역을 추출합니다.
     */
    private String extractDocumentPrefix(
            String value,
            int htmlStartIndex) {

        if (htmlStartIndex <= 0) {
            return "";
        }

        return value
                .substring(0, htmlStartIndex)
                .trim();
    }

    /**
     * 문자열에서 XML 선언을 추출합니다.
     */
    private String extractXmlDeclaration(String value) {
        Matcher matcher =
                XML_DECLARATION_PATTERN.matcher(value);

        if (matcher.find()) {
            return matcher.group().trim();
        }

        return "";
    }

    /**
     * 문자열에서 HTML DOCTYPE을 추출합니다.
     */
    private String extractDoctype(String value) {
        Matcher matcher =
                DOCTYPE_PATTERN.matcher(value);

        if (matcher.find()) {
            return matcher.group().trim();
        }

        /*
         * XML 선언 뒤에 DOCTYPE이 있을 수 있으므로 전체 문자열에서도
         * 한 번 더 검색합니다.
         */
        Pattern searchPattern = Pattern.compile(
                "(?is)<!DOCTYPE\\s+html[^>]*>"
        );

        Matcher searchMatcher =
                searchPattern.matcher(value);

        if (searchMatcher.find()) {
            return searchMatcher.group().trim();
        }

        return "";
    }

    /**
     * XHTML 문서 구성 요소를 줄바꿈으로 연결합니다.
     */
    private String joinDocumentParts(
            String xmlDeclaration,
            String doctype,
            String html) {

        StringBuilder result = new StringBuilder();

        appendLine(result, xmlDeclaration);
        appendLine(result, doctype);
        appendLine(result, html);

        return result.toString().trim();
    }

    private void appendLine(
            StringBuilder builder,
            String value) {

        if (value == null || value.isBlank()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append('\n');
        }

        builder.append(value.trim());
    }

    /**
     * 추출된 XHTML의 줄바꿈과 외곽 공백을 정규화합니다.
     */
    private String normalizeXhtml(String xhtml) {
        return xhtml
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    /**
     * 파싱된 결과가 최소한의 XHTML 문서 구조를 가지는지 확인합니다.
     */
    private void validateParsedXhtml(String xhtml) {
        String lowerCase =
                xhtml.toLowerCase(Locale.ROOT);

        if (!lowerCase.contains("<html")) {
            throw new XhtmlResponseParseException(
                    "추출된 응답에 <html> 요소가 없습니다."
            );
        }

        if (!lowerCase.contains("</html>")) {
            throw new XhtmlResponseParseException(
                    "추출된 응답에 </html> 종료 태그가 없습니다."
            );
        }

        if (!lowerCase.contains("<head")) {
            throw new XhtmlResponseParseException(
                    "추출된 XHTML에 <head> 요소가 없습니다."
            );
        }

        if (!lowerCase.contains("<body")) {
            throw new XhtmlResponseParseException(
                    "추출된 XHTML에 <body> 요소가 없습니다."
            );
        }

        Matcher bodyMatcher =
                BODY_ELEMENT_PATTERN.matcher(xhtml);

        if (!bodyMatcher.find()) {
            throw new XhtmlResponseParseException(
                    "추출된 XHTML의 <body> 요소가 올바르게 닫히지 않았습니다."
            );
        }
    }

    private boolean containsHtmlElement(String value) {
        return HTML_ELEMENT_PATTERN
                .matcher(value)
                .find();
    }

    private void validateResponseContent(
            String responseContent) {

        if (responseContent == null) {
            throw new XhtmlResponseParseException(
                    "LLM 응답이 null입니다."
            );
        }

        if (responseContent.isBlank()) {
            throw new XhtmlResponseParseException(
                    "LLM 응답이 비어 있습니다."
            );
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}