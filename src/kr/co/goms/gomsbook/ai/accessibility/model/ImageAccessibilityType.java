/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.model;

import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 문서에서 이미지가 수행하는 접근성 목적을 나타낸다.
 *
 * <p>이미지 유형은 대체 텍스트 작성 방식과 접근성 속성 적용 방식을
 * 결정하는 기준으로 사용된다.</p>
 *
 * <p>예를 들어 장식 이미지는 빈 {@code alt} 속성을 사용하지만,
 * 정보 전달 이미지와 기능 이미지는 이미지의 의미나 기능을 설명하는
 * 대체 텍스트가 필요하다.</p>
 */
public enum ImageAccessibilityType {

    /**
     * 문서 내용을 시각적으로 전달하는 정보성 이미지.
     *
     * <p>사진, 삽화, 인물, 장소, 사물 등 이미지 자체가 문서의
     * 의미를 전달하는 경우에 사용한다.</p>
     *
     * <p>핵심 정보가 포함된 간결한 대체 텍스트가 필요하다.</p>
     */
    INFORMATIVE(
            "informative",
            "정보성 이미지",
            true,
            false,
            false
    ),

    /**
     * 문서의 의미를 전달하지 않는 장식 이미지.
     *
     * <p>구분선, 배경 장식, 분위기 표현용 꽃무늬 등 이미지가
     * 제거되어도 문서의 의미가 달라지지 않는 경우에 사용한다.</p>
     *
     * <p>일반적으로 {@code alt=\"\"}를 적용하며 필요하면
     * {@code role=\"presentation\"}을 함께 적용한다.</p>
     */
    DECORATIVE(
            "decorative",
            "장식 이미지",
            false,
            true,
            false
    ),

    /**
     * 링크, 버튼 또는 명령을 실행하는 기능성 이미지.
     *
     * <p>아이콘 버튼, 이미지 링크, 이전·다음 이동 이미지처럼
     * 이미지의 외형보다 동작이나 목적이 중요한 경우에 사용한다.</p>
     *
     * <p>대체 텍스트는 이미지 모양이 아니라 수행하는 기능을
     * 설명해야 한다.</p>
     */
    FUNCTIONAL(
            "functional",
            "기능성 이미지",
            true,
            false,
            false
    ),

    /**
     * 짧은 대체 텍스트만으로 정보를 충분히 전달하기 어려운 복합 이미지.
     *
     * <p>차트, 그래프, 지도, 인포그래픽, 흐름도, 조직도처럼
     * 여러 정보 요소와 관계를 포함하는 경우에 사용한다.</p>
     *
     * <p>간결한 대체 텍스트와 별도로 본문, 캡션 또는 상세 설명을
     * 제공해야 할 수 있다.</p>
     */
    COMPLEX(
            "complex",
            "복합 이미지",
            true,
            false,
            true
    ),

    /**
     * 의미 있는 텍스트가 이미지 형태로 제공되는 이미지.
     *
     * <p>스캔 문서, 포스터, 안내문, 표지 문구 또는 텍스트가
     * 핵심 정보를 구성하는 이미지에 사용한다.</p>
     *
     * <p>이미지에 표시된 텍스트를 접근 가능한 본문으로 제공하거나
     * 대체 텍스트 및 상세 설명에 포함해야 한다.</p>
     */
    TEXT_IMAGE(
            "text_image",
            "텍스트 이미지",
            true,
            false,
            true
    ),

    /**
     * 수학식, 화학식 또는 과학 기호를 이미지로 표현한 경우.
     *
     * <p>단순한 이미지 설명이 아니라 수식 자체를 접근 가능한
     * 텍스트, MathML 또는 상세 설명으로 제공해야 한다.</p>
     */
    MATHEMATICAL(
            "mathematical",
            "수식 이미지",
            true,
            false,
            true
    ),

    /**
     * 표 형태의 정보를 이미지로 제공하는 경우.
     *
     * <p>대체 텍스트만으로 모든 셀 정보를 나열하기보다 접근 가능한
     * XHTML 표 또는 상세 설명을 함께 제공하는 것이 바람직하다.</p>
     */
    TABULAR(
            "tabular",
            "표 이미지",
            true,
            false,
            true
    ),

    /**
     * 책 표지 또는 출판물의 대표 이미지.
     *
     * <p>표지 이미지는 일반적으로 책 제목, 저자명 및 핵심 시각
     * 요소를 포함하는 대체 텍스트가 필요하다.</p>
     */
    COVER(
            "cover",
            "표지 이미지",
            true,
            false,
            false
    ),

    /**
     * 로고, 상표 또는 기관 식별 이미지.
     *
     * <p>로고가 식별 정보를 전달한다면 기관명이나 브랜드명을
     * 대체 텍스트로 제공한다.</p>
     */
    LOGO(
            "logo",
            "로고 이미지",
            true,
            false,
            false
    ),

    /**
     * 이미지의 접근성 목적을 신뢰성 있게 분류하지 못한 상태.
     *
     * <p>자동으로 장식 이미지로 처리하거나 파일에 반영해서는 안 되며,
     * 사용자 검토 대상으로 반환해야 한다.</p>
     */
    UNKNOWN(
            "unknown",
            "분류되지 않은 이미지",
            false,
            false,
            true
    );

    private final String code;
    private final String displayName;
    private final boolean altTextRequired;
    private final boolean emptyAltRecommended;
    private final boolean detailedDescriptionRecommended;

    ImageAccessibilityType(
            String code,
            String displayName,
            boolean altTextRequired,
            boolean emptyAltRecommended,
            boolean detailedDescriptionRecommended) {

        this.code = code;
        this.displayName = displayName;
        this.altTextRequired = altTextRequired;
        this.emptyAltRecommended = emptyAltRecommended;
        this.detailedDescriptionRecommended =
                detailedDescriptionRecommended;
    }

    /**
     * 직렬화 및 LLM 응답 매핑에 사용하는 고정 코드.
     *
     * @return 이미지 접근성 유형 코드
     */
    public String getCode() {
        return code;
    }

    /**
     * UI에 표시할 한글 이름.
     *
     * @return 표시 이름
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 비어 있지 않은 대체 텍스트가 필요한 유형인지 반환한다.
     *
     * @return 대체 텍스트가 필요하면 {@code true}
     */
    public boolean isAltTextRequired() {
        return altTextRequired;
    }

    /**
     * 빈 대체 텍스트 사용이 권장되는 유형인지 반환한다.
     *
     * @return {@code alt=""}가 권장되면 {@code true}
     */
    public boolean isEmptyAltRecommended() {
        return emptyAltRecommended;
    }

    /**
     * 짧은 대체 텍스트 외에 상세 설명이 권장되는지 반환한다.
     *
     * @return 상세 설명이 권장되면 {@code true}
     */
    public boolean isDetailedDescriptionRecommended() {
        return detailedDescriptionRecommended;
    }

    /**
     * 장식 이미지인지 반환한다.
     *
     * @return 장식 이미지이면 {@code true}
     */
    public boolean isDecorative() {
        return this == DECORATIVE;
    }

    /**
     * 자동 적용 전에 사용자 검토가 필요한 유형인지 반환한다.
     *
     * <p>현재는 분류되지 않은 이미지에 대해 자동 수정을 제한한다.</p>
     *
     * @return 사용자 검토가 필요하면 {@code true}
     */
    public boolean requiresManualReview() {
        return this == UNKNOWN;
    }

    /**
     * 코드, enum 이름 또는 표시 이름을 이미지 접근성 유형으로 변환한다.
     *
     * <p>대소문자와 코드의 하이픈·공백 차이를 허용한다.</p>
     *
     * @param value 변환할 문자열
     * @return 일치하는 이미지 접근성 유형
     */
    public static Optional<ImageAccessibilityType> fromValue(
            String value) {

        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);

        for (ImageAccessibilityType type : values()) {
            if (normalize(type.code).equals(normalized)
                    || normalize(type.name()).equals(normalized)
                    || normalize(type.displayName).equals(normalized)) {

                return Optional.of(type);
            }
        }

        return Optional.empty();
    }

    /**
     * 문자열을 이미지 접근성 유형으로 변환한다.
     *
     * <p>일치하는 유형이 없으면 {@link #UNKNOWN}을 반환한다.</p>
     *
     * @param value 변환할 문자열
     * @return 이미지 접근성 유형
     */
    public static ImageAccessibilityType fromValueOrUnknown(
            String value) {

        return fromValue(value).orElse(UNKNOWN);
    }

    private static String normalize(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}