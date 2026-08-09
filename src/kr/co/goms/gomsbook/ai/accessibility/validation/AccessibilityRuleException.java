/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 개별 접근성 검사 규칙 실행 중 발생하는 예외.
 */
public class AccessibilityRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String ruleId;
    private final Path documentPath;

    public AccessibilityRuleException(
            String ruleId,
            String message) {

        this(
                ruleId,
                null,
                message,
                null
        );
    }

    public AccessibilityRuleException(
            String ruleId,
            String message,
            Throwable cause) {

        this(
                ruleId,
                null,
                message,
                cause
        );
    }

    public AccessibilityRuleException(
            String ruleId,
            Path documentPath,
            String message) {

        this(
                ruleId,
                documentPath,
                message,
                null
        );
    }

    public AccessibilityRuleException(
            String ruleId,
            Path documentPath,
            String message,
            Throwable cause) {

        super(message, cause);

        this.ruleId = normalizeRequiredRuleId(ruleId);
        this.documentPath = documentPath == null
                ? null
                : documentPath
                        .toAbsolutePath()
                        .normalize();
    }

    public String getRuleId() {
        return ruleId;
    }

    public Path getDocumentPath() {
        return documentPath;
    }

    private static String normalizeRequiredRuleId(
            String ruleId) {

        Objects.requireNonNull(
                ruleId,
                "ruleId must not be null"
        );

        String normalized = ruleId.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "ruleId must not be blank"
            );
        }

        return normalized;
    }

    @Override
    public String toString() {

        return "AccessibilityRuleException{"
                + "ruleId='" + ruleId + '\''
                + ", documentPath=" + documentPath
                + ", message='" + getMessage() + '\''
                + '}';
    }
}