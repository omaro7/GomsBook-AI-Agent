/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm.ollama;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Ollama 서버 상태 확인 결과입니다.
 *
 * <p>
 * 서버 연결 여부, 버전 정보, 응답 시간 및 오류 정보를 공통 형식으로
 * 표현합니다.
 * </p>
 * 
 * Instant startedAt = Instant.now();

	OllamaHealthResult result = OllamaHealthResult.available(
            "0.12.0",
            startedAt,
            Duration.ofMillis(35)
    );
	
	System.out.println(result.available());
	System.out.println(result.hasVersion());
	System.out.println(result.summary());

 */
public record OllamaHealthResult(

        /**
         * Ollama 서버를 사용할 수 있는지 나타냅니다.
         */
        boolean available,

        /**
         * Ollama 서버 버전입니다.
         *
         * <p>예: {@code 0.12.0}</p>
         */
        String version,

        /**
         * 상태 확인 시작 시각입니다.
         */
        Instant checkedAt,

        /**
         * 상태 확인에 소요된 시간입니다.
         */
        Duration duration,

        /**
         * 상태 설명 메시지입니다.
         */
        String message,

        /**
         * 오류 코드입니다.
         *
         * <p>
         * 정상 상태에서는 {@code null}입니다.
         * </p>
         */
        String errorCode,

        /**
         * 추가 상태 정보입니다.
         */
        Map<String, Object> metadata

) implements Serializable {

    /**
     * 상태 결과를 정규화하고 무결성을 검증합니다.
     */
    public OllamaHealthResult {
        version = normalizeText(version);

        checkedAt = checkedAt == null
                ? Instant.now()
                : checkedAt;

        duration = duration == null
                ? Duration.ZERO
                : duration;

        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                    "duration cannot be negative."
            );
        }

        message = normalizeText(message);

        if (message == null) {
            message = available
                    ? "Ollama server is available."
                    : "Ollama server is unavailable.";
        }

        errorCode = normalizeText(errorCode);

        metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);

        if (available && errorCode != null) {
            throw new IllegalArgumentException(
                    "Available health result cannot contain an errorCode."
            );
        }
    }

    /**
     * 정상 상태 결과를 생성합니다.
     *
     * @param version Ollama 버전
     * @param checkedAt 상태 확인 시각
     * @param duration 응답 시간
     * @return 정상 상태 결과
     */
    public static OllamaHealthResult available(
            String version,
            Instant checkedAt,
            Duration duration
    ) {
        return new OllamaHealthResult(
                true,
                version,
                checkedAt,
                duration,
                "Ollama server is available.",
                null,
                Map.of()
        );
    }

    /**
     * 정상 상태 결과를 추가 메타데이터와 함께 생성합니다.
     *
     * @param version Ollama 버전
     * @param checkedAt 상태 확인 시각
     * @param duration 응답 시간
     * @param metadata 추가 정보
     * @return 정상 상태 결과
     */
    public static OllamaHealthResult available(
            String version,
            Instant checkedAt,
            Duration duration,
            Map<String, Object> metadata
    ) {
        return new OllamaHealthResult(
                true,
                version,
                checkedAt,
                duration,
                "Ollama server is available.",
                null,
                metadata
        );
    }

    /**
     * 비정상 상태 결과를 생성합니다.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @param checkedAt 상태 확인 시각
     * @param duration 응답 시간
     * @return 비정상 상태 결과
     */
    public static OllamaHealthResult unavailable(
            String errorCode,
            String message,
            Instant checkedAt,
            Duration duration
    ) {
        return new OllamaHealthResult(
                false,
                null,
                checkedAt,
                duration,
                message,
                errorCode,
                Map.of()
        );
    }

    /**
     * 비정상 상태 결과를 추가 메타데이터와 함께 생성합니다.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @param checkedAt 상태 확인 시각
     * @param duration 응답 시간
     * @param metadata 추가 정보
     * @return 비정상 상태 결과
     */
    public static OllamaHealthResult unavailable(
            String errorCode,
            String message,
            Instant checkedAt,
            Duration duration,
            Map<String, Object> metadata
    ) {
        return new OllamaHealthResult(
                false,
                null,
                checkedAt,
                duration,
                message,
                errorCode,
                metadata
        );
    }

    /**
     * 서버 버전 정보가 존재하는지 확인합니다.
     *
     * @return 버전 정보가 있으면 true
     */
    public boolean hasVersion() {
        return version != null;
    }

    /**
     * 오류 정보가 존재하는지 확인합니다.
     *
     * @return 오류 코드가 있으면 true
     */
    public boolean hasError() {
        return errorCode != null;
    }

    /**
     * 상태 확인 시간을 밀리초로 반환합니다.
     *
     * @return 상태 확인 시간
     */
    public long durationMillis() {
        return duration.toMillis();
    }

    /**
     * 상태 확인이 지정한 시간보다 오래 걸렸는지 확인합니다.
     *
     * @param threshold 기준 시간
     * @return 기준 시간 이상이면 true
     */
    public boolean isSlow(
            Duration threshold
    ) {
        if (threshold == null
                || threshold.isNegative()) {
            throw new IllegalArgumentException(
                    "threshold must not be null or negative."
            );
        }

        return duration.compareTo(threshold) >= 0;
    }

    /**
     * 메타데이터를 추가한 새로운 결과를 반환합니다.
     *
     * @param key 메타데이터 키
     * @param value 메타데이터 값
     * @return 새로운 상태 결과
     */
    public OllamaHealthResult withMetadata(
            String key,
            Object value
    ) {
        String normalizedKey = requireText(
                key,
                "key"
        );

        Map<String, Object> newMetadata =
                new java.util.LinkedHashMap<>(
                        metadata
                );

        if (value == null) {
            newMetadata.remove(normalizedKey);
        } else {
            newMetadata.put(
                    normalizedKey,
                    value
            );
        }

        return new OllamaHealthResult(
                available,
                version,
                checkedAt,
                duration,
                message,
                errorCode,
                newMetadata
        );
    }

    /**
     * 사용자 표시용 요약 문자열을 반환합니다.
     *
     * @return 상태 요약
     */
    public String summary() {
        StringBuilder builder =
                new StringBuilder();

        builder.append(
                available
                        ? "AVAILABLE"
                        : "UNAVAILABLE"
        );

        if (version != null) {
            builder.append(
                    " version="
            ).append(
                    version
            );
        }

        builder.append(
                " duration="
        ).append(
                durationMillis()
        ).append(
                "ms"
        );

        if (errorCode != null) {
            builder.append(
                    " errorCode="
            ).append(
                    errorCode
            );
        }

        return builder.toString();
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

    private static String normalizeText(
            String value
    ) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}