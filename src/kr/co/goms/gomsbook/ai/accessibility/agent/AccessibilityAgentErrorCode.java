/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

/**
 * 접근성 Agent 오류 코드.
 */
public enum AccessibilityAgentErrorCode {

    INVALID_REQUEST(false),

    CONFIGURATION_INVALID(false),

    TOOL_NOT_ALLOWED(false),

    TOOL_EXECUTION_FAILED(true),

    LLM_INVOCATION_FAILED(true),

    MAX_ITERATIONS_REACHED(false),

    MAX_TOOL_CALLS_REACHED(false),

    EXECUTION_TIMEOUT(true),

    POLICY_VIOLATION(false),

    EXECUTION_FAILED(true),

    UNKNOWN(true);

    private final boolean retryable;

    AccessibilityAgentErrorCode(
            boolean retryable) {

        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}