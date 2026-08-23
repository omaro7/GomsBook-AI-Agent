/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval;

import java.util.Objects;

/**
 * RAG 평가용 단일 테스트 케이스.
 *
 * Golden Dataset의 질문과 기준 답변을 표현한다.
 */
public final class RagEvaluationCase {

    private final String id;
    private final RagEvaluationCaseType type;
    private final String question;
    private final String referenceAnswer;
    
    public RagEvaluationCase(String id, RagEvaluationCaseType type, String question, String referenceAnswer) {
        this.id = requireText(id, "id");

        if (type == null) {
            throw new NullPointerException("type must not be null");
        }

        this.type = type;
        this.question = requireText(question, "question");
        this.referenceAnswer = requireText(referenceAnswer, "referenceAnswer");
    }

    public String getId() {
        return id;
    }

    public RagEvaluationCaseType getType() {
        return type;
    }
    
    public String getQuestion() {
        return question;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return normalized;
    }

    @Override
    public String toString() {
        return "RagEvaluationCase{" +
                "id='" + id + '\'' +
                ", question='" + question + '\'' +
                '}';
    }
}