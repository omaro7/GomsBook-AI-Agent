/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 접근성 검사 실행 중 발생하는 예외.
 */
public class AccessibilityValidationException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AccessibilityValidationErrorCode errorCode;
    private final Path documentPath;
    private final String ruleId;

    public AccessibilityValidationException(
            AccessibilityValidationErrorCode errorCode,
            String message) {

        this(
                errorCode,
                null,
                null,
                message,
                null
        );
    }

    public AccessibilityValidationException(
            AccessibilityValidationErrorCode errorCode,
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

    public AccessibilityValidationException(
            AccessibilityValidationErrorCode errorCode,
            Path documentPath,
            String ruleId,
            String message) {

        this(
                errorCode,
                documentPath,
                ruleId,
                message,
                null
        );
    }

    public AccessibilityValidationException(
            AccessibilityValidationErrorCode errorCode,
            Path documentPath,
            String ruleId,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.documentPath =
                documentPath == null
                        ? null
                        : documentPath
                                .toAbsolutePath()
                                .normalize();

        this.ruleId =
                ruleId == null || ruleId.isBlank()
                        ? null
                        : ruleId.trim();
    }

    public AccessibilityValidationErrorCode getErrorCode() {
        return errorCode;
    }

    public Path getDocumentPath() {
        return documentPath;
    }

    public String getRuleId() {
        return ruleId;
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }
}