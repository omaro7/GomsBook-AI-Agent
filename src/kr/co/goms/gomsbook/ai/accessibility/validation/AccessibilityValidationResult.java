/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 접근성 검사 전체 결과를 나타낸다.
 *
 * <p>검사 대상 문서, 발견된 문제 목록, 심각도별 집계,
 * 검사 시간과 실행 메타데이터를 포함하는 불변 객체이다.</p>
 */
public final class AccessibilityValidationResult {

    private static final Comparator<AccessibilityIssue> ISSUE_ORDER =
            Comparator
                    .comparingInt(
                            (AccessibilityIssue issue) ->
                                    issue.getSeverity().getPriority()
                    )
                    .reversed()
                    .thenComparing(
                            issue -> issue.getCode().getCode()
                    )
                    .thenComparing(
                            AccessibilityValidationResult
                                    ::locationSortKey
                    );

    private final Path projectRoot;
    private final Path documentPath;
    private final String projectRelativePath;

    private final boolean validationCompleted;
    private final List<AccessibilityIssue> issues;
    private final List<String> warnings;

    private final int errorCount;
    private final int warningCount;
    private final int infoCount;
    private final int automaticallyFixableCount;
    private final int manualReviewCount;

    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;

    private final String validatorName;
    private final Map<String, String> metadata;

    private AccessibilityValidationResult(Builder builder) {

        this.projectRoot = normalizeOptionalPath(
                builder.projectRoot
        );

        this.documentPath = normalizeOptionalPath(
                builder.documentPath
        );

        validateDocumentPath(
                projectRoot,
                documentPath
        );

        this.projectRelativePath =
                resolveProjectRelativePath(
                        projectRoot,
                        documentPath,
                        builder.projectRelativePath
                );

        this.validationCompleted =
                builder.validationCompleted;

        this.issues = immutableIssues(
                builder.issues
        );

        this.warnings = immutableStrings(
                builder.warnings
        );

        this.errorCount = countSeverity(
                issues,
                AccessibilitySeverity.ERROR
        );

        this.warningCount = countSeverity(
                issues,
                AccessibilitySeverity.WARNING
        );

        this.infoCount = countSeverity(
                issues,
                AccessibilitySeverity.INFO
        );

        this.automaticallyFixableCount =
                (int) issues.stream()
                        .filter(
                                AccessibilityIssue
                                        ::isAutomaticallyFixable
                        )
                        .count();

        this.manualReviewCount =
                (int) issues.stream()
                        .filter(
                                AccessibilityIssue
                                        ::isManualReviewRequired
                        )
                        .count();

        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;

        validateTimes(
                startedAt,
                completedAt
        );

        this.duration = resolveDuration(
                startedAt,
                completedAt,
                builder.duration
        );

        this.validatorName = normalizeOptionalText(
                builder.validatorName
        );

        this.metadata = immutableMetadata(
                builder.metadata
        );
    }

    /**
     * 검사 대상 프로젝트 루트를 반환한다.
     *
     * @return 프로젝트 루트, 없으면 {@code null}
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 검사 대상 문서 경로를 반환한다.
     *
     * @return 문서 절대 경로, 프로젝트 전체 검사이면 {@code null}
     */
    public Path getDocumentPath() {
        return documentPath;
    }

    /**
     * 프로젝트 기준 상대 문서 경로를 반환한다.
     *
     * @return 상대 경로, 프로젝트 전체 검사이면 {@code null}
     */
    public String getProjectRelativePath() {
        return projectRelativePath;
    }

    /**
     * 검사가 정상적으로 완료되었는지 반환한다.
     *
     * @return 완료 여부
     */
    public boolean isValidationCompleted() {
        return validationCompleted;
    }

    /**
     * 발견된 접근성 문제 목록을 반환한다.
     *
     * <p>목록은 심각도 내림차순으로 정렬된다.</p>
     *
     * @return 수정할 수 없는 문제 목록
     */
    public List<AccessibilityIssue> getIssues() {
        return issues;
    }

    /**
     * 검사 실행 중 발생한 비문제성 경고 목록을 반환한다.
     *
     * @return 수정할 수 없는 경고 목록
     */
    public List<String> getWarnings() {
        return warnings;
    }

    public int getTotalIssueCount() {
        return issues.size();
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public int getAutomaticallyFixableCount() {
        return automaticallyFixableCount;
    }

    public int getManualReviewCount() {
        return manualReviewCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getValidatorName() {
        return validatorName;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getMetadata(String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    /**
     * 접근성 문제가 하나 이상 존재하는지 반환한다.
     *
     * @return 문제가 있으면 {@code true}
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * 오류 수준 문제가 존재하는지 반환한다.
     *
     * @return 오류가 있으면 {@code true}
     */
    public boolean hasErrors() {
        return errorCount > 0;
    }

    /**
     * 경고 수준 문제가 존재하는지 반환한다.
     *
     * @return 경고가 있으면 {@code true}
     */
    public boolean hasWarnings() {
        return warningCount > 0
                || !warnings.isEmpty();
    }

    /**
     * 출판 또는 접근성 완료 처리를 차단하는 문제가 있는지 반환한다.
     *
     * @return 차단 문제가 있으면 {@code true}
     */
    public boolean blocksPublication() {

        return issues.stream()
                .anyMatch(
                        AccessibilityIssue
                                ::blocksPublication
                );
    }

    /**
     * 검사 결과가 통과 상태인지 반환한다.
     *
     * <p>검사가 완료되었고 오류 수준 문제가 없어야 통과로 판단한다.</p>
     *
     * @return 통과 여부
     */
    public boolean isPassed() {
        return validationCompleted
                && !hasErrors();
    }

    /**
     * 접근성 문제가 전혀 없는지 반환한다.
     *
     * @return 문제가 없으면 {@code true}
     */
    public boolean isClean() {
        return validationCompleted
                && issues.isEmpty();
    }

    /**
     * 자동 수정 가능한 문제가 있는지 반환한다.
     *
     * @return 자동 수정 가능한 문제가 있으면 {@code true}
     */
    public boolean hasAutomaticallyFixableIssues() {
        return automaticallyFixableCount > 0;
    }

    /**
     * 사용자 검토가 필요한 문제가 있는지 반환한다.
     *
     * @return 검토 필요 문제가 있으면 {@code true}
     */
    public boolean hasManualReviewIssues() {
        return manualReviewCount > 0;
    }

    /**
     * 지정한 심각도 이상의 문제 목록을 반환한다.
     *
     * @param minimumSeverity 최소 심각도
     * @return 필터링된 문제 목록
     */
    public List<AccessibilityIssue> getIssuesAtLeast(
            AccessibilitySeverity minimumSeverity) {

        if (minimumSeverity == null) {
            return issues;
        }

        return issues.stream()
                .filter(
                        issue -> issue.getSeverity()
                                .isAtLeast(minimumSeverity)
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 지정한 문제 코드의 결과 목록을 반환한다.
     *
     * @param code 문제 코드
     * @return 일치하는 문제 목록
     */
    public List<AccessibilityIssue> getIssuesByCode(
            AccessibilityIssueCode code) {

        if (code == null) {
            return Collections.emptyList();
        }

        return issues.stream()
                .filter(issue -> issue.getCode() == code)
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 지정한 심각도의 결과 목록을 반환한다.
     *
     * @param severity 심각도
     * @return 일치하는 문제 목록
     */
    public List<AccessibilityIssue> getIssuesBySeverity(
            AccessibilitySeverity severity) {

        if (severity == null) {
            return Collections.emptyList();
        }

        return issues.stream()
                .filter(
                        issue -> issue.getSeverity()
                                == severity
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 자동 수정 가능한 문제 목록을 반환한다.
     *
     * @return 자동 수정 가능 문제 목록
     */
    public List<AccessibilityIssue>
            getAutomaticallyFixableIssues() {

        return issues.stream()
                .filter(
                        AccessibilityIssue
                                ::isAutomaticallyFixable
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 사용자 검토가 필요한 문제 목록을 반환한다.
     *
     * @return 검토 필요 문제 목록
     */
    public List<AccessibilityIssue>
            getManualReviewIssues() {

        return issues.stream()
                .filter(
                        AccessibilityIssue
                                ::isManualReviewRequired
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    /**
     * 심각도별 문제 개수를 반환한다.
     *
     * @return 수정할 수 없는 심각도별 집계
     */
    public Map<AccessibilitySeverity, Integer>
            getSeverityCounts() {

        Map<AccessibilitySeverity, Integer> result =
                new EnumMap<>(
                        AccessibilitySeverity.class
                );

        for (AccessibilitySeverity severity
                : AccessibilitySeverity.values()) {

            result.put(
                    severity,
                    getIssuesBySeverity(severity).size()
            );
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 문제 범주별 개수를 반환한다.
     *
     * @return 수정할 수 없는 범주별 집계
     */
    public Map<
            AccessibilityIssueCode.AccessibilityCategory,
            Integer> getCategoryCounts() {

        Map<
                AccessibilityIssueCode.AccessibilityCategory,
                Integer> result =
                new EnumMap<>(
                        AccessibilityIssueCode
                                .AccessibilityCategory.class
                );

        for (AccessibilityIssueCode.AccessibilityCategory category
                : AccessibilityIssueCode
                        .AccessibilityCategory.values()) {

            result.put(category, 0);
        }

        for (AccessibilityIssue issue : issues) {
            AccessibilityIssueCode.AccessibilityCategory category =
                    issue.getCode().getCategory();

            result.put(
                    category,
                    result.get(category) + 1
            );
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * UI나 로그에 표시할 요약 문자열을 반환한다.
     *
     * @return 검사 결과 요약
     */
    public String toSummaryString() {

        StringBuilder result = new StringBuilder();

        result.append(
                validationCompleted
                        ? "접근성 검사 완료"
                        : "접근성 검사 미완료"
        );

        result.append(" - 오류 ");
        result.append(errorCount);

        result.append(", 경고 ");
        result.append(warningCount);

        result.append(", 정보 ");
        result.append(infoCount);

        if (documentPath != null) {
            result.append(" - ");
            result.append(
                    projectRelativePath != null
                            ? projectRelativePath
                            : documentPath
            );
        }

        return result.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 단일 문서 검사 결과 Builder를 생성한다.
     *
     * @param projectRoot 프로젝트 루트
     * @param documentPath 검사 문서
     * @return 초기화된 Builder
     */
    public static Builder builder(
            Path projectRoot,
            Path documentPath) {

        Objects.requireNonNull(
                projectRoot,
                "projectRoot must not be null"
        );

        Objects.requireNonNull(
                documentPath,
                "documentPath must not be null"
        );

        return builder()
                .projectRoot(projectRoot)
                .documentPath(documentPath);
    }

    private static String locationSortKey(
            AccessibilityIssue issue) {

        if (issue == null
                || issue.getLocation() == null) {

            return "";
        }

        AccessibilityLocation location =
                issue.getLocation();

        String path =
                location.getProjectRelativePath();

        if (path == null
                && location.getDocumentPath() != null) {

            path = location.getDocumentPath()
                    .toString();
        }

        String line =
                location.getLineNumber() == null
                        ? ""
                        : String.format(
                                "%010d",
                                location.getLineNumber()
                        );

        return String.valueOf(path)
                + ":"
                + line
                + ":"
                + String.valueOf(
                        location.getXpath()
                );
    }

    private static List<AccessibilityIssue> immutableIssues(
            List<AccessibilityIssue> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<AccessibilityIssue> result =
                source.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(ISSUE_ORDER)
                        .collect(
                                Collectors.toCollection(
                                        ArrayList::new
                                )
                        );

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(result);
    }

    private static List<String> immutableStrings(
            List<String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (String value : source) {
            String normalized =
                    normalizeOptionalText(value);

            if (normalized != null
                    && !result.contains(normalized)) {

                result.add(normalized);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(result);
    }

    private static int countSeverity(
            List<AccessibilityIssue> issues,
            AccessibilitySeverity severity) {

        return (int) issues.stream()
                .filter(
                        issue -> issue.getSeverity()
                                == severity
                )
                .count();
    }

    private static Path normalizeOptionalPath(
            Path value) {

        if (value == null) {
            return null;
        }

        return value
                .toAbsolutePath()
                .normalize();
    }

    private static void validateDocumentPath(
            Path projectRoot,
            Path documentPath) {

        if (projectRoot == null
                || documentPath == null) {

            return;
        }

        if (!documentPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "documentPath must be inside projectRoot"
            );
        }
    }

    private static String resolveProjectRelativePath(
            Path projectRoot,
            Path documentPath,
            String explicitRelativePath) {

        String normalizedExplicit =
                normalizeOptionalPathText(
                        explicitRelativePath
                );

        if (normalizedExplicit != null) {
            return normalizedExplicit;
        }

        if (projectRoot == null
                || documentPath == null) {

            return null;
        }

        return projectRoot
                .relativize(documentPath)
                .toString()
                .replace('\\', '/');
    }

    private static void validateTimes(
            Instant startedAt,
            Instant completedAt) {

        if (startedAt != null
                && completedAt != null
                && completedAt.isBefore(startedAt)) {

            throw new IllegalArgumentException(
                    "completedAt must not be before startedAt"
            );
        }
    }

    private static Duration resolveDuration(
            Instant startedAt,
            Instant completedAt,
            Duration explicitDuration) {

        if (explicitDuration != null) {
            if (explicitDuration.isNegative()) {
                throw new IllegalArgumentException(
                        "duration must not be negative"
                );
            }

            return explicitDuration;
        }

        if (startedAt != null && completedAt != null) {
            return Duration.between(
                    startedAt,
                    completedAt
            );
        }

        return null;
    }

    private static String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private static String normalizeOptionalPathText(
            String value) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        return normalized.replace('\\', '/');
    }

    private static Map<String, String> immutableMetadata(
            Map<String, String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry
                : source.entrySet()) {

            String key =
                    normalizeOptionalText(
                            entry.getKey()
                    );

            String value =
                    normalizeOptionalText(
                            entry.getValue()
                    );

            if (key != null && value != null) {
                result.put(key, value);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * {@link AccessibilityValidationResult} Builder.
     */
    public static final class Builder {

        private Path projectRoot;
        private Path documentPath;
        private String projectRelativePath;

        private boolean validationCompleted = true;

        private final List<AccessibilityIssue> issues =
                new ArrayList<>();

        private final List<String> warnings =
                new ArrayList<>();

        private Instant startedAt;
        private Instant completedAt;
        private Duration duration;

        private String validatorName;

        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder documentPath(
                Path documentPath) {

            this.documentPath = documentPath;
            return this;
        }

        public Builder projectRelativePath(
                String projectRelativePath) {

            this.projectRelativePath =
                    projectRelativePath;

            return this;
        }

        /**
         * 프로젝트 루트와 상대 문서 경로를 함께 설정한다.
         *
         * @param projectRoot 프로젝트 루트
         * @param relativeDocumentPath 상대 문서 경로
         * @return 현재 Builder
         */
        public Builder projectDocument(
                Path projectRoot,
                Path relativeDocumentPath) {

            Objects.requireNonNull(
                    projectRoot,
                    "projectRoot must not be null"
            );

            Objects.requireNonNull(
                    relativeDocumentPath,
                    "relativeDocumentPath must not be null"
            );

            if (relativeDocumentPath.isAbsolute()) {
                throw new IllegalArgumentException(
                        "relativeDocumentPath must be relative"
                );
            }

            this.projectRoot = projectRoot;
            this.documentPath =
                    projectRoot.resolve(
                            relativeDocumentPath
                    );

            this.projectRelativePath =
                    relativeDocumentPath
                            .normalize()
                            .toString()
                            .replace('\\', '/');

            return this;
        }

        public Builder validationCompleted(
                boolean validationCompleted) {

            this.validationCompleted =
                    validationCompleted;

            return this;
        }

        public Builder issue(
                AccessibilityIssue issue) {

            if (issue != null) {
                issues.add(issue);
            }

            return this;
        }

        public Builder issues(
                List<AccessibilityIssue> issues) {

            if (issues == null) {
                return this;
            }

            for (AccessibilityIssue issue : issues) {
                issue(issue);
            }

            return this;
        }

        public Builder warning(String warning) {

            String normalized =
                    normalizeOptionalText(warning);

            if (normalized != null
                    && !warnings.contains(normalized)) {

                warnings.add(normalized);
            }

            return this;
        }

        public Builder warnings(
                List<String> warnings) {

            if (warnings == null) {
                return this;
            }

            for (String warning : warnings) {
                warning(warning);
            }

            return this;
        }

        public Builder startedAt(
                Instant startedAt) {

            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(
                Instant completedAt) {

            this.completedAt = completedAt;
            return this;
        }

        public Builder duration(
                Duration duration) {

            this.duration = duration;
            return this;
        }

        /**
         * 현재 시각을 검사 시작 시각으로 설정한다.
         *
         * @return 현재 Builder
         */
        public Builder startNow() {
            this.startedAt = Instant.now();
            return this;
        }

        /**
         * 현재 시각을 검사 종료 시각으로 설정한다.
         *
         * @return 현재 Builder
         */
        public Builder completeNow() {
            this.completedAt = Instant.now();
            return this;
        }

        public Builder validatorName(
                String validatorName) {

            this.validatorName = validatorName;
            return this;
        }

        public Builder metadata(
                String key,
                String value) {

            String normalizedKey =
                    normalizeOptionalText(key);

            String normalizedValue =
                    normalizeOptionalText(value);

            if (normalizedKey != null
                    && normalizedValue != null) {

                metadata.put(
                        normalizedKey,
                        normalizedValue
                );
            }

            return this;
        }

        public Builder metadata(
                Map<String, String> metadata) {

            if (metadata == null) {
                return this;
            }

            for (Map.Entry<String, String> entry
                    : metadata.entrySet()) {

                metadata(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            return this;
        }

        public AccessibilityValidationResult build() {
            return new AccessibilityValidationResult(this);
        }
    }
}