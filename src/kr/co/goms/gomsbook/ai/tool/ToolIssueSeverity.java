/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

/**
 * ToolIssue의 심각도를 나타냅니다.
 *
 * <p>
 * 심각도는 Validation, Prompt, LLM, RAG 등
 * AI Framework 전체에서 공통으로 사용됩니다.
 * </p>
 * 
 * new ToolIssue(
    "XHTML001",
    ToolIssueSeverity.ERROR,
    "Duplicate id detected.",
    "Paragraph id 'p_01' already exists.",
    "chapter01.xhtml",
    32,
    5,
    Map.of("id", "p_01")
);

new ToolIssue(
    "LLM001",
    ToolIssueSeverity.CRITICAL,
    "Failed to connect to Ollama.",
    "Connection timeout.",
    null,
    null,
    null,
    Map.of("host", "localhost:11434")
);

 */
public enum ToolIssueSeverity {

    /**
     * 참고용 정보입니다.
     *
     * 예:
     * <ul>
     *   <li>AI 응답 시간이 다소 길었습니다.</li>
     *   <li>캐시를 사용했습니다.</li>
     * </ul>
     */
    INFO,

    /**
     * 작업은 계속 진행할 수 있지만
     * 확인이 필요한 사항입니다.
     *
     * 예:
     * <ul>
     *   <li>이미지 Alt 속성이 없습니다.</li>
     *   <li>긴 문장이 감지되었습니다.</li>
     * </ul>
     */
    WARNING,

    /**
     * 오류가 발생하여
     * 정상적인 결과를 생성할 수 없습니다.
     *
     * 예:
     * <ul>
     *   <li>XHTML Parsing Error</li>
     *   <li>잘못된 XML 구조</li>
     * </ul>
     */
    ERROR,

    /**
     * 시스템 수준의 심각한 오류입니다.
     *
     * 예:
     * <ul>
     *   <li>LLM 연결 실패</li>
     *   <li>Vector DB 손상</li>
     *   <li>프로젝트 파일 접근 실패</li>
     * </ul>
     */
    CRITICAL;

    /**
     * ERROR 이상 여부를 반환합니다.
     *
     * @return ERROR 또는 CRITICAL이면 true
     */
    public boolean isError() {
        return this == ERROR || this == CRITICAL;
    }

    /**
     * WARNING 이상 여부를 반환합니다.
     *
     * @return WARNING, ERROR, CRITICAL이면 true
     */
    public boolean isWarningOrHigher() {
        return this != INFO;
    }

    /**
     * 치명적인 오류 여부를 반환합니다.
     *
     * @return CRITICAL이면 true
     */
    public boolean isCritical() {
        return this == CRITICAL;
    }
}