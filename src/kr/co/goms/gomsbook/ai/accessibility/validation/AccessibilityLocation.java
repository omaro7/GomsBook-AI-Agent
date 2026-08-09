/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 접근성 문제가 발견된 문서 위치를 나타낸다.
 *
 * <p>XHTML 파일 경로, 요소 이름, 요소 id, 속성명, 행·열 위치,
 * XPath와 같은 식별 정보를 포함하는 불변 객체이다.</p>
 */
public final class AccessibilityLocation {

    private final Path documentPath;
    private final String projectRelativePath;
    private final String elementName;
    private final String elementId;
    private final String attributeName;
    private final String attributeValue;
    private final Integer lineNumber;
    private final Integer columnNumber;
    private final String xpath;
    private final String textExcerpt;
    private final Map<String, String> metadata;

    private AccessibilityLocation(Builder builder) {

        this.documentPath = normalizeOptionalPath(
                builder.documentPath
        );

        this.projectRelativePath = normalizeOptionalPathText(
                builder.projectRelativePath
        );

        this.elementName = normalizeOptionalText(
                builder.elementName
        );

        this.elementId = normalizeOptionalText(
                builder.elementId
        );

        this.attributeName = normalizeOptionalText(
                builder.attributeName
        );

        this.attributeValue = normalizeNullableAttributeValue(
                builder.attributeValue
        );

        this.lineNumber = validatePositiveNumber(
                builder.lineNumber,
                "lineNumber"
        );

        this.columnNumber = validatePositiveNumber(
                builder.columnNumber,
                "columnNumber"
        );

        this.xpath = normalizeOptionalText(
                builder.xpath
        );

        this.textExcerpt = normalizeOptionalText(
                builder.textExcerpt
        );

        this.metadata = immutableMetadata(
                builder.metadata
        );

        validateState();
    }

    /**
     * 문제가 발견된 문서의 절대 경로를 반환한다.
     *
     * @return 문서 절대 경로, 없으면 {@code null}
     */
    public Path getDocumentPath() {
        return documentPath;
    }

    /**
     * 프로젝트 기준 문서 상대 경로를 반환한다.
     *
     * @return 슬래시 형식 상대 경로, 없으면 {@code null}
     */
    public String getProjectRelativePath() {
        return projectRelativePath;
    }

    /**
     * 문제가 발견된 요소 이름을 반환한다.
     *
     * @return 요소 이름, 없으면 {@code null}
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * 문제가 발견된 요소의 id를 반환한다.
     *
     * @return 요소 id, 없으면 {@code null}
     */
    public String getElementId() {
        return elementId;
    }

    /**
     * 문제가 발견된 속성 이름을 반환한다.
     *
     * @return 속성명, 없으면 {@code null}
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * 문제가 발견된 속성값을 반환한다.
     *
     * <p>속성값이 빈 문자열인 경우 빈 문자열을 유지한다.</p>
     *
     * @return 속성값, 없으면 {@code null}
     */
    public String getAttributeValue() {
        return attributeValue;
    }

    /**
     * 문제가 발견된 행 번호를 반환한다.
     *
     * @return 1부터 시작하는 행 번호, 없으면 {@code null}
     */
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * 문제가 발견된 열 번호를 반환한다.
     *
     * @return 1부터 시작하는 열 번호, 없으면 {@code null}
     */
    public Integer getColumnNumber() {
        return columnNumber;
    }

    /**
     * 문제가 발견된 요소의 XPath를 반환한다.
     *
     * @return XPath, 없으면 {@code null}
     */
    public String getXpath() {
        return xpath;
    }

    /**
     * 문제가 발견된 주변 텍스트 일부를 반환한다.
     *
     * @return 텍스트 일부, 없으면 {@code null}
     */
    public String getTextExcerpt() {
        return textExcerpt;
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

    public boolean hasDocumentPath() {
        return documentPath != null;
    }

    public boolean hasProjectRelativePath() {
        return projectRelativePath != null;
    }

    public boolean hasElementName() {
        return elementName != null;
    }

    public boolean hasElementId() {
        return elementId != null;
    }

    public boolean hasAttribute() {
        return attributeName != null;
    }

    public boolean hasSourcePosition() {
        return lineNumber != null;
    }

    public boolean hasXpath() {
        return xpath != null;
    }

    /**
     * UI 또는 로그에 사용할 간단한 위치 문자열을 반환한다.
     *
     * @return 사람이 읽을 수 있는 위치 문자열
     */
    public String toDisplayString() {

        StringBuilder result = new StringBuilder();

        if (projectRelativePath != null) {
            result.append(projectRelativePath);
        } else if (documentPath != null) {
            result.append(documentPath);
        }

        if (lineNumber != null) {
            if (result.length() > 0) {
                result.append(':');
            }

            result.append(lineNumber);

            if (columnNumber != null) {
                result.append(':');
                result.append(columnNumber);
            }
        }

        if (elementName != null) {
            if (result.length() > 0) {
                result.append(" ");
            }

            result.append('<');
            result.append(elementName);

            if (elementId != null) {
                result.append(" id=\"");
                result.append(elementId);
                result.append('"');
            }

            result.append('>');
        }

        if (attributeName != null) {
            if (result.length() > 0) {
                result.append(" @");
            }

            result.append(attributeName);
        }

        if (result.length() == 0) {
            return "위치 정보 없음";
        }

        return result.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 프로젝트 루트와 문서 경로를 이용하여 Builder를 생성한다.
     *
     * @param projectRoot 프로젝트 루트
     * @param documentPath 문서 경로
     * @return 초기화된 Builder
     */
    public static Builder builder(
            Path projectRoot,
            Path documentPath) {

        Objects.requireNonNull(
                projectRoot,
                "projectRoot must not be null"
        );

        Objects.requireNonNull(
                documentPath,
                "documentPath must not be null"
        );

        Path normalizedRoot = projectRoot
                .toAbsolutePath()
                .normalize();

        Path normalizedDocument = documentPath
                .toAbsolutePath()
                .normalize();

        if (!normalizedDocument.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "documentPath must be inside projectRoot"
            );
        }

        return builder()
                .documentPath(normalizedDocument)
                .projectRelativePath(
                        normalizedRoot
                                .relativize(normalizedDocument)
                                .toString()
                                .replace('\\', '/')
                );
    }

    private void validateState() {

        if (columnNumber != null && lineNumber == null) {
            throw new IllegalArgumentException(
                    "lineNumber is required when columnNumber is provided"
            );
        }

        if (attributeValue != null && attributeName == null) {
            throw new IllegalArgumentException(
                    "attributeName is required when attributeValue is provided"
            );
        }
    }

    private static Path normalizeOptionalPath(Path value) {

        if (value == null) {
            return null;
        }

        return value
                .toAbsolutePath()
                .normalize();
    }

    private static String normalizeOptionalPathText(
            String value) {

        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        return normalized.replace('\\', '/');
    }

    private static String normalizeOptionalText(String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    /**
     * 속성값에서는 빈 문자열과 값 없음의 차이를 유지한다.
     */
    private static String normalizeNullableAttributeValue(
            String value) {

        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static Integer validatePositiveNumber(
            Integer value,
            String fieldName) {

        if (value == null) {
            return null;
        }

        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }

        return value;
    }

    private static Map<String, String> immutableMetadata(
            Map<String, String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

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
     * {@link AccessibilityLocation} Builder.
     */
    public static final class Builder {

        private Path documentPath;
        private String projectRelativePath;
        private String elementName;
        private String elementId;
        private String attributeName;
        private String attributeValue;
        private Integer lineNumber;
        private Integer columnNumber;
        private String xpath;
        private String textExcerpt;
        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder documentPath(Path documentPath) {
            this.documentPath = documentPath;
            return this;
        }

        public Builder projectRelativePath(
                String projectRelativePath) {

            this.projectRelativePath =
                    projectRelativePath;

            return this;
        }

        public Builder elementName(
                String elementName) {

            this.elementName = elementName;
            return this;
        }

        public Builder elementId(String elementId) {
            this.elementId = elementId;
            return this;
        }

        public Builder attributeName(
                String attributeName) {

            this.attributeName = attributeName;
            return this;
        }

        public Builder attributeValue(
                String attributeValue) {

            this.attributeValue = attributeValue;
            return this;
        }

        /**
         * 속성명과 속성값을 한 번에 설정한다.
         *
         * @param attributeName 속성명
         * @param attributeValue 속성값
         * @return 현재 Builder
         */
        public Builder attribute(
                String attributeName,
                String attributeValue) {

            this.attributeName = attributeName;
            this.attributeValue = attributeValue;

            return this;
        }

        public Builder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder columnNumber(
                Integer columnNumber) {

            this.columnNumber = columnNumber;
            return this;
        }

        /**
         * 행과 열 위치를 한 번에 설정한다.
         *
         * @param lineNumber 행 번호
         * @param columnNumber 열 번호
         * @return 현재 Builder
         */
        public Builder sourcePosition(
                int lineNumber,
                int columnNumber) {

            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;

            return this;
        }

        public Builder xpath(String xpath) {
            this.xpath = xpath;
            return this;
        }

        public Builder textExcerpt(
                String textExcerpt) {

            this.textExcerpt = textExcerpt;
            return this;
        }

        /**
         * DOM 요소 정보를 설정한다.
         *
         * @param elementName 요소 이름
         * @param elementId 요소 id
         * @return 현재 Builder
         */
        public Builder element(
                String elementName,
                String elementId) {

            this.elementName = elementName;
            this.elementId = elementId;

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

        public AccessibilityLocation build() {
            return new AccessibilityLocation(this);
        }
    }
}