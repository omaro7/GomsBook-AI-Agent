/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;

import com.adobe.epubcheck.api.EPUBLocation;
import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.api.MasterReport;
import com.adobe.epubcheck.messages.Message;
import com.adobe.epubcheck.messages.Severity;
import com.adobe.epubcheck.util.FeatureEnum;

/**
 * EPUBCheck Java API를 이용하여 최종 EPUB 파일을 검증하는
 * 기본 구현체입니다.
 *
 * <p>EPUBCheck의 {@link EpubCheck} API를 직접 호출하며,
 * 사용자 정의 {@link MasterReport} 구현을 사용하여 검증 메시지를
 * 구조화된 형태로 수집합니다.</p>
 *
 * <p>EPUBCheck의 내부 타입은 이 클래스 내부에서만 사용하고,
 * 외부 EPUB 계층에는 {@link EpubValidationIssue}와
 * {@link EpubValidationResult}만 노출합니다.</p>
 *
 * <p>처리 흐름은 다음과 같습니다.</p>
 *
 * <pre>
 * EPUB file
 *     ↓
 * EpubCheck
 *     ↓
 * CollectingReport
 *     ↓
 * EPUBCheck Message
 *     ↓
 * CheckMessage
 *     ↓
 * EpubValidationIssue
 *     ↓
 * EpubValidationResult
 * </pre>
 */
public final class DefaultEpubCheckValidator
        implements EpubCheckValidator {

    private static final String VALIDATOR_NAME =
            "EPUBCheck";

    private static final String FALLBACK_VERSION =
            "unknown";

    /**
     * EPUBCheck 메시지 locale입니다.
     *
     * <p>기본적으로 영어 메시지를 사용합니다.
     * 메시지 코드가 동일하므로 UI에서 별도 현지화가 가능합니다.</p>
     */
    private final Locale locale;

    /**
     * 기본 Locale.ENGLISH를 사용하는 Validator를 생성합니다.
     */
    public DefaultEpubCheckValidator() {
        this(Locale.ENGLISH);
    }

    /**
     * EPUBCheck 메시지 Locale을 지정합니다.
     *
     * @param locale EPUBCheck 메시지 locale
     */
    public DefaultEpubCheckValidator(
            Locale locale
    ) {
        this.locale = Objects.requireNonNull(
                locale,
                "EPUBCheck locale must not be null."
        );
    }

    /**
     * 최종 EPUB 파일을 EPUBCheck로 검증합니다.
     *
     * @param epubFile EPUB 파일
     * @param options  EPUB 생성 옵션
     * @return 검증 결과
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    ) {

        Instant startedAt =
                Instant.now();

        EpubValidationResult.Builder result =
                EpubValidationResult.builder()
                        .validatorName(getName())
                        .validatorVersion(getVersion())
                        .startedAt(startedAt);

        if (epubFile != null) {
            result.target(
                    epubFile
                            .toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        try {
            validateInput(
                    epubFile,
                    options
            );

            Path normalized =
                    epubFile
                            .toAbsolutePath()
                            .normalize();

            CollectingReport report =
                    new CollectingReport(
                            normalized.toString()
                    );

            /*
             * EPUBCheck API 자체가 locale 설정을 지원합니다.
             */

            EpubCheck epubCheck =
                    new EpubCheck(
                            normalized.toFile()
                    );
            
            epubCheck.setLocale(locale);

            /*
             * doValidate()는 bit flag 형태의 결과 코드를 반환합니다.
             *
             * 1 = warning
             * 2 = error
             * 4 = fatal
             *
             * 하지만 최종 판단은 Report에 수집된 메시지를 기준으로
             * 수행하는 것이 더 정확합니다.
             */
            int exitCode =
                    epubCheck.doValidate();

            List<EpubValidationIssue> issues =
                    convertMessages(
                            report.getCollectedMessages()
                    );

            /*
             * EPUBCheck 자체에서 예외 없이 종료했지만
             * exitCode와 Report 카운트가 불일치하면
             * 진단용 INFO를 추가합니다.
             */
            if (exitCode != 0
                    && !containsBlockingOrWarningIssue(issues)) {

                issues = new ArrayList<>(issues);

                issues.add(
                        EpubValidationIssue.builder(
                                "EPUBCHECK-RESULT-001",
                                EpubValidationIssue.Severity.WARNING,
                                "EPUBCheck returned a non-zero result "
                                        + "without a corresponding "
                                        + "captured validation message."
                        )
                                .category(
                                        EpubValidationIssue.Category
                                                .EPUB_CHECK
                                )
                                .validator(getName())
                                .detail(
                                        "exitCode",
                                        String.valueOf(exitCode)
                                )
                                .build()
                );
            }

            result.issues(issues)
                    .completedAt(
                            Instant.now()
                    )
                    .message(
                            createResultMessage(
                                    report,
                                    issues
                            )
                    );

        } catch (Throwable exception) {

            /*
             * EPUBCheck의 라이브러리 초기화 오류까지
             * 검증 실패 결과로 변환합니다.
             *
             * LinkageError / NoClassDefFoundError 등 dependency
             * 문제 역시 사용자가 진단할 수 있어야 합니다.
             */
            result.cause(exception)
                    .completedAt(
                            Instant.now()
                    )
                    .message(
                            "EPUBCheck validation could not "
                                    + "be completed: "
                                    + safeMessage(exception)
                    );
        }

        return result.build();
    }

    /**
     * EPUBCheck Java API가 classpath에서 사용 가능한지 확인합니다.
     */
    @Override
    public boolean isAvailable() {
        try {
            Class.forName(
                    "com.adobe.epubcheck.api.EpubCheck",
                    false,
                    DefaultEpubCheckValidator.class
                            .getClassLoader()
            );

            Class.forName(
                    "com.adobe.epubcheck.api.Report",
                    false,
                    DefaultEpubCheckValidator.class
                            .getClassLoader()
            );

            return true;

        } catch (ClassNotFoundException
                | LinkageError exception) {

            return false;
        }
    }

    /**
     * EPUBCheck 실행 환경 정보를 반환합니다.
     */
    @Override
    public Availability getAvailability() {

        if (!isAvailable()) {
            return Availability.unavailable(
                    "EPUBCheck Java API is not available "
                            + "on the application classpath."
            );
        }

        return Availability.available(
                getVersion()
        );
    }

    /**
     * EPUBCheck 버전을 반환합니다.
     */
    @Override
    public String getVersion() {
        try {
            String version =
                    EpubCheck.version();

            if (version == null
                    || version.isBlank()) {
                return FALLBACK_VERSION;
            }

            return version.trim();

        } catch (Throwable exception) {
            return FALLBACK_VERSION;
        }
    }

    @Override
    public String getName() {
        return VALIDATOR_NAME;
    }

    /**
     * Java API 직접 호출 방식입니다.
     */
    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.JAVA_API;
    }

    /**
     * EPUBCheck 메시지를 EPUB 공통 검증 이슈로 변환합니다.
     */
    private List<EpubValidationIssue> convertMessages(
            List<CapturedMessage> messages
    ) {

        if (messages == null
                || messages.isEmpty()) {

            return Collections.emptyList();
        }

        List<EpubValidationIssue> result =
                new ArrayList<>(
                        messages.size()
                );

        for (CapturedMessage captured :
                messages) {

            if (captured == null) {
                continue;
            }

            CheckMessage message =
                    new CheckMessage(
                            captured.code(),
                            mapLevel(
                                    captured.severity()
                            ),
                            captured.message(),
                            captured.epubPath(),
                            captured.line(),
                            captured.column(),
                            captured.originalMessage()
                    );

            EpubValidationIssue.Builder builder =
                    message
                            .toValidationIssue()
                            .toBuilder();

            if (captured.suggestion() != null
                    && !captured.suggestion()
                            .isBlank()) {

                builder.suggestion(
                        captured.suggestion()
                );
            }

            if (captured.context() != null
                    && !captured.context()
                            .isBlank()) {

                builder.detail(
                        "context",
                        captured.context()
                );
            }

            result.add(
                    builder.build()
            );
        }

        return List.copyOf(result);
    }

    /**
     * EPUBCheck Severity를 내부 Level로 변환합니다.
     */
    private Level mapLevel(
            Severity severity
    ) {

        if (severity == null) {
            return Level.ERROR;
        }

        switch (severity) {

            case INFO:
                return Level.INFO;

            case WARNING:
                return Level.WARNING;

            case ERROR:
                return Level.ERROR;

            case FATAL:
                return Level.FATAL;

            default:
                return Level.ERROR;
        }
    }

    private boolean containsBlockingOrWarningIssue(
            List<EpubValidationIssue> issues
    ) {

        return issues.stream()
                .anyMatch(issue ->
                        issue.isBlocking()
                                || issue.isWarning()
                );
    }

    private String createResultMessage(
            CollectingReport report,
            List<EpubValidationIssue> issues
    ) {

        int fatal =
                report.getFatalErrorCount();

        int errors =
                report.getErrorCount();

        int warnings =
                report.getWarningCount();

        int infos =
                report.getInfoCount();

        int usages =
                report.getUsageCount();

        if (fatal == 0
                && errors == 0
                && warnings == 0) {

            return "EPUBCheck validation passed. "
                    + "fatal=0, errors=0, warnings=0, "
                    + "infos="
                    + infos
                    + ", usages="
                    + usages;
        }

        return "EPUBCheck validation completed. "
                + "fatal="
                + fatal
                + ", errors="
                + errors
                + ", warnings="
                + warnings
                + ", infos="
                + infos
                + ", usages="
                + usages
                + ", captured="
                + issues.size();
    }

    private String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown EPUBCheck error.";
        }

        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        return message.trim();
    }

    /**
     * EPUBCheck API에서 발생한 하나의 메시지를 보관합니다.
     */
    private record CapturedMessage(
            String code,
            Severity severity,
            String message,
            String epubPath,
            int line,
            int column,
            String suggestion,
            String context,
            String originalMessage
    ) {
    }

    /**
     * EPUBCheck의 {@link MasterReport}를 확장하여
     * 검증 메시지를 메모리에 수집합니다.
     *
     * <p>MasterReport가 message(MessageId, ...)에서
     * 오류/경고/치명 오류 등의 카운트를 자동으로 증가시키므로
     * 여기서는 실제 구조화된 메시지 정보만 저장합니다.</p>
     *
     * <p>EPUBCheck 5.3.0의 Report/MasterReport API를 기준으로 합니다.</p>
     */
    private static final class CollectingReport
            extends MasterReport {

        private final List<CapturedMessage> messages =
                new ArrayList<>();

        private CollectingReport(
                String epubFileName
        ) {
            super();

            setEpubFileName(
                    epubFileName
            );
        }

        /**
         * MasterReport.message(MessageId, ...)에서
         * dictionary lookup 및 severity count가 처리된 뒤
         * 이 메서드가 호출됩니다.
         */
        @Override
        public void message(
                Message message,
                EPUBLocation location,
                Object... args
        ) {

            if (message == null) {
                return;
            }

            String code =
                    message.getID() == null
                            ? "UNKNOWN"
                            : message
                                    .getID()
                                    .toString();

            Severity severity =
                    message.getSeverity();

            String formattedMessage =
                    formatMessage(
                            message,
                            args
                    );

            String suggestion =
                    normalize(
                            message.getSuggestion()
                    );

            String epubPath = null;

            int line = -1;

            int column = -1;

            String context = null;

            if (location != null) {

                epubPath =
                        normalizeEpubPath(
                                location.getPath()
                        );

                line =
                        location.getLine();

                column =
                        location.getColumn();

                /*
                 * EPUBCheck 5.3.0의 EPUBLocation#getContext()는
                 * Guava Optional<String>을 반환합니다.
                 */
                String locationContext =
                        location.getContext().orNull();

                if (locationContext != null
                        && !locationContext.isBlank()) {

                    context =
                            normalize(
                                    locationContext
                            );
                }
            }

            String original =
                    buildOriginalMessage(
                            code,
                            severity,
                            formattedMessage,
                            epubPath,
                            line,
                            column
                    );

            messages.add(
                    new CapturedMessage(
                            code,
                            severity,
                            formattedMessage,
                            epubPath,
                            normalizePosition(line),
                            normalizePosition(column),
                            suggestion,
                            context,
                            original
                    )
            );
        }

        /**
         * EPUBCheck가 검증 과정에서 수집하는 부가 feature 정보입니다.
         *
         * <p>FORMAT_VERSION 등의 정보는 검증 오류가 아니므로
         * EpubValidationIssue로 변환하지 않습니다.</p>
         */
        @Override
        public void info(
                String resource,
                FeatureEnum feature,
                String value
        ) {
            /*
             * 필요 시 향후 PublicationInfo 모델로 확장합니다.
             */
        }

        /**
         * 별도 report 파일을 생성하지 않으므로 0을 반환합니다.
         */
        @Override
        public int generate() {
            return 0;
        }

        /**
         * EPUBCheck Report 초기화 hook입니다.
         */
        @Override
        public void initialize() {
            /*
             * 별도 초기화가 필요하지 않습니다.
             */
        }

        private List<CapturedMessage>
                getCollectedMessages() {

            return List.copyOf(messages);
        }

        private String formatMessage(
                Message message,
                Object... args
        ) {

            try {
                String value;

                if (args != null
                        && args.length > 0) {

                    value =
                            message.getMessage(args);

                } else {
                    value =
                            message.getMessage();
                }

                if (value == null
                        || value.isBlank()) {

                    return "EPUBCheck validation issue.";
                }

                return normalizeWhitespace(
                        value
                );

            } catch (RuntimeException exception) {

                String fallback =
                        message.getMessage();

                return fallback == null
                        || fallback.isBlank()
                                ? "EPUBCheck validation issue."
                                : normalizeWhitespace(
                                        fallback
                                );
            }
        }

        private String buildOriginalMessage(
                String code,
                Severity severity,
                String message,
                String path,
                int line,
                int column
        ) {

            StringBuilder result =
                    new StringBuilder();

            if (severity != null) {
                result.append(
                        severity
                ).append(' ');
            }

            result.append('[')
                    .append(code)
                    .append(']');

            if (path != null) {
                result.append(' ')
                        .append(path);

                if (line >= 0) {
                    result.append(':')
                            .append(line);

                    if (column >= 0) {
                        result.append(':')
                                .append(column);
                    }
                }
            }

            result.append(" - ")
                    .append(message);

            return result.toString();
        }

        private static String normalizeEpubPath(
                String value
        ) {

            String normalized =
                    normalize(value);

            if (normalized == null) {
                return null;
            }

            normalized =
                    normalized.replace(
                            '\\',
                            '/'
                    );

            while (normalized.startsWith("./")) {
                normalized =
                        normalized.substring(2);
            }

            while (normalized.startsWith("/")) {
                normalized =
                        normalized.substring(1);
            }

            return normalized;
        }

        private static int normalizePosition(
                int value
        ) {
            return value < 0
                    ? -1
                    : value;
        }

        private static String normalizeWhitespace(
                String value
        ) {

            if (value == null) {
                return null;
            }

            return value
                    .replaceAll(
                            "\\s+",
                            " "
                    )
                    .trim();
        }

        private static String normalize(
                String value
        ) {

            if (value == null
                    || value.isBlank()) {
                return null;
            }

            return value.trim();
        }
    }
}
