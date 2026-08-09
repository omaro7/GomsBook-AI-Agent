/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.agent;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.AgentRequest;
import kr.co.goms.gomsbook.ai.agent.AgentResponse;

/**
 * EPUB 생성/검증/조회 기능을 제공하는 Agent 계약입니다.
 *
 * <p>EPUB Agent는 사용자 요청을 해석하고 필요에 따라
 * 다음 Tool을 호출합니다.</p>
 *
 * <ul>
 *     <li>generate_epub</li>
 *     <li>validate_epub</li>
 *     <li>inspect_epub</li>
 * </ul>
 *
 * <p>실제 LLM 호출 및 Tool 실행 루프는 Framework 계층의
 * AgentExecutor 구현체가 담당합니다.</p>
 */
/**
 * EPUB AI Agent 인터페이스입니다.
 */
public interface EpubAgent {

    /**
     * EPUB Agent를 실행합니다.
     *
     * @param request Agent 요청
     * @return Agent 응답
     */
    AgentResponse execute(
            AgentRequest request
    );

    /**
     * EPUB Agent 설정을 반환합니다.
     */
    EpubAgentConfiguration getConfiguration();

    /**
     * Agent 이름을 반환합니다.
     */
    default String getName() {
        return getConfiguration()
                .getAgentName();
    }

    /**
     * EPUB 생성 Tool 사용 가능 여부입니다.
     */
    default boolean canGenerateEpub() {
        return getConfiguration()
                .canGenerateEpub();
    }

    /**
     * EPUB 검증 Tool 사용 가능 여부입니다.
     */
    default boolean canValidateEpub() {
        return getConfiguration()
                .canValidateEpub();
    }

    /**
     * EPUB inspection Tool 사용 가능 여부입니다.
     */
    default boolean canInspectEpub() {
        return getConfiguration()
                .canInspectEpub();
    }
}