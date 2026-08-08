/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm;

/**
 * LLM 응답이 종료된 사유입니다.
 */
public enum LlmFinishReason {

    /**
     * 정상적으로 응답 생성이 완료되었습니다.
     */
    STOP,

    /**
     * 최대 토큰 또는 출력 길이에 도달했습니다.
     */
    LENGTH,

    /**
     * LLM이 Tool 호출을 요청했습니다.
     */
    TOOL_CALL,

    /**
     * 콘텐츠 안전 정책에 의해 차단되었습니다.
     */
    CONTENT_FILTER,

    /**
     * 요청이 취소되었습니다.
     */
    CANCELLED,

    /**
     * Provider 실행 오류가 발생했습니다.
     */
    ERROR,

    /**
     * 종료 사유를 확인할 수 없습니다.
     */
    UNKNOWN
}