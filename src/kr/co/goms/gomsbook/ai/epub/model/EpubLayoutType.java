/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 출판물의 레이아웃 유형을 정의합니다.
 *
 * <p>EPUB 3의 {@code rendition:layout} 메타데이터와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <meta property="rendition:layout">reflowable</meta>
 * }
 * </pre>
 *
 * <pre>
 * {@code
 * <meta property="rendition:layout">pre-paginated</meta>
 * }
 * </pre>
 */
public enum EpubLayoutType {

    /**
     * 가변형 레이아웃입니다.
     *
     * <p>독서 시스템의 화면 크기, 글꼴 크기 및 사용자 설정에 따라
     * 본문이 다시 배치됩니다.</p>
     */
    REFLOWABLE(
            "reflowable",
            "가변형",
            "Reflowable",
            false
    ),

    /**
     * 고정형 레이아웃입니다.
     *
     * <p>페이지의 크기와 요소 위치가 고정되며 EPUB 3의
     * {@code pre-paginated} 값으로 출력합니다.</p>
     */
    FIXED(
            "pre-paginated",
            "고정형",
            "Fixed Layout",
            true
    );

    /**
     * OPF의 {@code rendition:layout} 메타데이터에 기록할 값입니다.
     */
    private final String renditionValue;

    private final String koreanName;

    private final String englishName;

    /**
     * 고정된 뷰포트가 필요한지 여부입니다.
     */
    private final boolean viewportRequired;

    EpubLayoutType(
            String renditionValue,
            String koreanName,
            String englishName,
            boolean viewportRequired
    ) {
        this.renditionValue = renditionValue;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.viewportRequired = viewportRequired;
    }

    /**
     * EPUB OPF의 {@code rendition:layout} 값으로 사용할 문자열을 반환합니다.
     *
     * @return {@code reflowable} 또는 {@code pre-paginated}
     */
    public String getRenditionValue() {
        return renditionValue;
    }

    /**
     * 한국어 표시 이름을 반환합니다.
     *
     * @return 한국어 레이아웃 이름
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 영어 표시 이름을 반환합니다.
     *
     * @return 영어 레이아웃 이름
     */
    public String getEnglishName() {
        return englishName;
    }

    /**
     * 고정형 레이아웃인지 확인합니다.
     *
     * @return 고정형이면 {@code true}
     */
    public boolean isFixed() {
        return this == FIXED;
    }

    /**
     * 가변형 레이아웃인지 확인합니다.
     *
     * @return 가변형이면 {@code true}
     */
    public boolean isReflowable() {
        return this == REFLOWABLE;
    }

    /**
     * XHTML 문서에 viewport 메타데이터가 필요한지 확인합니다.
     *
     * <p>고정형 레이아웃에서는 일반적으로 다음과 같은 viewport 선언이
     * 필요합니다.</p>
     *
     * <pre>
     * {@code
     * <meta name="viewport" content="width=1410,height=1994"/>
     * }
     * </pre>
     *
     * @return viewport가 필요하면 {@code true}
     */
    public boolean requiresViewport() {
        return viewportRequired;
    }

    /**
     * EPUB 버전에서 이 레이아웃을 사용할 수 있는지 확인합니다.
     *
     * <p>고정형 레이아웃의 표준 메타데이터는 EPUB 3에서 지원됩니다.</p>
     *
     * @param version EPUB 버전
     * @return 사용할 수 있으면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        if (version == null) {
            return false;
        }

        return this == REFLOWABLE || version.isEpub3();
    }

    /**
     * 문자열을 EPUB 레이아웃 유형으로 변환합니다.
     *
     * <p>다음 형식을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code reflowable}</li>
     *     <li>{@code REFLOWABLE}</li>
     *     <li>{@code fixed}</li>
     *     <li>{@code FIXED}</li>
     *     <li>{@code fixed-layout}</li>
     *     <li>{@code pre-paginated}</li>
     *     <li>{@code 가변형}</li>
     *     <li>{@code 고정형}</li>
     * </ul>
     *
     * @param value 레이아웃 문자열
     * @return 일치하는 레이아웃 유형
     */
    public static Optional<EpubLayoutType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String normalized = normalize(trimmed);

        if ("FIXED_LAYOUT".equals(normalized)
                || "PRE_PAGINATED".equals(normalized)) {
            return Optional.of(FIXED);
        }

        return Arrays.stream(values())
                .filter(type ->
                        type.name().equals(normalized)
                                || type.renditionValue.equalsIgnoreCase(trimmed)
                                || type.koreanName.equalsIgnoreCase(trimmed)
                                || type.englishName.equalsIgnoreCase(trimmed)
                )
                .findFirst();
    }

    /**
     * 문자열을 EPUB 레이아웃 유형으로 변환합니다.
     *
     * @param value 레이아웃 문자열
     * @return EPUB 레이아웃 유형
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubLayoutType require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB layout type: " + value
                ));
    }

    /**
     * 기본 레이아웃 유형을 반환합니다.
     *
     * @return 가변형 레이아웃
     */
    public static EpubLayoutType defaultType() {
        return REFLOWABLE;
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