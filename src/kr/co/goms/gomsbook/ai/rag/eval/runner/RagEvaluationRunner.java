/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationCase;
import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationContext;
import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationResult;
import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluator;
import kr.co.goms.gomsbook.ai.rag.eval.dataset.RagEvaluationDataset;
import kr.co.goms.gomsbook.ai.rag.eval.report.RagEvaluationReport;

/**
 * Golden Dataset 전체를 대상으로 RAG 평가를 실행한다.
 * 
 * RagEvaluationDataset
 *         │
 *         ▼
 * RagEvaluationRunner
 *         │
 *         ├─ Case 1
 *         │     ↓
 *         │  RagExecutionAdapter
 *         │     ↓
 *         │  Retriever + RAG Answer
 *         │     ↓
 *         │  RagEvaluationContext
 *         │     ↓
 *         │  RagEvaluator
 *         │
 *         ├─ Case 2
 *         │     ↓
 *         │    ...
 *         │
 *         └─ Case N
 *               ↓
 *       RagEvaluationReport    
 */
public final class RagEvaluationRunner {

    private final RagEvaluator evaluator;
    private final RagExecutionAdapter executionAdapter;

    public RagEvaluationRunner(RagEvaluator evaluator, RagExecutionAdapter executionAdapter) {
        if (evaluator == null) {
            throw new NullPointerException("evaluator must not be null");
        }

        if (executionAdapter == null) {
            throw new NullPointerException("executionAdapter must not be null");
        }

        this.evaluator = evaluator;
        this.executionAdapter = executionAdapter;
    }

    public RagEvaluationReport run(RagEvaluationDataset dataset) {
        if (dataset == null) {
            throw new NullPointerException("dataset must not be null");
        }

        executionAdapter.validateProject(dataset.getProjectId());
        
        List<RagEvaluationReport.Entry> entries = new ArrayList<>();

        for (RagEvaluationCase evaluationCase : dataset.getCases()) {
            entries.add(evaluateCase(evaluationCase));
        }

        return new RagEvaluationReport(dataset.getName(), entries);
    }

    private RagEvaluationReport.Entry evaluateCase(RagEvaluationCase evaluationCase) {
        RagExecutionResult executionResult = executionAdapter.execute(evaluationCase.getQuestion());

        if (executionResult == null) {
            throw new IllegalStateException(
                    "RAG execution result must not be null: " + evaluationCase.getId());
        }

        RagEvaluationContext context = new RagEvaluationContext(
        		evaluationCase.getType(),
                evaluationCase.getQuestion(),
                executionResult.getRetrievedContexts(),
                executionResult.getAnswer(),
                evaluationCase.getReferenceAnswer());

        RagEvaluationResult evaluationResult = evaluator.evaluate(context);

        return new RagEvaluationReport.Entry(
                evaluationCase.getId(),
                evaluationCase.getQuestion(),
                evaluationResult);
    }
}