/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG 실행 결과.
 *
 * Retriever가 검색한 Context와 LLM이 생성한 최종 답변을 보관한다.
 */
public final class RagExecutionResult {

    private final List<String> retrievedContexts;
    private final String answer;

    public RagExecutionResult(List<String> retrievedContexts, String answer) {
        this.retrievedContexts = normalizeContexts(retrievedContexts);
        this.answer = requireText(answer, "answer");
    }

    public List<String> getRetrievedContexts() {
        return retrievedContexts;
    }

    public String getAnswer() {
        return answer;
    }

    private static List<String> normalizeContexts(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>();

        for (String context : contexts) {
            if (context == null) {
                continue;
            }

            String value = context.trim();

            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }

        return Collections.unmodifiableList(normalized);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return normalized;
    }

    @Override
    public String toString() {
        return "RagExecutionResult{" +
                "retrievedContexts=" + retrievedContexts.size() +
                ", answer='" + answer + '\'' +
                '}';
    }
}