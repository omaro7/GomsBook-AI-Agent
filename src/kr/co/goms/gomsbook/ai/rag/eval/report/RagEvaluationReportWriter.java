/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.report;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import kr.co.goms.gomsbook.ai.rag.eval.RagMetricResult;

/**
 * RAG Evaluation Report를 JSON 형식으로 저장한다.
 * 
 * RagEvaluationReportWriter writer = new RagEvaluationReportWriter();
 * writer.write( report, Path.of("eval/result/lunchwork-seoul-v1-result.json"));
 *         
 * {
 *   "datasetName": "lunchwork-seoul-v1",
 *   "averageScore": 0.9175,
 *   "entries": [
 *     {
 *       "caseId": "LUNCH-001",
 *       "question": "덕수궁 돌담길은 어떤 장소인가요?",
 *       "overallScore": 0.93,
 *       "metrics": [
 *         {
 *           "name": "faithfulness",
 *           "score": 0.96,
 *           "reason": "The answer is supported by the retrieved contexts."
 *         },
 *         {
 *           "name": "answer_relevancy",
 *           "score": 0.94,
 *           "reason": "The answer directly addresses the question."
 *         },
 *         {
 *           "name": "context_precision",
 *           "score": 0.82,
 *           "reason": "Most retrieved contexts are relevant."
 *         },
 *         {
 *           "name": "context_recall",
 *           "score": 1.0,
 *           "reason": "All reference information is covered."
 *         }
 *       ]
 *     }
 *   ]
 * }    
 */
public final class RagEvaluationReportWriter {

    private final Gson gson;

    public RagEvaluationReportWriter() {
        this(new GsonBuilder()
                .setPrettyPrinting()
                .create());
    }

    public RagEvaluationReportWriter(Gson gson) {
        if (gson == null) {
            throw new NullPointerException("gson must not be null");
        }

        this.gson = gson;
    }

    public void write(RagEvaluationReport report, Path path)
            throws IOException {

        if (report == null) {
            throw new NullPointerException("report must not be null");
        }

        if (path == null) {
            throw new NullPointerException("path must not be null");
        }

        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Writer writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8)) {

            write(report, writer);
        }
    }

    public void write(RagEvaluationReport report, Writer writer) {
        if (report == null) {
            throw new NullPointerException("report must not be null");
        }

        if (writer == null) {
            throw new NullPointerException("writer must not be null");
        }

        ReportJson reportJson = convert(report);

        gson.toJson(reportJson, writer);
    }

    private ReportJson convert(RagEvaluationReport report) {
        ReportJson result = new ReportJson();

        result.datasetName = report.getDatasetName();
        result.averageScore = report.getAverageScore();
        result.entries = new ArrayList<>();

        for (RagEvaluationReport.Entry entry : report.getEntries()) {
            result.entries.add(convertEntry(entry));
        }

        return result;
    }

    private EntryJson convertEntry(RagEvaluationReport.Entry entry) {
        EntryJson result = new EntryJson();

        result.caseId = entry.getCaseId();
        result.question = entry.getQuestion();
        result.overallScore = entry.getResult().getOverallScore();
        result.metrics = new ArrayList<>();

        for (RagMetricResult metricResult
                : entry.getResult().getMetricResults()) {

            MetricJson metricJson = new MetricJson();

            metricJson.name = metricResult.getMetricName();
            metricJson.score = metricResult.getScore();
            metricJson.reason = metricResult.getReason();

            result.metrics.add(metricJson);
        }

        return result;
    }

    private static final class ReportJson {

        private String datasetName;
        private double averageScore;
        private List<EntryJson> entries;
    }

    private static final class EntryJson {

        private String caseId;
        private String question;
        private double overallScore;
        private List<MetricJson> metrics;
    }

    private static final class MetricJson {

        private String name;
        private double score;
        private String reason;
    }
}