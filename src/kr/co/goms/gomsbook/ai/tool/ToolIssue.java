/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;
import java.util.Map;

/**
 * Tool 실행 중 발생한 오류, 경고 또는 정보를 표현합니다.
 *
 * <p>
 * ToolIssue는 Validation, LLM, Prompt, RAG 등
 * AI Framework 전체에서 공통으로 사용하는 Issue 모델입니다.
 * </p>
 */
public record ToolIssue(

        /**
         * Issue 코드
         * (예: XHTML001, LLM001)
         */
        String code,

        /**
         * Issue 심각도
         */
        ToolIssueSeverity severity,

        /**
         * 사용자에게 표시할 메시지
         */
        String message,

        /**
         * 상세 설명
         */
        String detail,

        /**
         * Issue 발생 위치
         * (예: chapter01.xhtml)
         */
        String location,

        /**
         * Line Number
         */
        Integer line,

        /**
         * Column Number
         */
        Integer column,

        /**
         * 추가 정보
         */
        Map<String, Object> attributes

) implements Serializable {

    public ToolIssue {

        attributes = attributes == null
                ? Map.of()
                : Map.copyOf(attributes);
    }

}