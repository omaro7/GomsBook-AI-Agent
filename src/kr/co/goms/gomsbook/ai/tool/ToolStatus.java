/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

/**
 * Tool 실행 상태를 나타냅니다.
 *
 * <p>
 * ToolExecutor는 실행 결과를 ToolResult로 반환하며,
 * 실제 상태는 ToolStatus로 구분합니다.
 * </p>
 * 
 * ToolResult<XhtmlGenerationResponse> result = tool.execute(context, request);
 *		if (result.status() == ToolStatus.SUCCESS) {
 *   		editor.showPreview(result.response().xhtml());
 *		}
 */
public enum ToolStatus {

    /**
     * 정상적으로 완료되었습니다.
     */
    SUCCESS,

    /**
     * 입력값 검증에 실패했습니다.
     */
    VALIDATION_FAILED,

    /**
     * Tool 실행 중 오류가 발생했습니다.
     */
    FAILED,

    /**
     * 사용자가 작업을 취소했습니다.
     */
    CANCELLED,

    /**
     * 사용자 승인이 필요한 상태입니다.
     */
    WAITING_FOR_APPROVAL,

    /**
     * 아직 실행되지 않았습니다.
     */
    NOT_EXECUTED
}