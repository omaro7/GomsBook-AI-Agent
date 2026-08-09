/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 출판물의 합성 펼침면 정책을 정의합니다.
 *
 * <p>EPUB 3 패키지 문서의 {@code rendition:spread}
 * 메타데이터와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <meta property="rendition:spread">none</meta>
 * }
 * </pre>
 *
 * <p>합성 펼침면은 독서 시스템이 서로 인접한 두 개의 spine 문서를
 * 한 화면에 나란히 표시하는 방식을 의미합니다.</p>
 *
 * <p>이 값은 실제 XHTML 문서 두 개를 하나의 파일로 합치는 것이 아니라,
 * 독서 시스템에 페이지 표시 방식을 전달하는 렌디션 정책입니다.</p>
 */
public enum EpubSpread {

    /**
     * 펼침면 표시 여부를 독서 시스템에 맡깁니다.
     *
     * <p>{@code rendition:spread}의 기본값입니다.</p>
     */
    AUTO(
            "auto",
            "자동",
            "Automatic",
            true,
            false
    ),

    /**
     * 합성 펼침면을 사용하지 않습니다.
     *
     * <p>각 spine 문서를 하나의 화면 중앙에 단독으로 표시하도록
     * 독서 시스템에 요청합니다.</p>
     *
     * <p>GomsBook의 일반 단행본 및 세로형 고정 레이아웃 EPUB에
     * 권장되는 값입니다.</p>
     */
    NONE(
            "none",
            "펼침 없음",
            "No Spread",
            false,
            false
    ),

    /**
     * 장치가 가로 방향일 때만 합성 펼침면을 사용합니다.
     *
     * <p>고정형 그림책, 사진집 및 만화책에서 일반적으로 사용할 수
     * 있는 펼침 정책입니다.</p>
     */
    LANDSCAPE(
            "landscape",
            "가로 방향에서 펼침",
            "Landscape Spread",
            true,
            false
    ),

    /**
     * 장치 방향과 관계없이 합성 펼침면을 사용합니다.
     *
     * <p>세로 및 가로 방향 모두에서 두 페이지를 함께 표시하도록
     * 요청합니다.</p>
     */
    BOTH(
            "both",
            "항상 펼침",
            "Spread in Both Orientations",
            true,
            false
    ),

    /**
     * 장치가 세로 방향일 때만 합성 펼침면을 사용합니다.
     *
     * <p>EPUB 3.3에서 폐기 예정인 값입니다. 신규 EPUB에는 사용하지
     * 않고 기존 EPUB을 읽거나 변환할 때의 호환성을 위해 유지합니다.</p>
     *
     * @deprecated 신규 출판물에는 {@link #BOTH} 또는
     *             {@link #LANDSCAPE}를 사용하십시오.
     */
    @Deprecated
    PORTRAIT(
            "portrait",
            "세로 방향에서 펼침",
            "Portrait Spread",
            true,
            true
    );

    /**
     * OPF의 {@code rendition:spread} 메타데이터에 기록할 값입니다.
     */
    private final String renditionValue;

    private final String koreanName;

    private final String englishName;

    /**
     * 합성 펼침면을 허용할 수 있는지 여부입니다.
     */
    private final boolean syntheticSpreadAllowed;

    /**
     * EPUB 표준에서 폐기 예정인지 여부입니다.
     */
    private final boolean deprecated;

    EpubSpread(
            String renditionValue,
            String koreanName,
            String englishName,
            boolean syntheticSpreadAllowed,
            boolean deprecated
    ) {
        this.renditionValue = renditionValue;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.syntheticSpreadAllowed = syntheticSpreadAllowed;
        this.deprecated = deprecated;
    }

    /**
     * OPF의 {@code rendition:spread}에 기록할 값을 반환합니다.
     *
     * @return EPUB spread 메타데이터 값
     */
    public String getRenditionValue() {
        return renditionValue;
    }

    /**
     * 한국어 표시 이름을 반환합니다.
     *
     * @return 한국어 펼침 정책명
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 영어 표시 이름을 반환합니다.
     *
     * @return 영어 펼침 정책명
     */
    public String getEnglishName() {
        return englishName;
    }

    /**
     * 독서 시스템이 펼침 여부를 자동으로 결정하는지 확인합니다.
     *
     * @return 자동 정책이면 {@code true}
     */
    public boolean isAuto() {
        return this == AUTO;
    }

    /**
     * 합성 펼침면을 사용하지 않는지 확인합니다.
     *
     * @return 펼침을 사용하지 않으면 {@code true}
     */
    public boolean isNone() {
        return this == NONE;
    }

    /**
     * 가로 방향에서 펼침을 사용하는지 확인합니다.
     *
     * @return 가로 방향 펼침이면 {@code true}
     */
    public boolean isLandscape() {
        return this == LANDSCAPE;
    }

    /**
     * 모든 화면 방향에서 펼침을 사용하는지 확인합니다.
     *
     * @return 항상 펼침이면 {@code true}
     */
    public boolean isBoth() {
        return this == BOTH;
    }

    /**
     * 세로 방향에서만 펼침을 사용하는지 확인합니다.
     *
     * @return 세로 방향 펼침이면 {@code true}
     */
    public boolean isPortrait() {
        return this == PORTRAIT;
    }

    /**
     * 합성 펼침면이 사용될 가능성이 있는 설정인지 확인합니다.
     *
     * <p>{@link #NONE}만 {@code false}를 반환합니다.</p>
     *
     * @return 펼침면이 허용되면 {@code true}
     */
    public boolean allowsSyntheticSpread() {
        return syntheticSpreadAllowed;
    }

    /**
     * EPUB 표준에서 폐기 예정인 값인지 확인합니다.
     *
     * @return 폐기 예정이면 {@code true}
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * 신규 EPUB 생성에 권장되는 값인지 확인합니다.
     *
     * @return 폐기 예정 값이 아니면 {@code true}
     */
    public boolean isRecommendedForGeneration() {
        return !deprecated;
    }

    /**
     * OPF에 {@code rendition:spread} 메타데이터를 명시적으로
     * 출력할지 확인합니다.
     *
     * <p>{@link #AUTO}는 기본값이므로 생성 정책에 따라 생략할 수 있습니다.</p>
     *
     * @return 명시적 정책이면 {@code true}
     */
    public boolean shouldWriteMetadata() {
        return this != AUTO;
    }

    /**
     * 현재 화면 방향에서 합성 펼침면을 사용할 수 있는지 확인합니다.
     *
     * <p>{@link #AUTO}는 최종 결정을 독서 시스템에 맡기므로
     * {@code true}를 반환합니다.</p>
     *
     * @param orientation 현재 장치 또는 렌디션 화면 방향
     * @return 펼침면을 사용할 수 있으면 {@code true}
     */
    public boolean allowsOrientation(EpubOrientation orientation) {
        if (orientation == null) {
            return this == AUTO || this == BOTH;
        }

        return switch (this) {
            case AUTO -> true;
            case NONE -> false;
            case LANDSCAPE -> orientation.isLandscape();
            case BOTH -> true;
            case PORTRAIT -> orientation.isPortrait();
        };
    }

    /**
     * EPUB 레이아웃 유형과 출판물 유형을 기준으로 권장 펼침 정책을
     * 반환합니다.
     *
     * <p>가변형 출판물은 독서 시스템의 사용자 설정을 존중하도록
     * {@link #AUTO}를 반환합니다.</p>
     *
     * <p>고정형 그림책, 만화, 사진집은 가로 화면에서 두 페이지를
     * 함께 표시할 수 있도록 {@link #LANDSCAPE}를 반환합니다.</p>
     *
     * <p>그 외 고정형 출판물은 한 페이지씩 표시하도록
     * {@link #NONE}을 반환합니다.</p>
     *
     * @param layoutType     EPUB 레이아웃 유형
     * @param publicationType 출판물 유형
     * @return 권장 펼침 정책
     */
    public static EpubSpread resolve(
            EpubLayoutType layoutType,
            EpubPublicationType publicationType
    ) {
        if (layoutType == null) {
            throw new IllegalArgumentException(
                    "EPUB layout type must not be null."
            );
        }

        if (layoutType.isReflowable()) {
            return AUTO;
        }

        if (publicationType == null) {
            return NONE;
        }

        return switch (publicationType) {
            case PICTURE_BOOK,
                 COMIC,
                 PHOTO_BOOK,
                 MAGAZINE,
                 MUSIC_SCORE -> LANDSCAPE;

            default -> NONE;
        };
    }

    /**
     * 화면 크기와 레이아웃 유형을 기준으로 권장 펼침 정책을 반환합니다.
     *
     * <p>가변형 레이아웃은 {@link #AUTO}를 반환합니다.</p>
     *
     * <p>고정형 레이아웃에서 화면이 가로형이면
     * {@link #LANDSCAPE}, 세로형이면 {@link #NONE}을 반환합니다.</p>
     *
     * @param layoutType EPUB 레이아웃 유형
     * @param width      뷰포트 너비
     * @param height     뷰포트 높이
     * @return 권장 펼침 정책
     */
    public static EpubSpread resolve(
            EpubLayoutType layoutType,
            int width,
            int height
    ) {
        if (layoutType == null) {
            throw new IllegalArgumentException(
                    "EPUB layout type must not be null."
            );
        }

        validateViewport(width, height);

        if (layoutType.isReflowable()) {
            return AUTO;
        }

        return width > height
                ? LANDSCAPE
                : NONE;
    }

    /**
     * EPUB 버전에서 이 펼침 정책을 사용할 수 있는지 확인합니다.
     *
     * <p>{@code rendition:spread} 메타데이터는 EPUB 3에서
     * 사용할 수 있습니다.</p>
     *
     * @param version EPUB 버전
     * @return 지원되는 버전이면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        return version != null && version.isEpub3();
    }

    /**
     * 문자열을 EPUB 펼침 정책으로 변환합니다.
     *
     * <p>다음 값을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code auto}</li>
     *     <li>{@code none}</li>
     *     <li>{@code single}</li>
     *     <li>{@code landscape}</li>
     *     <li>{@code both}</li>
     *     <li>{@code double}</li>
     *     <li>{@code portrait}</li>
     *     <li>{@code 펼침 없음}</li>
     *     <li>{@code 항상 펼침}</li>
     * </ul>
     *
     * @param value 펼침 정책 문자열
     * @return 일치하는 펼침 정책
     */
    public static Optional<EpubSpread> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String normalized = normalize(trimmed);

        if ("SINGLE".equals(normalized)
                || "SINGLE_PAGE".equals(normalized)
                || "NO_SPREAD".equals(normalized)) {
            return Optional.of(NONE);
        }

        if ("DOUBLE".equals(normalized)
                || "DOUBLE_PAGE".equals(normalized)
                || "TWO_PAGE".equals(normalized)) {
            return Optional.of(BOTH);
        }

        return Arrays.stream(values())
                .filter(spread ->
                        spread.name().equals(normalized)
                                || spread.renditionValue
                                        .equalsIgnoreCase(trimmed)
                                || spread.koreanName
                                        .equalsIgnoreCase(trimmed)
                                || spread.englishName
                                        .equalsIgnoreCase(trimmed)
                )
                .findFirst();
    }

    /**
     * 문자열을 EPUB 펼침 정책으로 변환합니다.
     *
     * @param value 펼침 정책 문자열
     * @return EPUB 펼침 정책
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubSpread require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB spread value: " + value
                ));
    }

    /**
     * 기본 펼침 정책을 반환합니다.
     *
     * @return 자동 펼침 정책
     */
    public static EpubSpread defaultSpread() {
        return AUTO;
    }

    /**
     * GomsBook의 일반 세로형 고정 레이아웃에 권장되는 펼침 정책을
     * 반환합니다.
     *
     * @return 펼침 없음
     */
    public static EpubSpread gomsBookFixedLayoutDefault() {
        return NONE;
    }

    private static void validateViewport(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException(
                    "Viewport width must be greater than zero: " + width
            );
        }

        if (height <= 0) {
            throw new IllegalArgumentException(
                    "Viewport height must be greater than zero: " + height
            );
        }
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    @Override
    public String toString() {
        return renditionValue;
    }
}