/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB 검증 과정에서 발견된 단일 이슈를 표현합니다.
 *
 * <p>내부 EPUB 검증, XHTML 검증, 접근성 검증, EPUBCheck 등의
 * 다양한 검증 결과를 공통 형식으로 표현하기 위한 모델입니다.</p>
 *
 * <p>하나의 검증 이슈는 다음 정보를 가질 수 있습니다.</p>
 *
 * <ul>
 *     <li>검증 코드</li>
 *     <li>심각도</li>
 *     <li>검증 영역</li>
 *     <li>메시지</li>
 *     <li>EPUB 내부 경로</li>
 *     <li>로컬 파일 경로</li>
 *     <li>리소스 ID</li>
 *     <li>행/열 위치</li>
 *     <li>수정 가능 여부</li>
 *     <li>권장 수정 방법</li>
 * </ul>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubValidationIssue {

    /**
     * 검증 이슈 코드입니다.
     *
     * <p>예:</p>
     *
     * <pre>
     * EPUB-MANIFEST-001
     * EPUB-SPINE-001
     * EPUB-A11Y-ALT-001
     * EPUB-XHTML-001
     * EPUBCHECK-OPF-001
     * </pre>
     */
    private final String code;

    /**
     * 검증 이슈 심각도입니다.
     */
    private final Severity severity;

    /**
     * 검증 대상 영역입니다.
     */
    private final Category category;

    /**
     * 사람이 읽을 수 있는 오류 또는 경고 메시지입니다.
     */
    private final String message;

    /**
     * EPUB 내부 경로입니다.
     *
     * <p>예: {@code Text/chapter01.xhtml}</p>
     */
    private final String epubPath;

    /**
     * 로컬 파일 경로입니다.
     */
    private final Path filePath;

    /**
     * manifest 리소스 ID입니다.
     */
    private final String resourceId;

    /**
     * XML/HTML의 행 번호입니다.
     *
     * <p>알 수 없는 경우 {@code -1}입니다.</p>
     */
    private final int line;

    /**
     * XML/HTML의 열 번호입니다.
     *
     * <p>알 수 없는 경우 {@code -1}입니다.</p>
     */
    private final int column;

    /**
     * 이슈와 관련된 XML/HTML 요소명입니다.
     */
    private final String element;

    /**
     * 관련 XML/HTML 속성명입니다.
     */
    private final String attribute;

    /**
     * 현재 문제 값입니다.
     */
    private final String actualValue;

    /**
     * 기대되는 값입니다.
     */
    private final String expectedValue;

    /**
     * 자동 수정 가능한 문제인지 여부입니다.
     */
    private final boolean autoFixable;

    /**
     * 권장 수정 방법입니다.
     */
    private final String suggestion;

    /**
     * 검증기 이름입니다.
     *
     * <p>예: InternalValidator, AccessibilityValidator,
     * EPUBCheck</p>
     */
    private final String validator;

    /**
     * 검증기의 원본 메시지입니다.
     */
    private final String originalMessage;

    /**
     * 추가 진단 정보입니다.
     */
    private final Map<String, String> details;

    private EpubValidationIssue(Builder builder) {
        this.code = requireCode(builder.code);

        this.severity = Objects.requireNonNull(
                builder.severity,
                "EPUB validation severity must not be null."
        );

        this.category = builder.category == null
                ? Category.GENERAL
                : builder.category;

        this.message = requireText(
                builder.message,
                "EPUB validation message"
        );

        this.epubPath = normalizeEpubPath(
                builder.epubPath
        );

        this.filePath = normalizePath(
                builder.filePath
        );

        this.resourceId = normalizeOptionalText(
                builder.resourceId
        );

        this.line = normalizePosition(
                builder.line,
                "line"
        );

        this.column = normalizePosition(
                builder.column,
                "column"
        );

        this.element = normalizeOptionalText(
                builder.element
        );

        this.attribute = normalizeOptionalText(
                builder.attribute
        );

        this.actualValue = normalizeOptionalText(
                builder.actualValue
        );

        this.expectedValue = normalizeOptionalText(
                builder.expectedValue
        );

        this.autoFixable = builder.autoFixable;

        this.suggestion = normalizeOptionalText(
                builder.suggestion
        );

        this.validator = normalizeOptionalText(
                builder.validator
        );

        this.originalMessage = normalizeOptionalText(
                builder.originalMessage
        );

        this.details = immutableDetails(
                builder.details
        );

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(
            String code,
            Severity severity,
            String message
    ) {
        return new Builder()
                .code(code)
                .severity(severity)
                .message(message);
    }

    /**
     * 오류 이슈를 생성합니다.
     */
    public static EpubValidationIssue error(
            String code,
            String message
    ) {
        return builder(
                code,
                Severity.ERROR,
                message
        ).build();
    }

    /**
     * 경고 이슈를 생성합니다.
     */
    public static EpubValidationIssue warning(
            String code,
            String message
    ) {
        return builder(
                code,
                Severity.WARNING,
                message
        ).build();
    }

    /**
     * 정보 이슈를 생성합니다.
     */
    public static EpubValidationIssue info(
            String code,
            String message
    ) {
        return builder(
                code,
                Severity.INFO,
                message
        ).build();
    }

    public String getCode() {
        return code;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Category getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public Optional<String> getEpubPath() {
        return Optional.ofNullable(epubPath);
    }

    public Optional<Path> getFilePath() {
        return Optional.ofNullable(filePath);
    }

    public Optional<String> getResourceId() {
        return Optional.ofNullable(resourceId);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public Optional<String> getElement() {
        return Optional.ofNullable(element);
    }

    public Optional<String> getAttribute() {
        return Optional.ofNullable(attribute);
    }

    public Optional<String> getActualValue() {
        return Optional.ofNullable(actualValue);
    }

    public Optional<String> getExpectedValue() {
        return Optional.ofNullable(expectedValue);
    }

    public boolean isAutoFixable() {
        return autoFixable;
    }

    public Optional<String> getSuggestion() {
        return Optional.ofNullable(suggestion);
    }

    public Optional<String> getValidator() {
        return Optional.ofNullable(validator);
    }

    public Optional<String> getOriginalMessage() {
        return Optional.ofNullable(originalMessage);
    }

    public Map<String, String> getDetails() {
        return details;
    }

    /**
     * 행 번호가 존재하는지 확인합니다.
     */
    public boolean hasLine() {
        return line >= 0;
    }

    /**
     * 열 번호가 존재하는지 확인합니다.
     */
    public boolean hasColumn() {
        return column >= 0;
    }

    /**
     * 파일 위치 정보가 존재하는지 확인합니다.
     */
    public boolean hasLocation() {
        return epubPath != null
                || filePath != null
                || line >= 0;
    }

    /**
     * 오류인지 확인합니다.
     */
    public boolean isError() {
        return severity == Severity.ERROR
                || severity == Severity.FATAL;
    }

    /**
     * 치명적 오류인지 확인합니다.
     */
    public boolean isFatal() {
        return severity == Severity.FATAL;
    }

    /**
     * 경고인지 확인합니다.
     */
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    /**
     * 정보 메시지인지 확인합니다.
     */
    public boolean isInfo() {
        return severity == Severity.INFO;
    }

    /**
     * EPUB 생성을 중단해야 하는 수준인지 확인합니다.
     */
    public boolean isBlocking() {
        return severity.isBlocking();
    }

    /**
     * 추가 상세 정보를 조회합니다.
     */
    public Optional<String> getDetail(
            String name
    ) {
        String normalized = normalizeOptionalText(name);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                details.get(normalized)
        );
    }

    /**
     * 사용자 화면 표시용 위치 문자열을 반환합니다.
     *
     * <p>예:</p>
     *
     * <pre>
     * Text/chapter01.xhtml:15:8
     * Text/chapter01.xhtml:15
     * Text/chapter01.xhtml
     * </pre>
     */
    public Optional<String> getLocationDescription() {
        String base = null;

        if (epubPath != null) {
            base = epubPath;

        } else if (filePath != null) {
            base = filePath.toString();
        }

        if (base == null) {
            return Optional.empty();
        }

        StringBuilder result =
                new StringBuilder(base);

        if (line >= 0) {
            result.append(':').append(line);

            if (column >= 0) {
                result.append(':').append(column);
            }
        }

        return Optional.of(result.toString());
    }

    /**
     * 사용자 화면 표시용 전체 메시지를 반환합니다.
     */
    public String getDisplayMessage() {
        StringBuilder result =
                new StringBuilder();

        result.append('[')
                .append(severity.name())
                .append("] ")
                .append(code)
                .append(" - ")
                .append(message);

        getLocationDescription().ifPresent(
                location ->
                        result.append(" (")
                                .append(location)
                                .append(')')
        );

        return result.toString();
    }

    /**
     * 현재 이슈를 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .code(code)
                .severity(severity)
                .category(category)
                .message(message)
                .epubPath(epubPath)
                .filePath(filePath)
                .resourceId(resourceId)
                .line(line)
                .column(column)
                .element(element)
                .attribute(attribute)
                .actualValue(actualValue)
                .expectedValue(expectedValue)
                .autoFixable(autoFixable)
                .suggestion(suggestion)
                .validator(validator)
                .originalMessage(originalMessage)
                .details(details);
    }

    private void validate() {
        if (column >= 0 && line < 0) {
            throw new IllegalArgumentException(
                    "EPUB validation column requires a line number."
            );
        }

        if (autoFixable && suggestion == null) {
            /*
             * 자동 수정 가능한 경우 suggestion을 권장하지만
             * 반드시 필요한 것은 아니므로 오류로 처리하지 않습니다.
             */
        }
    }

    private static String requireCode(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "EPUB validation issue code must not be blank."
            );
        }

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB validation issue code must not contain "
                            + "whitespace: "
                            + value
            );
        }

        return normalized;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }

        return value.trim();
    }

    private static int normalizePosition(
            int value,
            String fieldName
    ) {
        if (value < -1) {
            throw new IllegalArgumentException(
                    "EPUB validation "
                            + fieldName
                            + " must be -1 or greater: "
                            + value
            );
        }

        return value;
    }

    private static String normalizeEpubPath(
            String value
    ) {
        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized =
                    normalized.substring(2);
        }

        return normalized;
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

    private static Map<String, String> immutableDetails(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
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

        return Map.copyOf(result);
    }

    private static String normalizeOptionalText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static boolean containsWhitespace(
            String value
    ) {
        for (int index = 0;
                index < value.length();
                index++) {

            if (Character.isWhitespace(
                    value.charAt(index)
            )) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubValidationIssue other)) {
            return false;
        }

        return code.equals(other.code)
                && severity == other.severity
                && Objects.equals(epubPath, other.epubPath)
                && line == other.line
                && column == other.column
                && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                code,
                severity,
                epubPath,
                line,
                column,
                message
        );
    }

    @Override
    public String toString() {
        return "EpubValidationIssue{"
                + "code='" + code + '\''
                + ", severity=" + severity
                + ", category=" + category
                + ", message='" + message + '\''
                + ", epubPath='" + epubPath + '\''
                + ", resourceId='" + resourceId + '\''
                + ", line=" + line
                + ", column=" + column
                + ", autoFixable=" + autoFixable
                + '}';
    }

    /**
     * EPUB 검증 이슈 심각도입니다.
     */
    public enum Severity {

        /**
         * 참고 정보입니다.
         */
        INFO(0, false),

        /**
         * EPUB 생성은 가능하지만 확인이 필요한 경고입니다.
         */
        WARNING(1, false),

        /**
         * EPUB 유효성에 영향을 주는 오류입니다.
         */
        ERROR(2, true),

        /**
         * EPUB 생성 또는 검증을 계속하기 어려운 치명적 오류입니다.
         */
        FATAL(3, true);

        private final int level;

        private final boolean blocking;

        Severity(
                int level,
                boolean blocking
        ) {
            this.level = level;
            this.blocking = blocking;
        }

        public int getLevel() {
            return level;
        }

        public boolean isBlocking() {
            return blocking;
        }

        public boolean isAtLeast(
                Severity other
        ) {
            Objects.requireNonNull(
                    other,
                    "Severity must not be null."
            );

            return level >= other.level;
        }
    }

    /**
     * EPUB 검증 영역입니다.
     */
    public enum Category {

        /**
         * 분류되지 않은 일반 검증입니다.
         */
        GENERAL,

        /**
         * mimetype 검증입니다.
         */
        MIMETYPE,

        /**
         * META-INF/container.xml 검증입니다.
         */
        CONTAINER,

        /**
         * OPF 패키지 문서 검증입니다.
         */
        PACKAGE_DOCUMENT,

        /**
         * metadata 검증입니다.
         */
        METADATA,

        /**
         * manifest 검증입니다.
         */
        MANIFEST,

        /**
         * spine 검증입니다.
         */
        SPINE,

        /**
         * Navigation Document 검증입니다.
         */
        NAVIGATION,

        /**
         * NCX 검증입니다.
         */
        NCX,

        /**
         * XHTML 문서 검증입니다.
         */
        XHTML,

        /**
         * CSS 검증입니다.
         */
        CSS,

        /**
         * 이미지 검증입니다.
         */
        IMAGE,

        /**
         * 폰트 검증입니다.
         */
        FONT,

        /**
         * 오디오 리소스 검증입니다.
         */
        AUDIO,

        /**
         * 비디오 리소스 검증입니다.
         */
        VIDEO,

        /**
         * media overlay 검증입니다.
         */
        MEDIA_OVERLAY,

        /**
         * 파일 또는 경로 검증입니다.
         */
        RESOURCE,

        /**
         * ZIP/EPUB 아카이브 구조 검증입니다.
         */
        ARCHIVE,

        /**
         * EPUB 접근성 검증입니다.
         */
        ACCESSIBILITY,

        /**
         * 이미지 대체 텍스트 검증입니다.
         */
        ALTERNATIVE_TEXT,

        /**
         * 문서 언어 검증입니다.
         */
        LANGUAGE,

        /**
         * 제목 및 heading 구조 검증입니다.
         */
        HEADING,

        /**
         * 링크 및 참조 검증입니다.
         */
        LINK,

        /**
         * EPUBCheck 검증입니다.
         */
        EPUB_CHECK
    }

    /**
     * {@link EpubValidationIssue} Builder입니다.
     */
    public static final class Builder {

        private String code;

        private Severity severity =
                Severity.ERROR;

        private Category category =
                Category.GENERAL;

        private String message;

        private String epubPath;

        private Path filePath;

        private String resourceId;

        /**
         * -1은 위치 정보 없음입니다.
         */
        private int line = -1;

        /**
         * -1은 위치 정보 없음입니다.
         */
        private int column = -1;

        private String element;

        private String attribute;

        private String actualValue;

        private String expectedValue;

        private boolean autoFixable;

        private String suggestion;

        private String validator;

        private String originalMessage;

        private final Map<String, String> details =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder severity(
                Severity severity
        ) {
            this.severity = severity;
            return this;
        }

        public Builder category(
                Category category
        ) {
            this.category = category;
            return this;
        }

        public Builder message(
                String message
        ) {
            this.message = message;
            return this;
        }

        public Builder epubPath(
                String epubPath
        ) {
            this.epubPath = epubPath;
            return this;
        }

        public Builder filePath(
                Path filePath
        ) {
            this.filePath = filePath;
            return this;
        }

        public Builder resourceId(
                String resourceId
        ) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder column(int column) {
            this.column = column;
            return this;
        }

        /**
         * 행과 열을 한 번에 설정합니다.
         */
        public Builder location(
                int line,
                int column
        ) {
            this.line = line;
            this.column = column;

            return this;
        }

        /**
         * EPUB 경로와 행/열 위치를 한 번에 설정합니다.
         */
        public Builder location(
                String epubPath,
                int line,
                int column
        ) {
            this.epubPath = epubPath;
            this.line = line;
            this.column = column;

            return this;
        }

        public Builder element(
                String element
        ) {
            this.element = element;
            return this;
        }

        public Builder attribute(
                String attribute
        ) {
            this.attribute = attribute;
            return this;
        }

        public Builder actualValue(
                String actualValue
        ) {
            this.actualValue = actualValue;
            return this;
        }

        public Builder expectedValue(
                String expectedValue
        ) {
            this.expectedValue = expectedValue;
            return this;
        }

        public Builder autoFixable(
                boolean autoFixable
        ) {
            this.autoFixable = autoFixable;
            return this;
        }

        public Builder suggestion(
                String suggestion
        ) {
            this.suggestion = suggestion;
            return this;
        }

        /**
         * 자동 수정 가능한 이슈로 설정하면서 수정 방법을 지정합니다.
         */
        public Builder autoFixable(
                String suggestion
        ) {
            this.autoFixable = true;
            this.suggestion = suggestion;

            return this;
        }

        public Builder validator(
                String validator
        ) {
            this.validator = validator;
            return this;
        }

        public Builder originalMessage(
                String originalMessage
        ) {
            this.originalMessage = originalMessage;
            return this;
        }

        public Builder detail(
                String name,
                String value
        ) {
            details.put(name, value);
            return this;
        }

        public Builder details(
                Map<String, String> details
        ) {
            if (details != null) {
                this.details.putAll(details);
            }

            return this;
        }

        public EpubValidationIssue build() {
            return new EpubValidationIssue(this);
        }
    }
}