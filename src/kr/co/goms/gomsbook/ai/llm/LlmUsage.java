/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm;

import java.io.Serializable;

/**
 * LLM 요청과 응답에서 사용된 토큰 수를 나타냅니다.
 */
public record LlmUsage(

        /**
         * 입력 메시지에 사용된 토큰 수입니다.
         */
        int inputTokens,

        /**
         * 출력 생성에 사용된 토큰 수입니다.
         */
        int outputTokens,

        /**
         * 전체 토큰 수입니다.
         */
        int totalTokens,

        /**
         * 캐시된 입력 토큰 수입니다.
         */
        Integer cachedInputTokens,

        /**
         * 추론 과정에서 사용된 토큰 수입니다.
         */
        Integer reasoningTokens,

        /**
         * 사용량이 실제 Provider 값이 아닌 추정치인지 나타냅니다.
         */
        boolean estimated

) implements Serializable {

    public LlmUsage {
        if (inputTokens < 0) {
            throw new IllegalArgumentException(
                    "inputTokens cannot be negative."
            );
        }

        if (outputTokens < 0) {
            throw new IllegalArgumentException(
                    "outputTokens cannot be negative."
            );
        }

        if (totalTokens < 0) {
            throw new IllegalArgumentException(
                    "totalTokens cannot be negative."
            );
        }

        if (cachedInputTokens != null
                && cachedInputTokens < 0) {
            throw new IllegalArgumentException(
                    "cachedInputTokens cannot be negative."
            );
        }

        if (reasoningTokens != null
                && reasoningTokens < 0) {
            throw new IllegalArgumentException(
                    "reasoningTokens cannot be negative."
            );
        }

        int calculatedTotal =
                inputTokens + outputTokens;

        if (totalTokens == 0
                && calculatedTotal > 0) {
            totalTokens = calculatedTotal;
        }

        if (totalTokens < calculatedTotal) {
            throw new IllegalArgumentException(
                    "totalTokens cannot be less than inputTokens + outputTokens."
            );
        }
    }

    /**
     * 사용량을 확인할 수 없는 경우 사용합니다.
     */
    public static LlmUsage unknown() {
        return new LlmUsage(
                0,
                0,
                0,
                null,
                null,
                true
        );
    }

    /**
     * 기본 토큰 사용량을 생성합니다.
     */
    public static LlmUsage of(
            int inputTokens,
            int outputTokens
    ) {
        return new LlmUsage(
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                null,
                null,
                false
        );
    }

    /**
     * 사용량이 존재하는지 확인합니다.
     */
    public boolean isKnown() {
        return inputTokens > 0
                || outputTokens > 0
                || totalTokens > 0;
    }
}