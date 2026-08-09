/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EPUB 콘텐츠에서 발견된 개별 접근성 문제를 나타낸다.
 *
 * <p>문제 코드, 심각도, 위치, 설명, 수정 권고사항, 자동 수정 가능 여부,
 * 사용자 검토 필요 여부 등을 포함하는 불변 객체이다.</p>
 *
 * <p>{@code AccessibilityIssue}는 EPUB 콘텐츠 자체의 접근성 문제를
 * 표현한다. Agent Tool 실행 오류를 나타내는 {@code ToolIssue}와는
 * 역할이 다르므로 서로 혼용하지 않는다.</p>
 */
public final class AccessibilityIssue {

    private final AccessibilityIssueCode code;
    private final AccessibilitySeverity severity;
    private final String message;
    private final String description;
    private final String recommendation;
    private final AccessibilityLocation location;
    private final boolean automaticallyFixable;
    private final boolean manualReviewRequired;
    private final String ruleId;
    private final String currentValue;
    private final String suggestedValue;
    private final List<String> relatedValues;
    private final Map<String, String> metadata;

    private AccessibilityIssue(Builder builder) {

        this.code = Objects.requireNonNull(
                builder.code,
                "code must not be null"
        );

        this.severity = builder.severity == null
                ? code.getDefaultSeverity()
                : builder.severity;

        this.message = normalizeRequiredText(
                builder.message,
                "message"
        );

        this.description = normalizeOptionalText(
                builder.description
        );

        this.recommendation = normalizeOptionalText(
                builder.recommendation
        );

        this.location = builder.location;

        this.automaticallyFixable =
                builder.automaticallyFixable != null
                        ? builder.automaticallyFixable
                        : code.isAutomaticallyFixable();

        this.manualReviewRequired =
                builder.manualReviewRequired != null
                        ? builder.manualReviewRequired
                        : code.isManualReviewRequired();

        this.ruleId = normalizeOptionalText(
                builder.ruleId
        );

        this.currentValue =
                normalizeNullableValue(
                        builder.currentValue
                );

        this.suggestedValue =
                normalizeNullableValue(
                        builder.suggestedValue
                );

        this.relatedValues =
                immutableStringList(
                        builder.relatedValues
                );

        this.metadata =
                immutableMetadata(
                        builder.metadata
                );

        validateState();
    }

    /**
     * 접근성 문제 코드를 반환한다.
     *
     * @return 문제 코드
     */
    public AccessibilityIssueCode getCode() {
        return code;
    }

    /**
     * 문제 심각도를 반환한다.
     *
     * @return 심각도
     */
    public AccessibilitySeverity getSeverity() {
        return severity;
    }

    /**
     * 사용자에게 표시할 핵심 문제 메시지를 반환한다.
     *
     * @return 문제 메시지
     */
    public String getMessage() {
        return message;
    }

    /**
     * 문제에 대한 상세 설명을 반환한다.
     *
     * @return 상세 설명, 없으면 {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * 권장 수정 방법을 반환한다.
     *
     * @return 수정 권고사항, 없으면 {@code null}
     */
    public String getRecommendation() {
        return recommendation;
    }

    /**
     * 문제가 발견된 위치를 반환한다.
     *
     * @return 위치 정보, 없으면 {@code null}
     */
    public AccessibilityLocation getLocation() {
        return location;
    }

    /**
     * 규칙 기반 자동 수정이 가능한지 반환한다.
     *
     * @return 자동 수정 가능 여부
     */
    public boolean isAutomaticallyFixable() {
        return automaticallyFixable;
    }

    /**
     * 사용자 검토가 필요한 문제인지 반환한다.
     *
     * @return 사용자 검토 필요 여부
     */
    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    /**
     * 문제를 생성한 검사 규칙 식별자를 반환한다.
     *
     * @return 규칙 ID, 없으면 {@code null}
     */
    public String getRuleId() {
        return ruleId;
    }

    /**
     * 현재 문서에 설정된 값을 반환한다.
     *
     * <p>빈 문자열과 값 없음의 차이를 유지한다.</p>
     *
     * @return 현재 값, 없으면 {@code null}
     */
    public String getCurrentValue() {
        return currentValue;
    }

    /**
     * 권장되는 수정값을 반환한다.
     *
     * <p>빈 문자열과 값 없음의 차이를 유지한다.</p>
     *
     * @return 권장값, 없으면 {@code null}
     */
    public String getSuggestedValue() {
        return suggestedValue;
    }

    /**
     * 문제와 관련된 추가 값 목록을 반환한다.
     *
     * @return 수정할 수 없는 관련 값 목록
     */
    public List<String> getRelatedValues() {
        return relatedValues;
    }

    /**
     * 확장 메타데이터를 반환한다.
     *
     * @return 수정할 수 없는 메타데이터
     */
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
     * 오류 수준의 문제인지 반환한다.
     *
     * @return 오류이면 {@code true}
     */
    public boolean isError() {
        return severity.isError();
    }

    /**
     * 경고 수준의 문제인지 반환한다.
     *
     * @return 경고이면 {@code true}
     */
    public boolean isWarning() {
        return severity.isWarning();
    }

    /**
     * 정보 수준의 문제인지 반환한다.
     *
     * @return 정보이면 {@code true}
     */
    public boolean isInfo() {
        return severity.isInfo();
    }

    /**
     * 출판 또는 접근성 완료 처리를 차단할 수 있는 문제인지 반환한다.
     *
     * @return 차단 수준이면 {@code true}
     */
    public boolean blocksPublication() {
        return severity.isBlocksPublication();
    }

    /**
     * 위치 정보가 있는지 반환한다.
     *
     * @return 위치가 있으면 {@code true}
     */
    public boolean hasLocation() {
        return location != null;
    }

    /**
     * 수정 권고사항이 있는지 반환한다.
     *
     * @return 권고사항이 있으면 {@code true}
     */
    public boolean hasRecommendation() {
        return recommendation != null;
    }

    /**
     * 권장 수정값이 있는지 반환한다.
     *
     * <p>빈 문자열도 유효한 권장값일 수 있으므로 null 여부만 확인한다.</p>
     *
     * @return 권장값이 설정되었으면 {@code true}
     */
    public boolean hasSuggestedValue() {
        return suggestedValue != null;
    }

    /**
     * 이미지 관련 문제인지 반환한다.
     *
     * @return 이미지 문제이면 {@code true}
     */
    public boolean isImageIssue() {
        return code.isImageIssue();
    }

    /**
     * UI와 로그에서 사용할 간단한 설명 문자열을 반환한다.
     *
     * @return 문제 요약 문자열
     */
    public String toDisplayString() {

        StringBuilder result = new StringBuilder();

        result.append('[');
        result.append(severity.getDisplayName());
        result.append("] ");
        result.append(message);

        if (location != null) {
            result.append(" - ");
            result.append(location.toDisplayString());
        }

        return result.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 문제 코드와 메시지로 Builder를 생성한다.
     *
     * @param code 문제 코드
     * @param message 문제 메시지
     * @return 초기화된 Builder
     */
    public static Builder builder(
            AccessibilityIssueCode code,
            String message) {

        return builder()
                .code(code)
                .message(message);
    }

    /**
     * 문제 코드의 기본 표시명을 메시지로 사용하여 Builder를 생성한다.
     *
     * @param code 문제 코드
     * @return 초기화된 Builder
     */
    public static Builder builder(
            AccessibilityIssueCode code) {

        Objects.requireNonNull(
                code,
                "code must not be null"
        );

        return builder()
                .code(code)
                .message(code.getDisplayName());
    }

    private void validateState() {

        if (automaticallyFixable
                && code == AccessibilityIssueCode.UNKNOWN) {

            throw new IllegalArgumentException(
                    "UNKNOWN issue cannot be automatically fixable"
            );
        }

        if (automaticallyFixable
                && suggestedValue == null
                && recommendation == null) {

            throw new IllegalArgumentException(
                    "Automatically fixable issue requires "
                            + "suggestedValue or recommendation"
            );
        }

        if (severity.isError()
                && code == AccessibilityIssueCode.UNKNOWN
                && description == null) {

            throw new IllegalArgumentException(
                    "UNKNOWN error requires a description"
            );
        }
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

    /**
     * 현재값과 권장값에서는 빈 문자열을 보존한다.
     */
    private static String normalizeNullableValue(
            String value) {

        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static List<String> immutableStringList(
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

    /**
     * {@link AccessibilityIssue} Builder.
     */
    public static final class Builder {

        private AccessibilityIssueCode code;
        private AccessibilitySeverity severity;
        private String message;
        private String description;
        private String recommendation;
        private AccessibilityLocation location;
        private Boolean automaticallyFixable;
        private Boolean manualReviewRequired;
        private String ruleId;
        private String currentValue;
        private String suggestedValue;
        private final List<String> relatedValues =
                new ArrayList<>();
        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder code(
                AccessibilityIssueCode code) {

            this.code = code;
            return this;
        }

        public Builder severity(
                AccessibilitySeverity severity) {

            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder description(
                String description) {

            this.description = description;
            return this;
        }

        public Builder recommendation(
                String recommendation) {

            this.recommendation = recommendation;
            return this;
        }

        public Builder location(
                AccessibilityLocation location) {

            this.location = location;
            return this;
        }

        public Builder automaticallyFixable(
                boolean automaticallyFixable) {

            this.automaticallyFixable =
                    automaticallyFixable;

            return this;
        }

        public Builder manualReviewRequired(
                boolean manualReviewRequired) {

            this.manualReviewRequired =
                    manualReviewRequired;

            return this;
        }

        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Builder currentValue(
                String currentValue) {

            this.currentValue = currentValue;
            return this;
        }

        public Builder suggestedValue(
                String suggestedValue) {

            this.suggestedValue =
                    suggestedValue;

            return this;
        }

        /**
         * 현재값과 권장값을 한 번에 설정한다.
         *
         * @param currentValue 현재값
         * @param suggestedValue 권장값
         * @return 현재 Builder
         */
        public Builder values(
                String currentValue,
                String suggestedValue) {

            this.currentValue = currentValue;
            this.suggestedValue = suggestedValue;

            return this;
        }

        public Builder relatedValue(
                String relatedValue) {

            String normalized =
                    normalizeOptionalText(
                            relatedValue
                    );

            if (normalized != null
                    && !relatedValues
                            .contains(normalized)) {

                relatedValues.add(normalized);
            }

            return this;
        }

        public Builder relatedValues(
                List<String> relatedValues) {

            if (relatedValues == null) {
                return this;
            }

            for (String relatedValue
                    : relatedValues) {

                relatedValue(relatedValue);
            }

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

        public Builder metadata(
                Map<String, String> metadata) {

            if (metadata == null) {
                return this;
            }

            for (Map.Entry<String, String> entry
                    : metadata.entrySet()) {

                metadata(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            return this;
        }

        /**
         * 문제 코드의 기본 정책을 명시적으로 복사한다.
         *
         * <p>호출하지 않아도 생성 시 기본 정책이 자동 적용된다.</p>
         *
         * @return 현재 Builder
         */
        public Builder useCodeDefaults() {

            if (code == null) {
                throw new IllegalStateException(
                        "code must be set before useCodeDefaults"
                );
            }

            this.severity =
                    code.getDefaultSeverity();

            this.automaticallyFixable =
                    code.isAutomaticallyFixable();

            this.manualReviewRequired =
                    code.isManualReviewRequired();

            return this;
        }

        public AccessibilityIssue build() {
            return new AccessibilityIssue(this);
        }
    }
}