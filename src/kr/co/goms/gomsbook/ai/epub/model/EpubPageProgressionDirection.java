/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 페이지 진행 방향을 정의합니다.
 *
 * <p>OPF 패키지 문서의 {@code spine} 요소에 지정되는
 * {@code page-progression-direction} 속성과 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <spine page-progression-direction="ltr">
 *     <itemref idref="chapter01"/>
 *     <itemref idref="chapter02"/>
 * </spine>
 * }
 * </pre>
 *
 * <p>페이지 진행 방향은 본문의 문자 방향 자체가 아니라,
 * 독자가 다음 페이지로 이동할 때의 전역 페이지 이동 방향을 의미합니다.</p>
 */
public enum EpubPageProgressionDirection {

    /**
     * 왼쪽에서 오른쪽으로 페이지가 진행됩니다.
     *
     * <p>한국어 가로쓰기, 영어 및 대부분의 서양 언어 출판물에
     * 일반적으로 사용합니다.</p>
     */
    LEFT_TO_RIGHT(
            "ltr",
            "왼쪽에서 오른쪽",
            "Left to Right"
    ),

    /**
     * 오른쪽에서 왼쪽으로 페이지가 진행됩니다.
     *
     * <p>아랍어, 히브리어 또는 일본어 세로쓰기 출판물 등에
     * 사용할 수 있습니다.</p>
     */
    RIGHT_TO_LEFT(
            "rtl",
            "오른쪽에서 왼쪽",
            "Right to Left"
    ),

    /**
     * 페이지 진행 방향을 독서 시스템의 기본 정책에 맡깁니다.
     *
     * <p>명시적인 방향을 지정하지 않는 것과 유사하지만,
     * 애플리케이션 모델에서 기본 상태를 명확히 표현할 때 사용합니다.</p>
     */
    DEFAULT(
            "default",
            "기본 방향",
            "Default"
    );

    /**
     * OPF의 {@code page-progression-direction} 속성에 기록할 값입니다.
     */
    private final String opfValue;

    private final String koreanName;

    private final String englishName;

    EpubPageProgressionDirection(
            String opfValue,
            String koreanName,
            String englishName
    ) {
        this.opfValue = opfValue;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    /**
     * OPF spine 요소에 기록할 속성값을 반환합니다.
     *
     * @return {@code ltr}, {@code rtl} 또는 {@code default}
     */
    public String getOpfValue() {
        return opfValue;
    }

    /**
     * 한국어 표시 이름을 반환합니다.
     *
     * @return 한국어 페이지 진행 방향명
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 영어 표시 이름을 반환합니다.
     *
     * @return 영어 페이지 진행 방향명
     */
    public String getEnglishName() {
        return englishName;
    }

    /**
     * 왼쪽에서 오른쪽으로 진행하는 방향인지 확인합니다.
     *
     * @return 왼쪽에서 오른쪽이면 {@code true}
     */
    public boolean isLeftToRight() {
        return this == LEFT_TO_RIGHT;
    }

    /**
     * 오른쪽에서 왼쪽으로 진행하는 방향인지 확인합니다.
     *
     * @return 오른쪽에서 왼쪽이면 {@code true}
     */
    public boolean isRightToLeft() {
        return this == RIGHT_TO_LEFT;
    }

    /**
     * 독서 시스템의 기본 진행 방향을 사용하는지 확인합니다.
     *
     * @return 기본 방향이면 {@code true}
     */
    public boolean isDefault() {
        return this == DEFAULT;
    }

    /**
     * OPF spine 요소에 속성을 명시적으로 출력해야 하는지 확인합니다.
     *
     * <p>{@link #DEFAULT}는 속성을 생략할 수 있으므로 {@code false}를
     * 반환합니다.</p>
     *
     * @return 명시적으로 출력해야 하면 {@code true}
     */
    public boolean shouldWriteAttribute() {
        return this != DEFAULT;
    }

    /**
     * 페이지 넘김 방향이 일반적인 왼쪽 방향인지 확인합니다.
     *
     * <p>오른쪽에서 왼쪽으로 진행하는 출판물은 다음 페이지가
     * 현재 페이지의 왼쪽에 배치되는 경우가 일반적입니다.</p>
     *
     * @return 다음 페이지가 왼쪽 방향이면 {@code true}
     */
    public boolean isNextPageOnLeft() {
        return this == RIGHT_TO_LEFT;
    }

    /**
     * 페이지 넘김 방향이 일반적인 오른쪽 방향인지 확인합니다.
     *
     * @return 다음 페이지가 오른쪽 방향이면 {@code true}
     */
    public boolean isNextPageOnRight() {
        return this == LEFT_TO_RIGHT;
    }

    /**
     * 언어 태그를 기준으로 권장 페이지 진행 방향을 반환합니다.
     *
     * <p>아랍어, 히브리어, 페르시아어, 우르두어는
     * 오른쪽에서 왼쪽 방향을 반환합니다.</p>
     *
     * <p>그 외 언어는 왼쪽에서 오른쪽 방향을 반환합니다.
     * 일본어는 세로쓰기 여부를 언어 코드만으로 판단할 수 없으므로
     * 기본적으로 왼쪽에서 오른쪽 방향을 반환합니다.</p>
     *
     * @param languageTag BCP 47 언어 태그
     * @return 권장 페이지 진행 방향
     */
    public static EpubPageProgressionDirection fromLanguageTag(
            String languageTag
    ) {
        if (languageTag == null || languageTag.isBlank()) {
            return defaultDirection();
        }

        String normalized = languageTag.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');

        String primaryLanguage = normalized.split("-", 2)[0];

        return switch (primaryLanguage) {
            case "ar", "he", "fa", "ur", "yi", "ps", "sd" ->
                    RIGHT_TO_LEFT;
            default -> LEFT_TO_RIGHT;
        };
    }

    /**
     * 출판물 언어와 세로쓰기 여부를 기준으로 권장 방향을 반환합니다.
     *
     * <p>일본어 세로쓰기 출판물은 일반적으로 오른쪽에서 왼쪽으로
     * 페이지가 진행됩니다.</p>
     *
     * @param languageTag    BCP 47 언어 태그
     * @param verticalWriting 세로쓰기 여부
     * @return 권장 페이지 진행 방향
     */
    public static EpubPageProgressionDirection resolve(
            String languageTag,
            boolean verticalWriting
    ) {
        if (verticalWriting && isJapanese(languageTag)) {
            return RIGHT_TO_LEFT;
        }

        return fromLanguageTag(languageTag);
    }

    /**
     * 문자열을 페이지 진행 방향으로 변환합니다.
     *
     * <p>다음 형식을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code ltr}</li>
     *     <li>{@code left-to-right}</li>
     *     <li>{@code LEFT_TO_RIGHT}</li>
     *     <li>{@code rtl}</li>
     *     <li>{@code right-to-left}</li>
     *     <li>{@code RIGHT_TO_LEFT}</li>
     *     <li>{@code default}</li>
     * </ul>
     *
     * @param value 페이지 진행 방향 문자열
     * @return 일치하는 페이지 진행 방향
     */
    public static Optional<EpubPageProgressionDirection> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String normalized = normalize(trimmed);

        if ("LTR".equals(normalized)
                || "LEFT_TO_RIGHT".equals(normalized)) {
            return Optional.of(LEFT_TO_RIGHT);
        }

        if ("RTL".equals(normalized)
                || "RIGHT_TO_LEFT".equals(normalized)) {
            return Optional.of(RIGHT_TO_LEFT);
        }

        return Arrays.stream(values())
                .filter(direction ->
                        direction.name().equals(normalized)
                                || direction.opfValue.equalsIgnoreCase(trimmed)
                                || direction.koreanName.equalsIgnoreCase(trimmed)
                                || direction.englishName.equalsIgnoreCase(trimmed)
                )
                .findFirst();
    }

    /**
     * 문자열을 페이지 진행 방향으로 변환합니다.
     *
     * @param value 페이지 진행 방향 문자열
     * @return 페이지 진행 방향
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubPageProgressionDirection require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB page progression direction: " + value
                ));
    }

    /**
     * GomsBook EPUB 출판물의 기본 페이지 진행 방향을 반환합니다.
     *
     * <p>한국어 가로쓰기 출판물을 기준으로 왼쪽에서 오른쪽 방향을
     * 기본값으로 사용합니다.</p>
     *
     * @return 왼쪽에서 오른쪽 방향
     */
    public static EpubPageProgressionDirection defaultDirection() {
        return LEFT_TO_RIGHT;
    }

    private static boolean isJapanese(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return false;
        }

        String normalized = languageTag.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');

        return "ja".equals(normalized)
                || normalized.startsWith("ja-");
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    @Override
    public String toString() {
        return opfValue;
    }
}