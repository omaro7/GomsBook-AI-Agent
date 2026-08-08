/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm.ollama;

import java.io.Serializable;
import java.util.List;

/**
 * Ollama Log Probability의 상위 토큰 후보입니다.
 */
public record OllamaTopLogProbability(
        String token,
        double logprob,
        List<Integer> bytes
) implements Serializable {

    public OllamaTopLogProbability {
        token = token == null
                ? ""
                : token;

        bytes = bytes == null
                ? List.of()
                : validateBytes(bytes);
    }

    private static List<Integer> validateBytes(
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
}