/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.model.ImageAccessibilityType;

/**
 * XHTML 이미지 요소에 대체 텍스트와 접근성 속성을 적용한 결과.
 *
 * <p>실제 파일 변경 여부, 적용 전후 속성값, 대상 이미지 정보,
 * 경고 및 백업 경로를 포함하는 불변 객체이다.</p>
 */
public final class AltTextApplicationResult {

    private final Path xhtmlPath;
    private final String projectRelativeXhtmlPath;
    private final String imageElementId;
    private final String imageSource;
    private final ImageAccessibilityType accessibilityType;

    private final String previousAltText;
    private final String appliedAltText;

    private final String previousRole;
    private final String appliedRole;

    private final String previousAriaHidden;
    private final String appliedAriaHidden;

    private final boolean matched;
    private final int matchedElementCount;
    private final boolean changed;
    private final boolean fileUpdated;
    private final boolean dryRun;
    private final boolean backupCreated;

    private final Path backupPath;
    private final List<String> changedAttributes;
    private final List<String> warnings;
    private final Map<String, String> metadata;

    private AltTextApplicationResult(Builder builder) {

        this.xhtmlPath = normalizeRequiredPath(
                builder.xhtmlPath,
                "xhtmlPath"
        );

        this.projectRelativeXhtmlPath =
                normalizeRequiredText(
                        builder.projectRelativeXhtmlPath,
                        "projectRelativeXhtmlPath"
                ).replace('\\', '/');

        this.imageElementId =
                normalizeOptionalText(
                        builder.imageElementId
                );

        this.imageSource =
                normalizeOptionalPathReference(
                        builder.imageSource
                );

        this.accessibilityType =
                builder.accessibilityType == null
                        ? ImageAccessibilityType.UNKNOWN
                        : builder.accessibilityType;

        this.previousAltText =
                normalizeNullableAttributeValue(
                        builder.previousAltText
                );

        this.appliedAltText =
                normalizeNullableAttributeValue(
                        builder.appliedAltText
                );

        this.previousRole =
                normalizeOptionalText(
                        builder.previousRole
                );

        this.appliedRole =
                normalizeOptionalText(
                        builder.appliedRole
                );

        this.previousAriaHidden =
                normalizeOptionalText(
                        builder.previousAriaHidden
                );

        this.appliedAriaHidden =
                normalizeOptionalText(
                        builder.appliedAriaHidden
                );

        this.matched = builder.matched;
        this.matchedElementCount =
                validateMatchedElementCount(
                        builder.matchedElementCount
                );

        this.changed = builder.changed;
        this.fileUpdated = builder.fileUpdated;
        this.dryRun = builder.dryRun;
        this.backupCreated = builder.backupCreated;

        this.backupPath =
                normalizeOptionalPath(
                        builder.backupPath
                );

        this.changedAttributes =
                immutableStringList(
                        builder.changedAttributes
                );

        this.warnings =
                immutableStringList(
                        builder.warnings
                );

        this.metadata =
                immutableMetadata(
                        builder.metadata
                );

        validateState();
    }

    /**
     * 수정 대상 XHTML 절대 경로를 반환한다.
     *
     * @return XHTML 절대 경로
     */
    public Path getXhtmlPath() {
        return xhtmlPath;
    }

    /**
     * 프로젝트 기준 XHTML 상대 경로를 반환한다.
     *
     * @return 슬래시 형식 상대 경로
     */
    public String getProjectRelativeXhtmlPath() {
        return projectRelativeXhtmlPath;
    }

    /**
     * 대상 이미지 요소 id를 반환한다.
     *
     * @return 이미지 요소 id, 없으면 {@code null}
     */
    public String getImageElementId() {
        return imageElementId;
    }

    /**
     * 대상 이미지 src를 반환한다.
     *
     * @return 이미지 src, 없으면 {@code null}
     */
    public String getImageSource() {
        return imageSource;
    }

    /**
     * 적용한 이미지 접근성 유형을 반환한다.
     *
     * @return 이미지 접근성 유형
     */
    public ImageAccessibilityType getAccessibilityType() {
        return accessibilityType;
    }

    /**
     * 적용 전 alt 속성값을 반환한다.
     *
     * <p>속성이 없었던 경우 {@code null}, 빈 alt 속성이었던 경우
     * 빈 문자열을 반환한다.</p>
     *
     * @return 이전 alt 값
     */
    public String getPreviousAltText() {
        return previousAltText;
    }

    /**
     * 적용 후 alt 속성값을 반환한다.
     *
     * @return 적용된 alt 값
     */
    public String getAppliedAltText() {
        return appliedAltText;
    }

    public String getPreviousRole() {
        return previousRole;
    }

    public String getAppliedRole() {
        return appliedRole;
    }

    public String getPreviousAriaHidden() {
        return previousAriaHidden;
    }

    public String getAppliedAriaHidden() {
        return appliedAriaHidden;
    }

    /**
     * 대상 이미지 요소가 검색되었는지 반환한다.
     *
     * @return 대상이 존재하면 {@code true}
     */
    public boolean isMatched() {
        return matched;
    }

    /**
     * 검색 조건과 일치한 이미지 요소 수를 반환한다.
     *
     * @return 일치 요소 수
     */
    public int getMatchedElementCount() {
        return matchedElementCount;
    }

    /**
     * 접근성 속성이 실제로 변경되었는지 반환한다.
     *
     * @return 속성 변경이 있으면 {@code true}
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     * XHTML 파일이 실제로 저장되었는지 반환한다.
     *
     * @return 파일이 저장되었으면 {@code true}
     */
    public boolean isFileUpdated() {
        return fileUpdated;
    }

    /**
     * dry-run 실행 결과인지 반환한다.
     *
     * @return dry-run이면 {@code true}
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * 백업 파일이 생성되었는지 반환한다.
     *
     * @return 백업 생성 여부
     */
    public boolean isBackupCreated() {
        return backupCreated;
    }

    /**
     * 생성된 백업 파일 경로를 반환한다.
     *
     * @return 백업 경로, 없으면 {@code null}
     */
    public Path getBackupPath() {
        return backupPath;
    }

    /**
     * 변경된 속성명 목록을 반환한다.
     *
     * @return 수정할 수 없는 속성명 목록
     */
    public List<String> getChangedAttributes() {
        return changedAttributes;
    }

    /**
     * 처리 경고 목록을 반환한다.
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

    public String getMetadata(String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    /**
     * 성공적인 적용 결과인지 반환한다.
     *
     * <p>대상 요소가 하나만 검색되고, dry-run이거나 실제 파일 저장까지
     * 완료된 경우 성공으로 판단한다.</p>
     *
     * @return 성공 여부
     */
    public boolean isSuccessful() {

        if (!matched || matchedElementCount != 1) {
            return false;
        }

        if (dryRun) {
            return true;
        }

        if (!changed) {
            return true;
        }

        return fileUpdated;
    }

    /**
     * 대상이 여러 개 검색되어 선택이 모호한지 반환한다.
     *
     * @return 다중 일치이면 {@code true}
     */
    public boolean isAmbiguousMatch() {
        return matchedElementCount > 1;
    }

    /**
     * 변경할 필요가 없었던 결과인지 반환한다.
     *
     * @return 대상은 존재하지만 속성 변경이 없으면 {@code true}
     */
    public boolean isNoChange() {
        return matched
                && matchedElementCount == 1
                && !changed;
    }

    /**
     * 장식 이미지 적용 결과인지 반환한다.
     *
     * @return 장식 이미지이면 {@code true}
     */
    public boolean isDecorative() {
        return accessibilityType.isDecorative();
    }

    /**
     * 경고가 하나 이상 존재하는지 반환한다.
     *
     * @return 경고 존재 여부
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 프로젝트 루트와 XHTML 경로를 이용하여 Builder를 생성한다.
     *
     * @param projectRoot 프로젝트 루트
     * @param xhtmlPath XHTML 경로
     * @return 초기화된 Builder
     */
    public static Builder builder(
            Path projectRoot,
            Path xhtmlPath) {

        Objects.requireNonNull(
                projectRoot,
                "projectRoot must not be null"
        );

        Objects.requireNonNull(
                xhtmlPath,
                "xhtmlPath must not be null"
        );

        Path normalizedRoot =
                projectRoot
                        .toAbsolutePath()
                        .normalize();

        Path normalizedXhtml =
                xhtmlPath
                        .toAbsolutePath()
                        .normalize();

        if (!normalizedXhtml.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "xhtmlPath must be inside projectRoot"
            );
        }

        return builder()
                .xhtmlPath(normalizedXhtml)
                .projectRelativeXhtmlPath(
                        normalizedRoot
                                .relativize(normalizedXhtml)
                                .toString()
                                .replace('\\', '/')
                );
    }

    private void validateState() {

        if (matchedElementCount == 0 && matched) {
            throw new IllegalArgumentException(
                    "matched cannot be true when matchedElementCount is zero"
            );
        }

        if (matchedElementCount > 0 && !matched) {
            throw new IllegalArgumentException(
                    "matched must be true when matchedElementCount is positive"
            );
        }

        if (fileUpdated && dryRun) {
            throw new IllegalArgumentException(
                    "fileUpdated cannot be true in dry-run mode"
            );
        }

        if (fileUpdated && !changed) {
            throw new IllegalArgumentException(
                    "fileUpdated cannot be true when no attributes changed"
            );
        }

        if (backupCreated && backupPath == null) {
            throw new IllegalArgumentException(
                    "backupPath is required when backupCreated is true"
            );
        }

        if (!backupCreated && backupPath != null) {
            throw new IllegalArgumentException(
                    "backupCreated must be true when backupPath is provided"
            );
        }

        if (matchedElementCount > 1 && fileUpdated) {
            throw new IllegalArgumentException(
                    "File must not be updated when multiple elements match"
            );
        }

        if (accessibilityType == ImageAccessibilityType.DECORATIVE
                && appliedAltText != null
                && !appliedAltText.isEmpty()) {

            throw new IllegalArgumentException(
                    "Decorative image must use empty alt text"
            );
        }

        if (accessibilityType.isAltTextRequired()
                && matchedElementCount == 1
                && appliedAltText != null
                && appliedAltText.isBlank()) {

            throw new IllegalArgumentException(
                    "Non-decorative image must not use blank alt text"
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

    private static Path normalizeOptionalPath(
            Path value) {

        if (value == null) {
            return null;
        }

        return value
                .toAbsolutePath()
                .normalize();
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
     * alt 속성에서는 빈 문자열과 속성 없음의 차이를 유지한다.
     */
    private static String normalizeNullableAttributeValue(
            String value) {

        if (value == null) {
            return null;
        }

        return value.trim();
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

    private static int validateMatchedElementCount(
            int value) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "matchedElementCount must not be negative"
            );
        }

        return value;
    }

    private static List<String> immutableStringList(
            List<String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (String item : source) {
            String normalized =
                    normalizeOptionalText(item);

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
     * {@link AltTextApplicationResult} Builder.
     */
    public static final class Builder {

        private Path xhtmlPath;
        private String projectRelativeXhtmlPath;
        private String imageElementId;
        private String imageSource;

        private ImageAccessibilityType accessibilityType =
                ImageAccessibilityType.UNKNOWN;

        private String previousAltText;
        private String appliedAltText;

        private String previousRole;
        private String appliedRole;

        private String previousAriaHidden;
        private String appliedAriaHidden;

        private boolean matched;
        private int matchedElementCount;
        private boolean changed;
        private boolean fileUpdated;
        private boolean dryRun;
        private boolean backupCreated;

        private Path backupPath;

        private final List<String> changedAttributes =
                new ArrayList<>();

        private final List<String> warnings =
                new ArrayList<>();

        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder xhtmlPath(Path xhtmlPath) {
            this.xhtmlPath = xhtmlPath;
            return this;
        }

        public Builder projectRelativeXhtmlPath(
                String projectRelativeXhtmlPath) {

            this.projectRelativeXhtmlPath =
                    projectRelativeXhtmlPath;

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

        public Builder previousAltText(
                String previousAltText) {

            this.previousAltText =
                    previousAltText;

            return this;
        }

        public Builder appliedAltText(
                String appliedAltText) {

            this.appliedAltText =
                    appliedAltText;

            return this;
        }

        public Builder previousRole(
                String previousRole) {

            this.previousRole =
                    previousRole;

            return this;
        }

        public Builder appliedRole(
                String appliedRole) {

            this.appliedRole =
                    appliedRole;

            return this;
        }

        public Builder previousAriaHidden(
                String previousAriaHidden) {

            this.previousAriaHidden =
                    previousAriaHidden;

            return this;
        }

        public Builder appliedAriaHidden(
                String appliedAriaHidden) {

            this.appliedAriaHidden =
                    appliedAriaHidden;

            return this;
        }

        public Builder matched(boolean matched) {
            this.matched = matched;
            return this;
        }

        public Builder matchedElementCount(
                int matchedElementCount) {

            this.matchedElementCount =
                    matchedElementCount;

            return this;
        }

        public Builder changed(boolean changed) {
            this.changed = changed;
            return this;
        }

        public Builder fileUpdated(
                boolean fileUpdated) {

            this.fileUpdated = fileUpdated;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder backupCreated(
                boolean backupCreated) {

            this.backupCreated =
                    backupCreated;

            return this;
        }

        public Builder backupPath(Path backupPath) {
            this.backupPath = backupPath;
            return this;
        }

        public Builder changedAttribute(
                String attributeName) {

            String normalized =
                    normalizeOptionalText(
                            attributeName
                    );

            if (normalized != null
                    && !changedAttributes
                            .contains(normalized)) {

                changedAttributes.add(normalized);
            }

            return this;
        }

        public Builder changedAttributes(
                List<String> attributeNames) {

            if (attributeNames == null) {
                return this;
            }

            for (String attributeName
                    : attributeNames) {

                changedAttribute(attributeName);
            }

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

        /**
         * 요청 객체의 공통 정보를 복사한다.
         *
         * @param request 대체 텍스트 적용 요청
         * @return 현재 Builder
         */
        public Builder request(
                AltTextApplicationRequest request) {

            Objects.requireNonNull(
                    request,
                    "request must not be null"
            );

            this.xhtmlPath =
                    request.getXhtmlPath();

            this.projectRelativeXhtmlPath =
                    request.getNormalizedRelativeXhtmlPath();

            this.imageElementId =
                    request.getImageElementId();

            this.imageSource =
                    request.getImageSource();

            this.accessibilityType =
                    request.getAccessibilityType();

            this.appliedAltText =
                    request.getAltText();

            this.dryRun =
                    request.isDryRun();

            metadata(request.getMetadata());

            return this;
        }

        /**
         * 백업 결과를 한 번에 설정한다.
         *
         * @param backupPath 생성된 백업 경로
         * @return 현재 Builder
         */
        public Builder backup(Path backupPath) {

            this.backupPath = backupPath;
            this.backupCreated =
                    backupPath != null;

            return this;
        }

        public AltTextApplicationResult build() {
            return new AltTextApplicationResult(this);
        }
    }
}