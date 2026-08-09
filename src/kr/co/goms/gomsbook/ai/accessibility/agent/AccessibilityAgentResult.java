/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 접근성 Agent의 전체 실행 결과.
 */
public final class AccessibilityAgentResult {

    private final AccessibilityAgentStatus status;
    private final String agentName;
    private final String response;

    private final int iterationCount;
    private final int toolCallCount;

    private final boolean fileModified;
    private final boolean manualReviewRequired;

    private final List<AccessibilityAgentToolExecution>
            toolExecutions;

    private final List<String> warnings;
    private final List<String> modifiedDocuments;

    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;

    private final Map<String, String> metadata;

    private AccessibilityAgentResult(
            Builder builder) {

        this.status = Objects.requireNonNull(
                builder.status,
                "status must not be null"
        );

        this.agentName = normalizeRequiredText(
                builder.agentName,
                "agentName"
        );

        this.response = normalizeRequiredText(
                builder.response,
                "response"
        );

        this.iterationCount =
                validateNonNegative(
                        builder.iterationCount,
                        "iterationCount"
                );

        this.toolCallCount =
                validateNonNegative(
                        builder.toolCallCount,
                        "toolCallCount"
                );

        this.fileModified =
                builder.fileModified;

        this.manualReviewRequired =
                builder.manualReviewRequired;

        this.toolExecutions =
                immutableToolExecutions(
                        builder.toolExecutions
                );

        this.warnings =
                immutableStrings(
                        builder.warnings
                );

        this.modifiedDocuments =
                immutableStrings(
                        builder.modifiedDocuments
                );

        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;

        validateTimes(
                startedAt,
                completedAt
        );

        this.duration =
                resolveDuration(
                        startedAt,
                        completedAt,
                        builder.duration
                );

        this.metadata =
                immutableMetadata(
                        builder.metadata
                );

        validateState();
    }

    public AccessibilityAgentStatus getStatus() {
        return status;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getResponse() {
        return response;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public boolean isFileModified() {
        return fileModified;
    }

    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    public List<AccessibilityAgentToolExecution>
            getToolExecutions() {

        return toolExecutions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getModifiedDocuments() {
        return modifiedDocuments;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getDuration() {
        return duration;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isSuccessful() {
        return status.isSuccessful();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasToolExecutions() {
        return !toolExecutions.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validateState() {

        if (toolCallCount
                < toolExecutions.size()) {

            throw new IllegalArgumentException(
                    "toolCallCount must not be smaller than "
                            + "toolExecutions size"
            );
        }

        if (fileModified
                && modifiedDocuments.isEmpty()) {

            throw new IllegalArgumentException(
                    "modifiedDocuments is required when "
                            + "fileModified is true"
            );
        }
    }

    private static int validateNonNegative(
            int value,
            String fieldName) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + " must not be negative"
            );
        }

        return value;
    }

    private static void validateTimes(
            Instant startedAt,
            Instant completedAt) {

        if (startedAt != null
                && completedAt != null
                && completedAt.isBefore(startedAt)) {

            throw new IllegalArgumentException(
                    "completedAt must not be before startedAt"
            );
        }
    }

    private static Duration resolveDuration(
            Instant startedAt,
            Instant completedAt,
            Duration explicitDuration) {

        if (explicitDuration != null) {
            if (explicitDuration.isNegative()) {
                throw new IllegalArgumentException(
                        "duration must not be negative"
                );
            }

            return explicitDuration;
        }

        if (startedAt != null && completedAt != null) {
            return Duration.between(
                    startedAt,
                    completedAt
            );
        }

        return null;
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

    private static List<AccessibilityAgentToolExecution>
            immutableToolExecutions(
                    List<AccessibilityAgentToolExecution> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<AccessibilityAgentToolExecution> result =
                new ArrayList<>();

        for (AccessibilityAgentToolExecution execution : source) {
            if (execution != null) {
                result.add(execution);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(result);
    }

    private static List<String> immutableStrings(
            List<String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (String value : source) {
            String normalized =
                    normalizeOptionalText(value);

            if (normalized != null
                    && !result.contains(normalized)) {

                result.add(normalized);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(result);
    }

    private static Map<String, String> immutableMetadata(
            Map<String, String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry
                : source.entrySet()) {

            String key = normalizeOptionalText(
                    entry.getKey()
            );

            String value = normalizeOptionalText(
                    entry.getValue()
            );

            if (key != null && value != null) {
                result.put(key, value);
            }
        }

        return result.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(result);
    }

    public static final class Builder {

        private AccessibilityAgentStatus status;
        private String agentName;
        private String response;

        private int iterationCount;
        private int toolCallCount;

        private boolean fileModified;
        private boolean manualReviewRequired;

        private final List<AccessibilityAgentToolExecution>
                toolExecutions = new ArrayList<>();

        private final List<String> warnings =
                new ArrayList<>();

        private final List<String> modifiedDocuments =
                new ArrayList<>();

        private Instant startedAt;
        private Instant completedAt;
        private Duration duration;

        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder status(
                AccessibilityAgentStatus status) {

            this.status = status;
            return this;
        }

        public Builder agentName(
                String agentName) {

            this.agentName = agentName;
            return this;
        }

        public Builder response(
                String response) {

            this.response = response;
            return this;
        }

        public Builder iterationCount(
                int iterationCount) {

            this.iterationCount = iterationCount;
            return this;
        }

        public Builder toolCallCount(
                int toolCallCount) {

            this.toolCallCount = toolCallCount;
            return this;
        }

        public Builder fileModified(
                boolean fileModified) {

            this.fileModified = fileModified;
            return this;
        }

        public Builder manualReviewRequired(
                boolean manualReviewRequired) {

            this.manualReviewRequired =
                    manualReviewRequired;

            return this;
        }

        public Builder toolExecution(
                AccessibilityAgentToolExecution execution) {

            if (execution != null) {
                toolExecutions.add(execution);
            }

            return this;
        }

        public Builder toolExecutions(
                List<AccessibilityAgentToolExecution> executions) {

            if (executions != null) {
                for (AccessibilityAgentToolExecution execution
                        : executions) {

                    toolExecution(execution);
                }
            }

            return this;
        }

        public Builder warning(
                String warning) {

            String normalized =
                    normalizeOptionalText(warning);

            if (normalized != null
                    && !warnings.contains(normalized)) {

                warnings.add(normalized);
            }

            return this;
        }

        public Builder modifiedDocument(
                String documentPath) {

            String normalized =
                    normalizeOptionalText(documentPath);

            if (normalized != null
                    && !modifiedDocuments.contains(normalized)) {

                modifiedDocuments.add(
                        normalized.replace('\\', '/')
                );
            }

            return this;
        }

        public Builder startedAt(
                Instant startedAt) {

            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(
                Instant completedAt) {

            this.completedAt = completedAt;
            return this;
        }

        public Builder duration(
                Duration duration) {

            this.duration = duration;
            return this;
        }

        public Builder metadata(
                String key,
                String value) {

            String normalizedKey =
                    normalizeOptionalText(key);

            String normalizedValue =
                    normalizeOptionalText(value);

            if (normalizedKey != null
                    && normalizedValue != null) {

                metadata.put(
                        normalizedKey,
                        normalizedValue
                );
            }

            return this;
        }

        public AccessibilityAgentResult build() {
            return new AccessibilityAgentResult(this);
        }
    }
}