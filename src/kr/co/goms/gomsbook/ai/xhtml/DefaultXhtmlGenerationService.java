/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationRequest;
import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationResponse;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmMessage;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.llm.LlmRole;
import kr.co.goms.gomsbook.ai.prompt.PromptService;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidationResult;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidator;

/**
 * XHTML 생성 서비스의 기본 구현체입니다.
 *
 * <p>다음 순서로 XHTML을 생성합니다.</p>
 * <ol>
 *     <li>생성 요청 검증</li>
 *     <li>XHTML 생성 Prompt 생성</li>
 *     <li>LLM 호출</li>
 *     <li>LLM 응답에서 XHTML 추출</li>
 *     <li>XHTML 유효성 검증</li>
 *     <li>생성 결과 반환</li>
 * </ol>
 */
public final class DefaultXhtmlGenerationService
        implements XhtmlGenerationService {

    private final PromptService promptService;
    private final LlmClient llmClient;
    private final XhtmlResponseParser responseParser;
    private final XhtmlValidator xhtmlValidator;
    private final String model;

    /**
     * XHTML 생성 서비스를 생성합니다.
     *
     * @param promptService  Prompt 생성 서비스
     * @param llmClient      LLM 클라이언트
     * @param responseParser LLM 응답 XHTML 파서
     * @param xhtmlValidator XHTML 검증기
     * @param model          XHTML 생성에 사용할 LLM 모델명
     */
    public DefaultXhtmlGenerationService(
            PromptService promptService,
            LlmClient llmClient,
            XhtmlResponseParser responseParser,
            XhtmlValidator xhtmlValidator,
            String model) {

        this.promptService = Objects.requireNonNull(
                promptService,
                "promptService must not be null"
        );

        this.llmClient = Objects.requireNonNull(
                llmClient,
                "llmClient must not be null"
        );

        this.responseParser = Objects.requireNonNull(
                responseParser,
                "responseParser must not be null"
        );

        this.xhtmlValidator = Objects.requireNonNull(
                xhtmlValidator,
                "xhtmlValidator must not be null"
        );

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "model must not be blank"
            );
        }

        this.model = model.trim();
    }

    /**
     * XHTML 생성 요청을 처리합니다.
     *
     * @param request XHTML 생성 요청
     * @return XHTML 생성 결과
     */
    @Override
    public XhtmlGenerationResponse generate(
            XhtmlGenerationRequest request) {

        validateRequest(request);

        try {
            String prompt =
                    promptService.createXhtmlGenerationPrompt(
                            request
                    );

            LlmRequest llmRequest =
                    createLlmRequest(prompt);

            LlmResponse llmResponse =
                    llmClient.chat(llmRequest);

            String rawContent =
                    extractResponseContent(llmResponse);

            String xhtml =
                    responseParser.parse(rawContent);

            XhtmlValidationResult validationResult =
                    xhtmlValidator.validate(xhtml);

            return createResponse(
                    xhtml,
                    rawContent,
                    llmResponse,
                    validationResult
            );

        } catch (XhtmlGenerationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new XhtmlGenerationException(
                    "XHTML 생성 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    /**
     * 현재 XHTML 생성 서비스가 사용 가능한지 확인합니다.
     *
     * <p>기본 구현에서는 필수 구성요소와 모델명이 정상적으로
     * 설정되어 있는지를 확인합니다.</p>
     *
     * @return 사용 가능하면 {@code true}
     */
    @Override
    public boolean isAvailable() {
        return promptService != null
                && llmClient != null
                && responseParser != null
                && xhtmlValidator != null
                && model != null
                && !model.isBlank();
    }

    /**
     * XHTML 생성용 LLM 요청을 생성합니다.
     */
    private LlmRequest createLlmRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new XhtmlGenerationException(
                    "생성된 XHTML Prompt가 비어 있습니다."
            );
        }

        LlmMessage userMessage = new LlmMessage(
                LlmRole.USER,
                prompt
        );

        return new LlmRequest(
                model,
                List.of(userMessage)
        );
    }

    /**
     * LLM 응답에서 생성 결과 문자열을 추출합니다.
     */
    private String extractResponseContent(
            LlmResponse response) {

        if (response == null) {
            throw new XhtmlGenerationException(
                    "LLM 응답이 null입니다."
            );
        }

        String content = response.getContent();

        if (content == null || content.isBlank()) {
            throw new XhtmlGenerationException(
                    "LLM 응답 내용이 비어 있습니다."
            );
        }

        return content;
    }

    /**
     * XHTML 생성 요청을 검증합니다.
     */
    private void validateRequest(
            XhtmlGenerationRequest request) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        if (request.content() == null
                || request.content().isBlank()) {

            throw new IllegalArgumentException(
                    "request content must not be blank"
            );
        }

        if (request.title() == null
                || request.title().isBlank()) {

            throw new IllegalArgumentException(
                    "request title must not be blank"
            );
        }
    }

    /**
     * XHTML 생성 응답 객체를 생성합니다.
     */
    private XhtmlGenerationResponse createResponse(
            String xhtml,
            String rawContent,
            LlmResponse llmResponse,
            XhtmlValidationResult validationResult) {

        boolean valid = validationResult != null
                && validationResult.isValid();

        return new XhtmlGenerationResponse(
                xhtml,
                rawContent,
                valid,
                validationResult,
                llmResponse.getModel()
        );
    }
}