/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

/**
 * 접근성 Agent 실행 상태.
 */
public enum AccessibilityAgentStatus {

    /**
     * 요청한 접근성 작업이 정상적으로 완료됨.
     */
    COMPLETED,

    /**
     * 일부 작업은 완료했지만 Tool 실패 또는 검토 항목이 남아 있음.
     */
    PARTIALLY_COMPLETED,

    /**
     * 파일 변경 없이 분석 또는 검사만 완료함.
     */
    REVIEW_REQUIRED,

    /**
     * 요청값이 유효하지 않음.
     */
    INVALID_REQUEST,

    /**
     * Agent 실행에 실패함.
     */
    FAILED,

    /**
     * 최대 반복 횟수 또는 Tool 호출 제한에 도달함.
     */
    LIMIT_REACHED;

    public boolean isSuccessful() {
        return this == COMPLETED
                || this == PARTIALLY_COMPLETED
                || this == REVIEW_REQUIRED;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean requiresReview() {
        return this == REVIEW_REQUIRED
                || this == PARTIALLY_COMPLETED;
    }

    public boolean isFailure() {
        return this == INVALID_REQUEST
                || this == FAILED
                || this == LIMIT_REACHED;
    }
}