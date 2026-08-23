/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.metadata;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmMessage;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * EPUB 3 패키지 문서에 사용할 메타데이터를 생성하는 Tool.
 *
 * <p>명시적으로 전달된 제목, 저자, 출판사 등의 값은 그대로 보존하고,
 * 설명, 주제, 키워드처럼 내용 분석이 필요한 항목만 LLM으로 생성한다.</p>
 */
public final class GenerateMetadataTool implements AgentTool {

    public static final String TOOL_NAME =
            "generate_metadata";

    private static final String TOOL_DESCRIPTION =
            "책 정보와 원고를 분석하여 EPUB 3용 메타데이터를 생성합니다.";

    private static final String DEFAULT_MODEL =
            "gemma4:31b-cloud";

    private static final String DEFAULT_LANGUAGE =
            "ko";

    private static final String DEFAULT_TYPE =
            "Text";

    private static final int DEFAULT_MAX_DESCRIPTION_LENGTH =
            500;

    private static final int DEFAULT_MAX_KEYWORDS =
            10;

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;
    private final String model;

    public GenerateMetadataTool(
            LlmClient llmClient,
            JsonMapper jsonMapper) {

        this(
                llmClient,
                jsonMapper,
                DEFAULT_MODEL);
    }

    public GenerateMetadataTool(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            String model) {

        this.llmClient =
                Objects.requireNonNull(
                        llmClient,
                        "llmClient must not be null");

        this.jsonMapper =
                Objects.requireNonNull(
                        jsonMapper,
                        "jsonMapper must not be null");

        this.model =
                isBlank(model)
                        ? DEFAULT_MODEL
                        : model.trim();
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public ToolResult execute(
            ToolRequest toolRequest,
            ToolContext toolContext) {

        if (toolRequest == null) {
            return failure(
                    ToolStatus.FAILED,
                    "TOOL_REQUEST_REQUIRED",
                    "ToolRequest가 없습니다.");
        }

        try {
            GenerateMetadataRequest request = parseRequest(toolRequest);

            List<ToolIssue> validationIssues =
                    validateRequest(request);

            if (!validationIssues.isEmpty()) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message("메타데이터 생성 요청이 올바르지 않습니다.")
                        .issues(validationIssues)
                        .build();
            }

            GeneratedMetadata generatedMetadata =
                    generateSemanticMetadata(request);

            EpubMetadata metadata =
                    mergeMetadata(
                            request,
                            generatedMetadata);

            normalizeMetadata(metadata, request);

            GenerateMetadataResponse response =
                    new GenerateMetadataResponse();

            response.setGenerated(true);
            response.setModel(model);
            response.setMetadata(metadata);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put(
                    "generated",
                    response.isGenerated());

            data.put(
                    "model",
                    response.getModel());

            data.put(
                    "metadata",
                    response.getMetadata());
            
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB 메타데이터를 생성했습니다.")
                    .data(data)
                    .build();

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_METADATA_REQUEST",
                    buildExceptionMessage(exception));

        } catch (Exception exception) {
            return failure(
                    ToolStatus.FAILED,
                    "METADATA_GENERATION_FAILED",
                    buildExceptionMessage(exception));
        }
    }

    private GenerateMetadataRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "메타데이터 생성 인자가 없습니다.");
        }

        if (arguments instanceof GenerateMetadataRequest) {
            return (GenerateMetadataRequest) arguments;
        }

        String json =
                jsonMapper.toJson(arguments);

        GenerateMetadataRequest request =
                jsonMapper.fromJson(
                        json,
                        GenerateMetadataRequest.class);

        if (request == null) {
            throw new IllegalArgumentException(
                    "메타데이터 생성 요청을 변환할 수 없습니다.");
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            GenerateMetadataRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "메타데이터 생성 요청이 없습니다."));
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(request.getTitle())) {
            issues.add(
                    issue(
                            "TITLE_REQUIRED",
                            "책 제목은 필수입니다."));
        }

        if (request.getCreators() == null
                || request.getCreators().isEmpty()) {

            if (isBlank(request.getCreator())) {
                issues.add(
                        issue(
                                "CREATOR_REQUIRED",
                                "저자 정보는 필수입니다."));
            }
        }

        if (!isBlank(request.getLanguage())
                && !isValidLanguage(request.getLanguage())) {

            issues.add(
                    issue(
                            "INVALID_LANGUAGE",
                            "언어 코드는 ko, en 또는 ko-KR 형식이어야 합니다."));
        }

        if (!isBlank(request.getPublicationDate())
                && !isValidDate(request.getPublicationDate())) {

            issues.add(
                    issue(
                            "INVALID_PUBLICATION_DATE",
                            "발행일은 yyyy-MM-dd 형식이어야 합니다."));
        }

        if (!isBlank(request.getIdentifier())
                && !isValidIdentifier(request.getIdentifier())) {

            issues.add(
                    issue(
                            "INVALID_IDENTIFIER",
                            "식별자는 ISBN, UUID 또는 URI 형식이어야 합니다."));
        }

        if (request.getMaxDescriptionLength() < 0) {
            issues.add(
                    issue(
                            "INVALID_DESCRIPTION_LENGTH",
                            "설명 최대 길이는 0 이상이어야 합니다."));
        }

        if (request.getMaxKeywords() < 0) {
            issues.add(
                    issue(
                            "INVALID_KEYWORD_COUNT",
                            "키워드 최대 개수는 0 이상이어야 합니다."));
        }

        return issues;
    }

    /**
     * 설명, 주제, 키워드 등 의미 분석이 필요한 항목을 생성한다.
     */
    private GeneratedMetadata generateSemanticMetadata(
            GenerateMetadataRequest request) {

        /*
         * 사용자가 모든 의미 메타데이터를 직접 입력한 경우
         * 불필요한 LLM 호출을 생략한다.
         */
        if (hasCompleteSemanticMetadata(request)) {
            GeneratedMetadata result =
                    new GeneratedMetadata();

            result.setDescription(request.getDescription());
            result.setSubjects(copyList(request.getSubjects()));
            result.setKeywords(copyList(request.getKeywords()));
            result.setAudience(request.getAudience());
            result.setFormatDescription(
                    request.getFormatDescription());

            return result;
        }

        LlmRequest llmRequest =
                createLlmRequest(request);

        LlmResponse llmResponse = llmClient.chat(llmRequest);

        if (llmResponse == null) {
            throw new IllegalArgumentException(
                    "메타데이터 생성 모델의 응답이 없습니다.");
        }

        String content =
                extractResponseContent(llmResponse);

        if (isBlank(content)) {
            throw new IllegalArgumentException(
                    "메타데이터 생성 결과가 비어 있습니다.");
        }

        return parseGeneratedMetadata(content);
    }

    private boolean hasCompleteSemanticMetadata(
            GenerateMetadataRequest request) {

        return !isBlank(request.getDescription())
                && request.getSubjects() != null
                && !request.getSubjects().isEmpty()
                && request.getKeywords() != null
                && !request.getKeywords().isEmpty();
    }

    private LlmRequest createLlmRequest(
            GenerateMetadataRequest request) {

    	LlmMessage systemMessage =
    	        LlmMessage.system(
    	                createSystemPrompt(request));

    	LlmMessage userMessage =
    	        LlmMessage.user(
    	                createUserPrompt(request));

        return LlmRequest.builder()
                .model(model)
                .messages(
                        List.of(
                                systemMessage,
                                userMessage))
                .temperature(0.2)
                .stream(false)
                .build();
    }

    private String createSystemPrompt(
            GenerateMetadataRequest request) {

        int maxDescriptionLength =
                request.getMaxDescriptionLength() > 0
                        ? request.getMaxDescriptionLength()
                        : DEFAULT_MAX_DESCRIPTION_LENGTH;

        int maxKeywords =
                request.getMaxKeywords() > 0
                        ? request.getMaxKeywords()
                        : DEFAULT_MAX_KEYWORDS;

        return """
                당신은 EPUB 3 전자책 메타데이터 편집 전문가입니다.

                제공된 책 정보와 원고 내용을 분석하여
                서점 유통 및 EPUB package.opf에 사용할 메타데이터를 생성하십시오.

                반드시 JSON 객체 하나만 출력하십시오.
                Markdown 코드 블록, 설명, 주석은 출력하지 마십시오.

                규칙:
                1. 책의 실제 내용에 근거해서 작성합니다.
                2. 제공되지 않은 저자, 출판사, ISBN을 임의로 생성하지 않습니다.
                3. description은 한국어로 자연스럽게 작성합니다.
                4. description은 최대 %d자 이내로 작성합니다.
                5. subjects는 책의 핵심 주제를 나타내는 배열입니다.
                6. keywords는 검색에 사용할 구체적인 단어로 작성합니다.
                7. keywords는 최대 %d개로 제한합니다.
                8. 중복되는 subject와 keyword는 제거합니다.
                9. audience는 일반 독자, 청소년, 개발자 등 대상 독자를 작성합니다.
                10. formatDescription은 책의 형식을 간단히 작성합니다.
                    예: 포토에세이, 장편소설, 기술서, 인문에세이
                11. 확인할 수 없는 사실은 작성하지 않습니다.

                출력 형식:
                {
                  "description": "",
                  "subjects": [],
                  "keywords": [],
                  "audience": "",
                  "formatDescription": ""
                }
                """
                .formatted(
                        maxDescriptionLength,
                        maxKeywords);
    }

    private String createUserPrompt(
            GenerateMetadataRequest request) {

        StringBuilder prompt =
                new StringBuilder();

        appendField(
                prompt,
                "제목",
                request.getTitle());

        appendField(
                prompt,
                "부제",
                defaultIfBlank(
                        request.getSubtitle(),
                        "없음"));

        appendField(
                prompt,
                "저자",
                resolveCreatorText(request));

        appendField(
                prompt,
                "출판사",
                defaultIfBlank(
                        request.getPublisher(),
                        "제공되지 않음"));

        appendField(
                prompt,
                "책 유형",
                defaultIfBlank(
                        request.getFormatDescription(),
                        "제공되지 않음"));

        appendField(
                prompt,
                "기존 설명",
                defaultIfBlank(
                        request.getDescription(),
                        "제공되지 않음"));

        appendField(
                prompt,
                "기존 주제",
                joinValues(request.getSubjects()));

        appendField(
                prompt,
                "기존 키워드",
                joinValues(request.getKeywords()));

        if (!isBlank(request.getTableOfContents())) {
            appendLine(prompt, "");
            appendLine(prompt, "[목차]");
            appendLine(
                    prompt,
                    request.getTableOfContents().trim());
        }

        if (!isBlank(request.getContentSummary())) {
            appendLine(prompt, "");
            appendLine(prompt, "[원고 요약]");
            appendLine(
                    prompt,
                    request.getContentSummary().trim());
        }

        if (!isBlank(request.getContentSample())) {
            appendLine(prompt, "");
            appendLine(prompt, "[원고 일부]");
            appendLine(
                    prompt,
                    request.getContentSample().trim());
        }

        appendLine(prompt, "");
        appendLine(
                prompt,
                "JSON 객체만 출력하십시오.");

        return prompt.toString();
    }

    private GeneratedMetadata parseGeneratedMetadata(
            String responseContent) {

        String normalized =
                normalizeJsonResponse(responseContent);

        try {
            GeneratedMetadata generated =
                    jsonMapper.fromJson(
                            normalized,
                            GeneratedMetadata.class);

            if (generated == null) {
                throw new IllegalArgumentException(
                        "생성된 메타데이터가 null입니다.");
            }

            return generated;

        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "모델 응답을 메타데이터 JSON으로 변환하지 못했습니다: "
                            + buildExceptionMessage(exception),
                    exception);
        }
    }

    private EpubMetadata mergeMetadata(
            GenerateMetadataRequest request,
            GeneratedMetadata generated) {

        EpubMetadata metadata =
                new EpubMetadata();

        metadata.setIdentifier(
                defaultIfBlank(
                        request.getIdentifier(),
                        createUuidIdentifier()));

        metadata.setIdentifierType(
                resolveIdentifierType(
                        metadata.getIdentifier()));

        metadata.setTitle(
                request.getTitle().trim());

        metadata.setSubtitle(
                trimToNull(request.getSubtitle()));

        metadata.setLanguage(
                normalizeLanguage(
                        request.getLanguage()));

        metadata.setCreators(
                resolveCreators(request));

        metadata.setPublisher(
                trimToNull(request.getPublisher()));

        metadata.setDescription(
                firstNonBlank(
                        request.getDescription(),
                        generated.getDescription()));

        metadata.setSubjects(
                mergeValues(
                        request.getSubjects(),
                        generated.getSubjects()));

        metadata.setKeywords(
                mergeValues(
                        request.getKeywords(),
                        generated.getKeywords()));

        metadata.setAudience(
                firstNonBlank(
                        request.getAudience(),
                        generated.getAudience()));

        metadata.setFormatDescription(
                firstNonBlank(
                        request.getFormatDescription(),
                        generated.getFormatDescription()));

        metadata.setType(
                defaultIfBlank(
                        request.getType(),
                        DEFAULT_TYPE));

        metadata.setRights(
                trimToNull(request.getRights()));

        metadata.setSource(
                trimToNull(request.getSource()));

        metadata.setRelation(
                trimToNull(request.getRelation()));

        metadata.setCoverage(
                trimToNull(request.getCoverage()));

        metadata.setPublicationDate(
                trimToNull(request.getPublicationDate()));

        metadata.setModified(
                normalizeModifiedDate(
                        request.getModified()));

        metadata.setAccessibilitySummary(
                trimToNull(
                        request.getAccessibilitySummary()));

        return metadata;
    }

    private void normalizeMetadata(
            EpubMetadata metadata,
            GenerateMetadataRequest request) {

        int maxDescriptionLength =
                request.getMaxDescriptionLength() > 0
                        ? request.getMaxDescriptionLength()
                        : DEFAULT_MAX_DESCRIPTION_LENGTH;

        int maxKeywords =
                request.getMaxKeywords() > 0
                        ? request.getMaxKeywords()
                        : DEFAULT_MAX_KEYWORDS;

        metadata.setDescription(
                truncate(
                        normalizeWhitespace(
                                metadata.getDescription()),
                        maxDescriptionLength));

        metadata.setSubjects(
                normalizeValues(
                        metadata.getSubjects(),
                        20));

        metadata.setKeywords(
                normalizeValues(
                        metadata.getKeywords(),
                        maxKeywords));

        metadata.setCreators(
                normalizeCreators(
                        metadata.getCreators()));

        if (metadata.getCreators().isEmpty()) {
            throw new IllegalArgumentException(
                    "유효한 저자 정보가 없습니다.");
        }
    }

    private List<MetadataCreator> resolveCreators(
            GenerateMetadataRequest request) {

        if (request.getCreators() != null
                && !request.getCreators().isEmpty()) {

            return new ArrayList<>(
                    request.getCreators());
        }

        MetadataCreator creator =
                new MetadataCreator();

        creator.setName(
                request.getCreator().trim());

        creator.setRole(
                defaultIfBlank(
                        request.getCreatorRole(),
                        "aut"));

        creator.setFileAs(
                trimToNull(request.getCreatorFileAs()));

        return new ArrayList<>(
                Collections.singletonList(creator));
    }

    private List<MetadataCreator> normalizeCreators(
            List<MetadataCreator> creators) {

        List<MetadataCreator> normalized =
                new ArrayList<>();

        if (creators == null) {
            return normalized;
        }

        for (MetadataCreator creator : creators) {
            if (creator == null
                    || isBlank(creator.getName())) {
                continue;
            }

            creator.setName(
                    creator.getName().trim());

            creator.setRole(
                    defaultIfBlank(
                            creator.getRole(),
                            "aut"));

            creator.setFileAs(
                    trimToNull(
                            creator.getFileAs()));

            normalized.add(creator);
        }

        return normalized;
    }

    private List<String> mergeValues(
            List<String> primary,
            List<String> secondary) {

        List<String> merged =
                new ArrayList<>();

        if (primary != null) {
            merged.addAll(primary);
        }

        if (secondary != null) {
            merged.addAll(secondary);
        }

        return merged;
    }

    private List<String> normalizeValues(
            List<String> values,
            int maximumCount) {

        Set<String> uniqueValues =
                new LinkedHashSet<>();

        if (values != null) {
            for (String value : values) {
                String normalized =
                        normalizeWhitespace(value);

                if (!isBlank(normalized)) {
                    uniqueValues.add(normalized);
                }

                if (maximumCount > 0
                        && uniqueValues.size()
                                >= maximumCount) {
                    break;
                }
            }
        }

        return new ArrayList<>(uniqueValues);
    }

    private String normalizeJsonResponse(
            String responseContent) {

        String normalized =
                removeCodeFence(responseContent.trim());

        int objectStart =
                normalized.indexOf('{');

        int objectEnd =
                normalized.lastIndexOf('}');

        if (objectStart >= 0
                && objectEnd > objectStart) {

            normalized =
                    normalized.substring(
                            objectStart,
                            objectEnd + 1);
        }

        return normalized.trim();
    }

    private String extractResponseContent(
            LlmResponse response) {

        if (response == null) {
            return null;
        }

        String content =
                response.getContent();

        if (isBlank(content)) {
            return null;
        }

        return content.trim();
    }

    private String createUuidIdentifier() {
        return "urn:uuid:"
                + UUID.randomUUID();
    }

    private String resolveIdentifierType(
            String identifier) {

        if (isBlank(identifier)) {
            return null;
        }

        String normalized =
                identifier.trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.startsWith("urn:uuid:")) {
            return "UUID";
        }

        if (normalized.startsWith("urn:isbn:")
                || normalized.matches(
                        "(?:97[89])?\\d{9}[\\dXx]")) {

            return "ISBN";
        }

        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("urn:")) {

            return "URI";
        }

        return "CUSTOM";
    }

    private boolean isValidIdentifier(
            String identifier) {

        String normalized =
                identifier.trim();

        if (normalized.startsWith("urn:uuid:")) {
            try {
                UUID.fromString(
                        normalized.substring(
                                "urn:uuid:".length()));

                return true;

            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        if (normalized.matches(
                "(?:97[89][- ]?)?"
                        + "\\d[- \\d]{8,15}[\\dXx]")) {

            return true;
        }

        try {
            URI uri =
                    new URI(normalized);

            return uri.getScheme() != null;

        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isValidLanguage(
            String language) {

        return language.trim()
                .matches(
                        "^[a-zA-Z]{2,3}"
                                + "(?:-[a-zA-Z]{2,4})?$");
    }

    private String normalizeLanguage(
            String language) {

        if (isBlank(language)) {
            return DEFAULT_LANGUAGE;
        }

        String[] parts =
                language.trim().split("-");

        if (parts.length == 1) {
            return parts[0]
                    .toLowerCase(Locale.ROOT);
        }

        return parts[0].toLowerCase(Locale.ROOT)
                + "-"
                + parts[1].toUpperCase(Locale.ROOT);
    }

    private boolean isValidDate(
            String date) {

        try {
            LocalDate.parse(date.trim());
            return true;

        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private String normalizeModifiedDate(
            String modified) {

        if (isBlank(modified)) {
            return Instant.now().toString();
        }

        try {
            return Instant.parse(
                    modified.trim())
                    .toString();

        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "수정일은 ISO-8601 UTC 형식이어야 합니다. "
                            + "예: 2026-08-04T04:00:00Z");
        }
    }

    private String resolveCreatorText(
            GenerateMetadataRequest request) {

        if (request.getCreators() != null
                && !request.getCreators().isEmpty()) {

            List<String> names =
                    new ArrayList<>();

            for (MetadataCreator creator
                    : request.getCreators()) {

                if (creator != null
                        && !isBlank(creator.getName())) {

                    names.add(
                            creator.getName().trim());
                }
            }

            return String.join(", ", names);
        }

        return defaultIfBlank(
                request.getCreator(),
                "제공되지 않음");
    }

    private String joinValues(
            List<String> values) {

        if (values == null || values.isEmpty()) {
            return "제공되지 않음";
        }

        return String.join(", ", values);
    }

    private List<String> copyList(
            List<String> values) {

        return values == null
                ? new ArrayList<>()
                : new ArrayList<>(values);
    }

    private String firstNonBlank(
            String first,
            String second) {

        if (!isBlank(first)) {
            return first.trim();
        }

        return trimToNull(second);
    }

    private String normalizeWhitespace(
            String value) {

        if (value == null) {
            return null;
        }

        return value.trim()
                .replaceAll("\\s+", " ");
    }

    private String truncate(
            String value,
            int maximumLength) {

        if (value == null
                || maximumLength <= 0
                || value.length() <= maximumLength) {

            return value;
        }

        return value.substring(
                0,
                maximumLength);
    }

    private String removeCodeFence(
            String value) {

        String normalized = value;

        if (normalized.startsWith("```")) {
            int firstLineEnd =
                    normalized.indexOf('\n');

            if (firstLineEnd >= 0) {
                normalized =
                        normalized.substring(
                                firstLineEnd + 1);
            }
        }

        if (normalized.endsWith("```")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 3);
        }

        return normalized.trim();
    }

    private ToolResult failure(
            ToolStatus status,
            String code,
            String message) {

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(status)
                .message(message)
                .issues(
                        Collections.singletonList(
                                issue(code, message)))
                .build();
    }

    private ToolIssue issue(
            String code,
            String message) {

        return ToolIssue.builder()
                .code(code)
                .severity(ToolIssueSeverity.ERROR)
                .message(message)
                .build();
    }

    private String buildExceptionMessage(
            Exception exception) {

        if (exception == null) {
            return "알 수 없는 오류가 발생했습니다.";
        }

        if (!isBlank(exception.getMessage())) {
            return exception.getMessage();
        }

        return exception.getClass()
                .getSimpleName()
                + " 오류가 발생했습니다.";
    }

    private String trimToNull(
            String value) {

        return isBlank(value)
                ? null
                : value.trim();
    }

    private String defaultIfBlank(
            String value,
            String defaultValue) {

        return isBlank(value)
                ? defaultValue
                : value.trim();
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }

    private void appendField(
            StringBuilder builder,
            String name,
            String value) {

        appendLine(
                builder,
                "- "
                        + name
                        + ": "
                        + value);
    }

    private void appendLine(
            StringBuilder builder,
            String value) {

        builder.append(
                        value == null
                                ? ""
                                : value)
                .append('\n');
    }

    /**
     * 메타데이터 생성 요청 DTO.
     *
     * 실제 프로젝트에서는 별도 파일로 분리하는 것을 권장한다.
     */
    public static final class GenerateMetadataRequest {

        private String identifier;

        private String title;
        private String subtitle;

        private String creator;
        private String creatorRole;
        private String creatorFileAs;

        private List<MetadataCreator> creators;

        private String publisher;
        private String language;

        private String description;
        private List<String> subjects;
        private List<String> keywords;

        private String audience;
        private String formatDescription;
        private String type;

        private String rights;
        private String source;
        private String relation;
        private String coverage;

        private String publicationDate;
        private String modified;

        private String accessibilitySummary;

        private String tableOfContents;
        private String contentSummary;
        private String contentSample;

        private int maxDescriptionLength =
                DEFAULT_MAX_DESCRIPTION_LENGTH;

        private int maxKeywords =
                DEFAULT_MAX_KEYWORDS;

        public GenerateMetadataRequest() {
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(
                String identifier) {
            this.identifier = identifier;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(
                String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(
                String subtitle) {
            this.subtitle = subtitle;
        }

        public String getCreator() {
            return creator;
        }

        public void setCreator(
                String creator) {
            this.creator = creator;
        }

        public String getCreatorRole() {
            return creatorRole;
        }

        public void setCreatorRole(
                String creatorRole) {
            this.creatorRole = creatorRole;
        }

        public String getCreatorFileAs() {
            return creatorFileAs;
        }

        public void setCreatorFileAs(
                String creatorFileAs) {
            this.creatorFileAs = creatorFileAs;
        }

        public List<MetadataCreator> getCreators() {
            return creators;
        }

        public void setCreators(
                List<MetadataCreator> creators) {
            this.creators = creators;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(
                String publisher) {
            this.publisher = publisher;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(
                String language) {
            this.language = language;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(
                String description) {
            this.description = description;
        }

        public List<String> getSubjects() {
            return subjects;
        }

        public void setSubjects(
                List<String> subjects) {
            this.subjects = subjects;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(
                List<String> keywords) {
            this.keywords = keywords;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(
                String audience) {
            this.audience = audience;
        }

        public String getFormatDescription() {
            return formatDescription;
        }

        public void setFormatDescription(
                String formatDescription) {
            this.formatDescription =
                    formatDescription;
        }

        public String getType() {
            return type;
        }

        public void setType(
                String type) {
            this.type = type;
        }

        public String getRights() {
            return rights;
        }

        public void setRights(
                String rights) {
            this.rights = rights;
        }

        public String getSource() {
            return source;
        }

        public void setSource(
                String source) {
            this.source = source;
        }

        public String getRelation() {
            return relation;
        }

        public void setRelation(
                String relation) {
            this.relation = relation;
        }

        public String getCoverage() {
            return coverage;
        }

        public void setCoverage(
                String coverage) {
            this.coverage = coverage;
        }

        public String getPublicationDate() {
            return publicationDate;
        }

        public void setPublicationDate(
                String publicationDate) {
            this.publicationDate =
                    publicationDate;
        }

        public String getModified() {
            return modified;
        }

        public void setModified(
                String modified) {
            this.modified = modified;
        }

        public String getAccessibilitySummary() {
            return accessibilitySummary;
        }

        public void setAccessibilitySummary(
                String accessibilitySummary) {
            this.accessibilitySummary =
                    accessibilitySummary;
        }

        public String getTableOfContents() {
            return tableOfContents;
        }

        public void setTableOfContents(
                String tableOfContents) {
            this.tableOfContents =
                    tableOfContents;
        }

        public String getContentSummary() {
            return contentSummary;
        }

        public void setContentSummary(
                String contentSummary) {
            this.contentSummary =
                    contentSummary;
        }

        public String getContentSample() {
            return contentSample;
        }

        public void setContentSample(
                String contentSample) {
            this.contentSample =
                    contentSample;
        }

        public int getMaxDescriptionLength() {
            return maxDescriptionLength;
        }

        public void setMaxDescriptionLength(
                int maxDescriptionLength) {
            this.maxDescriptionLength =
                    maxDescriptionLength;
        }

        public int getMaxKeywords() {
            return maxKeywords;
        }

        public void setMaxKeywords(
                int maxKeywords) {
            this.maxKeywords = maxKeywords;
        }
    }

    /**
     * EPUB 저자 및 기여자 정보.
     *
     * role은 MARC relator code를 사용한다.
     * aut: 저자, edt: 편집자, trl: 번역자, ill: 삽화가
     */
    public static final class MetadataCreator {

        private String name;
        private String role;
        private String fileAs;

        public MetadataCreator() {
        }

        public String getName() {
            return name;
        }

        public void setName(
                String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(
                String role) {
            this.role = role;
        }

        public String getFileAs() {
            return fileAs;
        }

        public void setFileAs(
                String fileAs) {
            this.fileAs = fileAs;
        }
    }

    /**
     * EPUB package.opf에 적용할 최종 메타데이터.
     */
    public static final class EpubMetadata {

        private String identifier;
        private String identifierType;

        private String title;
        private String subtitle;
        private String language;

        private List<MetadataCreator> creators;

        private String publisher;
        private String description;

        private List<String> subjects;
        private List<String> keywords;

        private String audience;
        private String formatDescription;
        private String type;

        private String rights;
        private String source;
        private String relation;
        private String coverage;

        private String publicationDate;
        private String modified;

        private String accessibilitySummary;

        public EpubMetadata() {
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(
                String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifierType() {
            return identifierType;
        }

        public void setIdentifierType(
                String identifierType) {
            this.identifierType =
                    identifierType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(
                String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(
                String subtitle) {
            this.subtitle = subtitle;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(
                String language) {
            this.language = language;
        }

        public List<MetadataCreator> getCreators() {
            return creators;
        }

        public void setCreators(
                List<MetadataCreator> creators) {
            this.creators = creators;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(
                String publisher) {
            this.publisher = publisher;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(
                String description) {
            this.description = description;
        }

        public List<String> getSubjects() {
            return subjects;
        }

        public void setSubjects(
                List<String> subjects) {
            this.subjects = subjects;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(
                List<String> keywords) {
            this.keywords = keywords;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(
                String audience) {
            this.audience = audience;
        }

        public String getFormatDescription() {
            return formatDescription;
        }

        public void setFormatDescription(
                String formatDescription) {
            this.formatDescription =
                    formatDescription;
        }

        public String getType() {
            return type;
        }

        public void setType(
                String type) {
            this.type = type;
        }

        public String getRights() {
            return rights;
        }

        public void setRights(
                String rights) {
            this.rights = rights;
        }

        public String getSource() {
            return source;
        }

        public void setSource(
                String source) {
            this.source = source;
        }

        public String getRelation() {
            return relation;
        }

        public void setRelation(
                String relation) {
            this.relation = relation;
        }

        public String getCoverage() {
            return coverage;
        }

        public void setCoverage(
                String coverage) {
            this.coverage = coverage;
        }

        public String getPublicationDate() {
            return publicationDate;
        }

        public void setPublicationDate(
                String publicationDate) {
            this.publicationDate =
                    publicationDate;
        }

        public String getModified() {
            return modified;
        }

        public void setModified(
                String modified) {
            this.modified = modified;
        }

        public String getAccessibilitySummary() {
            return accessibilitySummary;
        }

        public void setAccessibilitySummary(
                String accessibilitySummary) {
            this.accessibilitySummary =
                    accessibilitySummary;
        }
    }

    /**
     * LLM이 생성하는 의미 기반 메타데이터.
     */
    public static final class GeneratedMetadata {

        private String description;
        private List<String> subjects;
        private List<String> keywords;
        private String audience;
        private String formatDescription;

        public GeneratedMetadata() {
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(
                String description) {
            this.description = description;
        }

        public List<String> getSubjects() {
            return subjects;
        }

        public void setSubjects(
                List<String> subjects) {
            this.subjects = subjects;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(
                List<String> keywords) {
            this.keywords = keywords;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(
                String audience) {
            this.audience = audience;
        }

        public String getFormatDescription() {
            return formatDescription;
        }

        public void setFormatDescription(
                String formatDescription) {
            this.formatDescription =
                    formatDescription;
        }
    }

    /**
     * Tool 전체 응답 DTO.
     */
    public static final class GenerateMetadataResponse {

        private boolean generated;
        private String model;
        private EpubMetadata metadata;

        public GenerateMetadataResponse() {
        }

        public boolean isGenerated() {
            return generated;
        }

        public void setGenerated(
                boolean generated) {
            this.generated = generated;
        }

        public String getModel() {
            return model;
        }

        public void setModel(
                String model) {
            this.model = model;
        }

        public EpubMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(
                EpubMetadata metadata) {
            this.metadata = metadata;
        }
    }
}