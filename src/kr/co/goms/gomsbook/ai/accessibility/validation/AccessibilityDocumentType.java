/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 접근성 검사 대상 문서 유형.
 */
public enum AccessibilityDocumentType {

    XHTML,
    HTML,
    OPF,
    NAVIGATION,
    SVG,
    XML,
    UNKNOWN;

    /**
     * 파일 확장자로 문서 유형을 판별한다.
     *
     * @param path 문서 경로
     * @return 문서 유형
     */
    public static AccessibilityDocumentType fromPath(
            Path path) {

        if (path == null
                || path.getFileName() == null) {

            return UNKNOWN;
        }

        String fileName = path
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".xhtml")) {
            return XHTML;
        }

        if (fileName.endsWith(".html")
                || fileName.endsWith(".htm")) {

            return HTML;
        }

        if (fileName.endsWith(".opf")) {
            return OPF;
        }

        if (fileName.endsWith(".svg")) {
            return SVG;
        }

        if (fileName.endsWith(".xml")) {
            return XML;
        }

        return UNKNOWN;
    }
}