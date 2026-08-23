/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.dataset;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationCase;
import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationCaseType;

/**
 * JSON 파일에서 RAG Golden Dataset을 로드한다.
 * 
 * {
 *   "name": "lunchwork-seoul-v1",
 *   "projectId": "f1b415f62fe7243637477e7de75f2011b6012fdac9d5c56a2317358be058831c",
 *   "projectName": "lunchwork_seoul",
 *   "cases": [
 *     {
 *       "id": "LUNCH-001",
 *       "question": "덕수궁 돌담길은 어떤 장소인가요?",
 *       "referenceAnswer": "덕수궁 돌담길은 서울 정동의 대표적인 산책길이다."
 *     },
 *     {
 *       "id": "LUNCH-002",
 *       "question": "서울시립미술관은 어떤 공간으로 소개되나요?",
 *       "referenceAnswer": "점심시간에 예술을 접할 수 있는 공간으로 소개된다."
 *     }
 *   ]
 * }
 * 
 * RagEvaluationDatasetLoader loader = new RagEvaluationDatasetLoader();
 * 
 * RagEvaluationDataset dataset =
 *         loader.load(
 *                 Path.of("eval/lunchwork-seoul-v1.json"));
 */
public final class RagEvaluationDatasetLoader {

    private final Gson gson;

    public RagEvaluationDatasetLoader() {
        this(new Gson());
    }

    public RagEvaluationDatasetLoader(Gson gson) {
        if (gson == null) {
            throw new NullPointerException("gson must not be null");
        }

        this.gson = gson;
    }

    public RagEvaluationDataset load(Path path) throws IOException {
        if (path == null) {
            throw new NullPointerException("path must not be null");
        }

        if (!Files.exists(path)) {
            throw new IOException("Dataset file does not exist: " + path);
        }

        try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8)) {

            return load(reader);
        }
    }

    public RagEvaluationDataset load(Reader reader) {
        if (reader == null) {
            throw new NullPointerException("reader must not be null");
        }

        try {
            DatasetJson datasetJson = gson.fromJson(reader, DatasetJson.class);

            if (datasetJson == null) {
                throw new IllegalArgumentException(
                        "Dataset JSON must not be empty");
            }

            String name = requireText(datasetJson.name, "name");
            String projectId = requireText(datasetJson.projectId, "projectId");
            String projectName = requireText(datasetJson.projectName, "projectName");
            List<RagEvaluationCase> cases = convertCases(datasetJson.cases);

            return new RagEvaluationDataset(name, projectId, projectName, cases);

        } catch (JsonParseException e) {
            throw new IllegalArgumentException(
                    "Failed to parse RAG evaluation dataset JSON",
                    e);
        }
    }

    private List<RagEvaluationCase> convertCases(
            List<CaseJson> caseJsonList) {

        if (caseJsonList == null || caseJsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<RagEvaluationCase> cases = new ArrayList<>();

        for (CaseJson caseJson : caseJsonList) {
            if (caseJson == null) {
                continue;
            }

            RagEvaluationCaseType type = caseJson.type != null
                    ? caseJson.type
                    : RagEvaluationCaseType.ANSWERABLE;

            
            cases.add(
                    new RagEvaluationCase(
                            caseJson.id,
                            type,
                            caseJson.question,
                            caseJson.referenceAnswer));
        }

        return cases;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }

        return normalized;
    }

    private static final class DatasetJson {
        private String name;
        private String projectId;
        private String projectName;
        private List<CaseJson> cases;
    }

    private static final class CaseJson {
        private String id;
        private RagEvaluationCaseType type;
        private String question;
        private String referenceAnswer;
    }
}