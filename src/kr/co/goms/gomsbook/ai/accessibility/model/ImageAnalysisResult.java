/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 이미지 접근성 분석 결과.
 *
 * <p>Vision 모델 또는 이미지 분석기가 생성한 대체 텍스트,
 * 상세 설명, 이미지 접근성 유형, 이미지 내 텍스트 및 신뢰도를
 * 담는 불변 객체이다.</p>
 *
 * <p>이 객체는 분석 결과만 표현하며 XHTML 파일에 실제로
 * {@code alt} 속성을 적용하지 않는다.</p>
 */
public final class ImageAnalysisResult {

    private final Path imagePath;
    private final String projectRelativeImagePath;
    private final ImageAccessibilityType accessibilityType;
    private final String altText;
    private final String detailedDescription;
    private final String visibleText;
    private final double confidence;
    private final boolean manualReviewRequired;
    private final List<String> warnings;
    private final Map<String, String> metadata;
    private final String model;
    private final String rawResponse;

    private ImageAnalysisResult(Builder builder) {

        this.imagePath = normalizeRequiredPath(
                builder.imagePath,
                "imagePath"
        );

        this.projectRelativeImagePath =
                normalizeRequiredText(
                        builder.projectRelativeImagePath,
                        "projectRelativeImagePath"
                );

        this.accessibilityType =
                builder.accessibilityType == null
                        ? ImageAccessibilityType.UNKNOWN
                        : builder.accessibilityType;

        this.altText = normalizeOptionalText(
                builder.altText
        );

        this.detailedDescription =
                normalizeOptionalText(
                        builder.detailedDescription
                );

        this.visibleText = normalizeOptionalText(
                builder.visibleText
        );

        this.confidence = normalizeConfidence(
                builder.confidence
        );

        this.manualReviewRequired =
                builder.manualReviewRequired
                        || this.accessibilityType
                                .requiresManualReview();

        this.warnings = immutableWarnings(
                builder.warnings
        );

        this.metadata = immutableMetadata(
                builder.metadata
        );

        this.model = normalizeOptionalText(
                builder.model
        );

        this.rawResponse = normalizeOptionalText(
                builder.rawResponse
        );

        validateResult();
    }

    /**
     * 분석된 이미지의 절대 경로를 반환한다.
     *
     * @return 이미지 절대 경로
     */
    public Path getImagePath() {
        return imagePath;
    }

    /**
     * 프로젝트 기준 상대 이미지 경로를 반환한다.
     *
     * @return 슬래시 형식의 프로젝트 상대 경로
     */
    public String getProjectRelativeImagePath() {
        return projectRelativeImagePath;
    }

    /**
     * 이미지 접근성 유형을 반환한다.
     *
     * @return 이미지 접근성 유형
     */
    public ImageAccessibilityType getAccessibilityType() {
        return accessibilityType;
    }

    /**
     * 권장 대체 텍스트를 반환한다.
     *
     * <p>장식 이미지에서는 빈 문자열 또는 {@code null}일 수 있다.</p>
     *
     * @return 대체 텍스트
     */
    public String getAltText() {
        return altText;
    }

    /**
     * 복합 이미지 등에 사용할 상세 설명을 반환한다.
     *
     * @return 상세 설명, 없으면 {@code null}
     */
    public String getDetailedDescription() {
        return detailedDescription;
    }

    /**
     * 이미지 안에서 감지된 텍스트를 반환한다.
     *
     * @return 감지된 텍스트, 없으면 {@code null}
     */
    public String getVisibleText() {
        return visibleText;
    }

    /**
     * 분석 신뢰도를 반환한다.
     *
     * @return {@code 0.0} 이상 {@code 1.0} 이하의 값
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 자동 적용 전 사용자 검토가 필요한지 반환한다.
     *
     * @return 사용자 검토가 필요하면 {@code true}
     */
    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    /**
     * 분석 경고 목록을 반환한다.
     *
     * @return 수정할 수 없는 경고 목록
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * 확장 메타데이터를 반환한다.
     *
     * @return 수정할 수 없는 메타데이터
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 분석에 사용된 모델명을 반환한다.
     *
     * @return 모델명, 없으면 {@code null}
     */
    public String getModel() {
        return model;
    }

    /**
     * 모델의 원본 응답을 반환한다.
     *
     * <p>진단 및 재처리 용도로만 사용하며 UI에 기본 노출하지 않는 것이
     * 좋다.</p>
     *
     * @return 원본 응답, 없으면 {@code null}
     */
    public String getRawResponse() {
        return rawResponse;
    }

    /**
     * 이미지가 장식 이미지인지 반환한다.
     *
     * @return 장식 이미지이면 {@code true}
     */
    public boolean isDecorative() {
        return accessibilityType.isDecorative();
    }

    /**
     * 비어 있지 않은 대체 텍스트가 존재하는지 반환한다.
     *
     * @return 유효한 대체 텍스트가 있으면 {@code true}
     */
    public boolean hasAltText() {
        return altText != null && !altText.isBlank();
    }

    /**
     * 상세 설명이 존재하는지 반환한다.
     *
     * @return 상세 설명이 있으면 {@code true}
     */
    public boolean hasDetailedDescription() {
        return detailedDescription != null
                && !detailedDescription.isBlank();
    }

    /**
     * 감지된 이미지 내 텍스트가 존재하는지 반환한다.
     *
     * @return 감지된 텍스트가 있으면 {@code true}
     */
    public boolean hasVisibleText() {
        return visibleText != null
                && !visibleText.isBlank();
    }

    /**
     * 경고가 하나 이상 존재하는지 반환한다.
     *
     * @return 경고가 있으면 {@code true}
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * 결과를 자동으로 XHTML에 적용할 수 있는지 판단한다.
     *
     * <p>다음 조건을 모두 만족해야 한다.</p>
     *
     * <ul>
     *   <li>수동 검토가 필요하지 않음</li>
     *   <li>이미지 유형이 {@code UNKNOWN}이 아님</li>
     *   <li>장식 이미지이거나 유효한 대체 텍스트가 존재함</li>
     * </ul>
     *
     * @return 자동 적용 가능하면 {@code true}
     */
    public boolean isApplicable() {

        if (manualReviewRequired) {
            return false;
        }

        if (accessibilityType
                == ImageAccessibilityType.UNKNOWN) {
            return false;
        }

        if (isDecorative()) {
            return true;
        }

        return hasAltText();
    }

    /**
     * 지정한 최소 신뢰도를 기준으로 자동 적용 가능 여부를 반환한다.
     *
     * @param minimumConfidence 최소 신뢰도
     * @return 자동 적용 가능하면 {@code true}
     */
    public boolean isApplicable(
            double minimumConfidence) {

        if (minimumConfidence < 0.0d
                || minimumConfidence > 1.0d) {

            throw new IllegalArgumentException(
                    "minimumConfidence must be between "
                            + "0.0 and 1.0"
            );
        }

        return isApplicable()
                && confidence >= minimumConfidence;
    }

    /**
     * 상세 설명이 필요한 결과인지 반환한다.
     *
     * @return 상세 설명이 권장되면 {@code true}
     */
    public boolean requiresDetailedDescription() {
        return accessibilityType
                .isDetailedDescriptionRecommended();
    }

    /**
     * 결과 메타데이터 값을 반환한다.
     *
     * @param key 메타데이터 키
     * @return 값이 없으면 {@code null}
     */
    public String getMetadata(String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validateResult() {

        if (accessibilityType.isAltTextRequired()
                && !hasAltText()) {

            if (!manualReviewRequired) {
                throw new IllegalArgumentException(
                        "altText is required for accessibility type: "
                                + accessibilityType
                );
            }
        }

        if (accessibilityType.isDecorative()
                && hasAltText()) {

            throw new IllegalArgumentException(
                    "Decorative image must not have "
                            + "non-empty alt text"
            );
        }
    }

    private static Path normalizeRequiredPath(
            Path value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " must not be null"
        );

        return value
                .toAbsolutePath()
                .normalize();
    }

    private static String normalizeRequiredText(
            String value,
            String fieldName) {

        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized.replace('\\', '/');
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

    private static double normalizeConfidence(
            double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            throw new IllegalArgumentException(
                    "confidence must be a finite number"
            );
        }

        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 1.0: "
                            + value
            );
        }

        return value;
    }

    private static List<String> immutableWarnings(
            List<String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (String warning : source) {
            String normalized =
                    normalizeOptionalText(warning);

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

        if (result.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * {@link ImageAnalysisResult} Builder.
     */
    public static final class Builder {

        private Path imagePath;
        private String projectRelativeImagePath;
        private ImageAccessibilityType accessibilityType =
                ImageAccessibilityType.UNKNOWN;
        private String altText;
        private String detailedDescription;
        private String visibleText;
        private double confidence;
        private boolean manualReviewRequired;
        private final List<String> warnings =
                new ArrayList<>();
        private final Map<String, String> metadata =
                new LinkedHashMap<>();
        private String model;
        private String rawResponse;

        private Builder() {
        }

        public Builder imagePath(Path imagePath) {
            this.imagePath = imagePath;
            return this;
        }

        public Builder projectRelativeImagePath(
                String projectRelativeImagePath) {

            this.projectRelativeImagePath =
                    projectRelativeImagePath;

            return this;
        }

        /**
         * 프로젝트 루트와 이미지 경로로 절대·상대 경로를 동시에 설정한다.
         *
         * @param projectRoot 프로젝트 루트
         * @param imagePath 이미지 경로
         * @return 현재 Builder
         */
        public Builder image(
                Path projectRoot,
                Path imagePath) {

            Objects.requireNonNull(
                    projectRoot,
                    "projectRoot must not be null"
            );

            Objects.requireNonNull(
                    imagePath,
                    "imagePath must not be null"
            );

            Path normalizedRoot =
                    projectRoot
                            .toAbsolutePath()
                            .normalize();

            Path normalizedImage =
                    imagePath
                            .toAbsolutePath()
                            .normalize();

            if (!normalizedImage.startsWith(
                    normalizedRoot)) {

                throw new IllegalArgumentException(
                        "imagePath must be inside projectRoot"
                );
            }

            this.imagePath = normalizedImage;
            this.projectRelativeImagePath =
                    normalizedRoot
                            .relativize(normalizedImage)
                            .toString()
                            .replace('\\', '/');

            return this;
        }

        public Builder accessibilityType(
                ImageAccessibilityType accessibilityType) {

            this.accessibilityType =
                    accessibilityType;

            return this;
        }

        public Builder altText(String altText) {
            this.altText = altText;
            return this;
        }

        public Builder detailedDescription(
                String detailedDescription) {

            this.detailedDescription =
                    detailedDescription;

            return this;
        }

        public Builder visibleText(
                String visibleText) {

            this.visibleText = visibleText;
            return this;
        }

        public Builder confidence(
                double confidence) {

            this.confidence = confidence;
            return this;
        }

        public Builder manualReviewRequired(
                boolean manualReviewRequired) {

            this.manualReviewRequired =
                    manualReviewRequired;

            return this;
        }

        public Builder warning(String warning) {

            String normalized =
                    normalizeOptionalText(warning);

            if (normalized != null
                    && !warnings.contains(normalized)) {

                warnings.add(normalized);
            }

            return this;
        }

        public Builder warnings(
                List<String> warnings) {

            if (warnings == null) {
                return this;
            }

            for (String warning : warnings) {
                warning(warning);
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

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder rawResponse(
                String rawResponse) {

            this.rawResponse = rawResponse;
            return this;
        }

        public ImageAnalysisResult build() {
            return new ImageAnalysisResult(this);
        }
    }
}