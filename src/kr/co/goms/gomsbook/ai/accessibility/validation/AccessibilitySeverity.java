/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.util.Locale;
import java.util.Optional;

/**
 * 접근성 검사에서 발견된 문제의 심각도를 나타낸다.
 *
 * <p>이 값은 EPUB 콘텐츠의 접근성 문제 우선순위를 정하고,
 * 자동 수정 가능 여부 및 사용자 검토 필요성을 판단하는 데 사용된다.</p>
 */
public enum AccessibilitySeverity {

    /**
     * 접근성 요구사항을 직접 위반하는 치명적인 문제.
     *
     * <p>스크린 리더 사용자가 콘텐츠를 이해하거나 탐색하지 못할 수 있으며,
     * 자동 수정 또는 즉시 사용자 조치가 필요하다.</p>
     *
     * <p>예: 의미 있는 이미지의 alt 누락, 문서 언어 누락,
     * 잘못된 ARIA 참조 등.</p>
     */
    ERROR(
            "error",
            "오류",
            3,
            true,
            true
    ),

    /**
     * 접근성을 저하시킬 가능성이 높은 문제.
     *
     * <p>콘텐츠 접근이 완전히 차단되지는 않지만 사용성 저하나
     * 의미 손실이 발생할 수 있다.</p>
     *
     * <p>예: 지나치게 긴 대체 텍스트, 모호한 링크 텍스트,
     * 제목 계층 건너뛰기 등.</p>
     */
    WARNING(
            "warning",
            "경고",
            2,
            false,
            true
    ),

    /**
     * 접근성 품질 개선을 위한 권고 사항.
     *
     * <p>명확한 규칙 위반은 아니지만 콘텐츠 품질이나 사용자 경험을
     * 향상하기 위해 검토할 수 있다.</p>
     *
     * <p>예: 캡션과 alt 내용 중복, 상세 설명 권장,
     * 의미가 불명확한 아이콘 등.</p>
     */
    INFO(
            "info",
            "정보",
            1,
            false,
            false
    );

    private final String code;
    private final String displayName;
    private final int priority;
    private final boolean blocksPublication;
    private final boolean reviewRecommended;

    AccessibilitySeverity(
            String code,
            String displayName,
            int priority,
            boolean blocksPublication,
            boolean reviewRecommended) {

        this.code = code;
        this.displayName = displayName;
        this.priority = priority;
        this.blocksPublication = blocksPublication;
        this.reviewRecommended = reviewRecommended;
    }

    /**
     * 직렬화 및 외부 응답에 사용하는 고정 코드를 반환한다.
     *
     * @return 심각도 코드
     */
    public String getCode() {
        return code;
    }

    /**
     * UI에 표시할 이름을 반환한다.
     *
     * @return 표시 이름
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 심각도 우선순위를 반환한다.
     *
     * <p>값이 클수록 우선순위가 높다.</p>
     *
     * @return 우선순위
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 출판 또는 접근성 완료 처리를 차단해야 하는 수준인지 반환한다.
     *
     * @return 출판 차단 수준이면 {@code true}
     */
    public boolean isBlocksPublication() {
        return blocksPublication;
    }

    /**
     * 사용자 검토가 권장되는 수준인지 반환한다.
     *
     * @return 검토가 권장되면 {@code true}
     */
    public boolean isReviewRecommended() {
        return reviewRecommended;
    }

    /**
     * 현재 심각도가 오류 수준인지 반환한다.
     *
     * @return 오류이면 {@code true}
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * 현재 심각도가 경고 수준인지 반환한다.
     *
     * @return 경고이면 {@code true}
     */
    public boolean isWarning() {
        return this == WARNING;
    }

    /**
     * 현재 심각도가 정보 수준인지 반환한다.
     *
     * @return 정보이면 {@code true}
     */
    public boolean isInfo() {
        return this == INFO;
    }

    /**
     * 현재 심각도가 지정한 심각도 이상인지 반환한다.
     *
     * @param other 비교 기준 심각도
     * @return 현재 우선순위가 같거나 높으면 {@code true}
     */
    public boolean isAtLeast(
            AccessibilitySeverity other) {

        if (other == null) {
            return true;
        }

        return this.priority >= other.priority;
    }

    /**
     * 코드, enum 이름 또는 표시 이름을 심각도로 변환한다.
     *
     * @param value 변환할 문자열
     * @return 일치하는 심각도
     */
    public static Optional<AccessibilitySeverity> fromValue(
            String value) {

        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);

        for (AccessibilitySeverity severity : values()) {
            if (normalize(severity.code).equals(normalized)
                    || normalize(severity.name()).equals(normalized)
                    || normalize(severity.displayName).equals(normalized)) {

                return Optional.of(severity);
            }
        }

        return Optional.empty();
    }

    /**
     * 문자열을 심각도로 변환한다.
     *
     * <p>일치하는 값이 없으면 기본값을 반환한다.</p>
     *
     * @param value 변환할 문자열
     * @param defaultSeverity 기본 심각도
     * @return 변환된 심각도
     */
    public static AccessibilitySeverity fromValueOrDefault(
            String value,
            AccessibilitySeverity defaultSeverity) {

        return fromValue(value)
                .orElse(defaultSeverity == null
                        ? WARNING
                        : defaultSeverity);
    }

    private static String normalize(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}