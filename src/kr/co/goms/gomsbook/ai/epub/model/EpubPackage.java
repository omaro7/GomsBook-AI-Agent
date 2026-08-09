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
 * EPUB OPF 패키지 문서 전체를 표현합니다.
 *
 * <p>패키지 문서의 루트 {@code package} 요소와 그 하위의
 * {@code metadata}, {@code manifest}, {@code spine}을 통합합니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <package
 *     xmlns="http://www.idpf.org/2007/opf"
 *     version="3.0"
 *     unique-identifier="book-id"
 *     xml:lang="ko"
 *     prefix="rendition: http://www.idpf.org/vocab/rendition/#">
 *
 *     <metadata ...>
 *         ...
 *     </metadata>
 *
 *     <manifest>
 *         ...
 *     </manifest>
 *
 *     <spine page-progression-direction="ltr">
 *         ...
 *     </spine>
 * </package>
 * }
 * </pre>
 *
 * <p>이 클래스는 다음 패키지 수준 정보를 관리합니다.</p>
 *
 * <ul>
 *     <li>EPUB 버전</li>
 *     <li>출판물 유형</li>
 *     <li>메타데이터</li>
 *     <li>manifest</li>
 *     <li>spine</li>
 *     <li>전역 렌디션 설정</li>
 *     <li>패키지 언어와 텍스트 방향</li>
 *     <li>EPUB vocabulary prefix</li>
 * </ul>
 */
public final class EpubPackage {

    public static final String OPF_NAMESPACE =
            "http://www.idpf.org/2007/opf";

    public static final String DC_NAMESPACE =
            "http://purl.org/dc/elements/1.1/";

    public static final String DCTERMS_NAMESPACE =
            "http://purl.org/dc/terms/";

    public static final String RENDITION_VOCABULARY =
            "http://www.idpf.org/vocab/rendition/#";

    public static final String MEDIA_OVERLAYS_VOCABULARY =
            "http://www.idpf.org/epub/vocab/overlays/#";

    public static final String SCHEMA_VOCABULARY =
            "http://schema.org/";

    /**
     * EPUB 표준 버전입니다.
     */
    private final EpubVersion version;

    /**
     * 애플리케이션 내부 출판물 유형입니다.
     */
    private final EpubPublicationType publicationType;

    /**
     * 패키지 메타데이터입니다.
     */
    private final EpubMetadata metadata;

    /**
     * 패키지 manifest입니다.
     */
    private final EpubManifest manifest;

    /**
     * 패키지 spine입니다.
     */
    private final EpubSpine spine;

    /**
     * 출판물 전역 레이아웃입니다.
     */
    private final EpubLayoutType layoutType;

    /**
     * 출판물 전역 화면 방향입니다.
     */
    private final EpubOrientation orientation;

    /**
     * 출판물 전역 합성 펼침면 정책입니다.
     */
    private final EpubSpread spread;

    /**
     * package 요소의 xml:lang 값입니다.
     */
    private final String language;

    /**
     * package 요소의 dir 값입니다.
     */
    private final TextDirection direction;

    /**
     * package 요소에 선언할 vocabulary prefix입니다.
     */
    private final Map<String, String> prefixes;

    /**
     * OPF 파일의 EPUB 컨테이너 내부 경로입니다.
     *
     * <p>예: {@code OEBPS/content.opf}</p>
     */
    private final String packageDocumentPath;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubPackage(Builder builder) {
        this.version = builder.version == null
                ? EpubVersion.defaultVersion()
                : builder.version;

        this.publicationType = builder.publicationType == null
                ? EpubPublicationType.defaultType()
                : builder.publicationType;

        this.metadata = Objects.requireNonNull(
                builder.metadata,
                "EPUB metadata must not be null."
        );

        this.manifest = Objects.requireNonNull(
                builder.manifest,
                "EPUB manifest must not be null."
        );

        this.spine = Objects.requireNonNull(
                builder.spine,
                "EPUB spine must not be null."
        );

        this.layoutType = resolveLayoutType(
                builder.layoutType,
                this.publicationType
        );

        this.orientation = resolveOrientation(
                builder.orientation,
                this.layoutType
        );

        this.spread = resolveSpread(
                builder.spread,
                this.layoutType,
                this.publicationType
        );

        this.language = resolveLanguage(
                builder.language,
                this.metadata
        );

        this.direction = builder.direction == null
                ? TextDirection.AUTO
                : builder.direction;

        this.prefixes = buildPrefixes(
                builder.prefixes,
                this.version,
                this.layoutType,
                this.orientation,
                this.spread
        );

        this.packageDocumentPath = normalizePackagePath(
                builder.packageDocumentPath
        );

        this.description = normalizeOptionalText(
                builder.description
        );

        synchronizeMetadata();
        synchronizeSpine();

        if (builder.validateOnBuild) {
            validate();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EpubPackage of(
            EpubMetadata metadata,
            EpubManifest manifest,
            EpubSpine spine
    ) {
        return builder()
                .metadata(metadata)
                .manifest(manifest)
                .spine(spine)
                .build();
    }

    public EpubVersion getVersion() {
        return version;
    }

    /**
     * package 요소의 version 속성값을 반환합니다.
     *
     * @return OPF package 버전
     */
    public String getPackageVersion() {
        return version.getPackageVersion();
    }

    public EpubPublicationType getPublicationType() {
        return publicationType;
    }

    public EpubMetadata getMetadata() {
        return metadata;
    }

    public EpubManifest getManifest() {
        return manifest;
    }

    public EpubSpine getSpine() {
        return spine;
    }

    public EpubLayoutType getLayoutType() {
        return layoutType;
    }

    public EpubOrientation getOrientation() {
        return orientation;
    }

    public EpubSpread getSpread() {
        return spread;
    }

    public String getLanguage() {
        return language;
    }

    public TextDirection getDirection() {
        return direction;
    }

    public Map<String, String> getPrefixes() {
        return prefixes;
    }

    public String getPackageDocumentPath() {
        return packageDocumentPath;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * package 요소의 unique-identifier 속성값을 반환합니다.
     *
     * @return dc:identifier 요소의 ID
     */
    public String getUniqueIdentifierId() {
        return metadata.getUniqueIdentifierId()
                .orElseThrow(() -> new IllegalStateException(
                        "EPUB package unique identifier is not configured."
                ));
    }

    /**
     * 출판물의 고유 식별자 값을 반환합니다.
     *
     * @return ISBN, UUID, DOI 등의 식별자 값
     */
    public String getUniqueIdentifierValue() {
        return metadata.getUniqueIdentifierValue()
                .orElseThrow(() -> new IllegalStateException(
                        "EPUB package unique identifier value is missing."
                ));
    }

    public Optional<String> getTitle() {
        return metadata.getPrimaryTitleValue();
    }

    public Optional<String> getCreator() {
        return metadata.getPrimaryCreatorValue();
    }

    public boolean isEpub2() {
        return version.isEpub2();
    }

    public boolean isEpub3() {
        return version.isEpub3();
    }

    public boolean isFixedLayout() {
        return layoutType.isFixed();
    }

    public boolean isReflowable() {
        return layoutType.isReflowable();
    }

    /**
     * Navigation Document를 반환합니다.
     *
     * @return EPUB 3 Navigation Document
     */
    public Optional<EpubResource> getNavigationDocument() {
        return manifest.getNavigationDocument();
    }

    /**
     * 표지 이미지를 반환합니다.
     *
     * @return 표지 이미지 리소스
     */
    public Optional<EpubResource> getCoverImage() {
        return manifest.getCoverImage();
    }

    /**
     * NCX 리소스를 반환합니다.
     *
     * @return EPUB 2 또는 호환용 NCX 리소스
     */
    public Optional<EpubResource> getNcxResource() {
        return manifest.getNcxResource();
    }

    /**
     * 지정한 vocabulary prefix가 선언되어 있는지 확인합니다.
     *
     * @param prefix prefix 이름
     * @return 선언되어 있으면 {@code true}
     */
    public boolean hasPrefix(String prefix) {
        String normalized = normalizePrefixName(prefix);

        return normalized != null
                && prefixes.containsKey(normalized);
    }

    /**
     * 지정한 vocabulary prefix의 URI를 반환합니다.
     *
     * @param prefix prefix 이름
     * @return vocabulary URI
     */
    public Optional<String> getPrefixUri(String prefix) {
        String normalized = normalizePrefixName(prefix);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(prefixes.get(normalized));
    }

    /**
     * package 요소에 출력할 prefix 속성값을 반환합니다.
     *
     * <pre>
     * {@code
     * rendition: http://www.idpf.org/vocab/rendition/#
     * schema: http://schema.org/
     * }
     * </pre>
     *
     * @return prefix 속성 문자열
     */
    public String getPrefixAttributeValue() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : prefixes.entrySet()) {
            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue());
        }

        return result.toString();
    }

    public boolean shouldWritePrefixAttribute() {
        return version.isEpub3() && !prefixes.isEmpty();
    }

    public boolean shouldWriteLanguageAttribute() {
        return language != null && !language.isBlank();
    }

    public boolean shouldWriteDirectionAttribute() {
        return version.isEpub3()
                && direction != null
                && !direction.isAuto();
    }

    /**
     * 전역 rendition:layout 메타데이터를 출력할지 확인합니다.
     *
     * <p>가변형 레이아웃은 EPUB 기본값이므로 생략할 수 있지만,
     * 생성 결과를 명확하게 하기 위해 현재 모델에서는 항상 출력할 수
     * 있도록 값을 제공합니다.</p>
     *
     * @return EPUB 3이면 {@code true}
     */
    public boolean shouldWriteLayoutMetadata() {
        return version.isEpub3();
    }

    public boolean shouldWriteOrientationMetadata() {
        return version.isEpub3()
                && orientation.shouldWriteMetadata();
    }

    public boolean shouldWriteSpreadMetadata() {
        return version.isEpub3()
                && spread.shouldWriteMetadata();
    }

    /**
     * 전역 렌디션 메타데이터를 반환합니다.
     *
     * <p>실제 OPF 생성기에서 {@code metadata} 요소 끝에 출력할 수
     * 있습니다.</p>
     *
     * @return property와 값으로 구성된 맵
     */
    public Map<String, String> getRenditionMetadata() {
        if (!version.isEpub3()) {
            return Collections.emptyMap();
        }

        Map<String, String> values = new LinkedHashMap<>();

        if (shouldWriteLayoutMetadata()) {
            values.put(
                    "rendition:layout",
                    layoutType.getRenditionValue()
            );
        }

        if (shouldWriteOrientationMetadata()) {
            values.put(
                    "rendition:orientation",
                    orientation.getRenditionValue()
            );
        }

        if (shouldWriteSpreadMetadata()) {
            values.put(
                    "rendition:spread",
                    spread.getRenditionValue()
            );
        }

        return Collections.unmodifiableMap(values);
    }

    /**
     * 패키지 전체 무결성을 검증합니다.
     */
    public void validate() {
        validateVersionCompatibility();
        validateMetadata();
        validateManifest();
        validateSpine();
        validateUniqueIdentifier();
        validatePackageLanguage();
        validateRendition();
        validateNavigationRequirements();
        validatePackageDocumentPath();
    }

    private void validateVersionCompatibility() {
        if (!layoutType.isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB layout type is not supported by version "
                            + version
                            + ": "
                            + layoutType
            );
        }

        if (!orientation.isSupportedBy(version)
                && !orientation.isAuto()) {
            throw new IllegalStateException(
                    "EPUB orientation is not supported by version "
                            + version
                            + ": "
                            + orientation
            );
        }

        if (!spread.isSupportedBy(version)
                && !spread.isAuto()) {
            throw new IllegalStateException(
                    "EPUB spread is not supported by version "
                            + version
                            + ": "
                            + spread
            );
        }
    }

    private void validateMetadata() {
        metadata.validate(version);
    }

    private void validateManifest() {
        manifest.validate(version);
    }

    private void validateSpine() {
        spine.validate(manifest, version);

        if (!spine.hasLinearItems()) {
            throw new IllegalStateException(
                    "EPUB spine requires at least one linear item."
            );
        }
    }

    private void validateUniqueIdentifier() {
        String identifierId = metadata.getUniqueIdentifierId()
                .orElseThrow(() -> new IllegalStateException(
                        "EPUB package requires a unique-identifier."
                ));

        EpubMetadataEntry identifier = metadata.findById(identifierId)
                .orElseThrow(() -> new IllegalStateException(
                        "EPUB package unique-identifier target "
                                + "does not exist: "
                                + identifierId
                ));

        if (!identifier.isIdentifier()) {
            throw new IllegalStateException(
                    "EPUB package unique-identifier must reference "
                            + "a dc:identifier element: "
                            + identifierId
            );
        }
    }

    private void validatePackageLanguage() {
        if (language == null || language.isBlank()) {
            throw new IllegalStateException(
                    "EPUB package language must not be blank."
            );
        }

        boolean metadataContainsLanguage =
                metadata.getLanguages()
                        .stream()
                        .map(EpubMetadataEntry::getValue)
                        .anyMatch(value ->
                                languageEquals(value, language)
                        );

        if (!metadataContainsLanguage) {
            throw new IllegalStateException(
                    "EPUB package xml:lang must correspond to "
                            + "a dc:language value: "
                            + language
            );
        }
    }

    private void validateRendition() {
        if (version.isEpub2()) {
            if (layoutType.isFixed()
                    || !orientation.isAuto()
                    || !spread.isAuto()) {
                throw new IllegalStateException(
                        "EPUB 2 does not support EPUB 3 rendition "
                                + "metadata."
                );
            }

            return;
        }

        if (spread.isDeprecated()) {
            throw new IllegalStateException(
                    "Deprecated EPUB spread value must not be used "
                            + "for a new package: "
                            + spread
            );
        }

        if (layoutType.isReflowable()
                && spread.isNone()) {
            /*
             * 규격상 사용할 수 있지만 일반적인 가변형 출판물에서는
             * 독서 시스템의 페이지 구성을 제한하므로 오류로 보지 않습니다.
             */
        }

        if (layoutType.isReflowable()
                && !orientation.isAuto()) {
            throw new IllegalStateException(
                    "A reflowable EPUB package should not force "
                            + "a portrait or landscape orientation: "
                            + orientation
            );
        }
    }

    private void validateNavigationRequirements() {
        if (version.isEpub3()) {
            EpubResource navigation = manifest
                    .getNavigationDocument()
                    .orElseThrow(() -> new IllegalStateException(
                            "EPUB 3 package requires exactly one "
                                    + "Navigation Document."
                    ));

            if (!navigation.getResourceType()
                    .canBeNavigationDocument()) {
                throw new IllegalStateException(
                        "EPUB Navigation Document must be XHTML: "
                                + navigation.getId()
                );
            }
        }

        if (version.isEpub2()) {
            EpubResource ncx = manifest.getNcxResource()
                    .orElseThrow(() -> new IllegalStateException(
                            "EPUB 2 package requires an NCX resource."
                    ));

            String spineTocId = spine.getTocId()
                    .orElseThrow(() -> new IllegalStateException(
                            "EPUB 2 spine requires a toc attribute."
                    ));

            if (!spineTocId.equals(ncx.getId())) {
                throw new IllegalStateException(
                        "EPUB spine toc reference does not match "
                                + "the NCX manifest resource: "
                                + spineTocId
                                + " != "
                                + ncx.getId()
                );
            }
        }
    }

    private void validatePackageDocumentPath() {
        if (!packageDocumentPath
                .toLowerCase(Locale.ROOT)
                .endsWith(".opf")) {
            throw new IllegalStateException(
                    "EPUB package document path must end with .opf: "
                            + packageDocumentPath
            );
        }
    }

    /**
     * 메타데이터와 패키지 설정을 동기화합니다.
     */
    private void synchronizeMetadata() {
        if (version.isEpub3()) {
            putSinglePropertyMetadata(
                    "rendition:layout",
                    layoutType.getRenditionValue()
            );

            if (orientation.shouldWriteMetadata()) {
                putSinglePropertyMetadata(
                        "rendition:orientation",
                        orientation.getRenditionValue()
                );
            } else {
                removePropertyMetadata("rendition:orientation");
            }

            if (spread.shouldWriteMetadata()) {
                putSinglePropertyMetadata(
                        "rendition:spread",
                        spread.getRenditionValue()
                );
            } else {
                removePropertyMetadata("rendition:spread");
            }
        }
    }

    private void synchronizeSpine() {
        if (spine.getPageProgressionDirection() == null) {
            spine.setPageProgressionDirection(
                    EpubPageProgressionDirection
                            .fromLanguageTag(language)
            );
        }
    }

    private void putSinglePropertyMetadata(
            String property,
            String value
    ) {
        removePropertyMetadata(property);

        metadata.add(
                EpubMetadataEntry.meta(property, value)
                        .build()
        );
    }

    private void removePropertyMetadata(String property) {
        for (EpubMetadataEntry entry :
                metadata.findByProperty(property)) {
            metadata.remove(entry);
        }
    }

    /**
     * 현재 패키지를 기반으로 Builder를 생성합니다.
     *
     * <p>metadata, manifest, spine은 가변 모델이므로 독립 복사본을
     * Builder에 전달합니다.</p>
     *
     * @return 복사된 Builder
     */
    public Builder toBuilder() {
        return new Builder()
                .version(version)
                .publicationType(publicationType)
                .metadata(metadata.copy())
                .manifest(manifest.copy())
                .spine(spine.copy())
                .layoutType(layoutType)
                .orientation(orientation)
                .spread(spread)
                .language(language)
                .direction(direction)
                .prefixes(prefixes)
                .packageDocumentPath(packageDocumentPath)
                .description(description);
    }

    /**
     * 현재 EPUB 패키지의 독립 복사본을 생성합니다.
     *
     * @return 복사된 EPUB 패키지
     */
    public EpubPackage copy() {
        return toBuilder().build();
    }

    private static EpubLayoutType resolveLayoutType(
            EpubLayoutType layoutType,
            EpubPublicationType publicationType
    ) {
        if (layoutType != null) {
            return layoutType;
        }

        return publicationType == null
                ? EpubLayoutType.defaultType()
                : publicationType.getDefaultLayoutType();
    }

    private static EpubOrientation resolveOrientation(
            EpubOrientation orientation,
            EpubLayoutType layoutType
    ) {
        if (orientation != null) {
            return orientation;
        }

        /*
         * 뷰포트 크기는 개별 XHTML 또는 생성 옵션에서 결정하므로
         * 패키지 수준 기본 방향은 AUTO로 유지합니다.
         */
        return EpubOrientation.defaultOrientation();
    }

    private static EpubSpread resolveSpread(
            EpubSpread spread,
            EpubLayoutType layoutType,
            EpubPublicationType publicationType
    ) {
        if (spread != null) {
            return spread;
        }

        return EpubSpread.resolve(
                layoutType,
                publicationType
        );
    }

    private static String resolveLanguage(
            String language,
            EpubMetadata metadata
    ) {
        String normalized = normalizeLanguageTag(language);

        if (normalized != null) {
            return normalized;
        }

        return metadata.getPrimaryLanguage()
                .map(EpubPackage::normalizeRequiredLanguageTag)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB package language is required."
                ));
    }

    private static Map<String, String> buildPrefixes(
            Map<String, String> customPrefixes,
            EpubVersion version,
            EpubLayoutType layoutType,
            EpubOrientation orientation,
            EpubSpread spread
    ) {
        if (version.isEpub2()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        if (customPrefixes != null) {
            for (Map.Entry<String, String> entry :
                    customPrefixes.entrySet()) {
                String prefix = requirePrefixName(entry.getKey());
                String uri = requireVocabularyUri(entry.getValue());

                result.put(prefix, uri);
            }
        }

        /*
         * rendition은 EPUB 예약 vocabulary이므로 prefix 선언을
         * 생략할 수 있지만, 명시적인 XML 출력을 위해 기본 등록합니다.
         */
        result.putIfAbsent(
                "rendition",
                RENDITION_VOCABULARY
        );

        return Collections.unmodifiableMap(result);
    }

    private static String normalizePackagePath(String value) {
        String normalized = value == null || value.isBlank()
                ? "OEBPS/content.opf"
                : value.trim().replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException(
                    "EPUB package document path must be relative: "
                            + value
            );
        }

        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "EPUB package document path must not contain "
                                + "parent traversal: "
                                + value
                );
            }
        }

        return normalized;
    }

    private static String normalizeLanguageTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return normalizeRequiredLanguageTag(value);
    }

    private static String normalizeRequiredLanguageTag(String value) {
        String normalized = value.trim()
                .replace('_', '-');

        if (normalized.isBlank()
                || containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB language tag: " + value
            );
        }

        return normalized;
    }

    private static boolean languageEquals(
            String first,
            String second
    ) {
        if (first == null || second == null) {
            return false;
        }

        return first.trim()
                .replace('_', '-')
                .equalsIgnoreCase(
                        second.trim().replace('_', '-')
                );
    }

    private static String normalizePrefixName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(":", "");
    }

    private static String requirePrefixName(String value) {
        String normalized = normalizePrefixName(value);

        if (normalized == null
                || !isValidXmlName(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB vocabulary prefix: " + value
            );
        }

        return normalized;
    }

    private static String requireVocabularyUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB vocabulary URI must not be blank."
            );
        }

        String normalized = value.trim();

        if (!(normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("urn:"))) {
            throw new IllegalArgumentException(
                    "Invalid EPUB vocabulary URI: " + value
            );
        }

        return normalized;
    }

    private static boolean isValidXmlName(String value) {
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

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubPackage{"
                + "version=" + version
                + ", publicationType=" + publicationType
                + ", title='" + getTitle().orElse(null) + '\''
                + ", uniqueIdentifier='"
                + metadata.getUniqueIdentifierValue().orElse(null)
                + '\''
                + ", resourceCount=" + manifest.size()
                + ", spineItemCount=" + spine.size()
                + ", layoutType=" + layoutType
                + ", orientation=" + orientation
                + ", spread=" + spread
                + ", language='" + language + '\''
                + ", packageDocumentPath='"
                + packageDocumentPath + '\''
                + '}';
    }

    /**
     * package 요소의 dir 속성값입니다.
     */
    public enum TextDirection {

        LEFT_TO_RIGHT("ltr"),

        RIGHT_TO_LEFT("rtl"),

        AUTO("auto");

        private final String opfValue;

        TextDirection(String opfValue) {
            this.opfValue = opfValue;
        }

        public String getOpfValue() {
            return opfValue;
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
                    .toLowerCase(Locale.ROOT)
                    .replace('_', '-');

            return switch (normalized) {
                case "ltr", "left-to-right" ->
                        Optional.of(LEFT_TO_RIGHT);

                case "rtl", "right-to-left" ->
                        Optional.of(RIGHT_TO_LEFT);

                case "auto", "automatic" ->
                        Optional.of(AUTO);

                default -> Optional.empty();
            };
        }

        public static TextDirection require(String value) {
            return from(value)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported EPUB package direction: "
                                    + value
                    ));
        }

        @Override
        public String toString() {
            return opfValue;
        }
    }

    /**
     * {@link EpubPackage} 생성 Builder입니다.
     */
    public static final class Builder {

        private EpubVersion version =
                EpubVersion.defaultVersion();

        private EpubPublicationType publicationType =
                EpubPublicationType.defaultType();

        private EpubMetadata metadata;

        private EpubManifest manifest;

        private EpubSpine spine;

        private EpubLayoutType layoutType;

        private EpubOrientation orientation;

        private EpubSpread spread;

        private String language;

        private TextDirection direction =
                TextDirection.AUTO;

        private final Map<String, String> prefixes =
                new LinkedHashMap<>();

        private String packageDocumentPath =
                "OEBPS/content.opf";

        private String description;

        private boolean validateOnBuild = true;

        private Builder() {
        }

        public Builder version(EpubVersion version) {
            this.version = version;
            return this;
        }

        public Builder publicationType(
                EpubPublicationType publicationType
        ) {
            this.publicationType = publicationType;
            return this;
        }

        public Builder metadata(EpubMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder manifest(EpubManifest manifest) {
            this.manifest = manifest;
            return this;
        }

        public Builder spine(EpubSpine spine) {
            this.spine = spine;
            return this;
        }

        public Builder layoutType(
                EpubLayoutType layoutType
        ) {
            this.layoutType = layoutType;
            return this;
        }

        public Builder orientation(
                EpubOrientation orientation
        ) {
            this.orientation = orientation;
            return this;
        }

        public Builder spread(EpubSpread spread) {
            this.spread = spread;
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
                    ? TextDirection.AUTO
                    : TextDirection.require(direction);

            return this;
        }

        public Builder prefix(
                String prefix,
                String vocabularyUri
        ) {
            prefixes.put(prefix, vocabularyUri);
            return this;
        }

        public Builder prefixes(
                Map<String, String> prefixes
        ) {
            if (prefixes == null) {
                return this;
            }

            this.prefixes.putAll(prefixes);
            return this;
        }

        public Builder schemaVocabulary() {
            return prefix("schema", SCHEMA_VOCABULARY);
        }

        public Builder mediaOverlaysVocabulary() {
            return prefix(
                    "media",
                    MEDIA_OVERLAYS_VOCABULARY
            );
        }

        public Builder packageDocumentPath(
                String packageDocumentPath
        ) {
            this.packageDocumentPath = packageDocumentPath;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder validateOnBuild(
                boolean validateOnBuild
        ) {
            this.validateOnBuild = validateOnBuild;
            return this;
        }

        public EpubPackage build() {
            return new EpubPackage(this);
        }
    }
}