/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;

/**
 * EPUBCheck 기반 EPUB 검증 기능의 계약입니다.
 *
 * <p>공식 EPUBCheck 실행 결과를
 * {@link EpubValidationResult}와
 * {@link EpubValidationIssue} 형식으로 변환하여
 * EPUB 생성 계층에서 일관되게 사용할 수 있도록 합니다.</p>
 *
 * <p>이 인터페이스는 실제 EPUBCheck 실행 방식에 의존하지 않습니다.</p>
 *
 * <p>구현체는 다음 방식 중 하나를 사용할 수 있습니다.</p>
 *
 * <ul>
 *     <li>EPUBCheck Java API 직접 호출</li>
 *     <li>epubcheck.jar 실행</li>
 *     <li>외부 프로세스 실행</li>
 * </ul>
 *
 * <p>권장 방식은 가능하면 EPUBCheck Java API를 직접 사용하는 것입니다.
 * 외부 프로세스를 사용하는 경우 stdout/stderr 및 종료 코드를
 * {@link EpubValidationResult}로 변환해야 합니다.</p>
 */
public interface EpubCheckValidator
        extends EpubValidator {

    /**
     * 실제 EPUB 파일을 EPUBCheck로 검증합니다.
     *
     * @param epubFile EPUB 파일
     * @param options 생성 옵션
     * @return EPUBCheck 검증 결과
     */
    @Override
    EpubValidationResult validate(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    );

    /**
     * EPUB Package 모델 검증은 기본적으로 지원하지 않습니다.
     *
     * <p>EPUBCheck의 주 검증 대상은 실제 EPUB 파일 또는
     * EPUB 콘텐츠 문서이므로 모델 단계에서는
     * {@link EpubValidationResult#notPerformed()}를 반환합니다.</p>
     */
    @Override
    default EpubValidationResult validate(
    		Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) {

        return EpubValidationResult.builder()
                .performed(false)
                .status(
                        EpubValidationResult.Status
                                .NOT_PERFORMED
                )
                .validatorName(getName())
                .validatorVersion(getVersion())
                .message(
                        "EPUBCheck validates generated EPUB files; "
                                + "package-model validation was not performed."
                )
                .build();
    }

    /**
     * EPUBCheck 실행 환경이 사용 가능한지 확인합니다.
     *
     * <p>예를 들어 Java API 방식이라면 필요한 EPUBCheck 클래스가
     * classpath에 존재하는지 확인할 수 있고, 외부 jar 방식이라면
     * jar 파일 존재 여부를 확인할 수 있습니다.</p>
     *
     * @return 실행 가능하면 {@code true}
     */
    boolean isAvailable();

    /**
     * EPUBCheck 실행 환경 정보를 반환합니다.
     *
     * @return 실행 환경 정보
     */
    default Availability getAvailability() {
        return isAvailable()
                ? Availability.available(
                        getVersion()
                )
                : Availability.unavailable(
                        "EPUBCheck is not available."
                );
    }

    /**
     * EPUBCheck 실행 전에 입력 파일을 검증합니다.
     *
     * @param epubFile EPUB 파일
     * @param options 생성 옵션
     */
    @Override
    default void validateInput(
            Path epubFile,
            EpubGenerationOptions options
    ) {

        EpubValidator.super.validateInput(
                epubFile,
                options
        );

        Path normalized =
                epubFile.toAbsolutePath().normalize();

        if (!java.nio.file.Files.exists(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB file does not exist: "
                            + normalized
            );
        }

        if (!java.nio.file.Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB path is not a regular file: "
                            + normalized
            );
        }

        if (!java.nio.file.Files.isReadable(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB file is not readable: "
                            + normalized
            );
        }

        if (!isAvailable()) {
            throw new IllegalStateException(
                    "EPUBCheck is not available."
            );
        }
    }

    /**
     * EPUBCheck 결과가 EPUB 생성을 차단해야 하는지 판단합니다.
     *
     * <p>기본 정책에서는 ERROR 또는 FATAL 수준의 이슈가 있으면
     * 생성을 실패 처리합니다.</p>
     *
     * @param result EPUBCheck 결과
     * @return 생성 차단 여부
     */
    default boolean shouldBlockGeneration(
            EpubValidationResult result
    ) {

        if (result == null
                || !result.isPerformed()) {
            return false;
        }

        return result.hasBlockingIssues()
                || result.getStatus()
                        == EpubValidationResult.Status.FAILED;
    }

    /**
     * EPUBCheck 결과에 오류가 있는지 확인합니다.
     */
    default boolean hasErrors(
            EpubValidationResult result
    ) {

        return result != null
                && (
                        result.getErrorCount() > 0
                        || result.getFatalCount() > 0
                );
    }

    /**
     * EPUBCheck 결과에 경고가 있는지 확인합니다.
     */
    default boolean hasWarnings(
            EpubValidationResult result
    ) {

        return result != null
                && result.getWarningCount() > 0;
    }

    /**
     * Validator 유형은 EPUB_CHECK입니다.
     */
    @Override
    default Type getType() {
        return Type.EPUB_CHECK;
    }

    /**
     * EPUBCheck는 실제 EPUB archive 검증을 지원합니다.
     */
    @Override
    default boolean supportsArchiveValidation() {
        return true;
    }

    /**
     * Package 모델 검증은 지원하지 않습니다.
     */
    @Override
    default boolean supportsPackageValidation() {
        return false;
    }

    /**
     * EPUBCheck Validator 기본 이름입니다.
     */
    @Override
    default String getName() {
        return "EPUBCheck";
    }

    /**
     * EPUBCheck 실행 방식입니다.
     */
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.JAVA_API;
    }

    /**
     * EPUBCheck 실행 방식입니다.
     */
    enum ExecutionMode {

        /**
         * EPUBCheck Java API 직접 호출 방식입니다.
         */
        JAVA_API,

        /**
         * epubcheck.jar를 별도 Java 프로세스로 실행합니다.
         */
        EXTERNAL_JAR,

        /**
         * CLI executable 또는 wrapper를 실행합니다.
         */
        EXTERNAL_PROCESS
    }

    /**
     * EPUBCheck 실행 환경 상태입니다.
     */
    final class Availability {

        private final boolean available;

        private final String version;

        private final String message;

        private Availability(
                boolean available,
                String version,
                String message
        ) {
            this.available = available;
            this.version = normalize(version);
            this.message = normalize(message);
        }

        public static Availability available(
                String version
        ) {
            return new Availability(
                    true,
                    version,
                    "EPUBCheck is available."
            );
        }

        public static Availability unavailable(
                String message
        ) {
            return new Availability(
                    false,
                    null,
                    message
            );
        }

        public boolean isAvailable() {
            return available;
        }

        public java.util.Optional<String> getVersion() {
            return java.util.Optional.ofNullable(
                    version
            );
        }

        public java.util.Optional<String> getMessage() {
            return java.util.Optional.ofNullable(
                    message
            );
        }

        private static String normalize(
                String value
        ) {
            if (value == null || value.isBlank()) {
                return null;
            }

            return value.trim();
        }

        @Override
        public String toString() {
            return "Availability{"
                    + "available=" + available
                    + ", version='" + version + '\''
                    + ", message='" + message + '\''
                    + '}';
        }
    }

    /**
     * EPUBCheck 원본 메시지를 EPUB 공통 검증 이슈로 변환하기 위한
     * 메시지 모델입니다.
     */
    final class CheckMessage {

        private final String code;

        private final Level level;

        private final String message;

        private final String epubPath;

        private final int line;

        private final int column;

        private final String originalMessage;

        public CheckMessage(
                String code,
                Level level,
                String message,
                String epubPath,
                int line,
                int column,
                String originalMessage
        ) {
            this.code = normalizeCode(code);

            this.level = Objects.requireNonNull(
                    level,
                    "EPUBCheck message level must not be null."
            );

            this.message = requireMessage(message);

            this.epubPath =
                    normalizeEpubPath(epubPath);

            this.line =
                    normalizePosition(line);

            this.column =
                    normalizePosition(column);

            this.originalMessage =
                    normalizeText(originalMessage);

            if (this.column >= 0
                    && this.line < 0) {
                throw new IllegalArgumentException(
                        "EPUBCheck column requires a line number."
                );
            }
        }

        public String getCode() {
            return code;
        }

        public Level getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public java.util.Optional<String> getEpubPath() {
            return java.util.Optional.ofNullable(
                    epubPath
            );
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public java.util.Optional<String>
                getOriginalMessage() {

            return java.util.Optional.ofNullable(
                    originalMessage
            );
        }

        public boolean hasLine() {
            return line >= 0;
        }

        public boolean hasColumn() {
            return column >= 0;
        }

        /**
         * 공통 EPUB ValidationIssue로 변환합니다.
         */
        public EpubValidationIssue toValidationIssue() {

            EpubValidationIssue.Builder builder =
                    EpubValidationIssue.builder(
                            resolveValidationCode(),
                            level.toSeverity(),
                            message
                    )
                            .category(
                                    EpubValidationIssue.Category
                                            .EPUB_CHECK
                            )
                            .validator("EPUBCheck");

            if (epubPath != null) {
                builder.epubPath(epubPath);
            }

            if (line >= 0) {
                builder.line(line);
            }

            if (column >= 0) {
                builder.column(column);
            }

            if (originalMessage != null) {
                builder.originalMessage(
                        originalMessage
                );
            }

            builder.detail(
                    "epubCheckCode",
                    code
            );

            builder.detail(
                    "epubCheckLevel",
                    level.name()
            );

            return builder.build();
        }

        private String resolveValidationCode() {

            String normalized =
                    code.replaceAll(
                            "[^A-Za-z0-9_.-]",
                            "-"
                    );

            return "EPUBCHECK-" + normalized;
        }

        private static String normalizeCode(
                String value
        ) {
            if (value == null || value.isBlank()) {
                return "UNKNOWN";
            }

            return value.trim();
        }

        private static String requireMessage(
                String value
        ) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "EPUBCheck message must not be blank."
                );
            }

            return value.trim();
        }

        private static String normalizeEpubPath(
                String value
        ) {
            String normalized =
                    normalizeText(value);

            if (normalized == null) {
                return null;
            }

            normalized =
                    normalized.replace('\\', '/');

            while (normalized.startsWith("./")) {
                normalized =
                        normalized.substring(2);
            }

            return normalized;
        }

        private static String normalizeText(
                String value
        ) {
            if (value == null || value.isBlank()) {
                return null;
            }

            return value.trim();
        }

        private static int normalizePosition(
                int value
        ) {
            if (value < -1) {
                return -1;
            }

            return value;
        }
    }

    /**
     * EPUBCheck 메시지 심각도입니다.
     *
     * <p>실제 EPUBCheck 버전에 따라 명칭 차이가 있을 수 있으므로
     * EPUB 계층에서는 이 중간 표현으로 정규화합니다.</p>
     */
    enum Level {

        INFO,

        USAGE,

        WARNING,

        ERROR,

        FATAL;

        public EpubValidationIssue.Severity
                toSeverity() {

            return switch (this) {
                case INFO, USAGE ->
                        EpubValidationIssue.Severity.INFO;

                case WARNING ->
                        EpubValidationIssue.Severity.WARNING;

                case ERROR ->
                        EpubValidationIssue.Severity.ERROR;

                case FATAL ->
                        EpubValidationIssue.Severity.FATAL;
            };
        }

        /**
         * 외부 EPUBCheck 심각도 문자열을 정규화합니다.
         */
        public static Level from(
                String value
        ) {
            if (value == null || value.isBlank()) {
                return ERROR;
            }

            String normalized =
                    value.trim()
                            .toUpperCase(
                                    java.util.Locale.ROOT
                            );

            return switch (normalized) {
                case "INFO" ->
                        INFO;

                case "USAGE" ->
                        USAGE;

                case "WARN", "WARNING" ->
                        WARNING;

                case "ERROR", "ERR" ->
                        ERROR;

                case "FATAL", "FATAL_ERROR" ->
                        FATAL;

                default ->
                        ERROR;
            };
        }
    }
}