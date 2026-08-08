/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.dto.xhtml;

import java.util.Objects;

/**
 * XHTML 생성 요청입니다.
 *
 * <p>제목, 본문, 작성자, 장 정보, 언어, 스타일,
 * 추가 지시사항 및 검증 여부를 포함합니다.</p>
 */
public final class XhtmlGenerationRequest {

    private final String title;
    private final String content;
    private final String author;
    private final String chapter;
    private final String language;
    private final String style;
    private final String instruction;
    private final boolean validationEnabled;

    private XhtmlGenerationRequest(Builder builder) {
        this.title = requireText(
                builder.title,
                "title"
        );

        this.content = requireText(
                builder.content,
                "content"
        );

        this.author = normalizeOptional(
                builder.author
        );

        this.chapter = normalizeOptional(
                builder.chapter
        );

        this.language = normalizeLanguage(
                builder.language
        );

        this.style = normalizeOptional(
                builder.style
        );

        this.instruction = normalizeOptional(
                builder.instruction
        );

        this.validationEnabled =
                builder.validationEnabled;
    }

    /**
     * Builder를 생성합니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기존 요청을 기반으로 Builder를 생성합니다.
     */
    public static Builder builder(
            XhtmlGenerationRequest source) {

        Objects.requireNonNull(
                source,
                "source must not be null"
        );

        return new Builder(source);
    }

    // =========================================================
    // JavaBean 스타일 Getter
    // =========================================================

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getAuthor() {
        return author;
    }

    public String getChapter() {
        return chapter;
    }

    public String getLanguage() {
        return language;
    }

    public String getStyle() {
        return style;
    }

    public String getInstruction() {
        return instruction;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    // =========================================================
    // 기존 record 스타일 호환 메서드
    // =========================================================

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    public String author() {
        return author;
    }

    public String chapter() {
        return chapter;
    }

    public String language() {
        return language;
    }

    public String style() {
        return style;
    }

    public String instruction() {
        return instruction;
    }

    public boolean validationEnabled() {
        return validationEnabled;
    }

    // =========================================================
    // 편의 메서드
    // =========================================================

    public boolean hasAuthor() {
        return author != null;
    }

    public boolean hasChapter() {
        return chapter != null;
    }

    public boolean hasStyle() {
        return style != null;
    }

    public boolean hasInstruction() {
        return instruction != null;
    }

    /**
     * 기존 요청을 기반으로 Builder를 반환합니다.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    private static String normalizeOptional(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeLanguage(
            String value) {

        if (value == null || value.isBlank()) {
            return "ko";
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "XhtmlGenerationRequest{"
                + "title='" + title + '\''
                + ", contentLength="
                + (content == null ? 0 : content.length())
                + ", author='" + author + '\''
                + ", chapter='" + chapter + '\''
                + ", language='" + language + '\''
                + ", style='" + style + '\''
                + ", instruction='" + instruction + '\''
                + ", validationEnabled="
                + validationEnabled
                + '}';
    }

    /**
     * XhtmlGenerationRequest Builder입니다.
     */
    public static final class Builder {

        private String title;
        private String content;
        private String author;
        private String chapter;
        private String language = "ko";
        private String style;
        private String instruction;
        private boolean validationEnabled = true;

        private Builder() {
        }

        private Builder(
                XhtmlGenerationRequest source) {

            this.title = source.title;
            this.content = source.content;
            this.author = source.author;
            this.chapter = source.chapter;
            this.language = source.language;
            this.style = source.style;
            this.instruction = source.instruction;
            this.validationEnabled =
                    source.validationEnabled;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder chapter(String chapter) {
            this.chapter = chapter;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public Builder instruction(
                String instruction) {

            this.instruction = instruction;
            return this;
        }

        public Builder validationEnabled(
                boolean validationEnabled) {

            this.validationEnabled =
                    validationEnabled;

            return this;
        }

        public XhtmlGenerationRequest build() {
            return new XhtmlGenerationRequest(this);
        }
    }
}