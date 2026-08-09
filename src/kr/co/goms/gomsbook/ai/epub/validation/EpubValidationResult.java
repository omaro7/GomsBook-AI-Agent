/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue.Category;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue.Severity;

/**
 * EPUB 검증 작업의 전체 결과를 표현합니다.
 *
 * <p>내부 EPUB 구조 검증, XHTML 검증, 접근성 검증,
 * EPUBCheck 등의 결과를 공통 형식으로 집계합니다.</p>
 *
 * <p>주요 정보는 다음과 같습니다.</p>
 *
 * <ul>
 *     <li>검증 수행 여부</li>
 *     <li>검증 성공 여부</li>
 *     <li>전체 이슈 목록</li>
 *     <li>FATAL / ERROR / WARNING / INFO 개수</li>
 *     <li>검증기 이름과 버전</li>
 *     <li>검증 시작/종료 시각</li>
 *     <li>검증 대상 정보</li>
 * </ul>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubValidationResult {

    /**
     * 검증 작업의 상태입니다.
     */
    private final Status status;

    /**
     * 검증기 이름입니다.
     */
    private final String validatorName;

    /**
     * 검증기 버전입니다.
     */
    private final String validatorVersion;

    /**
     * 검증 대상 EPUB 내부 또는 로컬 경로입니다.
     */
    private final String target;

    /**
     * 검증 시작 시각입니다.
     */
    private final Instant startedAt;

    /**
     * 검증 종료 시각입니다.
     */
    private final Instant completedAt;

    /**
     * 검증 이슈 목록입니다.
     */
    private final List<EpubValidationIssue> issues;

    /**
     * 심각도별 이슈 개수입니다.
     */
    private final Map<Severity, Integer> severityCounts;

    /**
     * 카테고리별 이슈 개수입니다.
     */
    private final Map<Category, Integer> categoryCounts;

    /**
     * 검증 결과 메시지입니다.
     */
    private final String message;

    /**
     * 검증 실패 원인 예외입니다.
     */
    private final Throwable cause;

    /**
     * 검증 수행 중 일부 검사를 생략했는지 여부입니다.
     */
    private final boolean partial;

    private EpubValidationResult(Builder builder) {
        this.validatorName =
                normalizeOptionalText(builder.validatorName);

        this.validatorVersion =
                normalizeOptionalText(builder.validatorVersion);

        this.target =
                normalizeOptionalText(builder.target);

        this.startedAt =
                builder.startedAt;

        this.completedAt =
                builder.completedAt;

        this.issues =
                immutableIssues(builder.issues);

        this.severityCounts =
                createSeverityCounts(this.issues);

        this.categoryCounts =
                createCategoryCounts(this.issues);

        this.message =
                normalizeOptionalText(builder.message);

        this.cause =
                builder.cause;

        this.partial =
                builder.partial;

        this.status =
                builder.status == null
                        ? resolveStatus(
                                builder.performed,
                                builder.partial,
                                this.issues,
                                builder.cause
                        )
                        : builder.status;

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 검증 미실행 결과를 생성합니다.
     */
    public static EpubValidationResult notPerformed() {
        return builder()
                .performed(false)
                .status(Status.NOT_PERFORMED)
                .message("EPUB validation was not performed.")
                .build();
    }

    /**
     * 검증 성공 결과를 생성합니다.
     */
    public static EpubValidationResult success(
            String validatorName
    ) {
        return builder()
                .performed(true)
                .validatorName(validatorName)
                .status(Status.PASSED)
                .message("EPUB validation completed successfully.")
                .build();
    }

    /**
     * 단일 오류 이슈를 포함한 실패 결과를 생성합니다.
     */
    public static EpubValidationResult failure(
            String validatorName,
            EpubValidationIssue issue
    ) {
        return builder()
                .performed(true)
                .validatorName(validatorName)
                .issue(issue)
                .status(Status.FAILED)
                .build();
    }

    /**
     * 예외로 인한 검증 실패 결과를 생성합니다.
     */
    public static EpubValidationResult failure(
            String validatorName,
            Throwable cause
    ) {
        return builder()
                .performed(true)
                .validatorName(validatorName)
                .cause(cause)
                .status(Status.FAILED)
                .message(
                        cause == null
                                ? "EPUB validation failed."
                                : cause.getMessage()
                )
                .build();
    }

    public Status getStatus() {
        return status;
    }

    public Optional<String> getValidatorName() {
        return Optional.ofNullable(
                validatorName
        );
    }

    public Optional<String> getValidatorVersion() {
        return Optional.ofNullable(
                validatorVersion
        );
    }

    public Optional<String> getTarget() {
        return Optional.ofNullable(target);
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public List<EpubValidationIssue> getIssues() {
        return issues;
    }

    public Map<Severity, Integer> getSeverityCounts() {
        return severityCounts;
    }

    public Map<Category, Integer> getCategoryCounts() {
        return categoryCounts;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<Throwable> getCause() {
        return Optional.ofNullable(cause);
    }

    public boolean isPartial() {
        return partial;
    }

    /**
     * 검증이 수행되었는지 확인합니다.
     */
    public boolean isPerformed() {
        return status != Status.NOT_PERFORMED;
    }

    /**
     * 검증을 통과했는지 확인합니다.
     *
     * <p>경고가 포함된 성공도 {@code true}입니다.</p>
     */
    public boolean isPassed() {
        return status == Status.PASSED
                || status == Status.PASSED_WITH_WARNINGS;
    }

    /**
     * 완전히 성공했는지 확인합니다.
     */
    public boolean isCompleteSuccess() {
        return status == Status.PASSED;
    }

    /**
     * 오류가 존재하는지 확인합니다.
     */
    public boolean hasErrors() {
        return getErrorCount() > 0
                || getFatalCount() > 0
                || cause != null;
    }

    /**
     * 경고가 존재하는지 확인합니다.
     */
    public boolean hasWarnings() {
        return getWarningCount() > 0;
    }

    /**
     * 차단 수준 이슈가 존재하는지 확인합니다.
     */
    public boolean hasBlockingIssues() {
        return issues.stream()
                .anyMatch(
                        EpubValidationIssue::isBlocking
                );
    }

    public int getIssueCount() {
        return issues.size();
    }

    public int getFatalCount() {
        return getSeverityCount(
                Severity.FATAL
        );
    }

    public int getErrorCount() {
        return getSeverityCount(
                Severity.ERROR
        );
    }

    public int getWarningCount() {
        return getSeverityCount(
                Severity.WARNING
        );
    }

    public int getInfoCount() {
        return getSeverityCount(
                Severity.INFO
        );
    }

    public int getBlockingIssueCount() {
        return (int) issues.stream()
                .filter(EpubValidationIssue::isBlocking)
                .count();
    }

    public int getAutoFixableIssueCount() {
        return (int) issues.stream()
                .filter(
                        EpubValidationIssue::isAutoFixable
                )
                .count();
    }

    public int getSeverityCount(
            Severity severity
    ) {
        if (severity == null) {
            return 0;
        }

        return severityCounts.getOrDefault(
                severity,
                0
        );
    }

    public int getCategoryCount(
            Category category
    ) {
        if (category == null) {
            return 0;
        }

        return categoryCounts.getOrDefault(
                category,
                0
        );
    }

    /**
     * 지정한 심각도의 이슈만 반환합니다.
     */
    public List<EpubValidationIssue> getIssues(
            Severity severity
    ) {
        if (severity == null) {
            return Collections.emptyList();
        }

        return issues.stream()
                .filter(issue ->
                        issue.getSeverity() == severity
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 지정한 카테고리의 이슈만 반환합니다.
     */
    public List<EpubValidationIssue> getIssues(
            Category category
    ) {
        if (category == null) {
            return Collections.emptyList();
        }

        return issues.stream()
                .filter(issue ->
                        issue.getCategory() == category
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 지정한 최소 심각도 이상의 이슈를 반환합니다.
     */
    public List<EpubValidationIssue> getIssuesAtLeast(
            Severity severity
    ) {
        Objects.requireNonNull(
                severity,
                "Validation severity must not be null."
        );

        return issues.stream()
                .filter(issue ->
                        issue.getSeverity()
                                .isAtLeast(severity)
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 자동 수정 가능한 이슈 목록을 반환합니다.
     */
    public List<EpubValidationIssue> getAutoFixableIssues() {
        return issues.stream()
                .filter(
                        EpubValidationIssue::isAutoFixable
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 검증 소요 시간을 반환합니다.
     */
    public Optional<Duration> getDuration() {
        if (startedAt == null
                || completedAt == null) {
            return Optional.empty();
        }

        return Optional.of(
                Duration.between(
                        startedAt,
                        completedAt
                )
        );
    }

    public long getDurationMillis() {
        return getDuration()
                .map(Duration::toMillis)
                .orElse(0L);
    }

    /**
     * 가장 높은 심각도를 반환합니다.
     */
    public Optional<Severity> getHighestSeverity() {
        return issues.stream()
                .map(EpubValidationIssue::getSeverity)
                .max(
                        Comparator.comparingInt(
                                Severity::getLevel
                        )
                );
    }

    /**
     * 사용자 화면용 검증 요약 문자열을 반환합니다.
     */
    public String getSummary() {
        return status.getDisplayName()
                + " [fatal="
                + getFatalCount()
                + ", errors="
                + getErrorCount()
                + ", warnings="
                + getWarningCount()
                + ", info="
                + getInfoCount()
                + ", total="
                + getIssueCount()
                + "]";
    }

    /**
     * EpubGenerationResult에서 사용할 요약 객체로 변환할 때
     * 필요한 값들을 직접 조회할 수 있습니다.
     */
    public boolean canGenerateEpub() {
        return isPerformed()
                && !hasBlockingIssues()
                && cause == null;
    }

    /**
     * 현재 결과를 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .status(status)
                .performed(isPerformed())
                .validatorName(validatorName)
                .validatorVersion(validatorVersion)
                .target(target)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .issues(issues)
                .message(message)
                .cause(cause)
                .partial(partial);
    }

    private void validate() {
        if (startedAt != null
                && completedAt != null
                && completedAt.isBefore(startedAt)) {

            throw new IllegalArgumentException(
                    "EPUB validation completion time "
                            + "must not precede the start time."
            );
        }

        if (status == Status.NOT_PERFORMED
                && !issues.isEmpty()) {
            throw new IllegalArgumentException(
                    "A validation result that was not performed "
                            + "must not contain issues."
            );
        }

        if (status == Status.PASSED
                && hasErrors()) {
            throw new IllegalArgumentException(
                    "A passed EPUB validation result "
                            + "must not contain errors."
            );
        }

        if (status == Status.PASSED
                && hasWarnings()) {
            throw new IllegalArgumentException(
                    "Validation with warnings must use "
                            + "PASSED_WITH_WARNINGS."
            );
        }

        if (status == Status.PASSED_WITH_WARNINGS
                && !hasWarnings()) {
            throw new IllegalArgumentException(
                    "PASSED_WITH_WARNINGS requires "
                            + "at least one warning."
            );
        }

        if (status == Status.FAILED
                && !hasErrors()) {
            throw new IllegalArgumentException(
                    "FAILED validation result requires "
                            + "an error, fatal issue, or exception."
            );
        }
    }

    private static Status resolveStatus(
            boolean performed,
            boolean partial,
            List<EpubValidationIssue> issues,
            Throwable cause
    ) {
        if (!performed) {
            return Status.NOT_PERFORMED;
        }

        if (cause != null) {
            return Status.FAILED;
        }

        boolean blocking =
                issues.stream()
                        .anyMatch(
                                EpubValidationIssue::isBlocking
                        );

        if (blocking) {
            return Status.FAILED;
        }

        if (partial) {
            return Status.PARTIAL;
        }

        boolean warnings =
                issues.stream()
                        .anyMatch(
                                EpubValidationIssue::isWarning
                        );

        if (warnings) {
            return Status.PASSED_WITH_WARNINGS;
        }

        return Status.PASSED;
    }

    private static List<EpubValidationIssue> immutableIssues(
            Collection<EpubValidationIssue> values
    ) {

        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubValidationIssue> result =
                new ArrayList<>();

        for (EpubValidationIssue issue : values) {

            result.add(
                    Objects.requireNonNull(
                            issue,
                            "EPUB validation issue must not be null."
                    )
            );
        }

        result.sort(
                Comparator
                        .comparingInt(
                                (EpubValidationIssue issue) ->
                                        -issue.getSeverity()
                                                .getLevel()
                        )
                        .thenComparing(
                                EpubValidationIssue::getCode
                        )
        );

        return Collections.unmodifiableList(
                result
        );
    }

    private static Map<Severity, Integer>
            createSeverityCounts(
                    List<EpubValidationIssue> issues
            ) {

        Map<Severity, Integer> result =
                new EnumMap<>(Severity.class);

        for (Severity severity : Severity.values()) {
            result.put(severity, 0);
        }

        for (EpubValidationIssue issue : issues) {
            result.merge(
                    issue.getSeverity(),
                    1,
                    Integer::sum
            );
        }

        return Collections.unmodifiableMap(
                result
        );
    }

    private static Map<Category, Integer>
            createCategoryCounts(
                    List<EpubValidationIssue> issues
            ) {

        Map<Category, Integer> result =
                new EnumMap<>(Category.class);

        for (Category category : Category.values()) {
            result.put(category, 0);
        }

        for (EpubValidationIssue issue : issues) {
            result.merge(
                    issue.getCategory(),
                    1,
                    Integer::sum
            );
        }

        return Collections.unmodifiableMap(
                result
        );
    }

    private static String normalizeOptionalText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubValidationResult{"
                + "status=" + status
                + ", validatorName='"
                + validatorName + '\''
                + ", target='"
                + target + '\''
                + ", issueCount="
                + getIssueCount()
                + ", fatalCount="
                + getFatalCount()
                + ", errorCount="
                + getErrorCount()
                + ", warningCount="
                + getWarningCount()
                + ", infoCount="
                + getInfoCount()
                + ", durationMillis="
                + getDurationMillis()
                + ", partial="
                + partial
                + '}';
    }

    /**
     * EPUB 검증 작업 상태입니다.
     */
    public enum Status {

        /**
         * 검증이 수행되지 않았습니다.
         */
        NOT_PERFORMED("미실행"),

        /**
         * 오류와 경고 없이 검증을 통과했습니다.
         */
        PASSED("통과"),

        /**
         * 경고가 있지만 검증을 통과했습니다.
         */
        PASSED_WITH_WARNINGS("경고 포함 통과"),

        /**
         * 일부 검증만 수행되었습니다.
         */
        PARTIAL("부분 검증"),

        /**
         * 검증에 실패했습니다.
         */
        FAILED("실패");

        private final String displayName;

        Status(String displayName) {
            this.displayName =
                    displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isSuccess() {
            return this == PASSED
                    || this == PASSED_WITH_WARNINGS;
        }

        public boolean isPerformed() {
            return this != NOT_PERFORMED;
        }
    }

    /**
     * {@link EpubValidationResult} Builder입니다.
     */
    public static final class Builder {

        private Status status;

        private boolean performed = true;

        private String validatorName;

        private String validatorVersion;

        private String target;

        private Instant startedAt;

        private Instant completedAt;

        private final List<EpubValidationIssue> issues =
                new ArrayList<>();

        private String message;

        private Throwable cause;

        private boolean partial;

        private Builder() {
        }

        public Builder status(
                Status status
        ) {
            this.status = status;
            return this;
        }

        public Builder performed(
                boolean performed
        ) {
            this.performed = performed;
            return this;
        }

        public Builder validatorName(
                String validatorName
        ) {
            this.validatorName =
                    validatorName;
            return this;
        }

        public Builder validatorVersion(
                String validatorVersion
        ) {
            this.validatorVersion =
                    validatorVersion;
            return this;
        }

        public Builder target(
                String target
        ) {
            this.target = target;
            return this;
        }

        public Builder startedAt(
                Instant startedAt
        ) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(
                Instant completedAt
        ) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder startNow() {
            this.startedAt = Instant.now();
            return this;
        }

        public Builder completeNow() {
            this.completedAt = Instant.now();
            return this;
        }

        public Builder issue(
                EpubValidationIssue issue
        ) {
            issues.add(
                    Objects.requireNonNull(
                            issue,
                            "EPUB validation issue "
                                    + "must not be null."
                    )
            );

            return this;
        }

        public Builder issues(
                Collection<EpubValidationIssue> issues
        ) {
            if (issues == null) {
                return this;
            }

            for (EpubValidationIssue issue : issues) {
                issue(issue);
            }

            return this;
        }

        public Builder error(
                String code,
                String message
        ) {
            return issue(
                    EpubValidationIssue.error(
                            code,
                            message
                    )
            );
        }

        public Builder warning(
                String code,
                String message
        ) {
            return issue(
                    EpubValidationIssue.warning(
                            code,
                            message
                    )
            );
        }

        public Builder info(
                String code,
                String message
        ) {
            return issue(
                    EpubValidationIssue.info(
                            code,
                            message
                    )
            );
        }

        public Builder message(
                String message
        ) {
            this.message = message;
            return this;
        }

        public Builder cause(
                Throwable cause
        ) {
            this.cause = cause;
            return this;
        }

        public Builder partial(
                boolean partial
        ) {
            this.partial = partial;
            return this;
        }

        /**
         * 상태를 이슈 목록에서 자동 결정하도록 설정합니다.
         */
        public Builder resolveStatus() {
            this.status = null;
            return this;
        }

        public EpubValidationResult build() {
            return new EpubValidationResult(this);
        }
    }
}