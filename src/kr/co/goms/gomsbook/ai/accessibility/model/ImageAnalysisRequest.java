/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 이미지 접근성 분석 요청.
 *
 * <p>이미지 파일, 문서 문맥, 출력 언어 및 대체 텍스트 생성 조건을
 * {@link com.gomsbook.ai.accessibility.analysis.ImageAnalyzer}에 전달한다.</p>
 *
 * <p>이 객체는 이미지 분석에 필요한 정보만 포함하며,
 * 실제 파일 수정이나 XHTML 속성 반영 정보는 포함하지 않는다.</p>
 */
public final class ImageAnalysisRequest {

    public static final String DEFAULT_LANGUAGE = "ko";

    public static final int DEFAULT_MAX_ALT_TEXT_LENGTH = 150;

    public static final int MIN_ALT_TEXT_LENGTH = 20;

    public static final int MAX_ALT_TEXT_LENGTH = 500;

    private final Path projectRoot;
    private final Path imagePath;
    private final String language;
    private final String purpose;
    private final String surroundingText;
    private final String figureCaption;
    private final String documentTitle;
    private final String documentLanguage;
    private final int maxAltTextLength;
    private final boolean detectVisibleText;
    private final boolean generateDetailedDescription;
    private final boolean classifyAccessibilityType;
    private final Map<String, String> metadata;

    private ImageAnalysisRequest(Builder builder) {

        this.projectRoot = normalizeRequiredPath(
                builder.projectRoot,
                "projectRoot"
        );

        this.imagePath = normalizeRequiredPath(
                builder.imagePath,
                "imagePath"
        );

        validateProjectPath(
                projectRoot,
                imagePath
        );

        this.language = normalizeLanguage(
                builder.language
        );

        this.purpose = normalizeOptionalText(
                builder.purpose
        );

        this.surroundingText = normalizeOptionalText(
                builder.surroundingText
        );

        this.figureCaption = normalizeOptionalText(
                builder.figureCaption
        );

        this.documentTitle = normalizeOptionalText(
                builder.documentTitle
        );

        this.documentLanguage = normalizeOptionalText(
                builder.documentLanguage
        );

        this.maxAltTextLength = validateMaxAltTextLength(
                builder.maxAltTextLength
        );

        this.detectVisibleText =
                builder.detectVisibleText;

        this.generateDetailedDescription =
                builder.generateDetailedDescription;

        this.classifyAccessibilityType =
                builder.classifyAccessibilityType;

        this.metadata = immutableMetadata(
                builder.metadata
        );
    }

    /**
     * 현재 프로젝트의 루트 경로를 반환한다.
     *
     * @return 정규화된 프로젝트 루트 절대 경로
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 분석할 이미지 경로를 반환한다.
     *
     * @return 정규화된 이미지 절대 경로
     */
    public Path getImagePath() {
        return imagePath;
    }

    /**
     * 프로젝트 루트를 기준으로 한 이미지 상대 경로를 반환한다.
     *
     * @return 프로젝트 상대 이미지 경로
     */
    public Path getProjectRelativeImagePath() {
        return projectRoot.relativize(imagePath);
    }

    /**
     * 프로젝트 상대 이미지 경로를 슬래시 형식 문자열로 반환한다.
     *
     * @return 예: {@code OEBPS/Images/chapter01.png}
     */
    public String getNormalizedRelativeImagePath() {
        return getProjectRelativeImagePath()
                .toString()
                .replace('\\', '/');
    }

    /**
     * 분석 결과를 생성할 언어를 반환한다.
     *
     * @return 언어 코드
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 이미지의 문서상 목적 또는 사용 의도를 반환한다.
     *
     * @return 이미지 목적, 없으면 {@code null}
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * 이미지 주변의 XHTML 본문을 반환한다.
     *
     * @return 주변 문장, 없으면 {@code null}
     */
    public String getSurroundingText() {
        return surroundingText;
    }

    /**
     * 이미지가 포함된 figure의 figcaption 내용을 반환한다.
     *
     * @return 캡션, 없으면 {@code null}
     */
    public String getFigureCaption() {
        return figureCaption;
    }

    /**
     * 이미지가 포함된 문서 제목을 반환한다.
     *
     * @return 문서 제목, 없으면 {@code null}
     */
    public String getDocumentTitle() {
        return documentTitle;
    }

    /**
     * 이미지가 포함된 XHTML 문서의 언어를 반환한다.
     *
     * @return 문서 언어, 없으면 {@code null}
     */
    public String getDocumentLanguage() {
        return documentLanguage;
    }

    /**
     * 권장 대체 텍스트 최대 길이를 반환한다.
     *
     * @return 최대 문자 수
     */
    public int getMaxAltTextLength() {
        return maxAltTextLength;
    }

    /**
     * 이미지 내부의 텍스트 탐지를 요청했는지 반환한다.
     *
     * @return 텍스트 탐지가 필요하면 {@code true}
     */
    public boolean isDetectVisibleText() {
        return detectVisibleText;
    }

    /**
     * 상세 설명 생성을 요청했는지 반환한다.
     *
     * @return 상세 설명 생성이 필요하면 {@code true}
     */
    public boolean isGenerateDetailedDescription() {
        return generateDetailedDescription;
    }

    /**
     * 이미지 접근성 유형 분류를 요청했는지 반환한다.
     *
     * @return 이미지 유형 분류가 필요하면 {@code true}
     */
    public boolean isClassifyAccessibilityType() {
        return classifyAccessibilityType;
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
     * 이미지가 프로젝트 내부에 있는지 반환한다.
     *
     * <p>생성 시 이미 검증되므로 정상적으로 생성된 요청은 항상
     * {@code true}를 반환한다.</p>
     *
     * @return 프로젝트 내부 경로이면 {@code true}
     */
    public boolean isProjectImage() {
        return imagePath.startsWith(projectRoot);
    }

    /**
     * 분석 문맥이 하나 이상 존재하는지 반환한다.
     *
     * @return 문서 문맥이 있으면 {@code true}
     */
    public boolean hasContext() {
        return purpose != null
                || surroundingText != null
                || figureCaption != null
                || documentTitle != null;
    }

    public static Builder builder() {
        return new Builder();
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

    private static void validateProjectPath(
            Path projectRoot,
            Path imagePath) {

        if (!imagePath.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "imagePath must be inside projectRoot: "
                            + imagePath
            );
        }
    }

    private static String normalizeLanguage(
            String language) {

        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        return language.trim();
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

    private static int validateMaxAltTextLength(
            int maxAltTextLength) {

        if (maxAltTextLength < MIN_ALT_TEXT_LENGTH
                || maxAltTextLength > MAX_ALT_TEXT_LENGTH) {

            throw new IllegalArgumentException(
                    "maxAltTextLength must be between "
                            + MIN_ALT_TEXT_LENGTH
                            + " and "
                            + MAX_ALT_TEXT_LENGTH
                            + ": "
                            + maxAltTextLength
            );
        }

        return maxAltTextLength;
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
     * {@link ImageAnalysisRequest} Builder.
     */
    public static final class Builder {

        private Path projectRoot;
        private Path imagePath;
        private String language =
                DEFAULT_LANGUAGE;
        private String purpose;
        private String surroundingText;
        private String figureCaption;
        private String documentTitle;
        private String documentLanguage;
        private int maxAltTextLength =
                DEFAULT_MAX_ALT_TEXT_LENGTH;
        private boolean detectVisibleText = true;
        private boolean generateDetailedDescription = true;
        private boolean classifyAccessibilityType = true;
        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        /**
         * 프로젝트 루트와 상대 이미지 경로를 한 번에 설정한다.
         *
         * @param projectRoot 프로젝트 루트
         * @param relativeImagePath 프로젝트 상대 이미지 경로
         * @return 현재 Builder
         */
        public Builder projectImage(
                Path projectRoot,
                Path relativeImagePath) {

            Objects.requireNonNull(
                    projectRoot,
                    "projectRoot must not be null"
            );

            Objects.requireNonNull(
                    relativeImagePath,
                    "relativeImagePath must not be null"
            );

            if (relativeImagePath.isAbsolute()) {
                throw new IllegalArgumentException(
                        "relativeImagePath must be relative"
                );
            }

            this.projectRoot = projectRoot;
            this.imagePath = projectRoot.resolve(
                    relativeImagePath
            );

            return this;
        }

        public Builder imagePath(Path imagePath) {
            this.imagePath = imagePath;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder purpose(String purpose) {
            this.purpose = purpose;
            return this;
        }

        public Builder surroundingText(
                String surroundingText) {

            this.surroundingText =
                    surroundingText;

            return this;
        }

        public Builder figureCaption(
                String figureCaption) {

            this.figureCaption =
                    figureCaption;

            return this;
        }

        public Builder documentTitle(
                String documentTitle) {

            this.documentTitle =
                    documentTitle;

            return this;
        }

        public Builder documentLanguage(
                String documentLanguage) {

            this.documentLanguage =
                    documentLanguage;

            return this;
        }

        public Builder maxAltTextLength(
                int maxAltTextLength) {

            this.maxAltTextLength =
                    maxAltTextLength;

            return this;
        }

        public Builder detectVisibleText(
                boolean detectVisibleText) {

            this.detectVisibleText =
                    detectVisibleText;

            return this;
        }

        public Builder generateDetailedDescription(
                boolean generateDetailedDescription) {

            this.generateDetailedDescription =
                    generateDetailedDescription;

            return this;
        }

        public Builder classifyAccessibilityType(
                boolean classifyAccessibilityType) {

            this.classifyAccessibilityType =
                    classifyAccessibilityType;

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

        public ImageAnalysisRequest build() {
            return new ImageAnalysisRequest(this);
        }
    }
}