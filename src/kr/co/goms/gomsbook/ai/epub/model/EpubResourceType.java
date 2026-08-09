/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * EPUB 패키지에 포함되는 리소스 유형을 정의합니다.
 *
 * <p>각 리소스 유형은 OPF 패키지 문서의 {@code manifest/item}
 * 요소에 지정되는 {@code media-type} 속성과 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <manifest>
 *     <item
 *         id="chapter01"
 *         href="Text/chapter01.xhtml"
 *         media-type="application/xhtml+xml"/>
 * </manifest>
 * }
 * </pre>
 *
 * <p>이 열거형은 다음 정보를 제공합니다.</p>
 *
 * <ul>
 *     <li>권장 MIME 미디어 타입</li>
 *     <li>지원 파일 확장자</li>
 *     <li>EPUB 핵심 미디어 타입 여부</li>
 *     <li>spine 직접 배치 가능 여부</li>
 *     <li>텍스트 또는 바이너리 리소스 여부</li>
 *     <li>fallback 필요 여부</li>
 * </ul>
 */
public enum EpubResourceType {

    /**
     * EPUB XHTML 콘텐츠 문서입니다.
     */
    XHTML(
            "application/xhtml+xml",
            Category.DOCUMENT,
            true,
            true,
            true,
            "xhtml", "html", "htm"
    ),

    /**
     * SVG 콘텐츠 또는 이미지 문서입니다.
     */
    SVG(
            "image/svg+xml",
            Category.IMAGE,
            true,
            true,
            true,
            "svg"
    ),

    /**
     * CSS 스타일시트입니다.
     */
    CSS(
            "text/css",
            Category.STYLE,
            true,
            false,
            true,
            "css"
    ),

    /**
     * JavaScript 리소스입니다.
     */
    JAVASCRIPT(
            "application/javascript",
            Category.SCRIPT,
            true,
            false,
            true,
            "js", "mjs"
    ),

    /**
     * PNG 이미지입니다.
     */
    PNG(
            "image/png",
            Category.IMAGE,
            true,
            false,
            false,
            "png"
    ),

    /**
     * JPEG 이미지입니다.
     */
    JPEG(
            "image/jpeg",
            Category.IMAGE,
            true,
            false,
            false,
            "jpg", "jpeg", "jpe"
    ),

    /**
     * GIF 이미지입니다.
     */
    GIF(
            "image/gif",
            Category.IMAGE,
            true,
            false,
            false,
            "gif"
    ),

    /**
     * WebP 이미지입니다.
     */
    WEBP(
            "image/webp",
            Category.IMAGE,
            true,
            false,
            false,
            "webp"
    ),

    /**
     * AVIF 이미지입니다.
     *
     * <p>EPUB 3.3 핵심 미디어 타입은 아니므로 콘텐츠에서 사용할 경우
     * 호환 가능한 fallback 리소스를 제공하는 것이 적절합니다.</p>
     */
    AVIF(
            "image/avif",
            Category.IMAGE,
            false,
            false,
            false,
            "avif"
    ),

    /**
     * MP3 오디오입니다.
     */
    MP3(
            "audio/mpeg",
            Category.AUDIO,
            true,
            false,
            false,
            "mp3"
    ),

    /**
     * MP4 컨테이너의 AAC 오디오입니다.
     */
    AAC_MP4(
            "audio/mp4",
            Category.AUDIO,
            true,
            false,
            false,
            "m4a", "mp4"
    ),

    /**
     * Ogg 컨테이너의 Opus 오디오입니다.
     */
    OPUS(
            "audio/ogg; codecs=opus",
            Category.AUDIO,
            true,
            false,
            false,
            "opus", "ogg", "oga"
    ),

    /**
     * WAV 오디오입니다.
     *
     * <p>EPUB 3.3 핵심 미디어 타입은 아닙니다.</p>
     */
    WAV(
            "audio/wav",
            Category.AUDIO,
            false,
            false,
            false,
            "wav"
    ),

    /**
     * MP4 비디오입니다.
     *
     * <p>EPUB 3.3에서 비디오는 핵심 미디어 타입이 아니라
     * fallback 의무가 면제되는 exempt resource로 처리됩니다.</p>
     */
    MP4_VIDEO(
            "video/mp4",
            Category.VIDEO,
            false,
            false,
            false,
            "mp4", "m4v"
    ),

    /**
     * WebM 비디오입니다.
     */
    WEBM_VIDEO(
            "video/webm",
            Category.VIDEO,
            false,
            false,
            false,
            "webm"
    ),

    /**
     * TrueType 글꼴입니다.
     */
    TTF(
            "font/ttf",
            Category.FONT,
            true,
            false,
            false,
            "ttf"
    ),

    /**
     * OpenType 글꼴입니다.
     */
    OTF(
            "font/otf",
            Category.FONT,
            true,
            false,
            false,
            "otf"
    ),

    /**
     * WOFF 글꼴입니다.
     */
    WOFF(
            "font/woff",
            Category.FONT,
            true,
            false,
            false,
            "woff"
    ),

    /**
     * WOFF2 글꼴입니다.
     */
    WOFF2(
            "font/woff2",
            Category.FONT,
            true,
            false,
            false,
            "woff2"
    ),

    /**
     * EPUB Media Overlay 문서입니다.
     */
    SMIL(
            "application/smil+xml",
            Category.MEDIA_OVERLAY,
            true,
            false,
            true,
            "smil"
    ),

    /**
     * EPUB 2 호환용 NCX 탐색 문서입니다.
     */
    NCX(
            "application/x-dtbncx+xml",
            Category.NAVIGATION,
            true,
            false,
            true,
            "ncx"
    ),

    /**
     * XML 리소스입니다.
     *
     * <p>특정 EPUB 핵심 문서 형식이 아닌 일반 XML 리소스입니다.</p>
     */
    XML(
            "application/xml",
            Category.DATA,
            false,
            false,
            true,
            "xml"
    ),

    /**
     * JSON 데이터 리소스입니다.
     */
    JSON(
            "application/json",
            Category.DATA,
            false,
            false,
            true,
            "json"
    ),

    /**
     * WebVTT 자막 또는 설명 트랙입니다.
     */
    WEBVTT(
            "text/vtt",
            Category.TRACK,
            false,
            false,
            true,
            "vtt"
    ),

    /**
     * 일반 텍스트 리소스입니다.
     */
    TEXT(
            "text/plain",
            Category.DATA,
            false,
            false,
            true,
            "txt"
    ),

    /**
     * PDF 문서입니다.
     *
     * <p>EPUB 핵심 미디어 타입이 아니므로 콘텐츠 문서로 직접 사용할 경우
     * XHTML 또는 SVG fallback이 필요합니다.</p>
     */
    PDF(
            "application/pdf",
            Category.DOCUMENT,
            false,
            false,
            false,
            "pdf"
    ),

    /**
     * 알려지지 않았거나 명시적으로 지원하지 않는 리소스입니다.
     */
    UNKNOWN(
            "application/octet-stream",
            Category.UNKNOWN,
            false,
            false,
            false
    );

    /**
     * OPF manifest item의 media-type 속성값입니다.
     */
    private final String mediaType;

    /**
     * 리소스의 논리적 분류입니다.
     */
    private final Category category;

    /**
     * EPUB 3.3 핵심 미디어 타입 여부입니다.
     */
    private final boolean coreMediaType;

    /**
     * manifest fallback 없이 spine에 직접 배치할 수 있는지 여부입니다.
     */
    private final boolean directSpineAllowed;

    /**
     * 일반적인 텍스트 리소스인지 여부입니다.
     */
    private final boolean textual;

    /**
     * 지원 파일 확장자입니다.
     */
    private final Set<String> extensions;

    EpubResourceType(
            String mediaType,
            Category category,
            boolean coreMediaType,
            boolean directSpineAllowed,
            boolean textual,
            String... extensions
    ) {
        this.mediaType = mediaType;
        this.category = category;
        this.coreMediaType = coreMediaType;
        this.directSpineAllowed = directSpineAllowed;
        this.textual = textual;
        this.extensions = createExtensions(extensions);
    }

    /**
     * OPF manifest에 기록할 MIME 미디어 타입을 반환합니다.
     *
     * @return MIME 미디어 타입
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * 리소스의 논리적 분류를 반환합니다.
     *
     * @return 리소스 분류
     */
    public Category getCategory() {
        return category;
    }

    /**
     * 지원 파일 확장자를 반환합니다.
     *
     * @return 수정할 수 없는 파일 확장자 집합
     */
    public Set<String> getExtensions() {
        return extensions;
    }

    /**
     * 대표 파일 확장자를 반환합니다.
     *
     * @return 대표 확장자
     */
    public Optional<String> getPrimaryExtension() {
        return extensions.stream().findFirst();
    }

    /**
     * EPUB 핵심 미디어 타입인지 확인합니다.
     *
     * @return 핵심 미디어 타입이면 {@code true}
     */
    public boolean isCoreMediaType() {
        return coreMediaType;
    }

    /**
     * manifest fallback 없이 spine에 직접 배치할 수 있는지 확인합니다.
     *
     * <p>EPUB 3에서는 XHTML과 SVG 콘텐츠 문서만 fallback 없이
     * spine에 직접 배치할 수 있습니다.</p>
     *
     * @return 직접 배치할 수 있으면 {@code true}
     */
    public boolean isDirectSpineAllowed() {
        return directSpineAllowed;
    }

    /**
     * spine에 포함할 수 있는 문서 유형인지 확인합니다.
     *
     * <p>직접 배치가 불가능한 유형도 fallback이 있으면 spine에
     * 포함할 수 있으므로, 이 메서드는 콘텐츠 문서 가능성만 판단합니다.</p>
     *
     * @return 문서 또는 이미지 리소스이면 {@code true}
     */
    public boolean canAppearInSpine() {
        return category == Category.DOCUMENT
                || category == Category.IMAGE;
    }

    /**
     * spine에 배치할 때 fallback이 필요한지 확인합니다.
     *
     * @return fallback이 필요하면 {@code true}
     */
    public boolean requiresFallbackInSpine() {
        return canAppearInSpine() && !directSpineAllowed;
    }

    /**
     * 일반 텍스트 기반 리소스인지 확인합니다.
     *
     * @return 텍스트 리소스이면 {@code true}
     */
    public boolean isTextual() {
        return textual;
    }

    /**
     * 바이너리 리소스인지 확인합니다.
     *
     * @return 바이너리 리소스이면 {@code true}
     */
    public boolean isBinary() {
        return !textual;
    }

    public boolean isDocument() {
        return category == Category.DOCUMENT;
    }

    public boolean isImage() {
        return category == Category.IMAGE;
    }

    public boolean isStyle() {
        return category == Category.STYLE;
    }

    public boolean isScript() {
        return category == Category.SCRIPT;
    }

    public boolean isFont() {
        return category == Category.FONT;
    }

    public boolean isAudio() {
        return category == Category.AUDIO;
    }

    public boolean isVideo() {
        return category == Category.VIDEO;
    }

    public boolean isMediaOverlay() {
        return category == Category.MEDIA_OVERLAY;
    }

    public boolean isNavigation() {
        return category == Category.NAVIGATION;
    }

    public boolean isTrack() {
        return category == Category.TRACK;
    }

    public boolean isData() {
        return category == Category.DATA;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * EPUB 버전에서 사용할 수 있는지 확인합니다.
     *
     * <p>NCX는 EPUB 2에서 필수 탐색 문서이며 EPUB 3에서는
     * 하위 호환 목적으로 사용할 수 있습니다.</p>
     *
     * <p>SMIL Media Overlay는 EPUB 3 계열에서만 지원합니다.</p>
     *
     * @param version EPUB 버전
     * @return 사용할 수 있으면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        if (version == null) {
            return false;
        }

        if (this == SMIL) {
            return version.isEpub3();
        }

        if (this == WEBP || this == WOFF2 || this == OPUS) {
            return version.isEpub3();
        }

        return true;
    }

    /**
     * EPUB 3 Navigation Document로 사용할 수 있는지 확인합니다.
     *
     * @return XHTML이면 {@code true}
     */
    public boolean canBeNavigationDocument() {
        return this == XHTML;
    }

    /**
     * EPUB 표지 이미지로 사용할 수 있는 유형인지 확인합니다.
     *
     * @return 일반적인 이미지 유형이면 {@code true}
     */
    public boolean canBeCoverImage() {
        return switch (this) {
            case PNG, JPEG, GIF, SVG, WEBP, AVIF -> true;
            default -> false;
        };
    }

    /**
     * 접근성 대체 텍스트가 필요한 시각 리소스인지 확인합니다.
     *
     * @return 이미지 리소스이면 {@code true}
     */
    public boolean requiresAlternativeText() {
        return isImage();
    }

    /**
     * 지정한 파일 확장자를 지원하는지 확인합니다.
     *
     * @param extension 파일 확장자
     * @return 지원하면 {@code true}
     */
    public boolean supportsExtension(String extension) {
        String normalized = normalizeExtension(extension);

        return normalized != null && extensions.contains(normalized);
    }

    /**
     * MIME 미디어 타입으로 리소스 유형을 검색합니다.
     *
     * @param mediaType MIME 미디어 타입
     * @return 일치하는 리소스 유형
     */
    public static Optional<EpubResourceType> fromMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalizeMediaType(mediaType);

        return Arrays.stream(values())
                .filter(type -> mediaTypeMatches(type, normalized))
                .findFirst();
    }

    /**
     * 파일 확장자로 리소스 유형을 검색합니다.
     *
     * @param extension 파일 확장자
     * @return 일치하는 리소스 유형
     */
    public static Optional<EpubResourceType> fromExtension(String extension) {
        String normalized = normalizeExtension(extension);

        if (normalized == null) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(normalized))
                .findFirst();
    }

    /**
     * 파일명 또는 경로로 리소스 유형을 검색합니다.
     *
     * @param fileName 파일명 또는 경로
     * @return 일치하는 리소스 유형
     */
    public static Optional<EpubResourceType> fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }

        String normalized = removeQueryAndFragment(fileName.trim());
        int slashIndex = Math.max(
                normalized.lastIndexOf('/'),
                normalized.lastIndexOf('\\')
        );
        int dotIndex = normalized.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex < slashIndex) {
            return Optional.empty();
        }

        return fromExtension(normalized.substring(dotIndex + 1));
    }

    /**
     * MIME 타입, 열거형 이름, 확장자 또는 파일명으로 검색합니다.
     *
     * @param value 리소스 유형 문자열
     * @return 일치하는 리소스 유형
     */
    public static Optional<EpubResourceType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String enumName = trimmed.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        Optional<EpubResourceType> enumResult = Arrays.stream(values())
                .filter(type -> type.name().equals(enumName))
                .findFirst();

        if (enumResult.isPresent()) {
            return enumResult;
        }

        Optional<EpubResourceType> mediaTypeResult =
                fromMediaType(trimmed);

        if (mediaTypeResult.isPresent()) {
            return mediaTypeResult;
        }

        Optional<EpubResourceType> extensionResult =
                fromExtension(trimmed);

        if (extensionResult.isPresent()) {
            return extensionResult;
        }

        return fromFileName(trimmed);
    }

    /**
     * 문자열을 리소스 유형으로 변환합니다.
     *
     * @param value 리소스 유형 문자열
     * @return EPUB 리소스 유형
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubResourceType require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB resource type: " + value
                ));
    }

    /**
     * 파일명과 MIME 타입을 기준으로 리소스 유형을 결정합니다.
     *
     * <p>MIME 타입이 유효하면 MIME 타입을 우선하고, 찾지 못하면
     * 파일 확장자를 사용합니다.</p>
     *
     * @param fileName  파일명 또는 경로
     * @param mediaType MIME 미디어 타입
     * @return 결정된 리소스 유형
     */
    public static EpubResourceType resolve(
            String fileName,
            String mediaType
    ) {
        return fromMediaType(mediaType)
                .or(() -> fromFileName(fileName))
                .orElse(UNKNOWN);
    }

    /**
     * 기본 리소스 유형을 반환합니다.
     *
     * @return 알 수 없는 리소스 유형
     */
    public static EpubResourceType defaultType() {
        return UNKNOWN;
    }

    private static boolean mediaTypeMatches(
            EpubResourceType type,
            String normalizedMediaType
    ) {
        String candidate = normalizeMediaType(type.mediaType);

        if (candidate.equals(normalizedMediaType)) {
            return true;
        }

        /*
         * Opus는 codecs 매개변수를 포함하므로 단순 base type 비교로
         * 다른 Ogg 리소스와 혼동하지 않습니다.
         */
        if (type == OPUS) {
            return normalizedMediaType.startsWith("audio/ogg")
                    && normalizedMediaType.contains("codecs=opus");
        }

        /*
         * 이전 EPUB 또는 기존 프로젝트에서 사용한 호환 MIME 타입입니다.
         */
        return switch (type) {
            case JAVASCRIPT ->
                    normalizedMediaType.equals("text/javascript")
                            || normalizedMediaType.equals(
                                    "application/ecmascript"
                            );

            case TTF ->
                    normalizedMediaType.equals("application/font-sfnt");

            case OTF ->
                    normalizedMediaType.equals("application/font-sfnt")
                            || normalizedMediaType.equals(
                                    "application/vnd.ms-opentype"
                            );

            case WOFF ->
                    normalizedMediaType.equals("application/font-woff");

            case XML ->
                    normalizedMediaType.equals("text/xml");

            case WAV ->
                    normalizedMediaType.equals("audio/x-wav");

            default -> false;
        };
    }

    private static Set<String> createExtensions(String... extensions) {
        if (extensions == null || extensions.length == 0) {
            return Collections.emptySet();
        }

        Set<String> values = new LinkedHashSet<>();

        for (String extension : extensions) {
            String normalized = normalizeExtension(extension);

            if (normalized != null) {
                values.add(normalized);
            }
        }

        return Collections.unmodifiableSet(values);
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }

        String normalized = extension.trim()
                .toLowerCase(Locale.ROOT);

        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isBlank()
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("?")
                || normalized.contains("#")) {
            return null;
        }

        return normalized;
    }

    private static String normalizeMediaType(String mediaType) {
        return mediaType.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s*=\\s*", "=")
                .replaceAll("\\s*;\\s*", ";");
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
    public String toString() {
        return mediaType;
    }

    /**
     * EPUB 리소스의 논리적 분류입니다.
     */
    public enum Category {

        DOCUMENT,
        NAVIGATION,
        STYLE,
        SCRIPT,
        IMAGE,
        FONT,
        AUDIO,
        VIDEO,
        MEDIA_OVERLAY,
        TRACK,
        DATA,
        UNKNOWN
    }
}