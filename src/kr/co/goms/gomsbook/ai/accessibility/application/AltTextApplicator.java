/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;

/**
 * XHTML 문서의 이미지 요소에 대체 텍스트와 접근성 속성을
 * 적용하는 서비스 인터페이스.
 *
 * <p>구현체는 {@link AltTextApplicationRequest}에 정의된 대상 이미지
 * 요소를 찾아 대체 텍스트, role, aria-hidden 등의 접근성 속성을
 * 적용하고 처리 결과를 {@link AltTextApplicationResult}로 반환한다.</p>
 *
 * <p>이 인터페이스는 이미지 분석을 수행하지 않는다. 이미지 분석은
 * {@code ImageAnalyzer}가 담당하고, 이 인터페이스는 분석된 결과를
 * XHTML 문서에 반영하는 책임만 가진다.</p>
 */
public interface AltTextApplicator {

    /**
     * XHTML 이미지 요소에 대체 텍스트와 접근성 속성을 적용한다.
     *
     * @param request 대체 텍스트 적용 요청
     * @return 적용 결과
     * @throws AltTextApplicationException 요청이 유효하지 않거나,
     *                                     XHTML 읽기·파싱·저장 또는
     *                                     속성 적용에 실패한 경우
     */
    AltTextApplicationResult apply(
            AltTextApplicationRequest request)
            throws AltTextApplicationException;

    /**
     * 현재 Applicator가 요청을 처리할 수 있는지 확인한다.
     *
     * <p>기본 구현은 요청이 {@code null}이 아니고 XHTML 경로가
     * 존재하는 경우 처리 가능한 것으로 판단한다. 구현체는 확장자,
     * 파일 상태, 프로젝트 범위 등을 추가로 검증할 수 있다.</p>
     *
     * @param request 대체 텍스트 적용 요청
     * @return 처리 가능하면 {@code true}
     */
    default boolean supports(
            AltTextApplicationRequest request) {

        return request != null
                && request.getXhtmlPath() != null;
    }

    /**
     * Applicator 구현체의 식별 이름을 반환한다.
     *
     * <p>로그, 진단 정보, Agent 실행 결과에 사용할 수 있다.</p>
     *
     * @return Applicator 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}