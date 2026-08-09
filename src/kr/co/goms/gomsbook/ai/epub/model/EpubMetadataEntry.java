/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB 패키지 문서의 개별 메타데이터 항목을 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code metadata} 요소 내부에 작성되는
 * Dublin Core 요소 및 EPUB 3 {@code meta} 요소와 대응합니다.</p>
 *
 * <h2>Dublin Core 메타데이터</h2>
 *
 * <pre>
 * {@code
 * <dc:title id="title">점심시간, 서울을 걷다</dc:title>
 * <dc:creator id="creator">한정훈</dc:creator>
 * <dc:language>ko</dc:language>
 * }
 * </pre>
 *
 * <h2>EPUB 3 확장 메타데이터</h2>
 *
 * <pre>
 * {@code
 * <meta refines="#creator"
 *       property="role"
 *       scheme="marc:relators">aut</meta>
 * }
 * </pre>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubMetadataEntry {

    /**
     * 메타데이터 표현 형식입니다.
     */
    private final Kind kind;

    /**
     * Dublin Core 요소 이름입니다.
     *
     * <p>예: {@code dc:title}, {@code dc:creator},
     * {@code dc:identifier}</p>
     *
     * <p>{@link Kind#META_PROPERTY}에서는 {@code meta}입니다.</p>
     */
    private final String elementName;

    /**
     * 메타데이터의 텍스트 값입니다.
     */
    private final String value;

    /**
     * 메타데이터 요소의 선택적 ID입니다.
     */
    private final String id;

    /**
     * EPUB 3 meta 요소의 property 값입니다.
     *
     * <p>예: {@code role}, {@code file-as}, {@code title-type},
     * {@code dcterms:modified}</p>
     */
    private final String property;

    /**
     * 정제 대상 요소를 참조하는 refines 값입니다.
     *
     * <p>일반적으로 {@code #creator}, {@code #title}과 같이
     * 패키지 내부 ID를 참조합니다.</p>
     */
    private final String refines;

    /**
     * 메타데이터 값의 해석 체계를 나타냅니다.
     *
     * <p>예: {@code marc:relators}</p>
     */
    private final String scheme;

    /**
     * BCP 47 언어 태그입니다.
     *
     * <p>XML 출력 시 {@code xml:lang} 속성으로 작성합니다.</p>
     */
    private final String language;

    /**
     * 텍스트 방향입니다.
     */
    private final TextDirection direction;

    /**
     * 표준 필드 외 추가 XML 속성입니다.
     */
    private final Map<String, String> attributes;

    private EpubMetadataEntry(Builder builder) {
        this.kind = Objects.requireNonNull(
                builder.kind,
                "EPUB metadata kind must not be null."
        );
        this.elementName = resolveElementName(
                builder.kind,
                builder.elementName
        );
        this.value = requireValue(builder.value);
        this.id = normalizeOptionalIdentifier(builder.id);
        this.property = normalizeOptionalToken(builder.property);
        this.refines = normalizeRefines(builder.refines);
        this.scheme = normalizeOptionalToken(builder.scheme);
        this.language = normalizeLanguageTag(builder.language);
        this.direction = builder.direction;
        this.attributes = immutableAttributes(builder.attributes);

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Dublin Core 메타데이터 builder를 생성합니다.
     *
     * @param elementName Dublin Core 요소 이름
     * @param value       메타데이터 값
     * @return builder
     */
    public static Builder dc(
            String elementName,
            String value
    ) {
        return builder()
                .kind(Kind.DUBLIN_CORE)
                .elementName(elementName)
                .value(value);
    }

    /**
     * EPUB 3 property 메타데이터 builder를 생성합니다.
     *
     * @param property property 값
     * @param value    메타데이터 값
     * @return builder
     */
    public static Builder meta(
            String property,
            String value
    ) {
        return builder()
                .kind(Kind.META_PROPERTY)
                .property(property)
                .value(value);
    }

    public static EpubMetadataEntry title(String value) {
        return dc("dc:title", value).build();
    }

    public static EpubMetadataEntry title(
            String id,
            String value
    ) {
        return dc("dc:title", value)
                .id(id)
                .build();
    }

    public static EpubMetadataEntry creator(String value) {
        return dc("dc:creator", value).build();
    }

    public static EpubMetadataEntry creator(
            String id,
            String value
    ) {
        return dc("dc:creator", value)
                .id(id)
                .build();
    }

    public static EpubMetadataEntry identifier(String value) {
        return dc("dc:identifier", value).build();
    }

    public static EpubMetadataEntry identifier(
            String id,
            String value
    ) {
        return dc("dc:identifier", value)
                .id(id)
                .build();
    }

    public static EpubMetadataEntry language(String value) {
        return dc("dc:language", value).build();
    }

    public static EpubMetadataEntry publisher(String value) {
        return dc("dc:publisher", value).build();
    }

    public static EpubMetadataEntry description(String value) {
        return dc("dc:description", value).build();
    }

    public static EpubMetadataEntry subject(String value) {
        return dc("dc:subject", value).build();
    }

    public static EpubMetadataEntry rights(String value) {
        return dc("dc:rights", value).build();
    }

    public static EpubMetadataEntry date(String value) {
        return dc("dc:date", value).build();
    }

    public static EpubMetadataEntry modified(String value) {
        return meta("dcterms:modified", value).build();
    }

    /**
     * 특정 메타데이터의 역할 정제 항목을 생성합니다.
     *
     * @param targetId 정제 대상 메타데이터 ID
     * @param roleCode MARC 관계어 코드
     * @return 역할 메타데이터
     */
    public static EpubMetadataEntry role(
            String targetId,
            String roleCode
    ) {
        return meta("role", roleCode)
                .refines(targetId)
                .scheme("marc:relators")
                .build();
    }

    /**
     * 특정 메타데이터의 정렬 이름을 생성합니다.
     *
     * @param targetId 정제 대상 메타데이터 ID
     * @param fileAs   정렬용 문자열
     * @return file-as 메타데이터
     */
    public static EpubMetadataEntry fileAs(
            String targetId,
            String fileAs
    ) {
        return meta("file-as", fileAs)
                .refines(targetId)
                .build();
    }

    public Kind getKind() {
        return kind;
    }

    public String getElementName() {
        return elementName;
    }

    public String getValue() {
        return value;
    }

    public Optional<String> getId() {
        return Optional.ofNullable(id);
    }

    public Optional<String> getProperty() {
        return Optional.ofNullable(property);
    }

    public Optional<String> getRefines() {
        return Optional.ofNullable(refines);
    }

    public Optional<String> getScheme() {
        return Optional.ofNullable(scheme);
    }

    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    public Optional<TextDirection> getDirection() {
        return Optional.ofNullable(direction);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public boolean isDublinCore() {
        return kind == Kind.DUBLIN_CORE;
    }

    public boolean isMetaProperty() {
        return kind == Kind.META_PROPERTY;
    }

    public boolean hasId() {
        return id != null;
    }

    public boolean hasProperty() {
        return property != null;
    }

    public boolean hasRefines() {
        return refines != null;
    }

    public boolean hasScheme() {
        return scheme != null;
    }

    public boolean hasLanguage() {
        return language != null;
    }

    public boolean hasDirection() {
        return direction != null;
    }

    /**
     * 해당 메타데이터가 다른 메타데이터를 정제하는 항목인지 확인합니다.
     *
     * @return refines 속성이 있으면 {@code true}
     */
    public boolean isRefinement() {
        return refines != null;
    }

    /**
     * 정제 대상 ID를 {@code #} 없이 반환합니다.
     *
     * @return 대상 ID
     */
    public Optional<String> getRefinedTargetId() {
        if (refines == null) {
            return Optional.empty();
        }

        return Optional.of(
                refines.startsWith("#")
                        ? refines.substring(1)
                        : refines
        );
    }

    /**
     * 지정한 property와 일치하는지 확인합니다.
     *
     * @param expectedProperty property 값
     * @return 일치하면 {@code true}
     */
    public boolean hasProperty(String expectedProperty) {
        if (property == null
                || expectedProperty == null
                || expectedProperty.isBlank()) {
            return false;
        }

        return property.equalsIgnoreCase(
                expectedProperty.trim()
        );
    }

    /**
     * 지정한 Dublin Core 요소인지 확인합니다.
     *
     * @param expectedElementName 요소 이름
     * @return 일치하면 {@code true}
     */
    public boolean isElement(String expectedElementName) {
        if (expectedElementName == null
                || expectedElementName.isBlank()) {
            return false;
        }

        return elementName.equalsIgnoreCase(
                normalizeDcElementName(expectedElementName)
        );
    }

    public boolean isTitle() {
        return isDublinCore() && isElement("dc:title");
    }

    public boolean isCreator() {
        return isDublinCore() && isElement("dc:creator");
    }

    public boolean isIdentifier() {
        return isDublinCore() && isElement("dc:identifier");
    }

    public boolean isLanguage() {
        return isDublinCore() && isElement("dc:language");
    }

    public boolean isPublisher() {
        return isDublinCore() && isElement("dc:publisher");
    }

    public boolean isModifiedDate() {
        return isMetaProperty()
                && hasProperty("dcterms:modified");
    }

    /**
     * XML 요소에 출력할 전체 속성 맵을 반환합니다.
     *
     * <p>속성 순서는 다음 순서를 유지합니다.</p>
     *
     * <ol>
     *     <li>id</li>
     *     <li>refines</li>
     *     <li>property</li>
     *     <li>scheme</li>
     *     <li>xml:lang</li>
     *     <li>dir</li>
     *     <li>추가 속성</li>
     * </ol>
     *
     * @return 수정할 수 없는 XML 속성 맵
     */
    public Map<String, String> toXmlAttributes() {
        Map<String, String> result = new LinkedHashMap<>();

        if (id != null) {
            result.put("id", id);
        }

        if (refines != null) {
            result.put("refines", refines);
        }

        if (property != null) {
            result.put("property", property);
        }

        if (scheme != null) {
            result.put("scheme", scheme);
        }

        if (language != null) {
            result.put("xml:lang", language);
        }

        if (direction != null) {
            result.put("dir", direction.getXmlValue());
        }

        result.putAll(attributes);

        return Collections.unmodifiableMap(result);
    }

    /**
     * 현재 항목을 기반으로 builder를 생성합니다.
     *
     * @return 복사된 builder
     */
    public Builder toBuilder() {
        return new Builder()
                .kind(kind)
                .elementName(elementName)
                .value(value)
                .id(id)
                .property(property)
                .refines(refines)
                .scheme(scheme)
                .language(language)
                .direction(direction)
                .attributes(attributes);
    }

    /**
     * 지정한 EPUB 버전에서 사용할 수 있는지 확인합니다.
     *
     * @param version EPUB 버전
     * @return 사용할 수 있으면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        if (version == null) {
            return false;
        }

        if (kind == Kind.META_PROPERTY) {
            return version.isEpub3();
        }

        return true;
    }

    /**
     * EPUB 버전을 기준으로 메타데이터를 검증합니다.
     *
     * @param version EPUB 버전
     */
    public void validate(EpubVersion version) {
        Objects.requireNonNull(
                version,
                "EPUB version must not be null."
        );

        if (!isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB metadata entry is not supported by version "
                            + version
                            + ": "
                            + this
            );
        }

        if (version.isEpub2()
                && (refines != null
                        || property != null
                        || scheme != null
                        || direction != null)) {
            throw new IllegalStateException(
                    "EPUB 2 metadata does not support EPUB 3 "
                            + "metadata refinement attributes: "
                            + this
            );
        }
    }

    private void validate() {
        if (kind == Kind.DUBLIN_CORE) {
            if (!elementName.startsWith("dc:")) {
                throw new IllegalArgumentException(
                        "Dublin Core metadata element must use the "
                                + "dc prefix: "
                                + elementName
                );
            }

            if (property != null) {
                throw new IllegalArgumentException(
                        "Dublin Core metadata must not define a "
                                + "property attribute: "
                                + elementName
                );
            }

            if (refines != null) {
                throw new IllegalArgumentException(
                        "Dublin Core metadata must not define a "
                                + "refines attribute: "
                                + elementName
                );
            }

            if (scheme != null) {
                throw new IllegalArgumentException(
                        "Dublin Core metadata must not define a "
                                + "scheme attribute: "
                                + elementName
                );
            }
        }

        if (kind == Kind.META_PROPERTY) {
            if (!"meta".equals(elementName)) {
                throw new IllegalArgumentException(
                        "EPUB property metadata element must be meta."
                );
            }

            if (property == null) {
                throw new IllegalArgumentException(
                        "EPUB meta element requires a property value."
                );
            }

            if (scheme != null && refines == null) {
                throw new IllegalArgumentException(
                        "EPUB metadata scheme should be used with "
                                + "a refinement target: "
                                + property
                );
            }
        }

        if (refines != null && id != null) {
            String targetId = refines.substring(1);

            if (id.equals(targetId)) {
                throw new IllegalArgumentException(
                        "EPUB metadata entry must not refine itself: "
                                + id
                );
            }
        }

        for (String reserved : new String[] {
                "id",
                "refines",
                "property",
                "scheme",
                "xml:lang",
                "dir"
        }) {
            if (attributes.containsKey(reserved)) {
                throw new IllegalArgumentException(
                        "Reserved EPUB metadata attribute must use "
                                + "its dedicated builder method: "
                                + reserved
                );
            }
        }
    }

    private static String resolveElementName(
            Kind kind,
            String elementName
    ) {
        if (kind == Kind.META_PROPERTY) {
            return "meta";
        }

        if (elementName == null || elementName.isBlank()) {
            throw new IllegalArgumentException(
                    "Dublin Core element name must not be blank."
            );
        }

        return normalizeDcElementName(elementName);
    }

    private static String normalizeDcElementName(String value) {
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT);

        if (!normalized.contains(":")) {
            normalized = "dc:" + normalized;
        }

        validateQualifiedName(normalized);

        return normalized;
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB metadata value must not be blank."
            );
        }

        return value.trim();
    }

    private static String normalizeOptionalIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB metadata identifier: " + value
            );
        }

        return normalized;
    }

    private static String normalizeOptionalToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB metadata token must not contain whitespace: "
                            + value
            );
        }

        validateQualifiedName(normalized);

        return normalized;
    }

    private static String normalizeRefines(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }

        String targetId = normalized.substring(1);

        if (!isValidIdentifier(targetId)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB metadata refines target: " + value
            );
        }

        return normalized;
    }

    private static String normalizeLanguageTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .replace('_', '-');

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB metadata language tag: " + value
            );
        }

        return normalized;
    }

    private static Map<String, String> immutableAttributes(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = normalizeAttributeName(entry.getKey());
            String value = normalizeAttributeValue(
                    entry.getValue(),
                    name
            );

            if (result.put(name, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate EPUB metadata attribute: " + name
                );
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private static String normalizeAttributeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB metadata attribute name must not be blank."
            );
        }

        String normalized = value.trim();

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB metadata attribute name: " + value
            );
        }

        validateQualifiedName(normalized);

        return normalized;
    }

    private static String normalizeAttributeValue(
            String value,
            String attributeName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB metadata attribute value must not be blank: "
                            + attributeName
            );
        }

        return value.trim();
    }

    private static void validateQualifiedName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "XML qualified name must not be blank."
            );
        }

        int colonCount = 0;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            if (character == ':') {
                colonCount++;

                if (index == 0 || index == value.length() - 1) {
                    throw new IllegalArgumentException(
                            "Invalid XML qualified name: " + value
                    );
                }

                continue;
            }

            if (index == 0
                    || value.charAt(index - 1) == ':') {
                if (!(Character.isLetter(character)
                        || character == '_')) {
                    throw new IllegalArgumentException(
                            "Invalid XML qualified name: " + value
                    );
                }
            } else if (!(Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == '.')) {
                throw new IllegalArgumentException(
                        "Invalid XML qualified name: " + value
                );
            }
        }

        if (colonCount > 1) {
            throw new IllegalArgumentException(
                    "Invalid XML qualified name: " + value
            );
        }
    }

    private static boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        char first = value.charAt(0);

        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }

        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);

            if (!(Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == '.')) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubMetadataEntry other)) {
            return false;
        }

        if (id != null && other.id != null) {
            return id.equals(other.id);
        }

        return kind == other.kind
                && elementName.equals(other.elementName)
                && Objects.equals(property, other.property)
                && Objects.equals(refines, other.refines)
                && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }

        return Objects.hash(
                kind,
                elementName,
                property,
                refines,
                value
        );
    }

    @Override
    public String toString() {
        return "EpubMetadataEntry{"
                + "kind=" + kind
                + ", elementName='" + elementName + '\''
                + ", value='" + value + '\''
                + ", id='" + id + '\''
                + ", property='" + property + '\''
                + ", refines='" + refines + '\''
                + ", scheme='" + scheme + '\''
                + ", language='" + language + '\''
                + ", direction=" + direction
                + '}';
    }

    /**
     * EPUB 메타데이터 표현 형식입니다.
     */
    public enum Kind {

        /**
         * Dublin Core 요소입니다.
         *
         * <p>예: {@code dc:title}, {@code dc:creator}</p>
         */
        DUBLIN_CORE,

        /**
         * EPUB 3 meta property 요소입니다.
         */
        META_PROPERTY
    }

    /**
     * 메타데이터 텍스트 방향입니다.
     */
    public enum TextDirection {

        LEFT_TO_RIGHT("ltr"),

        RIGHT_TO_LEFT("rtl"),

        AUTO("auto");

        private final String xmlValue;

        TextDirection(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String getXmlValue() {
            return xmlValue;
        }

        public boolean isLeftToRight() {
            return this == LEFT_TO_RIGHT;
        }

        public boolean isRightToLeft() {
            return this == RIGHT_TO_LEFT;
        }

        public boolean isAuto() {
            return this == AUTO;
        }

        public static Optional<TextDirection> from(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }

            String normalized = value.trim()
                    .toLowerCase(Locale.ROOT);

            return switch (normalized) {
                case "ltr", "left-to-right", "left_to_right" ->
                        Optional.of(LEFT_TO_RIGHT);

                case "rtl", "right-to-left", "right_to_left" ->
                        Optional.of(RIGHT_TO_LEFT);

                case "auto", "automatic" ->
                        Optional.of(AUTO);

                default -> Optional.empty();
            };
        }

        public static TextDirection require(String value) {
            return from(value)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported EPUB metadata text direction: "
                                    + value
                    ));
        }

        @Override
        public String toString() {
            return xmlValue;
        }
    }

    /**
     * {@link EpubMetadataEntry} 생성 builder입니다.
     */
    public static final class Builder {

        private Kind kind = Kind.DUBLIN_CORE;

        private String elementName;

        private String value;

        private String id;

        private String property;

        private String refines;

        private String scheme;

        private String language;

        private TextDirection direction;

        private final Map<String, String> attributes =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder elementName(String elementName) {
            this.elementName = elementName;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder property(String property) {
            this.property = property;
            return this;
        }

        public Builder refines(String refines) {
            this.refines = refines;
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder direction(TextDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder direction(String direction) {
            this.direction = direction == null
                    ? null
                    : TextDirection.require(direction);

            return this;
        }

        public Builder attribute(
                String name,
                String value
        ) {
            attributes.put(name, value);
            return this;
        }

        public Builder attributes(
                Map<String, String> attributes
        ) {
            if (attributes == null) {
                return this;
            }

            for (Map.Entry<String, String> entry :
                    attributes.entrySet()) {
                attribute(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public Builder clearAttributes() {
            attributes.clear();
            return this;
        }

        public EpubMetadataEntry build() {
            return new EpubMetadataEntry(this);
        }
    }
}