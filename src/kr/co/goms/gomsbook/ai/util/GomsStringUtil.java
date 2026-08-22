/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.util;

public class GomsStringUtil
{

	/**
	 * AI 로그 답변에 대해서 긴 본문을 로그에 전부 찍지 않도록 처리
	 * @param text
	 * @param maximumLength
	 * @return
	 */
    public static String abbreviate(String text, int maximumLength) {
        if (text == null) {
            return "";
        }

        String normalized =
                text
                        .replace('\r', ' ')
                        .replace('\n', ' ')
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (normalized.length() <= maximumLength) {
            return normalized;
        }

        return normalized.substring(
                0,
                maximumLength
        ) + "...";
    }
    
    /**
     * 문자열 정규화
     * @param value
     * @return
     */
    public static String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(
                        java.util.Locale.ROOT
                );
    }
    
    /**
     * path 정규화
     * @param path
     * @return
     */
    public static String normalizePath(String path) {

        if (path == null) {
            return "";
        }

        return path
                .trim()
                .replace(
                        '\\',
                        '/'
                );
    }
    
}
