/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationRequest;

/**
 * PromptService 기본 구현체.
 *
 * <p>PromptTemplate, PromptBuilder를 이용하여
 * XHTML 관련 Prompt를 생성합니다.</p>
 */
public class DefaultPromptService implements PromptService {

    @Override
    public String createXhtmlGenerationPrompt(
            XhtmlGenerationRequest request) {

        Objects.requireNonNull(request, "request must not be null");

        return PromptBuilder
                .from(PromptTemplates.XHTML_GENERATION)
                .put(PromptVariables.TITLE, request.title())
                .put(PromptVariables.AUTHOR, request.author())
                .put(PromptVariables.CHAPTER, request.chapter())
                .put(PromptVariables.CONTENT, request.content())
                .putDefault(
                        PromptVariables.STYLE,
                        ""
                )
                .putDefault(
                        PromptVariables.INSTRUCTION,
                        ""
                )
                .build();
    }

    @Override
    public String createXhtmlValidationPrompt(
            String xhtml) {

        return PromptBuilder
                .from(PromptTemplates.XHTML_VALIDATION)
                .put(
                        PromptVariables.XHTML,
                        PromptUtils.sanitizePrompt(xhtml)
                )
                .build();
    }

    @Override
    public String createXhtmlImprovementPrompt(
            String xhtml) {

        return PromptBuilder
                .from(PromptTemplates.XHTML_IMPROVEMENT)
                .put(
                        PromptVariables.XHTML,
                        PromptUtils.sanitizePrompt(xhtml)
                )
                .build();
    }

    @Override
    public String createXhtmlRevisionPrompt(
            String xhtml,
            String instruction) {

        return PromptBuilder
                .from(PromptTemplates.XHTML_IMPROVEMENT)
                .put(
                        PromptVariables.XHTML,
                        PromptUtils.sanitizePrompt(xhtml)
                )
                .put(
                        PromptVariables.INSTRUCTION,
                        PromptUtils.sanitizePrompt(instruction)
                )
                .build();
    }

    @Override
    public String createInstructionPrompt(
            String instruction) {

        return PromptUtils.sanitizePrompt(instruction);
    }

}