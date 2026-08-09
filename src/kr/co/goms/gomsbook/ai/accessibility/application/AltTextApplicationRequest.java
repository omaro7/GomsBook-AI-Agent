/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.model.ImageAccessibilityType;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisResult;

/**
 * XHTML 문서의 이미지 요소에 대체 텍스트와 접근성 속성을
 * 적용하기 위한 요청 객체.
 *
 * <p>이 객체는 어떤 XHTML 파일의 어떤 이미지 요소를 수정할지와
 * 적용할 대체 텍스트 및 접근성 정책을 정의한다.</p>
 *
 * <p>실제 XHTML 파싱과 파일 저장은
 * {@link AltTextApplicator} 구현체가 담당한다.</p>
 */
public final class AltTextApplicationRequest {

    private final Path projectRoot;
    private final Path xhtmlPath;
    private final String imageElementId;
    private final String imageSource;
    private final ImageAccessibilityType accessibilityType;
    private final String altText;
    private final String detailedDescription;
    private final boolean overwriteExisting;
    private final boolean removeTitle;
    private final boolean removeAriaLabel;
    private final boolean applyPresentationRole;
    private final boolean applyAriaHidden;
    private final String expectedCurrentAlt;
    private final boolean createBackup;
    private final boolean dryRun;
    private final Map<String, String> metadata;

    private AltTextApplicationRequest(Builder builder) {

        this.projectRoot = normalizeRequiredPath(
                builder.projectRoot,
                "projectRoot"
        );

        this.xhtmlPath = normalizeRequiredPath(
                builder.xhtmlPath,
                "xhtmlPath"
        );

        validateProjectPath(
                projectRoot,
                xhtmlPath
        );

        this.imageElementId = normalizeOptionalText(
                builder.imageElementId
        );

        this.imageSource = normalizeOptionalPathReference(
                builder.imageSource
        );

        validateImageSelector(
                imageElementId,
                imageSource
        );

        this.accessibilityType =
                builder.accessibilityType == null
                        ? ImageAccessibilityType.UNKNOWN
                        : builder.accessibilityType;

        this.altText = normalizeAltText(
                builder.altText
        );

        this.detailedDescription = normalizeOptionalText(
                builder.detailedDescription
        );

        this.overwriteExisting =
                builder.overwriteExisting;

        this.removeTitle =
                builder.removeTitle;

        this.removeAriaLabel =
                builder.removeAriaLabel;

        this.applyPresentationRole =
                builder.applyPresentationRole;

        this.applyAriaHidden =
                builder.applyAriaHidden;

        this.expectedCurrentAlt =
                normalizeExpectedAlt(
                        builder.expectedCurrentAlt
                );

        this.createBackup =
                builder.createBackup;

        this.dryRun =
                builder.dryRun;

        this.metadata = immutableMetadata(
                builder.metadata
        );

        validateAccessibilityAttributes();
    }

    /**
     * 현재 프로젝트의 루트 경로를 반환한다.
     *
     * @return 프로젝트 루트 절대 경로
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 수정할 XHTML 파일의 절대 경로를 반환한다.
     *
     * @return XHTML 절대 경로
     */
    public Path getXhtmlPath() {
        return xhtmlPath;
    }

    /**
     * 프로젝트 루트 기준 XHTML 상대 경로를 반환한다.
     *
     * @return XHTML 상대 경로
     */
    public Path getProjectRelativeXhtmlPath() {
        return projectRoot.relativize(xhtmlPath);
    }

    /**
     * XHTML 상대 경로를 슬래시 형식으로 반환한다.
     *
     * @return 예: {@code OEBPS/Text/chapter01.xhtml}
     */
    public String getNormalizedRelativeXhtmlPath() {
        return getProjectRelativeXhtmlPath()
                .toString()
                .replace('\\', '/');
    }

    /**
     * 대상 img 요소의 id를 반환한다.
     *
     * @return 이미지 요소 id, 없으면 {@code null}
     */
    public String getImageElementId() {
        return imageElementId;
    }

    /**
     * 대상 img 요소의 src 값을 반환한다.
     *
     * @return 이미지 src, 없으면 {@code null}
     */
    public String getImageSource() {
        return imageSource;
    }

    /**
     * 적용할 이미지 접근성 유형을 반환한다.
     *
     * @return 이미지 접근성 유형
     */
    public ImageAccessibilityType getAccessibilityType() {
        return accessibilityType;
    }

    /**
     * 적용할 대체 텍스트를 반환한다.
     *
     * <p>장식 이미지는 빈 문자열을 반환할 수 있다.</p>
     *
     * @return 대체 텍스트
     */
    public String getAltText() {
        return altText;
    }

    /**
     * 상세 설명을 반환한다.
     *
     * @return 상세 설명, 없으면 {@code null}
     */
    public String getDetailedDescription() {
        return detailedDescription;
    }

    /**
     * 기존 비어 있지 않은 alt 속성을 덮어쓸 수 있는지 반환한다.
     *
     * @return 덮어쓰기 허용 여부
     */
    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    /**
     * 기존 title 속성을 제거할지 반환한다.
     *
     * @return title 제거 여부
     */
    public boolean isRemoveTitle() {
        return removeTitle;
    }

    /**
     * 기존 aria-label 및 aria-labelledby 속성을 제거할지 반환한다.
     *
     * @return ARIA 레이블 제거 여부
     */
    public boolean isRemoveAriaLabel() {
        return removeAriaLabel;
    }

    /**
     * 장식 이미지에 role="presentation"을 적용할지 반환한다.
     *
     * @return presentation 역할 적용 여부
     */
    public boolean isApplyPresentationRole() {
        return applyPresentationRole;
    }

    /**
     * 장식 이미지에 aria-hidden="true"를 적용할지 반환한다.
     *
     * @return aria-hidden 적용 여부
     */
    public boolean isApplyAriaHidden() {
        return applyAriaHidden;
    }

    /**
     * 수정 전에 기대하는 현재 alt 값을 반환한다.
     *
     * <p>값이 설정된 경우 실제 현재 alt 값과 다르면 수정하지 않는다.</p>
     *
     * @return 예상 alt 값, 설정하지 않았으면 {@code null}
     */
    public String getExpectedCurrentAlt() {
        return expectedCurrentAlt;
    }

    /**
     * 파일 수정 전 백업을 생성할지 반환한다.
     *
     * @return 백업 생성 여부
     */
    public boolean isCreateBackup() {
        return createBackup;
    }

    /**
     * 실제 파일을 저장하지 않고 변경 예정 결과만 생성할지 반환한다.
     *
     * @return dry-run 여부
     */
    public boolean isDryRun() {
        return dryRun;
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
     * 메타데이터 값을 반환한다.
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

    /**
     * 장식 이미지 적용 요청인지 반환한다.
     *
     * @return 장식 이미지이면 {@code true}
     */
    public boolean isDecorative() {
        return accessibilityType.isDecorative();
    }

    /**
     * 대상 이미지가 id로 지정되었는지 반환한다.
     *
     * @return 이미지 id가 있으면 {@code true}
     */
    public boolean hasImageElementId() {
        return imageElementId != null;
    }

    /**
     * 대상 이미지가 src로 지정되었는지 반환한다.
     *
     * @return 이미지 src가 있으면 {@code true}
     */
    public boolean hasImageSource() {
        return imageSource != null;
    }

    /**
     * 상세 설명이 포함되어 있는지 반환한다.
     *
     * @return 상세 설명이 있으면 {@code true}
     */
    public boolean hasDetailedDescription() {
        return detailedDescription != null;
    }

    /**
     * 현재 alt 값 비교 조건이 설정되어 있는지 반환한다.
     *
     * @return 예상 alt 값이 설정되어 있으면 {@code true}
     */
    public boolean hasExpectedCurrentAlt() {
        return expectedCurrentAlt != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validateAccessibilityAttributes() {

        if (accessibilityType
                == ImageAccessibilityType.UNKNOWN) {

            throw new IllegalArgumentException(
                    "UNKNOWN accessibility type cannot be applied"
            );
        }

        if (accessibilityType.isDecorative()) {

            if (altText != null && !altText.isEmpty()) {
                throw new IllegalArgumentException(
                        "Decorative image must use empty alt text"
                );
            }

            return;
        }

        if (accessibilityType.isAltTextRequired()
                && (altText == null || altText.isBlank())) {

            throw new IllegalArgumentException(
                    "altText is required for accessibility type: "
                            + accessibilityType
            );
        }

        if (applyPresentationRole) {
            throw new IllegalArgumentException(
                    "applyPresentationRole can only be used "
                            + "for decorative images"
            );
        }

        if (applyAriaHidden) {
            throw new IllegalArgumentException(
                    "applyAriaHidden can only be used "
                            + "for decorative images"
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

    private static void validateProjectPath(
            Path projectRoot,
            Path xhtmlPath) {

        if (!xhtmlPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "xhtmlPath must be inside projectRoot: "
                            + xhtmlPath
            );
        }
    }

    private static void validateImageSelector(
            String imageElementId,
            String imageSource) {

        if (imageElementId == null
                && imageSource == null) {

            throw new IllegalArgumentException(
                    "imageElementId or imageSource must be provided"
            );
        }
    }

    private static String normalizeAltText(
            String value) {

        if (value == null) {
            return null;
        }

        /*
         * 장식 이미지의 alt=""를 보존해야 하므로
         * 빈 문자열을 null로 변환하지 않는다.
         */
        return value.trim();
    }

    private static String normalizeExpectedAlt(
            String value) {

        if (value == null) {
            return null;
        }

        /*
         * expectedCurrentAlt=""는 현재 빈 alt 속성을 의미하므로
         * 빈 문자열을 유지한다.
         */
        return value.trim();
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

    private static String normalizeOptionalPathReference(
            String value) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized;
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
     * {@link AltTextApplicationRequest} Builder.
     */
    public static final class Builder {

        private Path projectRoot;
        private Path xhtmlPath;
        private String imageElementId;
        private String imageSource;
        private ImageAccessibilityType accessibilityType =
                ImageAccessibilityType.INFORMATIVE;
        private String altText;
        private String detailedDescription;
        private boolean overwriteExisting;
        private boolean removeTitle;
        private boolean removeAriaLabel;
        private boolean applyPresentationRole = true;
        private boolean applyAriaHidden;
        private String expectedCurrentAlt;
        private boolean createBackup = true;
        private boolean dryRun;
        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder xhtmlPath(Path xhtmlPath) {
            this.xhtmlPath = xhtmlPath;
            return this;
        }

        /**
         * 프로젝트 루트와 상대 XHTML 경로를 한 번에 설정한다.
         *
         * @param projectRoot 프로젝트 루트
         * @param relativeXhtmlPath 프로젝트 상대 XHTML 경로
         * @return 현재 Builder
         */
        public Builder projectDocument(
                Path projectRoot,
                Path relativeXhtmlPath) {

            Objects.requireNonNull(
                    projectRoot,
                    "projectRoot must not be null"
            );

            Objects.requireNonNull(
                    relativeXhtmlPath,
                    "relativeXhtmlPath must not be null"
            );

            if (relativeXhtmlPath.isAbsolute()) {
                throw new IllegalArgumentException(
                        "relativeXhtmlPath must be relative"
                );
            }

            this.projectRoot = projectRoot;
            this.xhtmlPath = projectRoot.resolve(
                    relativeXhtmlPath
            );

            return this;
        }

        public Builder imageElementId(
                String imageElementId) {

            this.imageElementId =
                    imageElementId;

            return this;
        }

        public Builder imageSource(
                String imageSource) {

            this.imageSource =
                    imageSource;

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

        public Builder overwriteExisting(
                boolean overwriteExisting) {

            this.overwriteExisting =
                    overwriteExisting;

            return this;
        }

        public Builder removeTitle(
                boolean removeTitle) {

            this.removeTitle = removeTitle;
            return this;
        }

        public Builder removeAriaLabel(
                boolean removeAriaLabel) {

            this.removeAriaLabel =
                    removeAriaLabel;

            return this;
        }

        public Builder applyPresentationRole(
                boolean applyPresentationRole) {

            this.applyPresentationRole =
                    applyPresentationRole;

            return this;
        }

        public Builder applyAriaHidden(
                boolean applyAriaHidden) {

            this.applyAriaHidden =
                    applyAriaHidden;

            return this;
        }

        public Builder expectedCurrentAlt(
                String expectedCurrentAlt) {

            this.expectedCurrentAlt =
                    expectedCurrentAlt;

            return this;
        }

        public Builder createBackup(
                boolean createBackup) {

            this.createBackup =
                    createBackup;

            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
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
         * 이미지 분석 결과를 적용 조건으로 설정한다.
         *
         * @param result 이미지 접근성 분석 결과
         * @return 현재 Builder
         */
        public Builder analysisResult(
                ImageAnalysisResult result) {

            Objects.requireNonNull(
                    result,
                    "result must not be null"
            );

            if (!result.isApplicable()) {
                throw new IllegalArgumentException(
                        "Image analysis result is not applicable"
                );
            }

            this.accessibilityType =
                    result.getAccessibilityType();

            this.altText = result.isDecorative()
                    ? ""
                    : result.getAltText();

            this.detailedDescription =
                    result.getDetailedDescription();

            metadata(result.getMetadata());

            metadata(
                    "analysisModel",
                    result.getModel()
            );

            metadata(
                    "analysisConfidence",
                    String.valueOf(
                            result.getConfidence()
                    )
            );

            return this;
        }

        public AltTextApplicationRequest build() {

            /*
             * 장식 이미지인 경우 기본 alt 값을 빈 문자열로 보정한다.
             */
            if (accessibilityType
                    == ImageAccessibilityType.DECORATIVE
                    && altText == null) {

                altText = "";
            }

            return new AltTextApplicationRequest(this);
        }
    }
}