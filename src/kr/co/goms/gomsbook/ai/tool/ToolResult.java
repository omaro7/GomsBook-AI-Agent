/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 모든 Tool 실행 결과를 나타냅니다.
 *
 * <p>
 * ToolResponse는 순수한 비즈니스 데이터만 포함하며,
 * ToolResult는 실행 상태, Issue, 실행 시간 등의
 * 공통 정보를 함께 관리합니다.
 * </p>
 *
 * @param <T> ToolResponse 타입
 * 
 * ToolResult<XhtmlGenerationResponse> result = tool.execute(context, request);
	if(result.isSuccess()){
    	String xhtml =  result.response().xhtml();
	}
 */
public record ToolResult<T extends ToolResponse>(

        /**
         * 실행 ID
         */
        String executionId,

        /**
         * 요청 ID
         */
        String requestId,

        /**
         * Tool 이름
         */
        String toolName,

        /**
         * Tool 버전
         */
        String toolVersion,

        /**
         * 실행 상태
         */
        ToolStatus status,

        /**
         * 응답 데이터
         */
        T response,

        /**
         * Issue 목록
         */
        List<ToolIssue> issues,

        /**
         * 시작 시간
         */
        Instant startedAt,

        /**
         * 종료 시간
         */
        Instant completedAt,

        /**
         * 실행 시간
         */
        Duration duration,

        /**
         * 추가 메타데이터
         */
        Map<String, Object> attributes

) implements Serializable {

    public ToolResult {

        executionId = executionId == null
                ? UUID.randomUUID().toString()
                : executionId;

        Objects.requireNonNull(requestId, "requestId");

        Objects.requireNonNull(toolName, "toolName");

        toolVersion = toolVersion == null
                ? "1.0.0"
                : toolVersion;

        Objects.requireNonNull(status, "status");

        issues = issues == null
                ? List.of()
                : List.copyOf(issues);

        attributes = attributes == null
                ? Map.of()
                : Map.copyOf(attributes);
    }

    /**
     * 성공 결과 생성
     */
    public static <T extends ToolResponse> ToolResult<T> success(
            String requestId,
            String toolName,
            String toolVersion,
            T response,
            Instant startedAt,
            Instant completedAt
    ) {

        return new ToolResult<>(

                UUID.randomUUID().toString(),

                requestId,

                toolName,

                toolVersion,

                ToolStatus.SUCCESS,

                response,

                List.of(),

                startedAt,

                completedAt,

                Duration.between(startedAt, completedAt),

                Map.of()
        );
    }

    /**
     * 실패 결과 생성
     */
    public static <T extends ToolResponse> ToolResult<T> failure(
            String requestId,
            String toolName,
            String toolVersion,
            ToolStatus status,
            List<ToolIssue> issues,
            Instant startedAt,
            Instant completedAt
    ) {

        return new ToolResult<>(

                UUID.randomUUID().toString(),

                requestId,

                toolName,

                toolVersion,

                status,

                null,

                issues,

                startedAt,

                completedAt,

                Duration.between(startedAt, completedAt),

                Map.of()
        );
    }

    /**
     * 성공 여부
     */
    public boolean isSuccess() {
        return status == ToolStatus.SUCCESS;
    }

    /**
     * Issue 존재 여부
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * ERROR 이상 존재 여부
     */
    public boolean hasErrors() {

        return issues.stream()

                .map(ToolIssue::severity)

                .filter(Objects::nonNull)

                .anyMatch(ToolIssueSeverity::isError);
    }

    /**
     * 실행 시간(ms)
     */
    public long executionTimeMillis() {

        return duration == null
                ? 0L
                : duration.toMillis();
    }

    /**
     * WARNING 존재 여부
     */
    public boolean hasWarnings() {

        return issues.stream()

                .map(ToolIssue::severity)

                .filter(Objects::nonNull)

                .anyMatch(
                        severity ->
                                severity == ToolIssueSeverity.WARNING
                );
    }

    /**
     * CRITICAL 존재 여부
     */
    public boolean hasCriticalErrors() {

        return issues.stream()

                .map(ToolIssue::severity)

                .filter(Objects::nonNull)

                .anyMatch(ToolIssueSeverity::isCritical);
    }

}