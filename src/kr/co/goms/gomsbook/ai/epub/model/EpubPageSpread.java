/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB spine 항목의 펼침면 배치 위치를 정의합니다.
 *
 * <p>OPF 패키지 문서의 {@code spine/itemref} 요소에 지정되는
 * {@code rendition:page-spread-*} 속성과 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <itemref
 *     idref="chapter01"
 *     properties="rendition:page-spread-right"/>
 * }
 * </pre>
 *
 * <p>이 값은 출판물 전체의 펼침 정책을 정의하는
 * {@link EpubSpread}와 다릅니다.</p>
 *
 * <ul>
 *     <li>{@link EpubSpread}: 출판물 또는 spine 항목의 펼침 허용 정책</li>
 *     <li>{@code EpubPageSpread}: 개별 spine 항목이 배치될 위치</li>
 * </ul>
 */
public enum EpubPageSpread {

    /**
     * 페이지 배치 위치를 명시하지 않습니다.
     *
     * <p>독서 시스템이 spine 순서, 페이지 진행 방향 및
     * 전역 펼침 정책에 따라 자동으로 배치합니다.</p>
     */
    AUTO(
            null,
            null,
            "자동",
            "Automatic"
    ),

    /**
     * spine 항목을 펼침면의 왼쪽에 배치합니다.
     */
    LEFT(
            "rendition:page-spread-left",
            "page-spread-left",
            "왼쪽 페이지",
            "Left Page"
    ),

    /**
     * spine 항목을 펼침면의 오른쪽에 배치합니다.
     */
    RIGHT(
            "rendition:page-spread-right",
            "page-spread-right",
            "오른쪽 페이지",
            "Right Page"
    ),

    /**
     * spine 항목을 단독 중앙 페이지로 배치합니다.
     *
     * <p>{@code rendition:page-spread-center}는
     * {@code spread-none}의 별칭으로 사용됩니다.</p>
     */
    CENTER(
            "rendition:page-spread-center",
            "spread-none",
            "가운데 페이지",
            "Centered Page"
    );

    /**
     * EPUB 3 rendition vocabulary 속성값입니다.
     */
    private final String renditionProperty;

    /**
     * 구형 독서 시스템 호환을 위한 비접두사 속성값입니다.
     */
    private final String legacyProperty;

    private final String koreanName;

    private final String englishName;

    EpubPageSpread(
            String renditionProperty,
            String legacyProperty,
            String koreanName,
            String englishName
    ) {
        this.renditionProperty = renditionProperty;
        this.legacyProperty = legacyProperty;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    /**
     * EPUB 3 rendition vocabulary 속성값을 반환합니다.
     *
     * @return {@code rendition:page-spread-left},
     *         {@code rendition:page-spread-right},
     *         {@code rendition:page-spread-center} 또는 {@code null}
     */
    public String getRenditionProperty() {
        return renditionProperty;
    }

    /**
     * 구형 독서 시스템 호환 속성값을 반환합니다.
     *
     * @return 비접두사 속성값 또는 {@code null}
     */
    public String getLegacyProperty() {
        return legacyProperty;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    /**
     * 명시적인 페이지 위치를 지정하지 않는지 확인합니다.
     *
     * @return 자동 배치이면 {@code true}
     */
    public boolean isAuto() {
        return this == AUTO;
    }

    /**
     * 왼쪽 페이지 배치인지 확인합니다.
     *
     * @return 왼쪽 배치이면 {@code true}
     */
    public boolean isLeft() {
        return this == LEFT;
    }

    /**
     * 오른쪽 페이지 배치인지 확인합니다.
     *
     * @return 오른쪽 배치이면 {@code true}
     */
    public boolean isRight() {
        return this == RIGHT;
    }

    /**
     * 중앙 단독 페이지 배치인지 확인합니다.
     *
     * @return 중앙 배치이면 {@code true}
     */
    public boolean isCenter() {
        return this == CENTER;
    }

    /**
     * OPF itemref의 properties 속성에 값을 출력해야 하는지 확인합니다.
     *
     * @return 명시적 배치이면 {@code true}
     */
    public boolean shouldWriteProperty() {
        return renditionProperty != null;
    }

    /**
     * 두 페이지 펼침면의 한쪽 위치인지 확인합니다.
     *
     * @return 왼쪽 또는 오른쪽이면 {@code true}
     */
    public boolean isSpreadSide() {
        return this == LEFT || this == RIGHT;
    }

    /**
     * 반대편 펼침 위치를 반환합니다.
     *
     * <p>{@link #LEFT}는 {@link #RIGHT}, {@link #RIGHT}는
     * {@link #LEFT}를 반환합니다.</p>
     *
     * <p>{@link #AUTO}와 {@link #CENTER}는 반대편 개념이 없으므로
     * 자기 자신을 반환합니다.</p>
     *
     * @return 반대편 페이지 위치
     */
    public EpubPageSpread opposite() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case AUTO, CENTER -> this;
        };
    }

    /**
     * rendition 속성만 사용하는 properties 문자열을 반환합니다.
     *
     * @return OPF properties 속성값
     */
    public String toPropertyValue() {
        return renditionProperty == null ? "" : renditionProperty;
    }

    /**
     * 구형 독서 시스템 호환 속성을 함께 포함한 문자열을 반환합니다.
     *
     * <p>예:</p>
     *
     * <pre>
     * {@code
     * rendition:page-spread-left page-spread-left
     * }
     * </pre>
     *
     * @param includeLegacyProperty 구형 속성 포함 여부
     * @return OPF properties 속성값
     */
    public String toPropertyValue(boolean includeLegacyProperty) {
        if (!shouldWriteProperty()) {
            return "";
        }

        if (!includeLegacyProperty
                || legacyProperty == null
                || legacyProperty.isBlank()) {
            return renditionProperty;
        }

        return renditionProperty + " " + legacyProperty;
    }

    /**
     * 페이지 진행 방향과 spine 순번을 기준으로 페이지 위치를 계산합니다.
     *
     * <p>순번은 0부터 시작합니다.</p>
     *
     * <p>LTR 출판물에서는 첫 페이지를 오른쪽에 배치하고,
     * 이후 왼쪽과 오른쪽을 교대로 반환합니다.</p>
     *
     * <p>RTL 출판물에서는 첫 페이지를 왼쪽에 배치하고,
     * 이후 오른쪽과 왼쪽을 교대로 반환합니다.</p>
     *
     * @param spineIndex 0부터 시작하는 spine 순번
     * @param direction 페이지 진행 방향
     * @return 권장 페이지 위치
     */
    public static EpubPageSpread resolve(
            int spineIndex,
            EpubPageProgressionDirection direction
    ) {
        if (spineIndex < 0) {
            throw new IllegalArgumentException(
                    "Spine index must not be negative: " + spineIndex
            );
        }

        EpubPageProgressionDirection resolvedDirection =
                direction == null
                        ? EpubPageProgressionDirection.defaultDirection()
                        : direction;

        boolean evenIndex = spineIndex % 2 == 0;

        if (resolvedDirection.isRightToLeft()) {
            return evenIndex ? LEFT : RIGHT;
        }

        return evenIndex ? RIGHT : LEFT;
    }

    /**
     * 연속된 두 페이지가 정상적인 펼침면 쌍인지 확인합니다.
     *
     * <p>페이지 진행 방향에 따라 올바른 순서가 달라집니다.</p>
     *
     * <ul>
     *     <li>LTR: LEFT 다음 RIGHT</li>
     *     <li>RTL: RIGHT 다음 LEFT</li>
     * </ul>
     *
     * @param first     첫 번째 spine 항목 위치
     * @param second    두 번째 spine 항목 위치
     * @param direction 페이지 진행 방향
     * @return 올바른 펼침면 쌍이면 {@code true}
     */
    public static boolean isValidPair(
            EpubPageSpread first,
            EpubPageSpread second,
            EpubPageProgressionDirection direction
    ) {
        if (first == null || second == null) {
            return false;
        }

        EpubPageProgressionDirection resolvedDirection =
                direction == null
                        ? EpubPageProgressionDirection.defaultDirection()
                        : direction;

        if (resolvedDirection.isRightToLeft()) {
            return first == RIGHT && second == LEFT;
        }

        return first == LEFT && second == RIGHT;
    }

    /**
     * EPUB 버전에서 사용할 수 있는지 확인합니다.
     *
     * @param version EPUB 버전
     * @return EPUB 3 이상이면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        if (this == AUTO) {
            return true;
        }

        return version != null && version.isEpub3();
    }

    /**
     * 문자열을 페이지 펼침 위치로 변환합니다.
     *
     * <p>다음 값을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code auto}</li>
     *     <li>{@code left}</li>
     *     <li>{@code page-spread-left}</li>
     *     <li>{@code rendition:page-spread-left}</li>
     *     <li>{@code right}</li>
     *     <li>{@code page-spread-right}</li>
     *     <li>{@code rendition:page-spread-right}</li>
     *     <li>{@code center}</li>
     *     <li>{@code spread-none}</li>
     *     <li>{@code rendition:page-spread-center}</li>
     * </ul>
     *
     * @param value 페이지 위치 문자열
     * @return 일치하는 페이지 위치
     */
    public static Optional<EpubPageSpread> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String normalized = normalize(trimmed);

        if ("NONE".equals(normalized)
                || "UNSPECIFIED".equals(normalized)
                || "DEFAULT".equals(normalized)) {
            return Optional.of(AUTO);
        }

        if ("PAGE_SPREAD_LEFT".equals(normalized)
                || "RENDITION_PAGE_SPREAD_LEFT".equals(normalized)) {
            return Optional.of(LEFT);
        }

        if ("PAGE_SPREAD_RIGHT".equals(normalized)
                || "RENDITION_PAGE_SPREAD_RIGHT".equals(normalized)) {
            return Optional.of(RIGHT);
        }

        if ("PAGE_SPREAD_CENTER".equals(normalized)
                || "RENDITION_PAGE_SPREAD_CENTER".equals(normalized)
                || "SPREAD_NONE".equals(normalized)) {
            return Optional.of(CENTER);
        }

        return Arrays.stream(values())
                .filter(pageSpread ->
                        pageSpread.name().equals(normalized)
                                || equalsIgnoreCase(
                                        pageSpread.renditionProperty,
                                        trimmed
                                )
                                || equalsIgnoreCase(
                                        pageSpread.legacyProperty,
                                        trimmed
                                )
                                || pageSpread.koreanName
                                        .equalsIgnoreCase(trimmed)
                                || pageSpread.englishName
                                        .equalsIgnoreCase(trimmed)
                )
                .findFirst();
    }

    /**
     * 문자열을 페이지 펼침 위치로 변환합니다.
     *
     * @param value 페이지 위치 문자열
     * @return EPUB 페이지 펼침 위치
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubPageSpread require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB page spread value: " + value
                ));
    }

    /**
     * 기본 페이지 펼침 위치를 반환합니다.
     *
     * @return 자동 배치
     */
    public static EpubPageSpread defaultPageSpread() {
        return AUTO;
    }

    private static boolean equalsIgnoreCase(
            String first,
            String second
    ) {
        return first != null
                && second != null
                && first.equalsIgnoreCase(second);
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace(':', '_')
                .replace('-', '_')
                .replace(' ', '_');
    }

    @Override
    public String toString() {
        return renditionProperty == null
                ? "auto"
                : renditionProperty;
    }
}