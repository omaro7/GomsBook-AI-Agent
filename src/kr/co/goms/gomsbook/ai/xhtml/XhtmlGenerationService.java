/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.xhtml;

import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationRequest;
import kr.co.goms.gomsbook.ai.dto.xhtml.XhtmlGenerationResponse;

/**
 * 사용자 요청을 기반으로 XHTML 문서를 생성하는 서비스입니다.
 *
 * <p>구현체는 일반적으로 다음 절차를 수행합니다.</p>
 * <ol>
 *     <li>XHTML 생성 요청 검증</li>
 *     <li>LLM Prompt 생성</li>
 *     <li>LLM 호출</li>
 *     <li>응답에서 XHTML 추출 및 정규화</li>
 *     <li>XHTML 유효성 검증</li>
 *     <li>생성 결과 반환</li>
 * </ol>
 */
public interface XhtmlGenerationService {

    /**
     * 요청 내용을 기반으로 XHTML 문서를 생성합니다.
     *
     * @param request XHTML 생성 요청
     * @return XHTML 생성 결과
     * @throws XhtmlGenerationException 생성 과정에서 오류가 발생한 경우
     */
    XhtmlGenerationResponse generate(
            XhtmlGenerationRequest request
    );

    /**
     * 이 서비스가 현재 XHTML 생성을 수행할 수 있는지 확인합니다.
     *
     * <p>구현체에서는 LLM 서버 연결 상태, 모델 설정 및 필수 구성값 등을
     * 기준으로 가용성을 판단할 수 있습니다.</p>
     *
     * @return XHTML 생성이 가능하면 {@code true}
     */
    default boolean isAvailable() {
        return true;
    }
}