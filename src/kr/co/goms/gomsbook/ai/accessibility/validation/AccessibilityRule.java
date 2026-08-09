/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode.AccessibilityCategory;


/**
 * XHTML 또는 EPUB 콘텐츠에 적용되는 개별 접근성 검사 규칙.
 *
 * <p>구현체는 {@link AccessibilityRuleContext}에 포함된 문서와
 * 프로젝트 정보를 검사하고, 발견한 접근성 문제를
 * {@link AccessibilityIssue} 목록으로 반환한다.</p>
 *
 * <p>접근성 규칙은 검사만 담당하며 문서 파일을 수정하지 않는다.
 * 자동 수정은 별도의 Applicator 또는 Fix 서비스에서 수행해야 한다.</p>
 */
public interface AccessibilityRule {

    /**
     * 규칙의 고유 식별자를 반환한다.
     *
     * <p>로그, 설정, 규칙 활성화 여부 및 검사 결과 추적에 사용한다.</p>
     *
     * @return 비어 있지 않은 규칙 ID
     */
    String getId();

    /**
     * 규칙의 표시 이름을 반환한다.
     *
     * @return 규칙 표시 이름
     */
    default String getDisplayName() {
        return getId();
    }

    /**
     * 규칙이 속한 접근성 범주를 반환한다.
     *
     * @return 접근성 문제 범주
     */
    AccessibilityCategory getCategory();

    /**
     * 규칙의 기본 심각도를 반환한다.
     *
     * <p>개별 문제를 생성할 때 상황에 따라 다른 심각도를 지정할 수 있다.</p>
     *
     * @return 기본 심각도
     */
    default AccessibilitySeverity getDefaultSeverity() {
        return AccessibilitySeverity.WARNING;
    }

    /**
     * 현재 규칙이 검사 컨텍스트를 처리할 수 있는지 반환한다.
     *
     * <p>예를 들어 XHTML 문서 전용 규칙은 OPF 문서에 대해
     * {@code false}를 반환할 수 있다.</p>
     *
     * @param context 접근성 검사 컨텍스트
     * @return 검사 가능하면 {@code true}
     */
    default boolean supports(
            AccessibilityRuleContext context) {

        return context != null
                && context.getDocument() != null;
    }

    /**
     * 접근성 검사를 수행한다.
     *
     * <p>문제가 없으면 빈 목록을 반환해야 한다. {@code null}을
     * 반환해서는 안 된다.</p>
     *
     * @param context 접근성 검사 컨텍스트
     * @return 발견한 접근성 문제 목록
     * @throws AccessibilityRuleException 규칙 실행에 실패한 경우
     */
    List<AccessibilityIssue> validate(
            AccessibilityRuleContext context)
            throws AccessibilityRuleException;

    /**
     * 규칙을 안전하게 실행한다.
     *
     * <p>현재 컨텍스트를 지원하지 않으면 빈 목록을 반환한다.
     * 구현체가 {@code null}을 반환한 경우에도 빈 목록로 보정한다.</p>
     *
     * @param context 접근성 검사 컨텍스트
     * @return 검사 문제 목록
     * @throws AccessibilityRuleException 규칙 실행에 실패한 경우
     */
    default List<AccessibilityIssue> validateIfSupported(
            AccessibilityRuleContext context)
            throws AccessibilityRuleException {

        if (!supports(context)) {
            return Collections.emptyList();
        }

        List<AccessibilityIssue> issues =
                validate(context);

        if (issues == null || issues.isEmpty()) {
            return Collections.emptyList();
        }

        return List.copyOf(
                issues.stream()
                        .filter(Objects::nonNull)
                        .toList()
        );
    }

    /**
     * 규칙이 기본적으로 활성화되는지 반환한다.
     *
     * <p>색상 대비처럼 자동 분석이 제한적인 규칙은 구현체에서
     * {@code false}를 반환하고 명시적으로 활성화할 수 있다.</p>
     *
     * @return 기본 활성화 여부
     */
    default boolean isEnabledByDefault() {
        return true;
    }

    /**
     * 규칙이 자동 수정 가능한 문제를 생성할 수 있는지 반환한다.
     *
     * <p>실제 자동 수정 가능 여부는 각 {@link AccessibilityIssue}에서
     * 최종적으로 판단한다.</p>
     *
     * @return 자동 수정 문제를 생성할 수 있으면 {@code true}
     */
    default boolean supportsAutomaticFix() {
        return false;
    }

    /**
     * 규칙 실행 우선순위를 반환한다.
     *
     * <p>값이 작을수록 먼저 실행한다. 구조 및 문법 검사는 낮은 값을,
     * 의미 분석이나 권고 규칙은 높은 값을 사용하는 것이 좋다.</p>
     *
     * @return 실행 우선순위
     */
    default int getOrder() {
        return 100;
    }
}