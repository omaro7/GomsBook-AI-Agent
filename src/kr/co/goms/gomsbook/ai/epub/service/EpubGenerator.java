/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationResult;

/**
 * EPUB 파일 생성 기능의 최상위 계약을 정의합니다.
 *
 * <p>구현체는 {@link EpubGenerationRequest}에 포함된 EPUB 패키지,
 * 경로 설정 및 생성 옵션을 사용하여 실제 {@code .epub} 파일을
 * 생성해야 합니다.</p>
 *
 * <p>구현체의 일반적인 처리 순서는 다음과 같습니다.</p>
 *
 * <ol>
 *     <li>생성 요청 검증</li>
 *     <li>작업 디렉터리 준비</li>
 *     <li>{@code mimetype} 파일 생성</li>
 *     <li>{@code META-INF/container.xml} 생성</li>
 *     <li>OPF 패키지 문서 생성</li>
 *     <li>Navigation Document 및 NCX 생성</li>
 *     <li>manifest 리소스 복사 또는 기록</li>
 *     <li>EPUB ZIP 패키징</li>
 *     <li>생성 결과 검증</li>
 *     <li>{@link EpubGenerationResult} 반환</li>
 * </ol>
 *
 * <p>예상 가능한 생성 실패는 가능하면
 * {@link EpubGenerationResult.Status#FAILED} 결과로 반환하고,
 * 요청 자체가 유효하지 않거나 생성 처리를 시작할 수 없는 경우에는
 * {@link EpubGenerationException}을 발생시키는 것이 적절합니다.</p>
 */
public interface EpubGenerator {

    /**
     * EPUB 파일을 생성합니다.
     *
     * @param request EPUB 생성 요청
     * @return EPUB 생성 결과
     * @throws EpubGenerationException EPUB 생성을 시작할 수 없거나
     *                                 치명적인 오류가 발생한 경우
     */
    EpubGenerationResult generate(EpubGenerationRequest request)
            throws EpubGenerationException;

    /**
     * 현재 생성기가 지정한 요청을 처리할 수 있는지 확인합니다.
     *
     * <p>기본 구현은 요청이 null이 아니면 {@code true}를 반환합니다.
     * 구현체는 지원 EPUB 버전, 리소스 유형, 출력 방식 등을 기준으로
     * 추가 조건을 적용할 수 있습니다.</p>
     *
     * @param request EPUB 생성 요청
     * @return 처리할 수 있으면 {@code true}
     */
    default boolean supports(EpubGenerationRequest request) {
        return request != null;
    }

    /**
     * 현재 생성기가 요청을 처리할 수 있는지 검증합니다.
     *
     * @param request EPUB 생성 요청
     * @throws EpubGenerationException 지원하지 않는 요청인 경우
     */
    default void validateSupport(EpubGenerationRequest request)
            throws EpubGenerationException {

        if (request == null) {
            throw new EpubGenerationException(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation request must not be null."
            );
        }

        if (!supports(request)) {
            throw new EpubGenerationException(
                    EpubGenerationException.ErrorCode.UNSUPPORTED_REQUEST,
                    "The EPUB generator does not support this request: "
                            + request.getRequestId()
            );
        }
    }

    /**
     * 생성기 구현체 이름을 반환합니다.
     *
     * @return 생성기 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}