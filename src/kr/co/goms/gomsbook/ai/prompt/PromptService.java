/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationRequest;

/**
 * XHTML 관련 LLM Prompt를 생성하는 서비스 인터페이스입니다.
 *
 * <p>LLM 클라이언트는 PromptTemplate이나 PromptBuilder의 내부 구현을
 * 직접 사용하지 않고 이 인터페이스를 통해 완성된 Prompt를 전달받습니다.</p>
 *
 * <p>이를 통해 다음 책임을 분리할 수 있습니다.</p>
 * <ul>
 *     <li>Prompt 템플릿 정의</li>
 *     <li>Prompt 변수 치환</li>
 *     <li>LLM 요청 전송</li>
 *     <li>LLM 응답 처리</li>
 * </ul>
 */
public interface PromptService {

    /**
     * XHTML 문서 생성을 위한 Prompt를 생성합니다.
     *
     * @param request XHTML 생성 요청 정보
     * @return 완성된 XHTML 생성 Prompt
     * @throws PromptRenderException Prompt 생성에 실패한 경우
     */
    String createXhtmlGenerationPrompt(
            XhtmlGenerationRequest request
    );

    /**
     * 생성된 XHTML의 유효성을 검증하기 위한 Prompt를 생성합니다.
     *
     * @param xhtml 검증할 XHTML 문자열
     * @return 완성된 XHTML 검증 Prompt
     * @throws PromptRenderException Prompt 생성에 실패한 경우
     */
    String createXhtmlValidationPrompt(
            String xhtml
    );

    /**
     * 기존 XHTML을 수정하거나 개선하기 위한 Prompt를 생성합니다.
     *
     * @param xhtml 개선할 XHTML 문자열
     * @return 완성된 XHTML 개선 Prompt
     * @throws PromptRenderException Prompt 생성에 실패한 경우
     */
    String createXhtmlImprovementPrompt(
            String xhtml
    );

    /**
     * 기존 XHTML에 사용자 지시사항을 반영하기 위한 Prompt를 생성합니다.
     *
     * @param xhtml       수정할 XHTML 문자열
     * @param instruction 사용자 수정 지시사항
     * @return 완성된 XHTML 수정 Prompt
     * @throws PromptRenderException Prompt 생성에 실패한 경우
     */
    String createXhtmlRevisionPrompt(
            String xhtml,
            String instruction
    );

    /**
     * 일반적인 사용자 요청을 위한 Prompt를 생성합니다.
     *
     * <p>특정 XHTML 작업에 속하지 않는 자유 형식 요청에 사용할 수 있습니다.</p>
     *
     * @param instruction 사용자 요청 내용
     * @return 정규화된 Prompt
     * @throws PromptRenderException Prompt 생성에 실패한 경우
     */
    String createInstructionPrompt(
            String instruction
    );
}