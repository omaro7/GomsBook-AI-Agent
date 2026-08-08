/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm.ollama;

/**
 * Ollama Chat 메시지 역할입니다.
 */
public enum OllamaChatRole {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String apiValue;

    OllamaChatRole(
            String apiValue
    ) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    /**
     * Ollama API 문자열을 Enum으로 변환합니다.
     *
     * @param value API 역할 문자열
     * @return Ollama Chat 역할
     */
    public static OllamaChatRole fromApiValue(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "role must not be blank."
            );
        }

        String normalized =
                value.trim().toLowerCase();

        for (OllamaChatRole role : values()) {
            if (role.apiValue.equals(normalized)) {
                return role;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported Ollama role: " + value
        );
    }
}