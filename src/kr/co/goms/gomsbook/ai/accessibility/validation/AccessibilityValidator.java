/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.util.List;

/**
 * EPUB 또는 XHTML 문서의 접근성 검사를 수행하는 서비스 인터페이스.
 *
 * <p>구현체는 검사 대상 문서를 파싱하고, 등록된
 * {@link AccessibilityRule}을 실행한 뒤
 * {@link AccessibilityValidationResult}를 반환한다.</p>
 *
 * <p>이 인터페이스는 접근성 문제를 검사하는 역할만 담당하며,
 * 문서 파일을 직접 수정하지 않는다.</p>
 */
public interface AccessibilityValidator {

    /**
     * 단일 문서에 대해 접근성 검사를 수행한다.
     *
     * @param request 접근성 검사 요청
     * @return 접근성 검사 결과
     * @throws AccessibilityValidationException 요청이 유효하지 않거나,
     *                                          문서 파싱 또는 규칙 실행에
     *                                          실패한 경우
     */
    AccessibilityValidationResult validate(
            AccessibilityValidationRequest request)
            throws AccessibilityValidationException;

    /**
     * 프로젝트 루트와 문서 경로를 이용하여 기본 접근성 검사를 수행한다.
     *
     * @param projectRoot 프로젝트 루트
     * @param documentPath 검사할 문서 경로
     * @return 접근성 검사 결과
     */
    default AccessibilityValidationResult validate(
            Path projectRoot,
            Path documentPath) {

        return validate(
                AccessibilityValidationRequest.builder()
                        .projectRoot(projectRoot)
                        .documentPath(documentPath)
                        .build()
        );
    }

    /**
     * 현재 Validator가 검사 요청을 처리할 수 있는지 반환한다.
     *
     * <p>기본 구현은 요청과 문서 경로가 존재하는 경우 처리 가능한
     * 것으로 판단한다. 구현체는 파일 확장자, 문서 유형 및 프로젝트
     * 범위 등을 추가로 검사할 수 있다.</p>
     *
     * @param request 접근성 검사 요청
     * @return 처리 가능하면 {@code true}
     */
    default boolean supports(
            AccessibilityValidationRequest request) {

        return request != null
                && request.getProjectRoot() != null
                && request.getDocumentPath() != null;
    }

    /**
     * Validator에 등록된 접근성 규칙 목록을 반환한다.
     *
     * <p>외부에서 반환 목록을 수정할 수 없어야 한다.</p>
     *
     * @return 등록된 접근성 규칙 목록
     */
    List<AccessibilityRule> getRules();

    /**
     * 지정한 규칙 ID가 등록되어 있는지 반환한다.
     *
     * @param ruleId 규칙 ID
     * @return 등록되어 있으면 {@code true}
     */
    default boolean hasRule(String ruleId) {

        if (ruleId == null || ruleId.isBlank()) {
            return false;
        }

        return getRules().stream()
                .anyMatch(
                        rule -> ruleId.equals(
                                rule.getId()
                        )
                );
    }

    /**
     * Validator 구현체의 식별 이름을 반환한다.
     *
     * @return Validator 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}