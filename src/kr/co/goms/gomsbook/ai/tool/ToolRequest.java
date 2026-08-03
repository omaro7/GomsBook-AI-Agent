/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;

/**
 * 모든 GomsBook AI Agent Tool 요청 객체가 구현하는 공통 인터페이스입니다.
 *
 * <p>
 * 실제 요청 데이터는 불변 {@code record} 또는 불변 클래스로 구현하는 것을
 * 권장합니다.
 * </p>
 *
 * <p>
 * 각 요청 객체는 필요할 경우 {@link #validate()}를 재정의하여
 * Tool 실행 전에 자체 입력값을 검증할 수 있습니다.
 * </p>
 */
public interface ToolRequest extends Serializable {

    /**
     * Tool 실행 전에 요청 데이터를 검증합니다.
     *
     * <p>
     * 별도의 검증이 필요하지 않은 요청은 기본 구현을 그대로 사용할 수 있습니다.
     * </p>
     *
     * @return 입력값 검증 결과
     */
    default ToolValidationResult validate() {
        return ToolValidationResult.success();
    }
}