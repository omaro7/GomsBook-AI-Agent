/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

/**
 * 접근성 검사 오류 코드.
 */
public enum AccessibilityValidationErrorCode {

    INVALID_REQUEST(false),

    DOCUMENT_NOT_FOUND(false),

    DOCUMENT_NOT_READABLE(false),

    UNSUPPORTED_DOCUMENT_TYPE(false),

    DOCUMENT_PARSE_FAILED(false),

    RULE_EXECUTION_FAILED(true),

    VALIDATION_FAILED(true),

    UNKNOWN(true);

    private final boolean retryable;

    AccessibilityValidationErrorCode(
            boolean retryable) {

        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}