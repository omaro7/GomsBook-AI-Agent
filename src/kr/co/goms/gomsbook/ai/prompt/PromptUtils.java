/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Prompt 문자열 처리 유틸리티.
 * 
	indent(String text, int spaces) : XHTML 코드 들여쓰기
	stripXmlDeclaration(String xml) : <?xml ...?> 제거
	stripDoctype(String xhtml) : <!DOCTYPE ...> 제거
	extractBody(String xhtml) : <body> 내부만 추출
	normalizeWhitespace(String text) : 공백 여러 개를 하나로 축소
	estimateTokens(String prompt) : Ollama/OpenAI 호출 전 대략적인 토큰 수 계산
	toPromptBlock(String title, String content) : [Title], [Content] 형태의 프롬프트 블록 생성

 */
public final class PromptUtils {

    private PromptUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * null이면 빈 문자열을 반환합니다.
     */
    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * null 또는 공백이면 기본값을 반환합니다.
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * 문자열이 null 또는 공백인지 확인합니다.
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 문자열 앞뒤 공백 제거.
     */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * CRLF/CR을 LF로 통일합니다.
     */
    public static String normalizeLineEndings(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    /**
     * 앞뒤 공백 제거 후 줄바꿈을 LF로 통일합니다.
     */
    public static String normalize(String value) {
        return trim(normalizeLineEndings(value));
    }

    /**
     * 연속된 빈 줄을 하나로 줄입니다.
     */
    public static String collapseBlankLines(String value) {

        String normalized = normalize(value);

        return normalized.replaceAll("\n{3,}", "\n\n");
    }

    /**
     * XML/XHTML 예약문자를 Escape 합니다.
     */
    public static String escapeXml(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Collection을 줄바꿈으로 연결합니다.
     */
    public static String joinLines(Collection<?> values) {

        Objects.requireNonNull(values);

        StringJoiner joiner = new StringJoiner("\n");

        for (Object value : values) {
            if (value != null) {
                joiner.add(String.valueOf(value));
            }
        }

        return joiner.toString();
    }

    /**
     * Collection을 구분자로 연결합니다.
     */
    public static String join(Collection<?> values, String delimiter) {

        Objects.requireNonNull(values);
        Objects.requireNonNull(delimiter);

        StringJoiner joiner = new StringJoiner(delimiter);

        for (Object value : values) {
            if (value != null) {
                joiner.add(String.valueOf(value));
            }
        }

        return joiner.toString();
    }

    /**
     * 최대 길이를 초과하면 잘라냅니다.
     */
    public static String truncate(String value, int maxLength) {

        if (value == null) {
            return "";
        }

        if (maxLength < 0) {
            throw new IllegalArgumentException(
                    "maxLength must be >= 0"
            );
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    /**
     * 최대 길이를 초과하면 말줄임표를 붙입니다.
     */
    public static String abbreviate(String value, int maxLength) {

        if (value == null) {
            return "";
        }

        if (maxLength < 4) {
            throw new IllegalArgumentException(
                    "maxLength must be >= 4"
            );
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }

    /**
     * 지정한 문자열을 반복합니다.
     */
    public static String repeat(String value, int count) {

        Objects.requireNonNull(value);

        if (count <= 0) {
            return "";
        }

        return value.repeat(count);
    }

    /**
     * Markdown 코드 블록 제거.
     */
    public static String removeMarkdownCodeFence(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("(?s)^```[a-zA-Z0-9_-]*\\n", "")
                .replaceAll("\\n```$", "");
    }

    /**
     * Prompt 입력값 정규화.
     */
    public static String sanitizePrompt(String value) {

        return collapseBlankLines(
                normalize(
                        removeMarkdownCodeFence(value)
                )
        );
    }

}