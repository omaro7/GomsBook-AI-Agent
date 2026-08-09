/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 접근성 Agent가 수행한 개별 Tool 호출 기록.
 */
public final class AccessibilityAgentToolExecution {

    private final String toolName;
    private final boolean successful;
    private final String message;
    private final Duration duration;
    private final Map<String, Object> data;

    public AccessibilityAgentToolExecution(
            String toolName,
            boolean successful,
            String message,
            Duration duration,
            Map<String, Object> data) {

        this.toolName = normalizeRequiredText(
                toolName,
                "toolName"
        );

        this.successful = successful;

        this.message = normalizeOptionalText(
                message
        );

        this.duration = validateDuration(duration);

        this.data = immutableData(data);
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getMessage() {
        return message;
    }

    public Duration getDuration() {
        return duration;
    }

    public Map<String, Object> getData() {
        return data;
    }

    private static Duration validateDuration(
            Duration value) {

        if (value != null && value.isNegative()) {
            throw new IllegalArgumentException(
                    "duration must not be negative"
            );
        }

        return value;
    }

    private static Map<String, Object> immutableData(
            Map<String, Object> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(source)
        );
    }

    private static String normalizeRequiredText(
            String value,
            String fieldName) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized;
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