/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * LLM 텍스트 생성 옵션입니다.
 */
public record LlmOptions(

        /**
         * 응답의 무작위성입니다.
         *
         * 일반적으로 0.0~2.0 범위를 사용합니다.
         */
        Double temperature,

        /**
         * 누적 확률 기반 샘플링 값입니다.
         */
        Double topP,

        /**
         * 상위 K개 토큰 샘플링 값입니다.
         */
        Integer topK,

        /**
         * 최대 출력 토큰 수입니다.
         */
        Integer maxOutputTokens,

        /**
         * 재현성 제어를 위한 Seed입니다.
         */
        Long seed,

        /**
         * 생성 중단 문자열입니다.
         */
        List<String> stopSequences,

        /**
         * Provider별 추가 옵션입니다.
         */
        Map<String, Object> providerOptions

) implements Serializable {

    public LlmOptions {
        if (temperature != null
                && (temperature < 0.0
                || temperature > 2.0)) {
            throw new IllegalArgumentException(
                    "temperature must be between 0.0 and 2.0."
            );
        }

        if (topP != null
                && (topP < 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException(
                    "topP must be between 0.0 and 1.0."
            );
        }

        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException(
                    "topK must be positive."
            );
        }

        if (maxOutputTokens != null
                && maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be positive."
            );
        }

        stopSequences = stopSequences == null
                ? List.of()
                : List.copyOf(stopSequences);

        providerOptions = providerOptions == null
                ? Map.of()
                : Map.copyOf(providerOptions);
    }

    /**
     * 기본 생성 옵션입니다.
     */
    public static LlmOptions defaults() {
        return new LlmOptions(
                0.2,
                null,
                null,
                4096,
                null,
                List.of(),
                Map.of()
        );
    }

    /**
     * 구조화 응답에 적합한 결정적 옵션입니다.
     */
    public static LlmOptions deterministic() {
        return new LlmOptions(
                0.0,
                null,
                null,
                4096,
                null,
                List.of(),
                Map.of()
        );
    }
}