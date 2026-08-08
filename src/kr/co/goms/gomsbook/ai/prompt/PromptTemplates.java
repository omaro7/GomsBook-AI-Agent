/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

/**
 * XHTML 및 LLM Prompt 중앙 관리 클래스.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * XHTML 생성 Prompt
     */
    public static final PromptTemplate XHTML_GENERATION =
            PromptTemplate.of("""
You are an EPUB3 XHTML expert.

Generate valid XHTML5 only.

Requirements

- Output only XHTML.
- UTF-8
- Semantic HTML5
- EPUB3 compatible
- Accessibility compliant
- Use Korean language.
- Do not wrap with markdown.
- Do not explain.

Title
${title}

Author
${author}

Chapter
${chapter}

Content
${content}

Style
${style}

Additional Instructions
${instruction}
""");

    /**
     * XHTML 검증 Prompt
     */
    public static final PromptTemplate XHTML_VALIDATION =
            PromptTemplate.of("""
You are an EPUB validator.

Validate the following XHTML.

Return JSON only.

XHTML

${xhtml}
""");

    /**
     * XHTML 개선 Prompt
     */
    public static final PromptTemplate XHTML_IMPROVEMENT =
            PromptTemplate.of("""
You are an EPUB editor.

Improve the following XHTML.

Requirements

- Keep meaning.
- Improve accessibility.
- Improve semantic markup.
- Preserve EPUB3 compatibility.
- Return XHTML only.

XHTML

${xhtml}
""");

}