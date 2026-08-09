/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * EPUB 생성 과정에서 발생하는 예외입니다.
 *
 * <p>EPUB 생성 실패의 단계, 오류 코드, 요청 ID, 관련 리소스 및
 * 파일 경로 등의 진단 정보를 함께 제공합니다.</p>
 *
 * <p>호출자는 {@link #getErrorCode()}와 {@link #getStage()}를 기준으로
 * 오류를 분류하고, 사용자 메시지 또는 로그를 구성할 수 있습니다.</p>
 */
public class EpubGenerationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * 오류를 식별하는 코드입니다.
     */
    private final ErrorCode errorCode;

    /**
     * 오류가 발생한 EPUB 생성 단계입니다.
     */
    private final Stage stage;

    /**
     * EPUB 생성 요청 ID입니다.
     */
    private final String requestId;

    /**
     * 오류와 관련된 manifest 리소스 ID입니다.
     */
    private final String resourceId;

    /**
     * 오류와 관련된 EPUB 내부 경로입니다.
     */
    private final String epubPath;

    /**
     * 오류와 관련된 로컬 파일 경로입니다.
     */
    private final Path filePath;

    /**
     * 재시도 가능한 오류인지 여부입니다.
     */
    private final boolean retryable;

    /**
     * 추가 진단 정보입니다.
     */
    private final Map<String, String> details;

    public EpubGenerationException(
            ErrorCode errorCode,
            String message
    ) {
        this(
                errorCode,
                Stage.UNKNOWN,
                message,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    public EpubGenerationException(
            ErrorCode errorCode,
            String message,
            Throwable cause
    ) {
        this(
                errorCode,
                Stage.UNKNOWN,
                message,
                cause,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    public EpubGenerationException(
            ErrorCode errorCode,
            Stage stage,
            String message
    ) {
        this(
                errorCode,
                stage,
                message,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    public EpubGenerationException(
            ErrorCode errorCode,
            Stage stage,
            String message,
            Throwable cause
    ) {
        this(
                errorCode,
                stage,
                message,
                cause,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    private EpubGenerationException(
            ErrorCode errorCode,
            Stage stage,
            String message,
            Throwable cause,
            String requestId,
            String resourceId,
            String epubPath,
            boolean retryable,
            Path filePath,
            Map<String, String> details
    ) {
        super(requireMessage(message), cause);

        this.errorCode = errorCode == null
                ? ErrorCode.UNKNOWN
                : errorCode;

        this.stage = stage == null
                ? this.errorCode.getDefaultStage()
                : stage;

        this.requestId = normalizeOptionalText(requestId);
        this.resourceId = normalizeOptionalText(resourceId);
        this.epubPath = normalizeEpubPath(epubPath);
        this.filePath = normalizeOptionalPath(filePath);
        this.retryable = retryable;
        this.details = immutableDetails(details);
    }

    /**
     * Builder를 생성합니다.
     *
     * @param errorCode 오류 코드
     * @param message   오류 메시지
     * @return 예외 Builder
     */
    public static Builder builder(
            ErrorCode errorCode,
            String message
    ) {
        return new Builder(errorCode, message);
    }

    /**
     * 원인 예외를 EPUB 생성 예외로 변환합니다.
     *
     * <p>원인이 이미 {@code EpubGenerationException}이면 그대로
     * 반환합니다.</p>
     *
     * @param cause     원인 예외
     * @param errorCode 오류 코드
     * @param stage     생성 단계
     * @param message   오류 메시지
     * @return EPUB 생성 예외
     */
    public static EpubGenerationException wrap(
            Throwable cause,
            ErrorCode errorCode,
            Stage stage,
            String message
    ) {
        if (cause instanceof EpubGenerationException exception) {
            return exception;
        }

        return builder(errorCode, message)
                .stage(stage)
                .cause(cause)
                .build();
    }

    /**
     * 생성 요청 검증 실패 예외를 생성합니다.
     *
     * @param requestId 요청 ID
     * @param message   오류 메시지
     * @return 생성 예외
     */
    public static EpubGenerationException invalidRequest(
            String requestId,
            String message
    ) {
        return builder(ErrorCode.INVALID_REQUEST, message)
                .stage(Stage.REQUEST_VALIDATION)
                .requestId(requestId)
                .build();
    }

    /**
     * 리소스 처리 실패 예외를 생성합니다.
     *
     * @param requestId  요청 ID
     * @param resourceId 리소스 ID
     * @param epubPath   EPUB 내부 경로
     * @param filePath   로컬 파일 경로
     * @param cause      원인 예외
     * @return 생성 예외
     */
    public static EpubGenerationException resourceProcessingFailed(
            String requestId,
            String resourceId,
            String epubPath,
            Path filePath,
            Throwable cause
    ) {
        return builder(
                ErrorCode.RESOURCE_PROCESSING_FAILED,
                "Failed to process EPUB resource: " + resourceId
        )
                .stage(Stage.RESOURCE_PROCESSING)
                .requestId(requestId)
                .resourceId(resourceId)
                .epubPath(epubPath)
                .filePath(filePath)
                .cause(cause)
                .build();
    }

    /**
     * EPUB ZIP 패키징 실패 예외를 생성합니다.
     *
     * @param requestId 요청 ID
     * @param outputFile 출력 파일
     * @param cause 원인 예외
     * @return 생성 예외
     */
    public static EpubGenerationException packagingFailed(
            String requestId,
            Path outputFile,
            Throwable cause
    ) {
        return builder(
                ErrorCode.PACKAGING_FAILED,
                "Failed to package the EPUB file."
        )
                .stage(Stage.PACKAGING)
                .requestId(requestId)
                .filePath(outputFile)
                .cause(cause)
                .build();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Stage getStage() {
        return stage;
    }

    public Optional<String> getRequestId() {
        return Optional.ofNullable(requestId);
    }

    public Optional<String> getResourceId() {
        return Optional.ofNullable(resourceId);
    }

    public Optional<String> getEpubPath() {
        return Optional.ofNullable(epubPath);
    }

    public Optional<Path> getFilePath() {
        return Optional.ofNullable(filePath);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Optional<String> getDetail(String name) {
        String normalized = normalizeOptionalText(name);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(details.get(normalized));
    }

    /**
     * 사용자 화면에 표시할 기본 메시지를 반환합니다.
     *
     * @return 오류 코드 표시명과 상세 메시지
     */
    public String getDisplayMessage() {
        return errorCode.getDisplayName() + ": " + getMessage();
    }

    /**
     * 로그에 사용할 상세 진단 문자열을 반환합니다.
     *
     * @return 진단 문자열
     */
    public String getDiagnosticMessage() {
        StringBuilder result = new StringBuilder();

        result.append('[')
                .append(errorCode.name())
                .append("] stage=")
                .append(stage.name());

        if (requestId != null) {
            result.append(", requestId=").append(requestId);
        }

        if (resourceId != null) {
            result.append(", resourceId=").append(resourceId);
        }

        if (epubPath != null) {
            result.append(", epubPath=").append(epubPath);
        }

        if (filePath != null) {
            result.append(", filePath=").append(filePath);
        }

        result.append(", retryable=")
                .append(retryable)
                .append(", message=")
                .append(getMessage());

        if (!details.isEmpty()) {
            result.append(", details=").append(details);
        }

        return result.toString();
    }

    /**
     * 동일 정보를 기반으로 Builder를 생성합니다.
     *
     * @return 복사된 Builder
     */
    public Builder toBuilder() {
        return new Builder(errorCode, getMessage())
                .stage(stage)
                .cause(getCause())
                .requestId(requestId)
                .resourceId(resourceId)
                .epubPath(epubPath)
                .filePath(filePath)
                .retryable(retryable)
                .details(details);
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB generation exception message must not be blank."
            );
        }

        return message.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeEpubPath(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }

    private static Path normalizeOptionalPath(Path value) {
        return value == null
                ? null
                : value.toAbsolutePath().normalize();
    }

    private static Map<String, String> immutableDetails(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = normalizeOptionalText(entry.getKey());
            String value = normalizeOptionalText(entry.getValue());

            if (name != null && value != null) {
                result.put(name, value);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public String toString() {
        return "EpubGenerationException{"
                + "errorCode=" + errorCode
                + ", stage=" + stage
                + ", requestId='" + requestId + '\''
                + ", resourceId='" + resourceId + '\''
                + ", epubPath='" + epubPath + '\''
                + ", filePath=" + filePath
                + ", retryable=" + retryable
                + ", message='" + getMessage() + '\''
                + '}';
    }

    /**
     * EPUB 생성 실패 코드입니다.
     */
    public enum ErrorCode {

        INVALID_REQUEST(
                "잘못된 생성 요청",
                Stage.REQUEST_VALIDATION,
                false
        ),

        UNSUPPORTED_REQUEST(
                "지원하지 않는 생성 요청",
                Stage.REQUEST_VALIDATION,
                false
        ),

        VALIDATION_FAILED(
                "생성 전 검증 실패",
                Stage.PRE_VALIDATION,
                false
        ),

        WORKING_DIRECTORY_PREPARATION_FAILED(
                "작업 디렉터리 준비 실패",
                Stage.WORKING_DIRECTORY_PREPARATION,
                true
        ),

        MIMETYPE_GENERATION_FAILED(
                "mimetype 파일 생성 실패",
                Stage.MIMETYPE_GENERATION,
                true
        ),

        CONTAINER_GENERATION_FAILED(
                "container.xml 생성 실패",
                Stage.CONTAINER_GENERATION,
                true
        ),

        PACKAGE_DOCUMENT_GENERATION_FAILED(
                "OPF 패키지 문서 생성 실패",
                Stage.PACKAGE_DOCUMENT_GENERATION,
                false
        ),

        NAVIGATION_DOCUMENT_GENERATION_FAILED(
                "Navigation Document 생성 실패",
                Stage.NAVIGATION_GENERATION,
                false
        ),

        NCX_GENERATION_FAILED(
                "NCX 생성 실패",
                Stage.NCX_GENERATION,
                false
        ),

        RESOURCE_NOT_FOUND(
                "EPUB 리소스를 찾을 수 없음",
                Stage.RESOURCE_PROCESSING,
                false
        ),

        RESOURCE_PROCESSING_FAILED(
                "EPUB 리소스 처리 실패",
                Stage.RESOURCE_PROCESSING,
                true
        ),

        RESOURCE_COPY_FAILED(
                "EPUB 리소스 복사 실패",
                Stage.RESOURCE_PROCESSING,
                true
        ),

        RESOURCE_WRITE_FAILED(
                "EPUB 리소스 기록 실패",
                Stage.RESOURCE_PROCESSING,
                true
        ),

        DOCUMENT_GENERATION_FAILED(
                "EPUB 문서 생성 실패",
                Stage.DOCUMENT_GENERATION,
                false
        ),

        PACKAGING_FAILED(
                "EPUB ZIP 패키징 실패",
                Stage.PACKAGING,
                true
        ),

        OUTPUT_WRITE_FAILED(
                "EPUB 출력 파일 기록 실패",
                Stage.OUTPUT_WRITE,
                true
        ),

        POST_VALIDATION_FAILED(
                "생성 후 검증 실패",
                Stage.POST_VALIDATION,
                false
        ),

        EPUB_CHECK_FAILED(
                "EPUBCheck 검증 실패",
                Stage.EPUB_CHECK,
                false
        ),

        ACCESSIBILITY_VALIDATION_FAILED(
                "EPUB 접근성 검증 실패",
                Stage.ACCESSIBILITY_VALIDATION,
                false
        ),

        CLEANUP_FAILED(
                "EPUB 작업 파일 정리 실패",
                Stage.CLEANUP,
                true
        ),

        CANCELLED(
                "EPUB 생성 취소",
                Stage.CANCELLED,
                false
        ),

        UNKNOWN(
                "알 수 없는 EPUB 생성 오류",
                Stage.UNKNOWN,
                false
        );

        private final String displayName;

        private final Stage defaultStage;

        private final boolean retryable;

        ErrorCode(
                String displayName,
                Stage defaultStage,
                boolean retryable
        ) {
            this.displayName = displayName;
            this.defaultStage = defaultStage;
            this.retryable = retryable;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Stage getDefaultStage() {
            return defaultStage;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /**
     * EPUB 생성 처리 단계입니다.
     */
    public enum Stage {

        REQUEST_VALIDATION,

        PRE_VALIDATION,

        WORKING_DIRECTORY_PREPARATION,

        MIMETYPE_GENERATION,

        CONTAINER_GENERATION,

        PACKAGE_DOCUMENT_GENERATION,

        NAVIGATION_GENERATION,

        NCX_GENERATION,

        DOCUMENT_GENERATION,

        RESOURCE_PROCESSING,

        PACKAGING,

        OUTPUT_WRITE,

        POST_VALIDATION,

        ACCESSIBILITY_VALIDATION,

        EPUB_CHECK,

        CLEANUP,

        CANCELLED,

        UNKNOWN
    }

    /**
     * {@link EpubGenerationException} 생성 Builder입니다.
     */
    public static final class Builder {

        private final ErrorCode errorCode;

        private final String message;

        private Stage stage;

        private Throwable cause;

        private String requestId;

        private String resourceId;

        private String epubPath;

        private Path filePath;

        private Boolean retryable;

        private final Map<String, String> details =
                new LinkedHashMap<>();

        private Builder(
                ErrorCode errorCode,
                String message
        ) {
            this.errorCode = errorCode == null
                    ? ErrorCode.UNKNOWN
                    : errorCode;
            this.message = requireMessage(message);
        }

        public Builder stage(Stage stage) {
            this.stage = stage;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder epubPath(String epubPath) {
            this.epubPath = epubPath;
            return this;
        }

        public Builder filePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
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

        public EpubGenerationException build() {
            return new EpubGenerationException(
                    errorCode,
                    stage == null
                            ? errorCode.getDefaultStage()
                            : stage,
                    message,
                    cause,
                    requestId,
                    resourceId,
                    epubPath,
                    retryable == null
                            ? errorCode.isRetryable()
                            : retryable,
                    filePath,
                    details
            );
        }
    }
}