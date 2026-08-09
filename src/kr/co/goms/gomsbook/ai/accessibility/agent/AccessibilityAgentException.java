/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.util.Objects;

/**
 * 접근성 Agent 실행 과정에서 발생하는 예외.
 */
public class AccessibilityAgentException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AccessibilityAgentErrorCode errorCode;
    private final String agentName;
    private final String toolName;

    public AccessibilityAgentException(
            AccessibilityAgentErrorCode errorCode,
            String message) {

        this(
                errorCode,
                null,
                null,
                message,
                null
        );
    }

    public AccessibilityAgentException(
            AccessibilityAgentErrorCode errorCode,
            String message,
            Throwable cause) {

        this(
                errorCode,
                null,
                null,
                message,
                cause
        );
    }

    public AccessibilityAgentException(
            AccessibilityAgentErrorCode errorCode,
            String agentName,
            String toolName,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.agentName = normalizeOptionalText(
                agentName
        );

        this.toolName = normalizeOptionalText(
                toolName
        );
    }

    public AccessibilityAgentErrorCode getErrorCode() {
        return errorCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    private static String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}