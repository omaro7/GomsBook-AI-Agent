/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.dto.xhtml;

/**
 * XHTML 생성 시 적용할 표준 규칙입니다.
 */
public enum XhtmlFormattingRule {

    UTF_8,
    XHTML5,
    LANGUAGE_ATTRIBUTES,
    EPUB_NAMESPACE,
    ARIA_LABELLEDBY,
    ONE_SENTENCE_PER_PARAGRAPH,
    TWO_DIGIT_PARAGRAPH_ID,
    IMAGE_ALT_REQUIRED,
    UNIQUE_ELEMENT_ID,
    HEADING_HIERARCHY,
    FIGURE_SEMANTICS
}