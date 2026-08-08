/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

/**
 * PromptTemplate에서 사용하는 변수명 상수.
 *
 * <p>모든 PromptTemplate은 가능한 한 이 클래스의 상수를 사용하여
 * 변수명을 정의하고 참조하도록 권장합니다.</p>
 *
 * <pre>
 * ${title}
 * ${content}
 * </pre>
 * 
 * String prompt = PromptBuilder
        .from(PromptTemplates.XHTML_GENERATION)
        .put("title", request.getTitle())
        .put("author", request.getAuthor())
        .put("chapter", request.getChapter())
        .put("content", request.getContent())
        .put("style", request.getStyle())
        .put("instruction", request.getInstruction())
        .build();
 */
public final class PromptVariables {

    private PromptVariables() {
        throw new AssertionError("Utility class");
    }

    /* ==========================================================
     * Common
     * ========================================================== */

    public static final String TITLE = "title";
    public static final String SUBTITLE = "subtitle";
    public static final String AUTHOR = "author";
    public static final String LANGUAGE = "language";

    /* ==========================================================
     * Chapter
     * ========================================================== */

    public static final String PART = "part";
    public static final String CHAPTER = "chapter";
    public static final String SECTION = "section";

    /* ==========================================================
     * XHTML
     * ========================================================== */

    public static final String CONTENT = "content";
    public static final String XHTML = "xhtml";
    public static final String STYLE = "style";
    public static final String INSTRUCTION = "instruction";

    /* ==========================================================
     * LLM
     * ========================================================== */

    public static final String SYSTEM_PROMPT = "systemPrompt";
    public static final String USER_PROMPT = "userPrompt";
    public static final String ASSISTANT_PROMPT = "assistantPrompt";

    /* ==========================================================
     * Image
     * ========================================================== */

    public static final String IMAGE_STYLE = "imageStyle";
    public static final String IMAGE_PROMPT = "imagePrompt";
    public static final String IMAGE_SIZE = "imageSize";

    /* ==========================================================
     * Metadata
     * ========================================================== */

    public static final String DATE = "date";
    public static final String VERSION = "version";
    public static final String PUBLISHER = "publisher";
    public static final String ISBN = "isbn";

}