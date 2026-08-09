/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB 생성 작업의 최종 결과를 표현합니다.
 *
 * <p>생성 성공 여부, 출력 파일, 처리 시간, 생성된 파일과 리소스 수,
 * 경고 및 오류 정보를 포함합니다.</p>
 *
 * <p>생성 구현체는 성공 여부와 관계없이 가능한 범위에서 결과 정보를
 * 채워 반환해야 합니다. 치명적인 예외가 발생한 경우에는
 * {@link #failure(String, Throwable)} 또는 Builder의
 * {@link Builder#failure(Throwable)}를 사용할 수 있습니다.</p>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubGenerationResult {

    /**
     * 생성 요청 ID입니다.
     */
    private final String requestId;

    /**
     * 생성 상태입니다.
     */
    private final Status status;

    /**
     * 최종 EPUB 파일 경로입니다.
     */
    private final Path outputFile;

    /**
     * 최종 EPUB 파일 크기입니다.
     */
    private final long outputFileSize;

    /**
     * 생성 시작 시각입니다.
     */
    private final Instant startedAt;

    /**
     * 생성 완료 시각입니다.
     */
    private final Instant completedAt;

    /**
     * 처리한 전체 리소스 수입니다.
     */
    private final int generatedResourceCount;

    /**
     * 원본에서 복사된 리소스 수입니다.
     */
    private final int copiedResourceCount;

    /**
     * 메모리 데이터에서 직접 기록된 리소스 수입니다.
     */
    private final int writtenResourceCount;

    /**
     * 건너뛴 리소스 수입니다.
     */
    private final int skippedResourceCount;

    /**
     * 생성된 XHTML 수입니다.
     */
    private final int generatedXhtmlCount;

    /**
     * 생성된 이미지 수입니다.
     */
    private final int generatedImageCount;

    /**
     * Workspace 내 생성된 파일 수입니다.
     */
    private final int generatedFileCount;

    /**
     * 생성된 실제 로컬 파일 목록입니다.
     */
    private final List<Path> generatedFiles;

    /**
     * 생성된 EPUB 내부 경로 목록입니다.
     */
    private final List<String> generatedEpubPaths;

    /**
     * 일반 EPUB 검증 결과입니다.
     */
    private final ValidationSummary validationSummary;

    /**
     * 접근성 검증 결과입니다.
     */
    private final ValidationSummary accessibilityValidationSummary;

    /**
     * EPUBCheck 결과입니다.
     */
    private final ValidationSummary epubCheckValidationSummary;

    /**
     * 생성 과정 경고 목록입니다.
     */
    private final List<String> warnings;

    /**
     * 생성 과정 오류 목록입니다.
     */
    private final List<String> errors;

    /**
     * 사용자 표시용 결과 메시지입니다.
     */
    private final String message;

    /**
     * 실패 원인 예외입니다.
     */
    private final Throwable cause;

    /**
     * 실패 예외 클래스명입니다.
     */
    private final String exceptionType;

    /**
     * 실패 예외 메시지입니다.
     */
    private final String exceptionMessage;

    /**
     * 추가 실행 정보입니다.
     */
    private final Map<String, String> attributes;

    private EpubGenerationResult(Builder builder) {
        this.requestId =
                normalizeOptionalText(
                        builder.requestId
                );

        this.outputFile =
                normalizePath(
                        builder.outputFile
                );

        this.outputFileSize =
                requireNonNegative(
                        builder.outputFileSize,
                        "EPUB output file size"
                );

        this.startedAt =
                builder.startedAt;

        this.completedAt =
                builder.completedAt;

        this.generatedResourceCount =
                requireNonNegative(
                        builder.generatedResourceCount,
                        "Generated resource count"
                );

        this.copiedResourceCount =
                requireNonNegative(
                        builder.copiedResourceCount,
                        "Copied resource count"
                );

        this.writtenResourceCount =
                requireNonNegative(
                        builder.writtenResourceCount,
                        "Written resource count"
                );

        this.skippedResourceCount =
                requireNonNegative(
                        builder.skippedResourceCount,
                        "Skipped resource count"
                );

        this.generatedXhtmlCount =
                requireNonNegative(
                        builder.generatedXhtmlCount,
                        "Generated XHTML count"
                );

        this.generatedImageCount =
                requireNonNegative(
                        builder.generatedImageCount,
                        "Generated image count"
                );

        this.generatedFileCount =
                requireNonNegative(
                        builder.generatedFileCount,
                        "Generated file count"
                );

        this.generatedFiles =
                immutablePaths(
                        builder.generatedFiles
                );

        this.generatedEpubPaths =
                immutableStrings(
                        builder.generatedEpubPaths,
                        true
                );

        this.validationSummary =
                builder.validationSummary == null
                        ? ValidationSummary.notPerformed()
                        : builder.validationSummary;

        this.accessibilityValidationSummary =
                builder.accessibilityValidationSummary == null
                        ? ValidationSummary.notPerformed()
                        : builder.accessibilityValidationSummary;

        this.epubCheckValidationSummary =
                builder.epubCheckValidationSummary == null
                        ? ValidationSummary.notPerformed()
                        : builder.epubCheckValidationSummary;

        this.warnings =
                immutableStrings(
                        builder.warnings,
                        false
                );

        this.errors =
                immutableStrings(
                        builder.errors,
                        false
                );

        this.message =
                normalizeOptionalText(
                        builder.message
                );

        this.cause =
                builder.cause;

        this.exceptionType =
                normalizeOptionalText(
                        builder.exceptionType
                );

        this.exceptionMessage =
                normalizeOptionalText(
                        builder.exceptionMessage
                );

        this.attributes =
                immutableAttributes(
                        builder.attributes
                );

        this.status =
                builder.status == null
                        ? resolveStatus()
                        : builder.status;

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 생성 요청을 기반으로 Builder를 생성합니다.
     */
    public static Builder builder(
            EpubGenerationRequest request
    ) {
        Objects.requireNonNull(
                request,
                "EPUB generation request must not be null."
        );

        return new Builder()
                .requestId(
                        request.getRequestId()
                )
                .outputFile(
                        request.getOutputFile()
                );
    }

    public Optional<String> getRequestId() {
        return Optional.ofNullable(requestId);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Path> getOutputFile() {
        return Optional.ofNullable(outputFile);
    }

    public long getOutputFileSize() {
        return outputFileSize;
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public int getGeneratedResourceCount() {
        return generatedResourceCount;
    }

    public int getCopiedResourceCount() {
        return copiedResourceCount;
    }

    public int getWrittenResourceCount() {
        return writtenResourceCount;
    }

    public int getSkippedResourceCount() {
        return skippedResourceCount;
    }

    public int getGeneratedXhtmlCount() {
        return generatedXhtmlCount;
    }

    public int getGeneratedImageCount() {
        return generatedImageCount;
    }

    public int getGeneratedFileCount() {
        return generatedFileCount;
    }

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    public List<String> getGeneratedEpubPaths() {
        return generatedEpubPaths;
    }

    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    public ValidationSummary
            getAccessibilityValidationSummary() {
        return accessibilityValidationSummary;
    }

    public ValidationSummary
            getEpubCheckValidationSummary() {
        return epubCheckValidationSummary;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<Throwable> getCause() {
        return Optional.ofNullable(cause);
    }

    public Optional<String> getExceptionType() {
        return Optional.ofNullable(exceptionType);
    }

    public Optional<String> getExceptionMessage() {
        return Optional.ofNullable(exceptionMessage);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Optional<String> getAttribute(
            String name
    ) {
        String normalized =
                normalizeOptionalText(name);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                attributes.get(normalized)
        );
    }

    /**
     * EPUB 생성이 성공했는지 확인합니다.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS
                || status == Status.SUCCESS_WITH_WARNINGS;
    }

    /**
     * 완전 성공인지 확인합니다.
     */
    public boolean isCompleteSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 경고 포함 성공인지 확인합니다.
     */
    public boolean isSuccessWithWarnings() {
        return status == Status.SUCCESS_WITH_WARNINGS;
    }

    /**
     * 실패 여부입니다.
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty()
                || validationSummary.hasWarnings()
                || accessibilityValidationSummary.hasWarnings()
                || epubCheckValidationSummary.hasWarnings();
    }

    public boolean hasErrors() {
        return !errors.isEmpty()
                || validationSummary.isFailed()
                || accessibilityValidationSummary.isFailed()
                || epubCheckValidationSummary.isFailed()
                || cause != null;
    }

    /**
     * 검증 중 하나라도 실패했는지 확인합니다.
     */
    public boolean hasValidationFailure() {
        return validationSummary.isFailed()
                || accessibilityValidationSummary.isFailed()
                || epubCheckValidationSummary.isFailed();
    }

    /**
     * EPUBCheck가 수행됐는지 확인합니다.
     */
    public boolean isEpubCheckPerformed() {
        return epubCheckValidationSummary.isPerformed();
    }

    /**
     * 접근성 검증이 수행됐는지 확인합니다.
     */
    public boolean isAccessibilityValidationPerformed() {
        return accessibilityValidationSummary.isPerformed();
    }

    /**
     * 전체 실행 시간을 반환합니다.
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
     * 사용자 표시용 요약 문자열입니다.
     */
    public String getSummary() {
        StringBuilder result =
                new StringBuilder();

        result.append(status.getDisplayName());

        if (outputFile != null) {
            result.append(" - ")
                    .append(outputFile);
        }

        result.append(" [resources=")
                .append(generatedResourceCount)
                .append(", files=")
                .append(generatedFileCount)
                .append(", warnings=")
                .append(warnings.size())
                .append(", errors=")
                .append(errors.size())
                .append(']');

        return result.toString();
    }

    /**
     * 현재 결과를 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .requestId(requestId)
                .status(status)
                .outputFile(outputFile)
                .outputFileSize(outputFileSize)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .generatedResourceCount(
                        generatedResourceCount
                )
                .copiedResourceCount(
                        copiedResourceCount
                )
                .writtenResourceCount(
                        writtenResourceCount
                )
                .skippedResourceCount(
                        skippedResourceCount
                )
                .generatedXhtmlCount(
                        generatedXhtmlCount
                )
                .generatedImageCount(
                        generatedImageCount
                )
                .generatedFileCount(
                        generatedFileCount
                )
                .generatedFiles(
                        generatedFiles
                )
                .generatedEpubPaths(
                        generatedEpubPaths
                )
                .validationSummary(
                        validationSummary
                )
                .accessibilityValidationSummary(
                        accessibilityValidationSummary
                )
                .epubCheckValidationSummary(
                        epubCheckValidationSummary
                )
                .warnings(warnings)
                .errors(errors)
                .message(message)
                .cause(cause)
                .exceptionType(exceptionType)
                .exceptionMessage(exceptionMessage)
                .attributes(attributes);
    }

    private Status resolveStatus() {
        if (cause != null
                || !errors.isEmpty()
                || hasValidationFailure()) {

            return Status.FAILED;
        }

        if (hasWarnings()) {
            return Status.SUCCESS_WITH_WARNINGS;
        }

        return Status.SUCCESS;
    }

    private void validate() {
        if (startedAt != null
                && completedAt != null
                && completedAt.isBefore(startedAt)) {

            throw new IllegalArgumentException(
                    "EPUB generation completion time "
                            + "must not precede start time."
            );
        }

        if (status == Status.SUCCESS
                && hasErrors()) {

            throw new IllegalArgumentException(
                    "Successful EPUB generation result "
                            + "must not contain errors."
            );
        }

        if (status == Status.SUCCESS
                && hasWarnings()) {

            throw new IllegalArgumentException(
                    "Result containing warnings must use "
                            + "SUCCESS_WITH_WARNINGS."
            );
        }

        if (status == Status.FAILED
                && !hasErrors()) {

            throw new IllegalArgumentException(
                    "FAILED EPUB generation result requires "
                            + "an error, validation failure, "
                            + "or exception."
            );
        }

        /*
         * generatedFileCount가 명시적으로 주어지지 않은 경우에도
         * 목록과 불일치가 발생할 수 있으므로 오류로 강제하지 않습니다.
         */
    }

    private static int requireNonNegative(
            int value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be negative: "
                            + value
            );
        }

        return value;
    }

    private static long requireNonNegative(
            long value,
            String fieldName
    ) {
        if (value < 0L) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be negative: "
                            + value
            );
        }

        return value;
    }

    private static Path normalizePath(
            Path value
    ) {
        if (value == null) {
            return null;
        }

        return value
                .toAbsolutePath()
                .normalize();
    }

    private static List<Path> immutablePaths(
            Collection<Path> values
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> result =
                new ArrayList<>();

        for (Path value : values) {
            if (value == null) {
                continue;
            }

            Path normalized =
                    normalizePath(value);

            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }

        return Collections.unmodifiableList(
                result
        );
    }

    private static List<String> immutableStrings(
            Collection<String> values,
            boolean normalizeEpubPath
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<>();

        for (String value : values) {
            String normalized =
                    normalizeOptionalText(value);

            if (normalized == null) {
                continue;
            }

            if (normalizeEpubPath) {
                normalized =
                        normalized.replace('\\', '/');

                while (normalized.startsWith("./")) {
                    normalized =
                            normalized.substring(2);
                }
            }

            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }

        return Collections.unmodifiableList(
                result
        );
    }

    private static Map<String, String>
            immutableAttributes(
                    Map<String, String> values
            ) {

        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry :
                values.entrySet()) {

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
        return "EpubGenerationResult{"
                + "requestId='" + requestId + '\''
                + ", status=" + status
                + ", outputFile=" + outputFile
                + ", outputFileSize=" + outputFileSize
                + ", generatedResourceCount="
                + generatedResourceCount
                + ", generatedFileCount="
                + generatedFileCount
                + ", warningCount="
                + warnings.size()
                + ", errorCount="
                + errors.size()
                + ", durationMillis="
                + getDurationMillis()
                + '}';
    }

    /**
     * EPUB 생성 상태입니다.
     */
    public enum Status {

        /**
         * 성공적으로 EPUB이 생성되었습니다.
         */
        SUCCESS("성공"),

        /**
         * EPUB은 생성됐지만 경고가 존재합니다.
         */
        SUCCESS_WITH_WARNINGS("경고 포함 성공"),

        /**
         * EPUB 생성에 실패했습니다.
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
            return this == SUCCESS
                    || this == SUCCESS_WITH_WARNINGS;
        }
    }

    /**
     * EPUB 생성 과정의 개별 검증 결과 요약입니다.
     */
    public static final class ValidationSummary {

        private final Status status;

        private final String validator;

        private final int errors;

        private final int warnings;

        private final String message;

        private ValidationSummary(
                Status status,
                String validator,
                int errors,
                int warnings,
                String message
        ) {
            this.status =
                    Objects.requireNonNull(
                            status,
                            "Validation summary status "
                                    + "must not be null."
                    );

            this.validator =
                    normalizeOptionalText(
                            validator
                    );

            this.errors =
                    requireNonNegative(
                            errors,
                            "Validation error count"
                    );

            this.warnings =
                    requireNonNegative(
                            warnings,
                            "Validation warning count"
                    );

            this.message =
                    normalizeOptionalText(
                            message
                    );

            validate();
        }

        /**
         * 검증 미실행 결과입니다.
         */
        public static ValidationSummary notPerformed() {
            return new ValidationSummary(
                    Status.NOT_PERFORMED,
                    null,
                    0,
                    0,
                    "Validation was not performed."
            );
        }

        /**
         * 검증 성공 결과입니다.
         */
        public static ValidationSummary passed(
                String validator
        ) {
            return new ValidationSummary(
                    Status.PASSED,
                    validator,
                    0,
                    0,
                    "Validation passed."
            );
        }

        /**
         * 경고 포함 검증 성공 결과입니다.
         */
        public static ValidationSummary
                passedWithWarnings(
                        String validator,
                        int warnings
                ) {

            if (warnings <= 0) {
                throw new IllegalArgumentException(
                        "Warnings must be greater than zero."
                );
            }

            return new ValidationSummary(
                    Status.PASSED_WITH_WARNINGS,
                    validator,
                    0,
                    warnings,
                    "Validation passed with warnings."
            );
        }

        /**
         * 검증 실패 결과입니다.
         */
        public static ValidationSummary failed(
                String validator,
                int errors,
                int warnings
        ) {
            if (errors <= 0) {
                throw new IllegalArgumentException(
                        "Failed validation requires "
                                + "at least one error."
                );
            }

            return new ValidationSummary(
                    Status.FAILED,
                    validator,
                    errors,
                    warnings,
                    "Validation failed."
            );
        }

        /**
         * 사용자 정의 메시지를 적용합니다.
         */
        public ValidationSummary withMessage(
                String message
        ) {
            return new ValidationSummary(
                    status,
                    validator,
                    errors,
                    warnings,
                    message
            );
        }

        public Status getStatus() {
            return status;
        }

        public Optional<String> getValidator() {
            return Optional.ofNullable(
                    validator
            );
        }

        public int getErrors() {
            return errors;
        }

        public int getWarnings() {
            return warnings;
        }

        public Optional<String> getMessage() {
            return Optional.ofNullable(
                    message
            );
        }

        public boolean isPerformed() {
            return status != Status.NOT_PERFORMED;
        }

        public boolean isPassed() {
            return status == Status.PASSED
                    || status
                            == Status.PASSED_WITH_WARNINGS;
        }

        public boolean isFailed() {
            return status == Status.FAILED;
        }

        public boolean hasWarnings() {
            return warnings > 0;
        }

        private void validate() {
            if (status == Status.NOT_PERFORMED
                    && (errors > 0 || warnings > 0)) {

                throw new IllegalArgumentException(
                        "NOT_PERFORMED validation summary "
                                + "cannot contain issues."
                );
            }

            if (status == Status.PASSED
                    && (errors > 0 || warnings > 0)) {

                throw new IllegalArgumentException(
                        "PASSED validation summary "
                                + "cannot contain issues."
                );
            }

            if (status
                    == Status.PASSED_WITH_WARNINGS
                    && (errors > 0 || warnings <= 0)) {

                throw new IllegalArgumentException(
                        "PASSED_WITH_WARNINGS requires "
                                + "warnings and no errors."
                );
            }

            if (status == Status.FAILED
                    && errors <= 0) {

                throw new IllegalArgumentException(
                        "FAILED validation summary requires errors."
                );
            }
        }

        @Override
        public String toString() {
            return "ValidationSummary{"
                    + "status=" + status
                    + ", validator='" + validator + '\''
                    + ", errors=" + errors
                    + ", warnings=" + warnings
                    + '}';
        }

        /**
         * 검증 요약 상태입니다.
         */
        public enum Status {

            NOT_PERFORMED,

            PASSED,

            PASSED_WITH_WARNINGS,

            FAILED
        }
    }

    /**
     * {@link EpubGenerationResult} Builder입니다.
     */
    public static final class Builder {

        private String requestId;

        private Status status;

        private Path outputFile;

        private long outputFileSize;

        private Instant startedAt;

        private Instant completedAt;

        private int generatedResourceCount;

        private int copiedResourceCount;

        private int writtenResourceCount;

        private int skippedResourceCount;

        private int generatedXhtmlCount;

        private int generatedImageCount;

        private int generatedFileCount;

        private final List<Path> generatedFiles =
                new ArrayList<>();

        private final List<String> generatedEpubPaths =
                new ArrayList<>();

        private ValidationSummary validationSummary =
                ValidationSummary.notPerformed();

        private ValidationSummary
                accessibilityValidationSummary =
                ValidationSummary.notPerformed();

        private ValidationSummary
                epubCheckValidationSummary =
                ValidationSummary.notPerformed();

        private final List<String> warnings =
                new ArrayList<>();

        private final List<String> errors =
                new ArrayList<>();

        private String message;

        private Throwable cause;

        private String exceptionType;

        private String exceptionMessage;

        private final Map<String, String> attributes =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder requestId(
                String requestId
        ) {
            this.requestId = requestId;
            return this;
        }

        public Builder status(
                Status status
        ) {
            this.status = status;
            return this;
        }

        public Builder outputFile(
                Path outputFile
        ) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder outputFileSize(
                long outputFileSize
        ) {
            this.outputFileSize =
                    outputFileSize;
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

        public Builder generatedResourceCount(
                int value
        ) {
            this.generatedResourceCount =
                    value;
            return this;
        }

        public Builder copiedResourceCount(
                int value
        ) {
            this.copiedResourceCount =
                    value;
            return this;
        }

        public Builder writtenResourceCount(
                int value
        ) {
            this.writtenResourceCount =
                    value;
            return this;
        }

        public Builder skippedResourceCount(
                int value
        ) {
            this.skippedResourceCount =
                    value;
            return this;
        }

        public Builder generatedXhtmlCount(
                int value
        ) {
            this.generatedXhtmlCount =
                    value;
            return this;
        }

        public Builder generatedImageCount(
                int value
        ) {
            this.generatedImageCount =
                    value;
            return this;
        }

        public Builder generatedFileCount(
                int value
        ) {
            this.generatedFileCount =
                    value;
            return this;
        }

        public Builder generatedFile(
                Path file
        ) {
            if (file != null) {
                generatedFiles.add(file);
            }

            return this;
        }

        public Builder generatedFiles(
                Collection<Path> files
        ) {
            if (files != null) {
                files.forEach(
                        this::generatedFile
                );
            }

            return this;
        }

        public Builder generatedEpubPath(
                String epubPath
        ) {
            if (epubPath != null) {
                generatedEpubPaths.add(
                        epubPath
                );
            }

            return this;
        }

        public Builder generatedEpubPaths(
                Collection<String> paths
        ) {
            if (paths != null) {
                paths.forEach(
                        this::generatedEpubPath
                );
            }

            return this;
        }

        public Builder validationSummary(
                ValidationSummary value
        ) {
            this.validationSummary =
                    value;
            return this;
        }

        public Builder accessibilityValidationSummary(
                ValidationSummary value
        ) {
            this.accessibilityValidationSummary =
                    value;
            return this;
        }

        public Builder epubCheckValidationSummary(
                ValidationSummary value
        ) {
            this.epubCheckValidationSummary =
                    value;
            return this;
        }

        public Builder warning(
                String warning
        ) {
            if (warning != null
                    && !warning.isBlank()) {
                warnings.add(
                        warning
                );
            }

            return this;
        }

        public Builder warnings(
                Collection<String> warnings
        ) {
            if (warnings != null) {
                warnings.forEach(
                        this::warning
                );
            }

            return this;
        }

        public Builder error(
                String error
        ) {
            if (error != null
                    && !error.isBlank()) {
                errors.add(
                        error
                );
            }

            return this;
        }

        public Builder errors(
                Collection<String> errors
        ) {
            if (errors != null) {
                errors.forEach(
                        this::error
                );
            }

            return this;
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

        public Builder exceptionType(
                String exceptionType
        ) {
            this.exceptionType =
                    exceptionType;
            return this;
        }

        public Builder exceptionMessage(
                String exceptionMessage
        ) {
            this.exceptionMessage =
                    exceptionMessage;
            return this;
        }

        public Builder attribute(
                String name,
                String value
        ) {
            attributes.put(
                    name,
                    value
            );

            return this;
        }

        public Builder attributes(
                Map<String, String> values
        ) {
            if (values != null) {
                attributes.putAll(values);
            }

            return this;
        }

        /**
         * Status를 경고/오류/검증 결과에서 자동 결정하도록 합니다.
         */
        public Builder resolveStatus() {
            this.status = null;
            return this;
        }

        public EpubGenerationResult build() {
            return new EpubGenerationResult(this);
        }
    }
}