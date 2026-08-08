/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm.ollama;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Ollama가 반환하는 토큰별 Log Probability 정보입니다.
 */
public record OllamaLogProbability(

        /**
         * 생성된 토큰입니다.
         */
        String token,

        /**
         * 토큰의 Log Probability입니다.
         */
        double logprob,

        /**
         * 토큰의 UTF-8 Byte 값입니다.
         */
        List<Integer> bytes,

        /**
         * 해당 위치에서의 상위 토큰 후보 목록입니다.
         */
        List<OllamaTopLogProbability> topLogprobs

) implements Serializable {

    public OllamaLogProbability {
        token = token == null
                ? ""
                : token;

        bytes = bytes == null
                ? List.of()
                : sanitizeBytes(bytes);

        topLogprobs = topLogprobs == null
                ? List.of()
                : sanitizeTopLogprobs(topLogprobs);
    }

    public boolean hasBytes() {
        return !bytes.isEmpty();
    }

    public boolean hasTopLogprobs() {
        return !topLogprobs.isEmpty();
    }

    private static List<Integer> sanitizeBytes(
            List<Integer> values
    ) {
        for (Integer value : values) {
            if (value == null
                    || value < 0
                    || value > 255) {
                throw new IllegalArgumentException(
                        "bytes must contain values between 0 and 255."
                );
            }
        }

        return List.copyOf(values);
    }

    private static List<OllamaTopLogProbability>
    sanitizeTopLogprobs(
            List<OllamaTopLogProbability> values
    ) {
        List<OllamaTopLogProbability> sanitized =
                values.stream()
                        .filter(Objects::nonNull)
                        .toList();

        if (sanitized.size() != values.size()) {
            throw new IllegalArgumentException(
                    "topLogprobs must not contain null values."
            );
        }

        return List.copyOf(sanitized);
    }
}