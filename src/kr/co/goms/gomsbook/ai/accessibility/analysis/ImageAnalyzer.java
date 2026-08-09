/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.analysis;

import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisRequest;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisResult;

/**
 * 이미지의 접근성 정보를 분석하는 서비스 인터페이스.
 *
 * <p>구현체는 이미지 파일과 문서 문맥을 분석하여 이미지 유형,
 * 대체 텍스트, 상세 설명, 이미지 내부 텍스트 및 신뢰도를 포함한
 * {@link ImageAnalysisResult}를 생성한다.</p>
 *
 * <p>이 인터페이스는 분석만 담당하며 XHTML 파일 수정이나
 * {@code alt} 속성 반영은 수행하지 않는다.</p>
 */
public interface ImageAnalyzer {

    /**
     * 이미지 접근성 분석을 수행한다.
     *
     * @param request 이미지 분석 요청
     * @return 이미지 접근성 분석 결과
     * @throws ImageAnalysisException 분석 요청이 유효하지 않거나
     *                                이미지 읽기 또는 모델 호출에 실패한 경우
     */
    ImageAnalysisResult analyze(
            ImageAnalysisRequest request)
            throws ImageAnalysisException;

    /**
     * 현재 분석기가 요청을 처리할 수 있는지 확인한다.
     *
     * <p>기본 구현은 요청이 {@code null}이 아닌 경우 처리할 수 있다고
     * 판단한다. 구현체는 이미지 형식, 파일 크기, 모델 지원 여부 등을
     * 기준으로 추가 검증할 수 있다.</p>
     *
     * @param request 이미지 분석 요청
     * @return 처리 가능하면 {@code true}
     */
    default boolean supports(
            ImageAnalysisRequest request) {

        return request != null;
    }

    /**
     * 분석기 구현체의 식별 이름을 반환한다.
     *
     * <p>로그, 진단 정보 및 Agent 실행 결과에 사용할 수 있다.</p>
     *
     * @return 분석기 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}