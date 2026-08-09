/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 접근성 검사 요청.
 *
 * <p>검사 대상 문서, 활성화 또는 비활성화할 규칙, 검사 수준과
 * 공통 옵션을 포함하는 불변 객체이다.</p>
 */
public final class AccessibilityValidationRequest {

    public static final int DEFAULT_MAX_ALT_TEXT_LENGTH = 150;

    private final Path projectRoot;
    private final Path documentPath;
    private final AccessibilityDocumentType documentType;

    private final boolean strictMode;
    private final boolean includeInformationalIssues;
    private final boolean continueOnRuleError;
    private final int maximumAltTextLength;

    private final Set<String> enabledRuleIds;
    private final Set<String> disabledRuleIds;

    private final Map<String, String> options;
    private final Map<String, String> metadata;

    private AccessibilityValidationRequest(
            Builder builder) {

        this.projectRoot = normalizeRequiredPath(
                builder.projectRoot,
                "projectRoot"
        );

        this.documentPath = normalizeRequiredPath(
                builder.documentPath,
                "documentPath"
        );

        if (!documentPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "documentPath must be inside projectRoot"
            );
        }

        this.documentType =
                builder.documentType == null
                        ? AccessibilityDocumentType
                                .fromPath(documentPath)
                        : builder.documentType;

        this.strictMode =
                builder.strictMode;

        this.includeInformationalIssues =
                builder.includeInformationalIssues;

        this.continueOnRuleError =
                builder.continueOnRuleError;

        this.maximumAltTextLength =
                validateMaximumAltTextLength(
                        builder.maximumAltTextLength
                );

        this.enabledRuleIds =
                immutableStringSet(
                        builder.enabledRuleIds
                );

        this.disabledRuleIds =
                immutableStringSet(
                        builder.disabledRuleIds
                );

        this.options =
                immutableMap(
                        builder.options
                );

        this.metadata =
                immutableMap(
                        builder.metadata
                );

        validateRuleConfiguration();
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getDocumentPath() {
        return documentPath;
    }

    public String getProjectRelativePath() {
        return projectRoot
                .relativize(documentPath)
                .toString()
                .replace('\\', '/');
    }

    public AccessibilityDocumentType getDocumentType() {
        return documentType;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public boolean isIncludeInformationalIssues() {
        return includeInformationalIssues;
    }

    public boolean isContinueOnRuleError() {
        return continueOnRuleError;
    }

    public int getMaximumAltTextLength() {
        return maximumAltTextLength;
    }

    public Set<String> getEnabledRuleIds() {
        return enabledRuleIds;
    }

    public Set<String> getDisabledRuleIds() {
        return disabledRuleIds;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public String getOption(String key) {

        if (key == null) {
            return null;
        }

        return options.get(key);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getMetadata(String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    /**
     * 특정 규칙이 실행 대상인지 반환한다.
     *
     * @param rule 검사 규칙
     * @return 실행 대상이면 {@code true}
     */
    public boolean isRuleEnabled(
            AccessibilityRule rule) {

        if (rule == null) {
            return false;
        }

        String ruleId = rule.getId();

        if (disabledRuleIds.contains(ruleId)) {
            return false;
        }

        if (!enabledRuleIds.isEmpty()) {
            return enabledRuleIds.contains(ruleId);
        }

        return rule.isEnabledByDefault();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validateRuleConfiguration() {

        for (String ruleId : enabledRuleIds) {
            if (disabledRuleIds.contains(ruleId)) {
                throw new IllegalArgumentException(
                        "Rule cannot be both enabled and disabled: "
                                + ruleId
                );
            }
        }
    }

    private static Path normalizeRequiredPath(
            Path path,
            String fieldName) {

        Objects.requireNonNull(
                path,
                fieldName + " must not be null"
        );

        return path
                .toAbsolutePath()
                .normalize();
    }

    private static int validateMaximumAltTextLength(
            int value) {

        if (value < 20 || value > 2_000) {
            throw new IllegalArgumentException(
                    "maximumAltTextLength must be between "
                            + "20 and 2000"
            );
        }

        return value;
    }

    private static Set<String> immutableStringSet(
            Set<String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String value : source) {
            String normalized =
                    normalizeOptionalText(value);

            if (normalized != null) {
                result.add(normalized);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(result);
    }

    private static Map<String, String> immutableMap(
            Map<String, String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry
                : source.entrySet()) {

            String key =
                    normalizeOptionalText(
                            entry.getKey()
                    );

            String value =
                    normalizeOptionalText(
                            entry.getValue()
                    );

            if (key != null && value != null) {
                result.put(key, value);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(result);
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

    public static final class Builder {

        private Path projectRoot;
        private Path documentPath;
        private AccessibilityDocumentType documentType;

        private boolean strictMode;
        private boolean includeInformationalIssues = true;
        private boolean continueOnRuleError = true;
        private int maximumAltTextLength =
                DEFAULT_MAX_ALT_TEXT_LENGTH;

        private final Set<String> enabledRuleIds =
                new LinkedHashSet<>();

        private final Set<String> disabledRuleIds =
                new LinkedHashSet<>();

        private final Map<String, String> options =
                new LinkedHashMap<>();

        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder projectRoot(
                Path projectRoot) {

            this.projectRoot = projectRoot;
            return this;
        }

        public Builder documentPath(
                Path documentPath) {

            this.documentPath = documentPath;
            return this;
        }

        public Builder projectDocument(
                Path projectRoot,
                Path relativeDocumentPath) {

            Objects.requireNonNull(
                    projectRoot,
                    "projectRoot must not be null"
            );

            Objects.requireNonNull(
                    relativeDocumentPath,
                    "relativeDocumentPath must not be null"
            );

            if (relativeDocumentPath.isAbsolute()) {
                throw new IllegalArgumentException(
                        "relativeDocumentPath must be relative"
                );
            }

            this.projectRoot = projectRoot;
            this.documentPath =
                    projectRoot.resolve(
                            relativeDocumentPath
                    );

            return this;
        }

        public Builder documentType(
                AccessibilityDocumentType documentType) {

            this.documentType = documentType;
            return this;
        }

        public Builder strictMode(
                boolean strictMode) {

            this.strictMode = strictMode;
            return this;
        }

        public Builder includeInformationalIssues(
                boolean includeInformationalIssues) {

            this.includeInformationalIssues =
                    includeInformationalIssues;

            return this;
        }

        public Builder continueOnRuleError(
                boolean continueOnRuleError) {

            this.continueOnRuleError =
                    continueOnRuleError;

            return this;
        }

        public Builder maximumAltTextLength(
                int maximumAltTextLength) {

            this.maximumAltTextLength =
                    maximumAltTextLength;

            return this;
        }

        public Builder enableRule(
                String ruleId) {

            String normalized =
                    normalizeOptionalText(ruleId);

            if (normalized != null) {
                enabledRuleIds.add(normalized);
            }

            return this;
        }

        public Builder disableRule(
                String ruleId) {

            String normalized =
                    normalizeOptionalText(ruleId);

            if (normalized != null) {
                disabledRuleIds.add(normalized);
            }

            return this;
        }

        public Builder option(
                String key,
                String value) {

            putNormalized(
                    options,
                    key,
                    value
            );

            return this;
        }

        public Builder option(
                String key,
                boolean value) {

            return option(
                    key,
                    Boolean.toString(value)
            );
        }

        public Builder option(
                String key,
                int value) {

            return option(
                    key,
                    Integer.toString(value)
            );
        }

        public Builder metadata(
                String key,
                String value) {

            putNormalized(
                    metadata,
                    key,
                    value
            );

            return this;
        }

        public AccessibilityValidationRequest build() {
            return new AccessibilityValidationRequest(this);
        }

        private static void putNormalized(
                Map<String, String> target,
                String key,
                String value) {

            String normalizedKey =
                    normalizeOptionalText(key);

            String normalizedValue =
                    normalizeOptionalText(value);

            if (normalizedKey != null
                    && normalizedValue != null) {

                target.put(
                        normalizedKey,
                        normalizedValue
                );
            }
        }
    }
}