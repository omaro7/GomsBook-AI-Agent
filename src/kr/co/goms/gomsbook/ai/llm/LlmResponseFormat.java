/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm;

import java.io.Serializable;

/**
 * LLM에 요청하는 응답 형식입니다.
 */
public record LlmResponseFormat(
        LlmResponseFormatType type,
        String schemaName,
        String schema
) implements Serializable {

    public LlmResponseFormat {
        type = type == null
                ? LlmResponseFormatType.TEXT
                : type;

        schemaName = normalize(schemaName);
        schema = normalize(schema);

        if (type == LlmResponseFormatType.JSON_SCHEMA
                && schema == null) {
            throw new IllegalArgumentException(
                    "schema is required for JSON_SCHEMA format."
            );
        }
    }

    public static LlmResponseFormat text() {
        return new LlmResponseFormat(
                LlmResponseFormatType.TEXT,
                null,
                null
        );
    }

    public static LlmResponseFormat json() {
        return new LlmResponseFormat(
                LlmResponseFormatType.JSON,
                null,
                null
        );
    }

    public static LlmResponseFormat xhtml() {
        return new LlmResponseFormat(
                LlmResponseFormatType.XHTML,
                null,
                null
        );
    }

    public static LlmResponseFormat jsonSchema(
            String schemaName,
            String schema
    ) {
        return new LlmResponseFormat(
                LlmResponseFormatType.JSON_SCHEMA,
                schemaName,
                schema
        );
    }

    private static String normalize(
            String value
    ) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}