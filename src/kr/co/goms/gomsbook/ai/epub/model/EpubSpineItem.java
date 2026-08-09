/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * EPUB spine의 개별 읽기 순서 항목을 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code spine/itemref} 요소와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <spine page-progression-direction="ltr">
 *     <itemref
 *         id="spine-cover"
 *         idref="cover"
 *         linear="no"
 *         properties="rendition:page-spread-center"/>
 *
 *     <itemref
 *         id="spine-chapter01"
 *         idref="chapter01"/>
 * </spine>
 * }
 * </pre>
 *
 * <p>{@code idref}는 반드시 manifest에 등록된
 * {@link EpubResource#getId()}를 참조해야 합니다.</p>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubSpineItem {

    /**
     * itemref 요소 자체의 선택적 ID입니다.
     */
    private final String id;

    /**
     * manifest 리소스를 참조하는 필수 ID입니다.
     */
    private final String idref;

    /**
     * 기본 읽기 순서에 포함되는지 여부입니다.
     *
     * <p>{@code true}이면 OPF의 기본값인 {@code linear="yes"}에
     * 해당하며 속성을 생략할 수 있습니다.</p>
     *
     * <p>{@code false}이면 {@code linear="no"}를 출력합니다.</p>
     */
    private final boolean linear;

    /**
     * itemref의 properties 속성값입니다.
     */
    private final Set<String> properties;

    /**
     * 개별 페이지의 펼침면 배치 위치입니다.
     */
    private final EpubPageSpread pageSpread;

    /**
     * 출판물 전역 설정을 덮어쓰는 개별 레이아웃 설정입니다.
     */
    private final EpubLayoutType layoutOverride;

    /**
     * 출판물 전역 설정을 덮어쓰는 개별 화면 방향 설정입니다.
     */
    private final EpubOrientation orientationOverride;

    /**
     * 출판물 전역 설정을 덮어쓰는 개별 펼침 정책입니다.
     */
    private final EpubSpread spreadOverride;

    /**
     * 애플리케이션 내부 표시 제목입니다.
     *
     * <p>OPF itemref 속성으로 직접 출력되지는 않습니다.</p>
     */
    private final String title;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubSpineItem(Builder builder) {
        this.id = normalizeOptionalIdentifier(builder.id);
        this.idref = requireIdentifier(builder.idref, "idref");
        this.linear = builder.linear;
        this.pageSpread = builder.pageSpread == null
                ? EpubPageSpread.defaultPageSpread()
                : builder.pageSpread;
        this.layoutOverride = builder.layoutOverride;
        this.orientationOverride = builder.orientationOverride;
        this.spreadOverride = builder.spreadOverride;
        this.title = normalizeOptionalText(builder.title);
        this.description = normalizeOptionalText(builder.description);
        this.properties = buildProperties(builder.properties);

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String idref) {
        return new Builder().idref(idref);
    }

    /**
     * 기본 선형 spine 항목을 생성합니다.
     *
     * @param idref manifest 리소스 ID
     * @return spine 항목
     */
    public static EpubSpineItem of(String idref) {
        return builder(idref).build();
    }

    /**
     * 비선형 spine 항목을 생성합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 비선형 spine 항목
     */
    public static EpubSpineItem nonLinear(String idref) {
        return builder(idref)
                .linear(false)
                .build();
    }

    public Optional<String> getId() {
        return Optional.ofNullable(id);
    }

    public String getIdref() {
        return idref;
    }

    public boolean isLinear() {
        return linear;
    }

    public boolean isNonLinear() {
        return !linear;
    }

    public Set<String> getProperties() {
        return properties;
    }

    public EpubPageSpread getPageSpread() {
        return pageSpread;
    }

    public Optional<EpubLayoutType> getLayoutOverride() {
        return Optional.ofNullable(layoutOverride);
    }

    public Optional<EpubOrientation> getOrientationOverride() {
        return Optional.ofNullable(orientationOverride);
    }

    public Optional<EpubSpread> getSpreadOverride() {
        return Optional.ofNullable(spreadOverride);
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * 지정한 itemref property가 존재하는지 확인합니다.
     *
     * @param property property 값
     * @return 포함되어 있으면 {@code true}
     */
    public boolean hasProperty(String property) {
        String normalized = normalizeProperty(property);

        return normalized != null && properties.contains(normalized);
    }

    /**
     * properties 속성을 OPF 출력용 문자열로 반환합니다.
     *
     * @return 공백으로 연결된 properties 문자열
     */
    public String getPropertiesValue() {
        return String.join(" ", properties);
    }

    /**
     * properties 속성을 출력해야 하는지 확인합니다.
     *
     * @return property가 하나 이상 있으면 {@code true}
     */
    public boolean shouldWriteProperties() {
        return !properties.isEmpty();
    }

    /**
     * linear 속성을 명시적으로 출력해야 하는지 확인합니다.
     *
     * <p>{@code linear="yes"}는 기본값이므로 비선형 항목에서만
     * 속성을 출력합니다.</p>
     *
     * @return 비선형이면 {@code true}
     */
    public boolean shouldWriteLinearAttribute() {
        return !linear;
    }

    /**
     * OPF linear 속성값을 반환합니다.
     *
     * @return {@code yes} 또는 {@code no}
     */
    public String getLinearValue() {
        return linear ? "yes" : "no";
    }

    /**
     * 개별 페이지 펼침 위치가 지정되었는지 확인합니다.
     *
     * @return 명시적 페이지 위치이면 {@code true}
     */
    public boolean hasPageSpread() {
        return pageSpread != null && !pageSpread.isAuto();
    }

    /**
     * 개별 렌디션 설정이 하나 이상 지정되었는지 확인합니다.
     *
     * @return override가 존재하면 {@code true}
     */
    public boolean hasRenditionOverride() {
        return layoutOverride != null
                || orientationOverride != null
                || spreadOverride != null;
    }

    /**
     * 고정형 레이아웃 override인지 확인합니다.
     *
     * @return 고정형 override이면 {@code true}
     */
    public boolean hasFixedLayoutOverride() {
        return layoutOverride != null
                && layoutOverride.isFixed();
    }

    /**
     * spine 항목이 중앙 단독 페이지인지 확인합니다.
     *
     * @return 중앙 페이지이면 {@code true}
     */
    public boolean isCenteredPage() {
        return pageSpread != null && pageSpread.isCenter();
    }

    /**
     * 연결된 manifest 리소스를 반환합니다.
     *
     * @param manifest EPUB manifest
     * @return 참조 대상 리소스
     * @throws IllegalArgumentException 참조 리소스가 없는 경우
     */
    public EpubResource resolveResource(EpubManifest manifest) {
        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        return manifest.requireById(idref);
    }

    /**
     * manifest 리소스 참조가 유효한지 확인합니다.
     *
     * @param manifest EPUB manifest
     * @return 참조 대상이 존재하면 {@code true}
     */
    public boolean referencesExistingResource(EpubManifest manifest) {
        return manifest != null && manifest.containsId(idref);
    }

    /**
     * 참조 리소스가 spine에 직접 배치 가능한지 확인합니다.
     *
     * @param manifest EPUB manifest
     * @return 직접 배치할 수 있으면 {@code true}
     */
    public boolean referencesDirectSpineResource(EpubManifest manifest) {
        if (manifest == null) {
            return false;
        }

        return manifest.findById(idref)
                .map(EpubResource::canBeDirectSpineItem)
                .orElse(false);
    }

    /**
     * spine 항목과 manifest의 참조 관계를 검증합니다.
     *
     * @param manifest EPUB manifest
     * @throws IllegalStateException 참조가 유효하지 않은 경우
     */
    public void validate(EpubManifest manifest) {
        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        EpubResource resource = manifest.findById(idref)
                .orElseThrow(() -> new IllegalStateException(
                        "EPUB spine item references a missing manifest "
                                + "resource: " + idref
                ));

        if (!resource.canBeDirectSpineItem()
                && !resource.hasFallback()) {
            throw new IllegalStateException(
                    "EPUB spine resource requires an XHTML or SVG "
                            + "fallback: "
                            + idref
                            + " ("
                            + resource.getResourceType()
                            + ")"
            );
        }

        if (resource.isNavigationDocument()) {
            throw new IllegalStateException(
                    "EPUB Navigation Document must not normally be "
                            + "included in the spine: "
                            + idref
            );
        }
    }

    /**
     * EPUB 버전을 기준으로 개별 rendition property를 검증합니다.
     *
     * @param version EPUB 버전
     * @throws IllegalStateException 지원되지 않는 설정인 경우
     */
    public void validate(EpubVersion version) {
        Objects.requireNonNull(
                version,
                "EPUB version must not be null."
        );

        if (version.isEpub2() && hasRenditionOverride()) {
            throw new IllegalStateException(
                    "EPUB 2 spine item does not support EPUB 3 "
                            + "rendition overrides: "
                            + idref
            );
        }

        if (!pageSpread.isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB page-spread property is not supported by "
                            + "version "
                            + version
                            + ": "
                            + idref
            );
        }

        if (layoutOverride != null
                && !layoutOverride.isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB layout override is not supported by version "
                            + version
                            + ": "
                            + idref
            );
        }

        if (orientationOverride != null
                && !orientationOverride.isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB orientation override is not supported by "
                            + "version "
                            + version
                            + ": "
                            + idref
            );
        }

        if (spreadOverride != null
                && !spreadOverride.isSupportedBy(version)) {
            throw new IllegalStateException(
                    "EPUB spread override is not supported by version "
                            + version
                            + ": "
                            + idref
            );
        }

        if (spreadOverride != null
                && spreadOverride.isDeprecated()) {
            throw new IllegalStateException(
                    "Deprecated EPUB spread override must not be used "
                            + "for new publications: "
                            + idref
                            + " -> "
                            + spreadOverride
            );
        }
    }

    /**
     * 현재 spine 항목을 기반으로 빌더를 생성합니다.
     *
     * @return 복사된 builder
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .idref(idref)
                .linear(linear)
                .properties(properties)
                .pageSpread(pageSpread)
                .layoutOverride(layoutOverride)
                .orientationOverride(orientationOverride)
                .spreadOverride(spreadOverride)
                .title(title)
                .description(description);
    }

    private Set<String> buildProperties(
            Collection<String> customProperties
    ) {
        Set<String> values = new LinkedHashSet<>();

        if (customProperties != null) {
            for (String property : customProperties) {
                String normalized = normalizeProperty(property);

                if (normalized != null) {
                    values.add(normalized);
                }
            }
        }

        removeManagedProperties(values);

        if (pageSpread != null && pageSpread.shouldWriteProperty()) {
            values.add(pageSpread.getRenditionProperty());
        }

        if (layoutOverride != null) {
            values.add(
                    "rendition:layout-"
                            + layoutOverride.getRenditionValue()
            );
        }

        if (orientationOverride != null) {
            values.add(
                    "rendition:orientation-"
                            + orientationOverride.getRenditionValue()
            );
        }

        if (spreadOverride != null) {
            values.add(
                    "rendition:spread-"
                            + spreadOverride.getRenditionValue()
            );
        }

        return Collections.unmodifiableSet(values);
    }

    private void validate() {
        if (id != null && id.equals(idref)) {
            throw new IllegalArgumentException(
                    "EPUB spine item id and idref must not be identical: "
                            + id
            );
        }

        if (layoutOverride != null
                && layoutOverride.isReflowable()
                && pageSpread != null
                && pageSpread.isSpreadSide()) {
            throw new IllegalArgumentException(
                    "Explicit left or right page-spread is not "
                            + "appropriate with a reflowable layout "
                            + "override: "
                            + idref
            );
        }

        if (spreadOverride != null
                && spreadOverride.isNone()
                && pageSpread != null
                && pageSpread.isSpreadSide()) {
            throw new IllegalArgumentException(
                    "Left or right page-spread cannot be combined with "
                            + "rendition:spread-none: "
                            + idref
            );
        }

        if (pageSpread != null
                && pageSpread.isCenter()
                && spreadOverride != null
                && spreadOverride.isBoth()) {
            throw new IllegalArgumentException(
                    "A centered page must not force a two-page spread: "
                            + idref
            );
        }
    }

    private static void removeManagedProperties(Set<String> properties) {
        properties.removeIf(property ->
                property.startsWith("rendition:page-spread-")
                        || property.startsWith("rendition:layout-")
                        || property.startsWith(
                                "rendition:orientation-"
                        )
                        || property.startsWith("rendition:spread-")
                        || property.equals("page-spread-left")
                        || property.equals("page-spread-right")
                        || property.equals("spread-none")
        );
    }

    private static String requireIdentifier(
            String value,
            String fieldName
    ) {
        String normalized = normalizeOptionalIdentifier(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "EPUB spine item " + fieldName + " must not be blank."
            );
        }

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB spine item "
                            + fieldName
                            + ": "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeOptionalIdentifier(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB identifier: " + value
            );
        }

        return normalized;
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

    private static String normalizeProperty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT);

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB spine property must be a single token: "
                            + value
            );
        }

        return normalized;
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
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubSpineItem other)) {
            return false;
        }

        return idref.equals(other.idref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idref);
    }

    @Override
    public String toString() {
        return "EpubSpineItem{"
                + "id='" + id + '\''
                + ", idref='" + idref + '\''
                + ", linear=" + linear
                + ", properties=" + properties
                + '}';
    }

    /**
     * {@link EpubSpineItem} 생성 빌더입니다.
     */
    public static final class Builder {

        private String id;

        private String idref;

        private boolean linear = true;

        private final Set<String> properties =
                new LinkedHashSet<>();

        private EpubPageSpread pageSpread =
                EpubPageSpread.defaultPageSpread();

        private EpubLayoutType layoutOverride;

        private EpubOrientation orientationOverride;

        private EpubSpread spreadOverride;

        private String title;

        private String description;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder idref(String idref) {
            this.idref = idref;
            return this;
        }

        public Builder linear(boolean linear) {
            this.linear = linear;
            return this;
        }

        public Builder nonLinear() {
            this.linear = false;
            return this;
        }

        public Builder property(String property) {
            String normalized = normalizeProperty(property);

            if (normalized != null) {
                properties.add(normalized);
            }

            return this;
        }

        /**
         * 공백으로 구분된 properties 문자열을 추가합니다.
         *
         * @param propertiesValue properties 문자열
         * @return 현재 builder
         */
        public Builder properties(String propertiesValue) {
            if (propertiesValue == null
                    || propertiesValue.isBlank()) {
                return this;
            }

            for (String property :
                    propertiesValue.trim().split("\\s+")) {
                property(property);
            }

            return this;
        }

        public Builder properties(Collection<String> properties) {
            if (properties == null) {
                return this;
            }

            for (String property : properties) {
                property(property);
            }

            return this;
        }

        public Builder clearProperties() {
            properties.clear();
            return this;
        }

        public Builder pageSpread(EpubPageSpread pageSpread) {
            this.pageSpread = pageSpread;
            return this;
        }

        public Builder layoutOverride(
                EpubLayoutType layoutOverride
        ) {
            this.layoutOverride = layoutOverride;
            return this;
        }

        public Builder orientationOverride(
                EpubOrientation orientationOverride
        ) {
            this.orientationOverride = orientationOverride;
            return this;
        }

        public Builder spreadOverride(
                EpubSpread spreadOverride
        ) {
            this.spreadOverride = spreadOverride;
            return this;
        }

        public Builder clearRenditionOverrides() {
            this.layoutOverride = null;
            this.orientationOverride = null;
            this.spreadOverride = null;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public EpubSpineItem build() {
            return new EpubSpineItem(this);
        }
    }
}