/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;

/**
 * 대체 텍스트 적용 오류 코드.
 */
public enum AltTextApplicationErrorCode {

    INVALID_REQUEST(false),

    XHTML_NOT_FOUND(false),

    XHTML_NOT_READABLE(false),

    XHTML_NOT_WRITABLE(false),

    XHTML_PARSE_FAILED(false),

    IMAGE_ELEMENT_NOT_FOUND(false),

    IMAGE_ELEMENT_AMBIGUOUS(false),

    EXISTING_ALT_CONFLICT(false),

    EXPECTED_ALT_MISMATCH(false),

    UNSUPPORTED_ACCESSIBILITY_TYPE(false),

    BACKUP_FAILED(true),

    XHTML_WRITE_FAILED(true),

    APPLICATION_FAILED(true),

    UNKNOWN(true);

    private final boolean retryable;

    AltTextApplicationErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}