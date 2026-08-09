/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.util.Objects;

/**
 * EPUB 3 및 XHTML 접근성 작업을 수행하는 전용 Agent.
 *
 * <p>접근성 Agent는 사용자의 요청을 해석하고 다음 Tool을 조율한다.</p>
 *
 * <ul>
 *   <li>{@code validate_accessibility}: 접근성 검사</li>
 *   <li>{@code analyze_image}: 이미지 의미 및 대체 텍스트 분석</li>
 *   <li>{@code apply_alt_text}: XHTML 대체 텍스트 적용</li>
 * </ul>
 *
 * <p>일반적인 수정 흐름은 다음과 같다.</p>
 *
 * <ol>
 *   <li>대상 XHTML 접근성 검사</li>
 *   <li>문제가 있는 이미지와 문맥 확인</li>
 *   <li>Vision 모델을 이용한 이미지 분석</li>
 *   <li>자동 적용 정책 검증</li>
 *   <li>대체 텍스트 및 접근성 속성 적용</li>
 *   <li>수정된 XHTML 재검사</li>
 * </ol>
 *
 * <p>구현체는 {@link AccessibilityAgentConfiguration}에 정의된
 * Tool 허용 목록, 실행 횟수, 신뢰도 기준 및 파일 수정 정책을
 * 준수해야 한다.</p>
 */
public interface AccessibilityAgent {

    /**
     * 접근성 Agent 요청을 실행한다.
     *
     * @param request 접근성 Agent 요청
     * @return 접근성 Agent 실행 결과
     * @throws AccessibilityAgentException 요청이 유효하지 않거나
     *                                     Agent 실행에 실패한 경우
     */
    AccessibilityAgentResult execute(
            AccessibilityAgentRequest request)
            throws AccessibilityAgentException;

    /**
     * 현재 Agent가 요청을 처리할 수 있는지 반환한다.
     *
     * @param request 접근성 Agent 요청
     * @return 처리할 수 있으면 {@code true}
     */
    default boolean supports(
            AccessibilityAgentRequest request) {

        return request != null
                && request.getProjectRoot() != null
                && request.getInstruction() != null
                && !request.getInstruction().isBlank();
    }

    /**
     * 접근성 Agent 구성을 반환한다.
     *
     * @return 접근성 Agent 구성
     */
    AccessibilityAgentConfiguration getConfiguration();

    /**
     * 접근성 Agent 이름을 반환한다.
     *
     * @return Agent 이름
     */
    default String getName() {

        AccessibilityAgentConfiguration configuration =
                getConfiguration();

        if (configuration == null) {
            return getClass().getSimpleName();
        }

        return configuration.getAgentName();
    }

    /**
     * 접근성 Agent 설명을 반환한다.
     *
     * @return Agent 설명
     */
    default String getDescription() {

        AccessibilityAgentConfiguration configuration =
                getConfiguration();

        if (configuration == null) {
            return "EPUB accessibility agent";
        }

        return configuration.getDescription();
    }

    /**
     * 지정한 Tool을 호출할 수 있는지 반환한다.
     *
     * @param toolName Tool 이름
     * @return 허용된 Tool이면 {@code true}
     */
    default boolean isToolAllowed(
            String toolName) {

        AccessibilityAgentConfiguration configuration =
                getConfiguration();

        return configuration != null
                && configuration.isToolAllowed(toolName);
    }

    /**
     * 파일 자동 수정이 허용되는지 반환한다.
     *
     * @return 자동 수정 허용 여부
     */
    default boolean isAutomaticModificationAllowed() {

        AccessibilityAgentConfiguration configuration =
                getConfiguration();

        return configuration != null
                && configuration.isAllowAutomaticModification();
    }

    /**
     * 이미지 분석 신뢰도가 자동 적용 기준을 충족하는지 반환한다.
     *
     * @param confidence 이미지 분석 신뢰도
     * @return 자동 적용 신뢰도 기준을 충족하면 {@code true}
     */
    default boolean satisfiesAnalysisConfidence(
            double confidence) {

        AccessibilityAgentConfiguration configuration =
                Objects.requireNonNull(
                        getConfiguration(),
                        "AccessibilityAgentConfiguration must not be null"
                );

        return configuration.satisfiesConfidence(confidence);
    }
}