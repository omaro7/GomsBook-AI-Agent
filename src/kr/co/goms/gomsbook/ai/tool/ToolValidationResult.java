/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Tool 요청 데이터의 검증 결과를 나타냅니다.
 *
 * <p>
 * {@link ToolRequest#validate()}에서 반환되며,
 * 검증 성공 여부와 발견된 Issue 목록을 함께 관리합니다.
 * </p>
 *
 * <p>
 * ERROR 또는 CRITICAL 수준의 Issue가 하나라도 존재하면
 * 검증 실패로 판단합니다.
 * WARNING과 INFO는 검증을 실패시키지 않습니다.
 * </p>
 */
public record ToolValidationResult(
        boolean valid,
        List<ToolIssue> issues
) implements Serializable {

    /**
     * 정규 생성자입니다.
     *
     * <p>
     * Issue 목록은 외부에서 변경되지 않도록 불변 목록으로 복사합니다.
     * {@code valid} 값과 실제 Issue 심각도가 일치하지 않으면,
     * ERROR 이상 Issue 존재 여부를 기준으로 보정합니다.
     * </p>
     */
    public ToolValidationResult {
        issues = issues == null
                ? List.of()
                : List.copyOf(issues);

        boolean containsError = issues.stream()
                .filter(Objects::nonNull)
                .map(ToolIssue::severity)
                .filter(Objects::nonNull)
                .anyMatch(ToolIssueSeverity::isError);

        valid = valid && !containsError;
    }

    /**
     * 검증 성공 결과를 생성합니다.
     *
     * @return Issue가 없는 성공 결과
     */
    public static ToolValidationResult success() {
        return new ToolValidationResult(
                true,
                List.of()
        );
    }

    /**
     * INFO 또는 WARNING Issue를 포함한 성공 결과를 생성합니다.
     *
     * <p>
     * ERROR 또는 CRITICAL Issue가 포함되어 있으면
     * 생성자에서 자동으로 실패 상태로 보정됩니다.
     * </p>
     *
     * @param issues Issue 목록
     * @return 검증 결과
     */
    public static ToolValidationResult successWithIssues(
            Collection<ToolIssue> issues
    ) {
        return new ToolValidationResult(
                true,
                sanitizeIssues(issues)
        );
    }

    /**
     * 하나 이상의 Issue를 포함한 검증 실패 결과를 생성합니다.
     *
     * @param issues 검증 Issue
     * @return 검증 실패 결과
     */
    public static ToolValidationResult failure(
            ToolIssue... issues
    ) {
        List<ToolIssue> issueList = issues == null
                ? List.of()
                : sanitizeIssues(Arrays.asList(issues));

        return new ToolValidationResult(
                false,
                issueList
        );
    }

    /**
     * Issue 목록을 포함한 검증 실패 결과를 생성합니다.
     *
     * @param issues 검증 Issue 목록
     * @return 검증 실패 결과
     */
    public static ToolValidationResult failure(
            Collection<ToolIssue> issues
    ) {
        return new ToolValidationResult(
                false,
                sanitizeIssues(issues)
        );
    }

    /**
     * ERROR 또는 CRITICAL Issue가 존재하는지 확인합니다.
     *
     * @return 오류 수준 Issue가 있으면 true
     */
    public boolean hasErrors() {
        return issues.stream()
                .filter(Objects::nonNull)
                .map(ToolIssue::severity)
                .filter(Objects::nonNull)
                .anyMatch(ToolIssueSeverity::isError);
    }

    /**
     * WARNING Issue가 존재하는지 확인합니다.
     *
     * @return WARNING Issue가 있으면 true
     */
    public boolean hasWarnings() {
        return issues.stream()
                .filter(Objects::nonNull)
                .map(ToolIssue::severity)
                .anyMatch(
                        severity ->
                                severity == ToolIssueSeverity.WARNING
                );
    }

    /**
     * CRITICAL Issue가 존재하는지 확인합니다.
     *
     * @return CRITICAL Issue가 있으면 true
     */
    public boolean hasCriticalErrors() {
        return issues.stream()
                .filter(Objects::nonNull)
                .map(ToolIssue::severity)
                .anyMatch(
                        severity ->
                                severity == ToolIssueSeverity.CRITICAL
                );
    }

    /**
     * 지정한 심각도의 Issue 개수를 반환합니다.
     *
     * @param severity 조회할 심각도
     * @return 해당 심각도의 Issue 개수
     */
    public long countBySeverity(
            ToolIssueSeverity severity
    ) {
        Objects.requireNonNull(
                severity,
                "severity must not be null."
        );

        return issues.stream()
                .filter(Objects::nonNull)
                .map(ToolIssue::severity)
                .filter(severity::equals)
                .count();
    }

    /**
     * 현재 결과에 다른 검증 결과를 병합합니다.
     *
     * @param other 병합할 검증 결과
     * @return 병합된 새로운 검증 결과
     */
    public ToolValidationResult merge(
            ToolValidationResult other
    ) {
        if (other == null) {
            return this;
        }

        List<ToolIssue> mergedIssues =
                new ArrayList<>(
                        this.issues.size()
                                + other.issues.size()
                );

        mergedIssues.addAll(this.issues);
        mergedIssues.addAll(other.issues);

        return new ToolValidationResult(
                this.valid && other.valid,
                mergedIssues
        );
    }

    /**
     * null Issue를 제거하고 불변 목록으로 반환합니다.
     *
     * @param issues 원본 Issue 목록
     * @return 정제된 Issue 목록
     */
    private static List<ToolIssue> sanitizeIssues(
            Collection<ToolIssue> issues
    ) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        return issues.stream()
                .filter(Objects::nonNull)
                .toList();
    }
}