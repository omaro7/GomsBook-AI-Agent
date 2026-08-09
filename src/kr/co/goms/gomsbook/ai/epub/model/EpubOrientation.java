/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 출판물의 화면 방향 정책을 정의합니다.
 *
 * <p>EPUB 3 패키지 문서의 {@code rendition:orientation}
 * 메타데이터와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <meta property="rendition:orientation">auto</meta>
 * }
 * </pre>
 *
 * <pre>
 * {@code
 * <meta property="rendition:orientation">portrait</meta>
 * }
 * </pre>
 *
 * <pre>
 * {@code
 * <meta property="rendition:orientation">landscape</meta>
 * }
 * </pre>
 *
 * <p>이 값은 출판물 또는 개별 콘텐츠 문서를 표시할 때
 * 독서 시스템이 사용할 권장 화면 방향을 나타냅니다.</p>
 */
public enum EpubOrientation {

    /**
     * 화면 방향을 독서 시스템과 장치 설정에 맡깁니다.
     *
     * <p>가변형 EPUB에서 일반적으로 사용하는 기본값입니다.</p>
     */
    AUTO(
            "auto",
            "자동",
            "Automatic"
    ),

    /**
     * 세로 방향으로 표시하도록 지정합니다.
     *
     * <p>일반 단행본, 에세이, 소설 및 세로형 고정 레이아웃
     * 출판물에 적합합니다.</p>
     */
    PORTRAIT(
            "portrait",
            "세로",
            "Portrait"
    ),

    /**
     * 가로 방향으로 표시하도록 지정합니다.
     *
     * <p>가로형 사진집, 교재, 프레젠테이션형 콘텐츠 및
     * 와이드 고정 레이아웃 출판물에 적합합니다.</p>
     */
    LANDSCAPE(
            "landscape",
            "가로",
            "Landscape"
    );

    /**
     * OPF의 {@code rendition:orientation} 메타데이터에 기록할 값입니다.
     */
    private final String renditionValue;

    private final String koreanName;

    private final String englishName;

    EpubOrientation(
            String renditionValue,
            String koreanName,
            String englishName
    ) {
        this.renditionValue = renditionValue;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    /**
     * OPF의 {@code rendition:orientation}에 기록할 값을 반환합니다.
     *
     * @return {@code auto}, {@code portrait} 또는 {@code landscape}
     */
    public String getRenditionValue() {
        return renditionValue;
    }

    /**
     * 한국어 표시 이름을 반환합니다.
     *
     * @return 한국어 화면 방향명
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 영어 표시 이름을 반환합니다.
     *
     * @return 영어 화면 방향명
     */
    public String getEnglishName() {
        return englishName;
    }

    /**
     * 화면 방향을 자동으로 결정하는지 확인합니다.
     *
     * @return 자동 방향이면 {@code true}
     */
    public boolean isAuto() {
        return this == AUTO;
    }

    /**
     * 세로 방향인지 확인합니다.
     *
     * @return 세로 방향이면 {@code true}
     */
    public boolean isPortrait() {
        return this == PORTRAIT;
    }

    /**
     * 가로 방향인지 확인합니다.
     *
     * @return 가로 방향이면 {@code true}
     */
    public boolean isLandscape() {
        return this == LANDSCAPE;
    }

    /**
     * 특정 화면 방향을 강제하는 설정인지 확인합니다.
     *
     * @return 세로 또는 가로 방향이면 {@code true}
     */
    public boolean isFixedOrientation() {
        return this != AUTO;
    }

    /**
     * OPF에 {@code rendition:orientation} 메타데이터를
     * 명시적으로 출력해야 하는지 확인합니다.
     *
     * <p>{@link #AUTO}는 EPUB 기본 동작과 동일하므로
     * 생성 정책에 따라 생략할 수 있습니다.</p>
     *
     * @return 특정 방향을 지정하면 {@code true}
     */
    public boolean shouldWriteMetadata() {
        return this != AUTO;
    }

    /**
     * 해당 방향이 지정된 뷰포트 크기와 일치하는지 확인합니다.
     *
     * <p>{@link #AUTO}는 모든 뷰포트 크기를 허용합니다.</p>
     *
     * @param width  뷰포트 너비
     * @param height 뷰포트 높이
     * @return 방향과 뷰포트 비율이 일치하면 {@code true}
     */
    public boolean matchesViewport(int width, int height) {
        validateViewport(width, height);

        return switch (this) {
            case AUTO -> true;
            case PORTRAIT -> height >= width;
            case LANDSCAPE -> width >= height;
        };
    }

    /**
     * 뷰포트 크기를 기준으로 권장 화면 방향을 반환합니다.
     *
     * <p>너비와 높이가 동일하면 화면 방향을 제한하지 않는
     * {@link #AUTO}를 반환합니다.</p>
     *
     * @param width  뷰포트 너비
     * @param height 뷰포트 높이
     * @return 권장 화면 방향
     */
    public static EpubOrientation fromViewport(int width, int height) {
        validateViewport(width, height);

        if (width == height) {
            return AUTO;
        }

        return width > height
                ? LANDSCAPE
                : PORTRAIT;
    }

    /**
     * EPUB 레이아웃과 뷰포트 크기를 기준으로 권장 방향을 결정합니다.
     *
     * <p>가변형 EPUB은 화면 회전을 제한하지 않도록
     * {@link #AUTO}를 반환합니다.</p>
     *
     * <p>고정형 EPUB은 뷰포트의 가로·세로 비율에 따라
     * 방향을 결정합니다.</p>
     *
     * @param layoutType EPUB 레이아웃 유형
     * @param width      뷰포트 너비
     * @param height     뷰포트 높이
     * @return 권장 화면 방향
     */
    public static EpubOrientation resolve(
            EpubLayoutType layoutType,
            int width,
            int height
    ) {
        if (layoutType == null) {
            throw new IllegalArgumentException(
                    "EPUB layout type must not be null."
            );
        }

        if (layoutType.isReflowable()) {
            return AUTO;
        }

        return fromViewport(width, height);
    }

    /**
     * EPUB 버전에서 이 화면 방향 설정을 사용할 수 있는지 확인합니다.
     *
     * <p>{@code rendition:orientation} 메타데이터는 EPUB 3에서
     * 사용할 수 있습니다.</p>
     *
     * @param version EPUB 버전
     * @return 지원되는 버전이면 {@code true}
     */
    public boolean isSupportedBy(EpubVersion version) {
        return version != null && version.isEpub3();
    }

    /**
     * 문자열을 EPUB 화면 방향으로 변환합니다.
     *
     * <p>다음 형식을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code auto}</li>
     *     <li>{@code automatic}</li>
     *     <li>{@code portrait}</li>
     *     <li>{@code vertical}</li>
     *     <li>{@code landscape}</li>
     *     <li>{@code horizontal}</li>
     *     <li>{@code 세로}</li>
     *     <li>{@code 가로}</li>
     *     <li>{@code 자동}</li>
     * </ul>
     *
     * @param value 화면 방향 문자열
     * @return 일치하는 EPUB 화면 방향
     */
    public static Optional<EpubOrientation> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String normalized = normalize(trimmed);

        if ("AUTOMATIC".equals(normalized)) {
            return Optional.of(AUTO);
        }

        if ("VERTICAL".equals(normalized)) {
            return Optional.of(PORTRAIT);
        }

        if ("HORIZONTAL".equals(normalized)) {
            return Optional.of(LANDSCAPE);
        }

        return Arrays.stream(values())
                .filter(orientation ->
                        orientation.name().equals(normalized)
                                || orientation.renditionValue
                                        .equalsIgnoreCase(trimmed)
                                || orientation.koreanName
                                        .equalsIgnoreCase(trimmed)
                                || orientation.englishName
                                        .equalsIgnoreCase(trimmed)
                )
                .findFirst();
    }

    /**
     * 문자열을 EPUB 화면 방향으로 변환합니다.
     *
     * @param value 화면 방향 문자열
     * @return EPUB 화면 방향
     * @throws IllegalArgumentException 지원하지 않는 값인 경우
     */
    public static EpubOrientation require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB orientation: " + value
                ));
    }

    /**
     * 기본 화면 방향을 반환합니다.
     *
     * @return 자동 화면 방향
     */
    public static EpubOrientation defaultOrientation() {
        return AUTO;
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