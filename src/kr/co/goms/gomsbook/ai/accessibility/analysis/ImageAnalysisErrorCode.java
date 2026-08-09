/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.analysis;

/**
 * 이미지 분석 오류 코드.
 */
public enum ImageAnalysisErrorCode {

    INVALID_REQUEST(false),

    IMAGE_NOT_FOUND(false),

    UNSUPPORTED_IMAGE_TYPE(false),

    IMAGE_TOO_LARGE(false),

    IMAGE_READ_FAILED(false),

    MODEL_NOT_AVAILABLE(true),

    MODEL_TIMEOUT(true),

    MODEL_RESPONSE_EMPTY(true),

    MODEL_RESPONSE_INVALID(true),

    PROMPT_BUILD_FAILED(false),

    RESPONSE_PARSE_FAILED(true),

    ANALYSIS_FAILED(true),

    UNKNOWN(true);

    private final boolean retryable;

    ImageAnalysisErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * 동일 요청을 재시도할 가치가 있는 오류인지 반환한다.
     *
     * @return 재시도 가능 여부
     */
    public boolean isRetryable() {
        return retryable;
    }
}