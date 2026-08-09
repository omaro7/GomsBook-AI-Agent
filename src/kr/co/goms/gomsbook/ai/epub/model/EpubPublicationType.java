/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 출판물의 유형을 정의합니다.
 *
 * <p>출판물 유형은 EPUB 생성 정책, 기본 메타데이터,
 * 페이지 진행 방향, 레이아웃 방식 등을 결정하는 데 사용됩니다.</p>
 *
 * <p>이 값은 애플리케이션 내부 분류용이며, 필요한 경우
 * ONIX, Thema 또는 schema.org 메타데이터 값으로 변환할 수 있습니다.</p>
 */
public enum EpubPublicationType {

    /**
     * 일반적인 단행본 전자책입니다.
     *
     * <p>별도의 세부 유형을 지정하지 않은 경우 사용합니다.</p>
     */
    GENERAL_BOOK(
            "general-book",
            "일반 도서",
            "General Book",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 소설, 단편소설 등 문학 작품입니다.
     */
    FICTION(
            "fiction",
            "소설",
            "Fiction",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 에세이 및 산문집입니다.
     */
    ESSAY(
            "essay",
            "에세이",
            "Essay",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 시집 및 운문 중심 출판물입니다.
     */
    POETRY(
            "poetry",
            "시",
            "Poetry",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 인문, 사회, 역사, 철학 등의 비문학 도서입니다.
     */
    NON_FICTION(
            "non-fiction",
            "비문학",
            "Non-fiction",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 학습서, 교과서, 참고서 등의 교육용 출판물입니다.
     */
    EDUCATIONAL(
            "educational",
            "교육용 도서",
            "Educational Publication",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 기술 문서, 개발 문서, 매뉴얼 등의 출판물입니다.
     */
    TECHNICAL(
            "technical",
            "기술 도서",
            "Technical Publication",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 사전, 백과사전, 용어집 등의 참고 자료입니다.
     */
    REFERENCE(
            "reference",
            "참고 도서",
            "Reference Publication",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 어린이 대상 출판물입니다.
     *
     * <p>그림책과 달리 본문 중심의 어린이 도서에 사용합니다.</p>
     */
    CHILDREN_BOOK(
            "children-book",
            "어린이 도서",
            "Children's Book",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 그림책입니다.
     *
     * <p>텍스트와 이미지의 위치 관계가 중요한 경우가 많으므로
     * 기본 레이아웃을 고정형으로 설정합니다.</p>
     */
    PICTURE_BOOK(
            "picture-book",
            "그림책",
            "Picture Book",
            true,
            false,
            EpubLayoutType.FIXED
    ),

    /**
     * 만화, 웹툰, 그래픽 노블 등의 출판물입니다.
     */
    COMIC(
            "comic",
            "만화",
            "Comic",
            true,
            false,
            EpubLayoutType.FIXED
    ),

    /**
     * 잡지 및 정기간행물입니다.
     */
    MAGAZINE(
            "magazine",
            "잡지",
            "Magazine",
            true,
            false,
            EpubLayoutType.FIXED
    ),

    /**
     * 사진집, 화보집 등의 이미지 중심 출판물입니다.
     */
    PHOTO_BOOK(
            "photo-book",
            "사진집",
            "Photo Book",
            true,
            false,
            EpubLayoutType.FIXED
    ),

    /**
     * 악보 중심 출판물입니다.
     */
    MUSIC_SCORE(
            "music-score",
            "악보",
            "Music Score",
            true,
            false,
            EpubLayoutType.FIXED
    ),

    /**
     * 오디오북입니다.
     *
     * <p>EPUB Audiobooks 또는 오디오 중심 EPUB 출판물에 사용합니다.</p>
     */
    AUDIOBOOK(
            "audiobook",
            "오디오북",
            "Audiobook",
            false,
            true,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 이미지, 오디오, 비디오 및 상호작용 요소가 포함된
     * 멀티미디어 출판물입니다.
     */
    MULTIMEDIA(
            "multimedia",
            "멀티미디어 도서",
            "Multimedia Publication",
            true,
            true,
            EpubLayoutType.REFLOWABLE
    ),

    /**
     * 접근성 기능을 강화한 출판물입니다.
     *
     * <p>대체 텍스트, 구조화된 제목, 랜드마크, 접근성 메타데이터,
     * 읽기 순서 등이 명시적으로 관리되는 EPUB에 사용합니다.</p>
     */
    ACCESSIBLE_PUBLICATION(
            "accessible-publication",
            "접근성 전자책",
            "Accessible Publication",
            false,
            false,
            EpubLayoutType.REFLOWABLE
    );

    private final String code;

    private final String koreanName;

    private final String englishName;

    /**
     * 이미지 배치와 페이지 구성이 출판물 의미에 중요한지 여부입니다.
     */
    private final boolean visualLayoutImportant;

    /**
     * 오디오 또는 비디오와 같은 시간 기반 미디어가 중요한지 여부입니다.
     */
    private final boolean mediaOverlayRelevant;

    /**
     * 출판물 유형에 권장되는 기본 레이아웃입니다.
     */
    private final EpubLayoutType defaultLayoutType;

    EpubPublicationType(
            String code,
            String koreanName,
            String englishName,
            boolean visualLayoutImportant,
            boolean mediaOverlayRelevant,
            EpubLayoutType defaultLayoutType
    ) {
        this.code = code;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.visualLayoutImportant = visualLayoutImportant;
        this.mediaOverlayRelevant = mediaOverlayRelevant;
        this.defaultLayoutType = defaultLayoutType;
    }

    /**
     * 애플리케이션 내부 식별 코드를 반환합니다.
     *
     * @return 출판물 유형 코드
     */
    public String getCode() {
        return code;
    }

    /**
     * 한국어 표시 이름을 반환합니다.
     *
     * @return 한국어 출판물 유형명
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * 영어 표시 이름을 반환합니다.
     *
     * @return 영어 출판물 유형명
     */
    public String getEnglishName() {
        return englishName;
    }

    /**
     * 시각적 배치가 중요한 출판물인지 확인합니다.
     *
     * @return 이미지 또는 페이지 배치가 중요하면 {@code true}
     */
    public boolean isVisualLayoutImportant() {
        return visualLayoutImportant;
    }

    /**
     * 오디오, 비디오 또는 미디어 오버레이와 관련된 유형인지 확인합니다.
     *
     * @return 시간 기반 미디어가 중요하면 {@code true}
     */
    public boolean isMediaOverlayRelevant() {
        return mediaOverlayRelevant;
    }

    /**
     * 권장 기본 EPUB 레이아웃을 반환합니다.
     *
     * @return 기본 레이아웃 유형
     */
    public EpubLayoutType getDefaultLayoutType() {
        return defaultLayoutType;
    }

    /**
     * 기본적으로 고정형 레이아웃이 권장되는지 확인합니다.
     *
     * @return 고정형 레이아웃이면 {@code true}
     */
    public boolean prefersFixedLayout() {
        return defaultLayoutType == EpubLayoutType.FIXED;
    }

    /**
     * 기본적으로 가변형 레이아웃이 권장되는지 확인합니다.
     *
     * @return 가변형 레이아웃이면 {@code true}
     */
    public boolean prefersReflowableLayout() {
        return defaultLayoutType == EpubLayoutType.REFLOWABLE;
    }

    /**
     * 코드, 열거형 이름 또는 표시 이름으로 출판물 유형을 검색합니다.
     *
     * <p>다음과 같은 값을 처리할 수 있습니다.</p>
     *
     * <ul>
     *     <li>{@code fiction}</li>
     *     <li>{@code FICTION}</li>
     *     <li>{@code photo-book}</li>
     *     <li>{@code PHOTO_BOOK}</li>
     *     <li>{@code 에세이}</li>
     *     <li>{@code Audiobook}</li>
     * </ul>
     *
     * @param value 출판물 유형 문자열
     * @return 일치하는 출판물 유형
     */
    public static Optional<EpubPublicationType> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmedValue = value.trim();
        String normalizedValue = normalize(trimmedValue);

        return Arrays.stream(values())
                .filter(type ->
                        type.code.equalsIgnoreCase(trimmedValue)
                                || type.name().equals(normalizedValue)
                                || type.koreanName.equalsIgnoreCase(trimmedValue)
                                || type.englishName.equalsIgnoreCase(trimmedValue)
                )
                .findFirst();
    }

    /**
     * 문자열을 출판물 유형으로 변환합니다.
     *
     * @param value 출판물 유형 문자열
     * @return 일치하는 출판물 유형
     * @throws IllegalArgumentException 지원하지 않는 출판물 유형인 경우
     */
    public static EpubPublicationType require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB publication type: " + value
                ));
    }

    /**
     * 기본 출판물 유형을 반환합니다.
     *
     * @return 일반 도서
     */
    public static EpubPublicationType defaultType() {
        return GENERAL_BOOK;
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    @Override
    public String toString() {
        return code;
    }
}