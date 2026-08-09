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
 * EPUB 패키지의 manifest에 등록되는 개별 리소스를 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code manifest/item} 요소와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <item
 *     id="chapter01"
 *     href="Text/chapter01.xhtml"
 *     media-type="application/xhtml+xml"/>
 * }
 * </pre>
 *
 * <p>다음 속성을 관리합니다.</p>
 *
 * <ul>
 *     <li>manifest 식별자</li>
 *     <li>EPUB 내부 상대 경로</li>
 *     <li>리소스 유형과 MIME 타입</li>
 *     <li>manifest properties</li>
 *     <li>fallback 리소스</li>
 *     <li>media overlay 리소스</li>
 *     <li>원본 데이터 또는 외부 파일 경로</li>
 * </ul>
 *
 * <p>인스턴스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubResource {

    /**
     * OPF manifest item의 id 속성입니다.
     */
    private final String id;

    /**
     * 패키지 문서를 기준으로 한 EPUB 내부 상대 경로입니다.
     */
    private final String href;

    /**
     * EPUB 리소스 유형입니다.
     */
    private final EpubResourceType resourceType;

    /**
     * OPF manifest에 기록할 MIME 타입입니다.
     *
     * <p>일반적으로 {@link EpubResourceType#getMediaType()}과 같지만,
     * 사용자 정의 또는 세부 MIME 매개변수가 필요한 경우 별도 지정할 수
     * 있습니다.</p>
     */
    private final String mediaType;

    /**
     * OPF manifest item의 properties 속성값입니다.
     */
    private final Set<String> properties;

    /**
     * 지원되지 않는 리소스를 대신할 fallback manifest item id입니다.
     */
    private final String fallbackId;

    /**
     * Media Overlay SMIL manifest item id입니다.
     */
    private final String mediaOverlayId;

    /**
     * 원본 파일 시스템 경로입니다.
     *
     * <p>EPUB 생성 시 로컬 파일을 복사하는 경우 사용합니다.</p>
     */
    private final String sourcePath;

    /**
     * 메모리에 존재하는 리소스 데이터입니다.
     */
    private final byte[] content;

    /**
     * 리소스가 원격 리소스인지 여부입니다.
     */
    private final boolean remote;

    /**
     * 리소스를 EPUB 생성 결과에 포함할지 여부입니다.
     */
    private final boolean included;

    /**
     * 리소스 설명입니다.
     */
    private final String description;

    private EpubResource(Builder builder) {
        this.id = requireIdentifier(builder.id);
        this.href = normalizeHref(builder.href);
        this.resourceType = resolveResourceType(
                builder.resourceType,
                builder.mediaType,
                this.href
        );
        this.mediaType = resolveMediaType(
                builder.mediaType,
                this.resourceType
        );
        this.properties = immutableProperties(builder.properties);
        this.fallbackId = normalizeOptionalIdentifier(builder.fallbackId);
        this.mediaOverlayId =
                normalizeOptionalIdentifier(builder.mediaOverlayId);
        this.sourcePath = normalizeOptionalText(builder.sourcePath);
        this.content = copy(builder.content);
        this.remote = builder.remote || isRemoteHref(this.href);
        this.included = builder.included;
        this.description = normalizeOptionalText(builder.description);

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String id, String href) {
        return new Builder()
                .id(id)
                .href(href);
    }

    public static EpubResource of(
            String id,
            String href,
            EpubResourceType resourceType
    ) {
        return builder(id, href)
                .resourceType(resourceType)
                .build();
    }

    public static EpubResource fromFile(
            String id,
            String href,
            String sourcePath
    ) {
        return builder(id, href)
                .sourcePath(sourcePath)
                .build();
    }

    public static EpubResource fromContent(
            String id,
            String href,
            byte[] content
    ) {
        return builder(id, href)
                .content(content)
                .build();
    }

    public String getId() {
        return id;
    }

    public String getHref() {
        return href;
    }

    public EpubResourceType getResourceType() {
        return resourceType;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Set<String> getProperties() {
        return properties;
    }

    public Optional<String> getFallbackId() {
        return Optional.ofNullable(fallbackId);
    }

    public Optional<String> getMediaOverlayId() {
        return Optional.ofNullable(mediaOverlayId);
    }

    public Optional<String> getSourcePath() {
        return Optional.ofNullable(sourcePath);
    }

    /**
     * 리소스 데이터를 반환합니다.
     *
     * <p>내부 배열 보호를 위해 복사본을 반환합니다.</p>
     *
     * @return 리소스 데이터
     */
    public Optional<byte[]> getContent() {
        return content == null
                ? Optional.empty()
                : Optional.of(copy(content));
    }

    public boolean isRemote() {
        return remote;
    }

    public boolean isIncluded() {
        return included;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * manifest properties에 지정한 값이 있는지 확인합니다.
     *
     * @param property manifest property
     * @return 포함되어 있으면 {@code true}
     */
    public boolean hasProperty(String property) {
        String normalized = normalizeProperty(property);

        return normalized != null && properties.contains(normalized);
    }

    /**
     * EPUB 3 Navigation Document인지 확인합니다.
     *
     * @return {@code nav} 속성을 가진 XHTML이면 {@code true}
     */
    public boolean isNavigationDocument() {
        return resourceType.canBeNavigationDocument()
                && hasProperty("nav");
    }

    /**
     * 표지 이미지인지 확인합니다.
     *
     * @return {@code cover-image} 속성이 있으면 {@code true}
     */
    public boolean isCoverImage() {
        return resourceType.canBeCoverImage()
                && hasProperty("cover-image");
    }

    /**
     * 스크립트 콘텐츠가 포함된 리소스인지 확인합니다.
     *
     * @return {@code scripted} 속성이 있으면 {@code true}
     */
    public boolean isScripted() {
        return hasProperty("scripted");
    }

    /**
     * 수학 표현이 포함된 콘텐츠 문서인지 확인합니다.
     *
     * @return {@code mathml} 속성이 있으면 {@code true}
     */
    public boolean containsMathMl() {
        return hasProperty("mathml");
    }

    /**
     * SVG가 포함된 XHTML 콘텐츠인지 확인합니다.
     *
     * @return {@code svg} 속성이 있으면 {@code true}
     */
    public boolean containsSvg() {
        return hasProperty("svg");
    }

    /**
     * 원격 리소스 참조가 포함된 콘텐츠인지 확인합니다.
     *
     * @return {@code remote-resources} 속성이 있으면 {@code true}
     */
    public boolean containsRemoteResources() {
        return hasProperty("remote-resources");
    }

    /**
     * 리소스에 fallback이 지정되어 있는지 확인합니다.
     *
     * @return fallback id가 있으면 {@code true}
     */
    public boolean hasFallback() {
        return fallbackId != null;
    }

    /**
     * Media Overlay가 지정되어 있는지 확인합니다.
     *
     * @return media-overlay id가 있으면 {@code true}
     */
    public boolean hasMediaOverlay() {
        return mediaOverlayId != null;
    }

    /**
     * 메모리 기반 콘텐츠를 가지고 있는지 확인합니다.
     *
     * @return 콘텐츠 데이터가 있으면 {@code true}
     */
    public boolean hasContent() {
        return content != null;
    }

    /**
     * 로컬 원본 파일 경로를 가지고 있는지 확인합니다.
     *
     * @return 원본 경로가 있으면 {@code true}
     */
    public boolean hasSourcePath() {
        return sourcePath != null;
    }

    /**
     * EPUB 생성기가 복사하거나 기록할 수 있는 소스를 가지고 있는지
     * 확인합니다.
     *
     * @return 원본 파일 또는 메모리 데이터가 있으면 {@code true}
     */
    public boolean hasLocalSource() {
        return hasContent() || hasSourcePath();
    }

    /**
     * 리소스가 spine에 fallback 없이 직접 배치될 수 있는지 확인합니다.
     *
     * @return 직접 배치 가능하면 {@code true}
     */
    public boolean canBeDirectSpineItem() {
        return resourceType.isDirectSpineAllowed();
    }

    /**
     * spine에 배치하려면 fallback이 필요한지 확인합니다.
     *
     * @return fallback이 필요하면 {@code true}
     */
    public boolean requiresFallbackInSpine() {
        return resourceType.requiresFallbackInSpine()
                && !hasFallback();
    }

    /**
     * EPUB 버전에서 이 리소스를 사용할 수 있는지 확인합니다.
     *
     * @param version EPUB 버전
     * @return 지원되면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        return resourceType.isSupportedBy(version);
    }

    /**
     * OPF manifest item의 properties 문자열을 반환합니다.
     *
     * @return 공백으로 연결된 properties 값
     */
    public String getPropertiesValue() {
        return String.join(" ", properties);
    }

    /**
     * 파일명 부분을 반환합니다.
     *
     * @return 파일명
     */
    public String getFileName() {
        String value = removeQueryAndFragment(href);
        int slashIndex = value.lastIndexOf('/');

        return slashIndex >= 0
                ? value.substring(slashIndex + 1)
                : value;
    }

    /**
     * EPUB 내부 상위 디렉터리를 반환합니다.
     *
     * @return 상위 디렉터리 또는 빈 문자열
     */
    public String getParentPath() {
        String value = removeQueryAndFragment(href);
        int slashIndex = value.lastIndexOf('/');

        return slashIndex >= 0
                ? value.substring(0, slashIndex)
                : "";
    }

    /**
     * 파일 확장자를 반환합니다.
     *
     * @return 확장자
     */
    public Optional<String> getExtension() {
        String fileName = getFileName();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(
                fileName.substring(dotIndex + 1)
                        .toLowerCase(Locale.ROOT)
        );
    }

    /**
     * 리소스의 경로와 MIME 타입이 일치하는지 확인합니다.
     *
     * @return 일치하거나 판별할 수 없으면 {@code true}
     */
    public boolean hasConsistentMediaType() {
        Optional<EpubResourceType> pathType =
                EpubResourceType.fromFileName(href);

        if (pathType.isEmpty()
                || pathType.get() == EpubResourceType.UNKNOWN) {
            return true;
        }

        if (pathType.get() == resourceType) {
            return true;
        }

        return EpubResourceType.fromMediaType(mediaType)
                .map(type -> type == pathType.get())
                .orElse(false);
    }

    /**
     * 현재 리소스가 NCX 문서인지 확인합니다.
     *
     * @return NCX 리소스이면 true
     */
    public boolean isNcx() {

        if ("application/x-dtbncx+xml"
                .equalsIgnoreCase(
                        mediaType
                )) {

            return true;
        }

        if (resourceType
                == EpubResourceType.NCX) {

            return true;
        }

        if (href != null
                && href.toLowerCase(
                        java.util.Locale.ROOT
                )
                .endsWith(".ncx")) {

            return true;
        }

        return false;
    }
    
    
    /**
     * 현재 리소스를 기반으로 수정 가능한 빌더를 생성합니다.
     *
     * @return 복사된 빌더
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .href(href)
                .resourceType(resourceType)
                .mediaType(mediaType)
                .properties(properties)
                .fallbackId(fallbackId)
                .mediaOverlayId(mediaOverlayId)
                .sourcePath(sourcePath)
                .content(content)
                .remote(remote)
                .included(included)
                .description(description);
    }

    private void validate() {
        if (resourceType == EpubResourceType.UNKNOWN
                && (mediaType == null || mediaType.isBlank())) {
            throw new IllegalArgumentException(
                    "Unknown EPUB resource requires an explicit media type."
            );
        }

        if (fallbackId != null && fallbackId.equals(id)) {
            throw new IllegalArgumentException(
                    "EPUB resource fallback must not reference itself: " + id
            );
        }

        if (mediaOverlayId != null && mediaOverlayId.equals(id)) {
            throw new IllegalArgumentException(
                    "EPUB resource media overlay must not reference itself: "
                            + id
            );
        }

        if (hasProperty("nav")
                && !resourceType.canBeNavigationDocument()) {
            throw new IllegalArgumentException(
                    "The nav property requires an XHTML resource: " + id
            );
        }

        if (hasProperty("cover-image")
                && !resourceType.canBeCoverImage()) {
            throw new IllegalArgumentException(
                    "The cover-image property requires an image resource: "
                            + id
            );
        }

        if (mediaOverlayId != null
                && !resourceType.isDirectSpineAllowed()) {
            throw new IllegalArgumentException(
                    "Media overlay can only be associated with an "
                            + "XHTML or SVG content document: " + id
            );
        }

        if (remote && hasLocalSource()) {
            throw new IllegalArgumentException(
                    "Remote EPUB resource must not have local content "
                            + "or source path: " + id
            );
        }

        if (!remote && !included && hasContent()) {
            throw new IllegalArgumentException(
                    "Excluded EPUB resource must not contain embedded data: "
                            + id
            );
        }
    }

    private static EpubResourceType resolveResourceType(
            EpubResourceType resourceType,
            String mediaType,
            String href
    ) {
        if (resourceType != null) {
            return resourceType;
        }

        return EpubResourceType.resolve(href, mediaType);
    }

    private static String resolveMediaType(
            String mediaType,
            EpubResourceType resourceType
    ) {
        String normalized = normalizeOptionalText(mediaType);

        if (normalized != null) {
            return normalized;
        }

        return resourceType.getMediaType();
    }

    private static String requireIdentifier(String value) {
        String normalized = normalizeOptionalIdentifier(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "EPUB resource id must not be blank."
            );
        }

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB resource id: " + value
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

        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);

            if (!(Character.isLetterOrDigit(ch)
                    || ch == '_'
                    || ch == '-'
                    || ch == '.')) {
                return false;
            }
        }

        return true;
    }

    private static String normalizeHref(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB resource href must not be blank."
            );
        }

        String normalized = value.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB resource href must not be blank."
            );
        }

        if (!isRemoteHref(normalized)) {
            if (normalized.startsWith("/")) {
                throw new IllegalArgumentException(
                        "EPUB resource href must be relative: " + value
                );
            }

            if (containsParentTraversal(normalized)) {
                throw new IllegalArgumentException(
                        "EPUB resource href must not contain parent traversal: "
                                + value
                );
            }
        }

        return normalized;
    }

    private static boolean containsParentTraversal(String value) {
        String path = removeQueryAndFragment(value);

        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isRemoteHref(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT);

        return normalized.startsWith("http://")
                || normalized.startsWith("https://");
    }

    private static Set<String> immutableProperties(
            Collection<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalizedValues = new LinkedHashSet<>();

        for (String value : values) {
            String normalized = normalizeProperty(value);

            if (normalized != null) {
                normalizedValues.add(normalized);
            }
        }

        return Collections.unmodifiableSet(normalizedValues);
    }

    private static String normalizeProperty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT);

        if (normalized.indexOf(' ') >= 0
                || normalized.indexOf('\t') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "EPUB manifest property must be a single token: " + value
            );
        }

        return normalized;
    }

    private static String normalizeOptionalIdentifier(String value) {
        return normalizeOptionalText(value);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static String removeQueryAndFragment(String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        int endIndex = value.length();

        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }

        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }

        return value.substring(0, endIndex);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubResource other)) {
            return false;
        }

        /*
         * manifest id는 패키지 내부에서 고유하므로 리소스의 논리적
         * 동등성 기준으로 사용합니다.
         */
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EpubResource{"
                + "id='" + id + '\''
                + ", href='" + href + '\''
                + ", resourceType=" + resourceType
                + ", mediaType='" + mediaType + '\''
                + ", properties=" + properties
                + ", fallbackId='" + fallbackId + '\''
                + ", mediaOverlayId='" + mediaOverlayId + '\''
                + ", remote=" + remote
                + ", included=" + included
                + '}';
    }

    /**
     * {@link EpubResource} 생성 빌더입니다.
     */
    public static final class Builder {

        private String id;

        private String href;

        private EpubResourceType resourceType;

        private String mediaType;

        private final Set<String> properties = new LinkedHashSet<>();

        private String fallbackId;

        private String mediaOverlayId;

        private String sourcePath;

        private byte[] content;

        private boolean remote;

        private boolean included = true;

        private String description;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder href(String href) {
            this.href = href;
            return this;
        }

        public Builder resourceType(EpubResourceType resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
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
         * 공백으로 구분된 manifest properties 값을 추가합니다.
         *
         * @param propertiesValue properties 문자열
         * @return 현재 빌더
         */
        public Builder properties(String propertiesValue) {
            if (propertiesValue == null || propertiesValue.isBlank()) {
                return this;
            }

            for (String property : propertiesValue.trim().split("\\s+")) {
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

        public Builder navigationDocument(boolean navigationDocument) {
            return setProperty("nav", navigationDocument);
        }

        public Builder coverImage(boolean coverImage) {
            return setProperty("cover-image", coverImage);
        }

        public Builder scripted(boolean scripted) {
            return setProperty("scripted", scripted);
        }

        public Builder containsMathMl(boolean containsMathMl) {
            return setProperty("mathml", containsMathMl);
        }

        public Builder containsSvg(boolean containsSvg) {
            return setProperty("svg", containsSvg);
        }

        public Builder containsRemoteResources(
                boolean containsRemoteResources
        ) {
            return setProperty(
                    "remote-resources",
                    containsRemoteResources
            );
        }

        public Builder fallbackId(String fallbackId) {
            this.fallbackId = fallbackId;
            return this;
        }

        public Builder mediaOverlayId(String mediaOverlayId) {
            this.mediaOverlayId = mediaOverlayId;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder content(byte[] content) {
            this.content = copy(content);
            return this;
        }

        public Builder remote(boolean remote) {
            this.remote = remote;
            return this;
        }

        public Builder included(boolean included) {
            this.included = included;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public EpubResource build() {
            return new EpubResource(this);
        }

        private Builder setProperty(
                String property,
                boolean enabled
        ) {
            String normalized = normalizeProperty(property);

            if (enabled) {
                properties.add(normalized);
            } else {
                properties.remove(normalized);
            }

            return this;
        }
    }
}