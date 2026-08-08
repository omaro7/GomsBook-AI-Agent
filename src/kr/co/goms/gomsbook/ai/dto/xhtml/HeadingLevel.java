/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.dto.xhtml;

/**
 * XHTML 제목 태그 수준입니다.
 */
public enum HeadingLevel {

    H1(1, "h1"),
    H2(2, "h2"),
    H3(3, "h3"),
    H4(4, "h4"),
    H5(5, "h5"),
    H6(6, "h6");

    private final int level;
    private final String tagName;

    HeadingLevel(
            int level,
            String tagName
    ) {
        this.level = level;
        this.tagName = tagName;
    }

    public int getLevel() {
        return level;
    }

    public String getTagName() {
        return tagName;
    }
}