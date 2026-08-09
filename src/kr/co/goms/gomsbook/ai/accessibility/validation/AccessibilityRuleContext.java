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
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 개별 접근성 검사 규칙에 전달되는 실행 컨텍스트.
 *
 * <p>현재 검사 중인 DOM 문서, 프로젝트 경로, 문서 유형,
 * 검사 옵션 및 공유 메타데이터를 포함하는 불변 객체이다.</p>
 *
 * <p>규칙 구현체는 이 컨텍스트를 이용해 문서를 검사하지만
 * DOM이나 원본 파일을 직접 저장하거나 수정해서는 안 된다.</p>
 */
public final class AccessibilityRuleContext {

    private final Path projectRoot;
    private final Path documentPath;
    private final String projectRelativePath;
    private final AccessibilityDocumentType documentType;
    private final Document document;
    private final String documentLanguage;
    private final String documentTitle;
    private final boolean strictMode;
    private final boolean includeInformationalIssues;
    private final int maximumAltTextLength;
    private final Map<String, String> options;
    private final Map<String, String> metadata;

    private AccessibilityRuleContext(Builder builder) {

        this.projectRoot = normalizeRequiredPath(
                builder.projectRoot,
                "projectRoot"
        );

        this.documentPath = normalizeRequiredPath(
                builder.documentPath,
                "documentPath"
        );

        validateDocumentPath(
                projectRoot,
                documentPath
        );

        this.projectRelativePath =
                resolveProjectRelativePath(
                        projectRoot,
                        documentPath,
                        builder.projectRelativePath
                );

        this.documentType =
                builder.documentType == null
                        ? AccessibilityDocumentType
                                .fromPath(documentPath)
                        : builder.documentType;

        this.document = Objects.requireNonNull(
                builder.document,
                "document must not be null"
        );

        this.documentLanguage =
                normalizeOptionalText(
                        builder.documentLanguage
                );

        this.documentTitle =
                normalizeOptionalText(
                        builder.documentTitle
                );

        this.strictMode = builder.strictMode;

        this.includeInformationalIssues =
                builder.includeInformationalIssues;

        this.maximumAltTextLength =
                validateMaximumAltTextLength(
                        builder.maximumAltTextLength
                );

        this.options = immutableMap(
                builder.options
        );

        this.metadata = immutableMap(
                builder.metadata
        );
    }

    /**
     * 프로젝트 루트 절대 경로를 반환한다.
     *
     * @return 프로젝트 루트
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 현재 검사 문서의 절대 경로를 반환한다.
     *
     * @return 문서 경로
     */
    public Path getDocumentPath() {
        return documentPath;
    }

    /**
     * 프로젝트 기준 문서 상대 경로를 반환한다.
     *
     * @return 슬래시 형식 상대 경로
     */
    public String getProjectRelativePath() {
        return projectRelativePath;
    }

    /**
     * 검사 문서 유형을 반환한다.
     *
     * @return 문서 유형
     */
    public AccessibilityDocumentType getDocumentType() {
        return documentType;
    }

    /**
     * 파싱된 DOM 문서를 반환한다.
     *
     * <p>규칙은 DOM을 읽기 전용으로 사용해야 한다.</p>
     *
     * @return DOM 문서
     */
    public Document getDocument() {
        return document;
    }

    /**
     * DOM 루트 요소를 반환한다.
     *
     * @return 문서 루트 요소
     */
    public Element getDocumentElement() {
        return document.getDocumentElement();
    }

    /**
     * 문서 언어를 반환한다.
     *
     * @return 언어 코드, 없으면 {@code null}
     */
    public String getDocumentLanguage() {
        return documentLanguage;
    }

    /**
     * 문서 제목을 반환한다.
     *
     * @return 문서 제목, 없으면 {@code null}
     */
    public String getDocumentTitle() {
        return documentTitle;
    }

    /**
     * 엄격한 검사 모드인지 반환한다.
     *
     * <p>엄격 모드에서는 권고 수준의 문제도 경고 또는 오류로
     * 승격할 수 있다.</p>
     *
     * @return 엄격 모드 여부
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    /**
     * 정보 수준 문제를 결과에 포함할지 반환한다.
     *
     * @return 정보 문제 포함 여부
     */
    public boolean isIncludeInformationalIssues() {
        return includeInformationalIssues;
    }

    /**
     * 대체 텍스트 권장 최대 길이를 반환한다.
     *
     * @return 최대 문자 수
     */
    public int getMaximumAltTextLength() {
        return maximumAltTextLength;
    }

    /**
     * 검사 옵션을 반환한다.
     *
     * @return 수정할 수 없는 옵션
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * 지정한 검사 옵션을 반환한다.
     *
     * @param key 옵션 키
     * @return 옵션 값, 없으면 {@code null}
     */
    public String getOption(String key) {

        if (key == null) {
            return null;
        }

        return options.get(key);
    }

    /**
     * Boolean 형식 검사 옵션을 반환한다.
     *
     * @param key 옵션 키
     * @param defaultValue 기본값
     * @return 옵션 값
     */
    public boolean getBooleanOption(
            String key,
            boolean defaultValue) {

        String value = getOption(key);

        if (value == null) {
            return defaultValue;
        }

        if ("true".equalsIgnoreCase(value)) {
            return true;
        }

        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        return defaultValue;
    }

    /**
     * 정수 형식 검사 옵션을 반환한다.
     *
     * @param key 옵션 키
     * @param defaultValue 기본값
     * @return 옵션 값
     */
    public int getIntegerOption(
            String key,
            int defaultValue) {

        String value = getOption(key);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /**
     * 공유 메타데이터를 반환한다.
     *
     * @return 수정할 수 없는 메타데이터
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 지정한 메타데이터 값을 반환한다.
     *
     * @param key 메타데이터 키
     * @return 값, 없으면 {@code null}
     */
    public String getMetadata(String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    /**
     * 현재 문서가 XHTML 계열인지 반환한다.
     *
     * @return XHTML 또는 HTML이면 {@code true}
     */
    public boolean isXhtmlDocument() {
        return documentType == AccessibilityDocumentType.XHTML
                || documentType == AccessibilityDocumentType.HTML;
    }

    /**
     * 현재 문서가 OPF 패키지 문서인지 반환한다.
     *
     * @return OPF이면 {@code true}
     */
    public boolean isPackageDocument() {
        return documentType == AccessibilityDocumentType.OPF;
    }

    /**
     * 현재 문서가 EPUB 내비게이션 문서인지 반환한다.
     *
     * @return 내비게이션 문서이면 {@code true}
     */
    public boolean isNavigationDocument() {

        if (!isXhtmlDocument()) {
            return false;
        }

        Element root = getDocumentElement();

        if (root == null) {
            return false;
        }

        String epubType =
                root.getAttributeNS(
                        "http://www.idpf.org/2007/ops",
                        "type"
                );

        if ("navigation".equalsIgnoreCase(epubType)) {
            return true;
        }

        return document.getElementsByTagNameNS(
                "http://www.w3.org/1999/xhtml",
                "nav"
        ).getLength() > 0;
    }

    /**
     * 현재 문서의 루트 요소에서 lang 값을 찾는다.
     *
     * <p>{@code lang}을 우선하고 없으면 {@code xml:lang}을 조회한다.</p>
     *
     * @return 문서 언어
     */
    public Optional<String> resolveRootLanguage() {

        Element root = getDocumentElement();

        if (root == null) {
            return Optional.empty();
        }

        String language = normalizeOptionalText(
                root.getAttribute("lang")
        );

        if (language != null) {
            return Optional.of(language);
        }

        language = normalizeOptionalText(
                root.getAttributeNS(
                        "http://www.w3.org/XML/1998/namespace",
                        "lang"
                )
        );

        return Optional.ofNullable(language);
    }

    /**
     * 위치 정보 Builder를 생성한다.
     *
     * @return 현재 문서 정보가 설정된 위치 Builder
     */
    public AccessibilityLocation.Builder locationBuilder() {

        return AccessibilityLocation
                .builder(
                        projectRoot,
                        documentPath
                );
    }

    /**
     * 요소 정보를 포함한 위치 Builder를 생성한다.
     *
     * @param element 대상 요소
     * @return 위치 Builder
     */
    public AccessibilityLocation.Builder locationBuilder(
            Element element) {

        AccessibilityLocation.Builder builder =
                locationBuilder();

        if (element == null) {
            return builder;
        }

        builder.element(
                resolveElementName(element),
                normalizeOptionalText(
                        element.getAttribute("id")
                )
        );

        return builder;
    }

    /**
     * 현재 컨텍스트 정보를 복사한 새 Builder를 반환한다.
     *
     * @return 복사 Builder
     */
    public Builder toBuilder() {

        return builder()
                .projectRoot(projectRoot)
                .documentPath(documentPath)
                .projectRelativePath(
                        projectRelativePath
                )
                .documentType(documentType)
                .document(document)
                .documentLanguage(
                        documentLanguage
                )
                .documentTitle(documentTitle)
                .strictMode(strictMode)
                .includeInformationalIssues(
                        includeInformationalIssues
                )
                .maximumAltTextLength(
                        maximumAltTextLength
                )
                .options(options)
                .metadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 필수 문서 정보로 Builder를 생성한다.
     *
     * @param projectRoot 프로젝트 루트
     * @param documentPath 문서 경로
     * @param document DOM 문서
     * @return Builder
     */
    public static Builder builder(
            Path projectRoot,
            Path documentPath,
            Document document) {

        return builder()
                .projectRoot(projectRoot)
                .documentPath(documentPath)
                .document(document);
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

    private static void validateDocumentPath(
            Path projectRoot,
            Path documentPath) {

        if (!documentPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "documentPath must be inside projectRoot: "
                            + documentPath
            );
        }
    }

    private static String resolveProjectRelativePath(
            Path projectRoot,
            Path documentPath,
            String explicitValue) {

        String normalized =
                normalizeOptionalPathText(
                        explicitValue
                );

        if (normalized != null) {
            return normalized;
        }

        return projectRoot
                .relativize(documentPath)
                .toString()
                .replace('\\', '/');
    }

    private static int validateMaximumAltTextLength(
            int value) {

        if (value < 20 || value > 2_000) {
            throw new IllegalArgumentException(
                    "maximumAltTextLength must be between "
                            + "20 and 2000: "
                            + value
            );
        }

        return value;
    }

    private static String resolveElementName(
            Element element) {

        String localName = element.getLocalName();

        if (localName != null
                && !localName.isBlank()) {

            return localName;
        }

        return element.getTagName();
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

    private static String normalizeOptionalPathText(
            String value) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        return normalized.replace('\\', '/');
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
     * {@link AccessibilityRuleContext} Builder.
     */
    public static final class Builder {

        private Path projectRoot;
        private Path documentPath;
        private String projectRelativePath;
        private AccessibilityDocumentType documentType;
        private Document document;
        private String documentLanguage;
        private String documentTitle;
        private boolean strictMode;
        private boolean includeInformationalIssues = true;
        private int maximumAltTextLength = 150;
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

        /**
         * 프로젝트 루트와 상대 문서 경로를 설정한다.
         *
         * @param projectRoot 프로젝트 루트
         * @param relativeDocumentPath 상대 문서 경로
         * @return 현재 Builder
         */
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

            this.projectRelativePath =
                    relativeDocumentPath
                            .normalize()
                            .toString()
                            .replace('\\', '/');

            return this;
        }

        public Builder projectRelativePath(
                String projectRelativePath) {

            this.projectRelativePath =
                    projectRelativePath;

            return this;
        }

        public Builder documentType(
                AccessibilityDocumentType documentType) {

            this.documentType = documentType;
            return this;
        }

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder documentLanguage(
                String documentLanguage) {

            this.documentLanguage =
                    documentLanguage;

            return this;
        }

        public Builder documentTitle(
                String documentTitle) {

            this.documentTitle = documentTitle;
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

        public Builder maximumAltTextLength(
                int maximumAltTextLength) {

            this.maximumAltTextLength =
                    maximumAltTextLength;

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

        public Builder options(
                Map<String, String> options) {

            putAllNormalized(
                    this.options,
                    options
            );

            return this;
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

        public Builder metadata(
                Map<String, String> metadata) {

            putAllNormalized(
                    this.metadata,
                    metadata
            );

            return this;
        }

        public AccessibilityRuleContext build() {
            return new AccessibilityRuleContext(this);
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

        private static void putAllNormalized(
                Map<String, String> target,
                Map<String, String> source) {

            if (source == null) {
                return;
            }

            for (Map.Entry<String, String> entry
                    : source.entrySet()) {

                putNormalized(
                        target,
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }
    }
}