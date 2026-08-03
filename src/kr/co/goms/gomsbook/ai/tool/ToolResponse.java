/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;

/**
 * 모든 GomsBook AI Agent Tool 응답 객체가 구현하는 공통 인터페이스입니다.
 *
 * <p>
 * Tool의 실행 결과는 반드시 ToolResponse를 구현하는
 * 불변 객체(record 또는 immutable class)로 반환하는 것을 권장합니다.
 * </p>
 *
 * <p>
 * 실제 성공 여부와 오류 정보는 ToolResult에서 관리하며,
 * ToolResponse는 순수한 비즈니스 데이터만 포함합니다.
 * </p>
 */
public interface ToolResponse extends Serializable {

}