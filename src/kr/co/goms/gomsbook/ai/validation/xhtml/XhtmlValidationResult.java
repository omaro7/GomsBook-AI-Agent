/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.validation.xhtml;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;

/**
 * XHTML 문서 검증 결과입니다.
 *
 * <p>XHTML 문서가 유효한지 여부와 검증 과정에서 발견된
 * 이슈 목록을 포함합니다.</p>
 *
 * <p>기존 record 스타일 접근자인 {@link #valid()}과
 * JavaBean 스타일 접근자인 {@link #isValid()}을 모두 제공합니다.</p>
 */
public final class XhtmlValidationResult {

    private final boolean valid;
    private final List<String> issues;

    /**
     * 검증 결과를 생성합니다.
     *
     * @param valid XHTML 유효 여부
     */
    public XhtmlValidationResult(boolean valid) {
        this(
                valid,
                List.of()
        );
    }

    /**
     * 검증 결과를 생성합니다.
     *
     * @param valid  XHTML 유효 여부
     * @param issues 검증 이슈 목록
     */
    public XhtmlValidationResult(
            boolean valid,
            List<String> issues) {

        this.valid = valid;
        this.issues = immutableIssues(issues);
    }

    /**
     * 단일 이슈를 포함하는 검증 결과를 생성합니다.
     *
     * @param valid XHTML 유효 여부
     * @param issue 검증 이슈
     */
    public XhtmlValidationResult(
            boolean valid,
            String issue) {

        this(
                valid,
                issue == null || issue.isBlank()
                        ? List.of()
                        : List.of(issue.trim())
        );
    }

    /**
     * Builder를 생성합니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기존 결과를 기반으로 Builder를 생성합니다.
     */
    public static Builder builder(
            XhtmlValidationResult source) {

        Objects.requireNonNull(
                source,
                "source must not be null"
        );

        return new Builder(source);
    }

    /**
     * 검증 성공 결과를 생성합니다.
     */
    public static XhtmlValidationResult validResult() {
        return new XhtmlValidationResult(
                true,
                List.of()
        );
    }

    /**
     * 검증 성공 결과를 생성합니다.
     *
     * @param issues 경고 또는 참고 이슈
     */
    public static XhtmlValidationResult validResult(
            List<String> issues) {

        return new XhtmlValidationResult(
                true,
                issues
        );
    }

    /**
     * 검증 실패 결과를 생성합니다.
     *
     * @param issue 실패 사유
     */
    public static XhtmlValidationResult invalid(
            String issue) {

        return new XhtmlValidationResult(
                false,
                issue
        );
    }

    /**
     * 검증 실패 결과를 생성합니다.
     *
     * @param issues 검증 실패 이슈 목록
     */
    public static XhtmlValidationResult invalid(
            List<String> issues) {

        return new XhtmlValidationResult(
                false,
                issues
        );
    }

    // =========================================================
    // JavaBean 스타일
    // =========================================================

    /**
     * XHTML 문서가 유효한지 반환합니다.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 검증 이슈 목록을 반환합니다.
     *
     * @return 수정할 수 없는 이슈 목록
     */
    public List<String> getIssues() {
        return issues;
    }

    // =========================================================
    // 기존 record 스타일 호환
    // =========================================================

    /**
     * 기존 record 스타일 접근자 호환용입니다.
     */
    public boolean valid() {
        return valid;
    }

    /**
     * 기존 record 스타일 접근자 호환용입니다.
     */
    public List<String> issues() {
        return issues;
    }

    // =========================================================
    // 편의 메서드
    // =========================================================

    /**
     * 검증 실패 여부를 반환합니다.
     */
    public boolean isInvalid() {
        return !valid;
    }

    /**
     * 검증 이슈가 존재하는지 확인합니다.
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * 검증 이슈 개수를 반환합니다.
     */
    public int getIssueCount() {
        return issues.size();
    }

    /**
     * 첫 번째 검증 이슈를 반환합니다.
     *
     * @return 첫 번째 이슈 또는 {@code null}
     */
    public String getFirstIssue() {
        if (issues.isEmpty()) {
            return null;
        }

        return issues.get(0);
    }

    /**
     * 현재 결과에 이슈를 추가한 새로운 결과를 생성합니다.
     */
    public XhtmlValidationResult withIssue(
            String issue) {

        if (issue == null || issue.isBlank()) {
            return this;
        }

        List<String> copied =
                new ArrayList<>(issues);

        copied.add(issue.trim());

        return new XhtmlValidationResult(
                valid,
                copied
        );
    }

    /**
     * 기존 결과를 기반으로 Builder를 반환합니다.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    private static List<String> immutableIssues(
            List<String> source) {

        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<String> copied =
                new ArrayList<>(source.size());

        for (String issue : source) {
            if (issue == null || issue.isBlank()) {
                continue;
            }

            copied.add(issue.trim());
        }

        return Collections.unmodifiableList(copied);
    }

    @Override
    public String toString() {
        return "XhtmlValidationResult{"
                + "valid=" + valid
                + ", issueCount=" + issues.size()
                + ", issues=" + issues
                + '}';
    }

    /**
     * XhtmlValidationResult Builder입니다.
     */
    public static final class Builder {

        private boolean valid = true;

        private final List<String> issues =
                new ArrayList<>();

        private Builder() {
        }

        private Builder(
                XhtmlValidationResult source) {

            this.valid = source.valid;
            this.issues.addAll(source.issues);
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder issue(String issue) {
            if (issue == null || issue.isBlank()) {
                return this;
            }

            this.issues.add(issue.trim());

            return this;
        }

        public Builder issues(List<String> issues) {
            if (issues == null) {
                return this;
            }

            for (String issue : issues) {
                issue(issue);
            }

            return this;
        }

        public Builder clearIssues() {
            this.issues.clear();
            return this;
        }

        public XhtmlValidationResult build() {
            return new XhtmlValidationResult(
                    valid,
                    issues
            );
        }
    }
}