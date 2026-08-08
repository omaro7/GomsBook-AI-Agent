/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm.ollama;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ollama 서버의 실행 상태와 버전을 확인하는 서비스입니다.
 *
 * <p>
 * {@code /api/version} 엔드포인트에 HTTP GET 요청을 보내고,
 * 서버 연결 여부와 Ollama 버전을 {@link OllamaHealthResult}로 반환합니다.
 * </p>
 *
 * <p>
 * 상태 확인 과정에서 발생하는 예외는 외부로 그대로 전달하지 않고
 * 표준화된 오류 코드와 메시지로 변환합니다.
 * </p>
 * 
 * OllamaConfiguration configuration = OllamaConfiguration.local(
            "qwen2.5:7b"
    );

	OllamaHealthService healthService = new OllamaHealthService(
            configuration
    );
	
	OllamaHealthResult result = healthService.check();
	
	System.out.println(result.summary());
	System.out.println(result.message());

 */
public final class OllamaHealthService {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile(
                    "\"version\"\\s*:\\s*\"([^\"]+)\""
            );

    private final OllamaConfiguration configuration;
    private final HttpClient httpClient;

    /**
     * 설정을 기반으로 Health Service를 생성합니다.
     *
     * @param configuration Ollama 설정
     */
    public OllamaHealthService(
            OllamaConfiguration configuration
    ) {
        this(
                configuration,
                createHttpClient(configuration)
        );
    }

    /**
     * 테스트 또는 사용자 정의 HttpClient를 사용할 때 생성합니다.
     *
     * @param configuration Ollama 설정
     * @param httpClient HTTP Client
     */
    public OllamaHealthService(
            OllamaConfiguration configuration,
            HttpClient httpClient
    ) {
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null."
        );

        this.httpClient = Objects.requireNonNull(
                httpClient,
                "httpClient must not be null."
        );
    }

    /**
     * Ollama 서버 상태를 확인합니다.
     *
     * @return 상태 확인 결과
     */
    public OllamaHealthResult check() {
        Instant startedAt = Instant.now();

        if (!configuration.enabled()) {
            return unavailable(
                    "OLLAMA_DISABLED",
                    "Ollama Client가 비활성화되어 있습니다.",
                    startedAt,
                    Map.of(
                            "endpoint",
                            configuration
                                    .versionEndpoint()
                                    .toString()
                    )
            );
        }

        URI endpoint =
                configuration.versionEndpoint();

        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(endpoint)
                .timeout(
                        effectiveTimeout()
                )
                .header(
                        "Accept",
                        "application/json"
                )
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            Duration duration =
                    Duration.between(
                            startedAt,
                            Instant.now()
                    );

            return handleResponse(
                    response,
                    startedAt,
                    duration,
                    endpoint
            );

        } catch (HttpTimeoutException exception) {
            return unavailable(
                    "OLLAMA_TIMEOUT",
                    "Ollama 서버 상태 확인 시간이 초과되었습니다.",
                    startedAt,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "timeoutMillis",
                            effectiveTimeout().toMillis(),
                            "exceptionType",
                            exception
                                    .getClass()
                                    .getName()
                    )
            );

        } catch (ConnectException exception) {
            return unavailable(
                    "OLLAMA_CONNECTION_REFUSED",
                    "Ollama 서버에 연결할 수 없습니다. "
                            + "Ollama가 실행 중인지 확인해 주세요.",
                    startedAt,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "exceptionType",
                            exception
                                    .getClass()
                                    .getName()
                    )
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return unavailable(
                    "OLLAMA_HEALTH_CHECK_INTERRUPTED",
                    "Ollama 서버 상태 확인이 중단되었습니다.",
                    startedAt,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "exceptionType",
                            exception
                                    .getClass()
                                    .getName()
                    )
            );

        } catch (IOException exception) {
            return unavailable(
                    "OLLAMA_IO_ERROR",
                    safeMessage(
                            exception,
                            "Ollama 서버와 통신 중 I/O 오류가 발생했습니다."
                    ),
                    startedAt,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "exceptionType",
                            exception
                                    .getClass()
                                    .getName()
                    )
            );

        } catch (RuntimeException exception) {
            return unavailable(
                    "OLLAMA_HEALTH_CHECK_FAILED",
                    safeMessage(
                            exception,
                            "Ollama 서버 상태 확인 중 오류가 발생했습니다."
                    ),
                    startedAt,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "exceptionType",
                            exception
                                    .getClass()
                                    .getName()
                    )
            );
        }
    }

    /**
     * Ollama 서버가 현재 사용 가능한지 간단히 확인합니다.
     *
     * @return 서버가 사용 가능하면 true
     */
    public boolean isAvailable() {
        return check().available();
    }

    /**
     * Ollama 서버 버전을 조회합니다.
     *
     * <p>
     * 서버를 사용할 수 없거나 버전 정보가 없으면 빈 문자열을 반환합니다.
     * </p>
     *
     * @return Ollama 버전
     */
    public String getVersion() {
        OllamaHealthResult result = check();

        return result.version() == null
                ? ""
                : result.version();
    }

    /**
     * 현재 설정을 반환합니다.
     *
     * @return Ollama 설정
     */
    public OllamaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * HTTP 응답을 상태 결과로 변환합니다.
     */
    private OllamaHealthResult handleResponse(
            HttpResponse<String> response,
            Instant startedAt,
            Duration duration,
            URI endpoint
    ) {
        int statusCode =
                response.statusCode();

        String body =
                response.body();

        if (statusCode < 200 || statusCode >= 300) {
            return OllamaHealthResult.unavailable(
                    "OLLAMA_HTTP_ERROR",
                    "Ollama 서버가 비정상 HTTP 상태를 반환했습니다: "
                            + statusCode,
                    startedAt,
                    duration,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "statusCode",
                            statusCode,
                            "responsePreview",
                            safePreview(body)
                    )
            );
        }

        String version =
                extractVersion(body);

        if (version == null) {
            return OllamaHealthResult.unavailable(
                    "OLLAMA_VERSION_NOT_FOUND",
                    "Ollama 서버 응답에서 버전 정보를 찾을 수 없습니다.",
                    startedAt,
                    duration,
                    Map.of(
                            "endpoint",
                            endpoint.toString(),
                            "statusCode",
                            statusCode,
                            "responsePreview",
                            safePreview(body)
                    )
            );
        }

        return OllamaHealthResult.available(
                version,
                startedAt,
                duration,
                Map.of(
                        "endpoint",
                        endpoint.toString(),
                        "statusCode",
                        statusCode,
                        "localServer",
                        configuration.isLocalServer()
                )
        );
    }

    /**
     * JSON 응답에서 version 값을 추출합니다.
     *
     * <p>
     * 현재 구현은 외부 JSON 라이브러리 의존성을 추가하지 않기 위해
     * 단순한 정규식을 사용합니다.
     * Provider API 구현 단계에서는 Jackson이나 Gson 기반 JSON Mapper로
     * 교체하는 것을 권장합니다.
     * </p>
     */
    private static String extractVersion(
            String responseBody
    ) {
        if (responseBody == null
                || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher =
                VERSION_PATTERN.matcher(
                        responseBody
                );

        if (!matcher.find()) {
            return null;
        }

        String version =
                matcher.group(1);

        return version == null
                || version.isBlank()
                        ? null
                        : version.trim();
    }

    /**
     * 상태 확인에 사용할 제한 시간을 결정합니다.
     */
    private Duration effectiveTimeout() {
        Duration configuredTimeout =
                configuration.connectTimeout();

        Duration maximumHealthTimeout =
                Duration.ofSeconds(10);

        return configuredTimeout.compareTo(
                maximumHealthTimeout
        ) > 0
                ? maximumHealthTimeout
                : configuredTimeout;
    }

    /**
     * 상태 확인 실패 결과를 생성합니다.
     */
    private static OllamaHealthResult unavailable(
            String errorCode,
            String message,
            Instant startedAt,
            Map<String, Object> metadata
    ) {
        return OllamaHealthResult.unavailable(
                errorCode,
                message,
                startedAt,
                Duration.between(
                        startedAt,
                        Instant.now()
                ),
                metadata
        );
    }

    /**
     * HttpClient를 생성합니다.
     */
    private static HttpClient createHttpClient(
            OllamaConfiguration configuration
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration must not be null."
        );

        return HttpClient
                .newBuilder()
                .connectTimeout(
                        configuration.connectTimeout()
                )
                .followRedirects(
                        HttpClient.Redirect.NORMAL
                )
                .version(
                        HttpClient.Version.HTTP_1_1
                )
                .build();
    }

    /**
     * 로그 또는 메타데이터용 응답 미리보기를 생성합니다.
     */
    private static String safePreview(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized =
                value.replaceAll(
                        "\\s+",
                        " "
                ).trim();

        int maximumLength = 300;

        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(
                        0,
                        maximumLength
                ) + "...";
    }

    /**
     * 예외 메시지를 안전하게 반환합니다.
     */
    private static String safeMessage(
            Exception exception,
            String defaultMessage
    ) {
        String message =
                exception.getMessage();

        return message == null || message.isBlank()
                ? defaultMessage
                : message;
    }
}