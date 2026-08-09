/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
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
 * 이미지 파일을 Vision LLM으로 분석하는 Agent Tool.
 *
 * <p>이미지 분석 결과는 EPUB 접근성 처리에 재사용할 수 있도록
 * 구조화된 형태로 반환한다.</p>
 *
 * <p>주요 분석 항목:</p>
 * <ul>
 *     <li>이미지 유형</li>
 *     <li>장식 이미지 여부</li>
 *     <li>핵심 피사체와 장면</li>
 *     <li>이미지에 포함된 문자</li>
 *     <li>짧은 대체 텍스트</li>
 *     <li>긴 설명</li>
 *     <li>분석 신뢰도</li>
 * </ul>
 */
public final class AnalyzeImageTool implements AgentTool {

    public static final String TOOL_NAME = "analyze_image";

    private static final String TOOL_DESCRIPTION =
            "이미지를 분석하여 EPUB 접근성용 대체 텍스트와 구조화된 정보를 생성합니다.";

    private static final String DEFAULT_MODEL =
            "gemma4:31b-cloud";

    private static final long DEFAULT_MAX_FILE_SIZE =
            20L * 1024L * 1024L;

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "gif",
                    "bmp");

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;
    private final String visionModel;
    private final long maxFileSize;

    public AnalyzeImageTool(
            LlmClient llmClient,
            JsonMapper jsonMapper) {

        this(
                llmClient,
                jsonMapper,
                DEFAULT_MODEL,
                DEFAULT_MAX_FILE_SIZE);
    }

    public AnalyzeImageTool(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            String visionModel) {

        this(
                llmClient,
                jsonMapper,
                visionModel,
                DEFAULT_MAX_FILE_SIZE);
    }

    public AnalyzeImageTool(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            String visionModel,
            long maxFileSize) {

        this.llmClient = Objects.requireNonNull(
                llmClient,
                "llmClient must not be null");

        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null");

        this.visionModel =
                isBlank(visionModel)
                        ? DEFAULT_MODEL
                        : visionModel.trim();

        if (maxFileSize <= 0) {
            throw new IllegalArgumentException(
                    "maxFileSize must be greater than zero");
        }

        this.maxFileSize = maxFileSize;
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
            AnalyzeImageRequest request =
                    parseRequest(toolRequest);

            List<ToolIssue> validationIssues =
                    validateRequest(request);

            if (!validationIssues.isEmpty()) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message("이미지 분석 요청이 올바르지 않습니다.")
                        .issues(validationIssues)
                        .build();
            }

            Path projectRoot =
                    resolveProjectRoot(
                            request,
                            toolContext);

            Path imagePath =
                    resolveImagePath(
                            request,
                            projectRoot);

            validateImageFile(imagePath);

            String base64Image =
                    encodeImage(imagePath);

            LlmRequest llmRequest =
                    createLlmRequest(
                            request,
                            imagePath,
                            base64Image);

            LlmResponse llmResponse = llmClient.chat(llmRequest);

            if (llmResponse == null) {
                return failure(
                        ToolStatus.FAILED,
                        "LLM_EMPTY_RESPONSE",
                        "이미지 분석 모델의 응답이 없습니다.");
            }


            String responseContent =
                    extractResponseContent(llmResponse);

            if (isBlank(responseContent)) {
                return failure(
                        ToolStatus.FAILED,
                        "IMAGE_ANALYSIS_EMPTY",
                        "이미지 분석 결과가 비어 있습니다.");
            }

            ImageAnalysisResult analysisResult =
                    parseAnalysisResult(responseContent);

            normalizeAnalysisResult(
                    analysisResult,
                    imagePath,
                    request);

            AnalyzeImageResponse response =
                    createResponse(
                            imagePath,
                            projectRoot,
                            analysisResult);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put(
                    "response",
                    response);
            
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message("이미지 분석을 완료했습니다.")
                    .data(data)
                    .build();

        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_IMAGE_PATH",
                    "유효하지 않은 이미지 경로입니다: "
                            + exception.getInput());

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "IMAGE_PATH_ACCESS_DENIED",
                    buildExceptionMessage(exception));

        } catch (IOException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "IMAGE_READ_FAILED",
                    buildExceptionMessage(exception));

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_IMAGE_REQUEST",
                    buildExceptionMessage(exception));

        } catch (Exception exception) {
            return failure(
                    ToolStatus.FAILED,
                    "IMAGE_ANALYSIS_FAILED",
                    buildExceptionMessage(exception));
        }
    }

    private AnalyzeImageRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "이미지 분석 인자가 없습니다.");
        }

        if (arguments instanceof AnalyzeImageRequest) {
            return (AnalyzeImageRequest) arguments;
        }
        
        String json = jsonMapper.toJson(arguments);

        AnalyzeImageRequest request =
                jsonMapper.fromJson(
                        json,
                        AnalyzeImageRequest.class);
        
        if (request == null) {
            throw new IllegalArgumentException(
                    "이미지 분석 요청을 변환할 수 없습니다.");
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            AnalyzeImageRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "이미지 분석 요청이 없습니다."));
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(request.getImagePath())) {
            issues.add(
                    issue(
                            "IMAGE_PATH_REQUIRED",
                            "분석할 이미지 경로가 필요합니다."));
        }

        if (request.getMaxAltLength() < 0) {
            issues.add(
                    issue(
                            "INVALID_MAX_ALT_LENGTH",
                            "대체 텍스트 최대 길이는 0 이상이어야 합니다."));
        }

        return issues;
    }

    private Path resolveProjectRoot(
            AnalyzeImageRequest request,
            ToolContext toolContext) {

        String projectRootValue =
                trimToNull(request.getProjectRoot());

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectRoot");
        }

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectPath");
        }

        if (projectRootValue == null) {
            return null;
        }

        Path projectRoot =
                Path.of(projectRootValue)
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(projectRoot)) {
            throw new IllegalArgumentException(
                    "프로젝트 루트가 존재하지 않습니다: "
                            + projectRoot);
        }

        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException(
                    "프로젝트 루트가 디렉터리가 아닙니다: "
                            + projectRoot);
        }

        return projectRoot;
    }

    private Path resolveImagePath(
            AnalyzeImageRequest request,
            Path projectRoot) {

        Path requestedPath =
                Path.of(request.getImagePath().trim());

        Path imagePath;

        if (requestedPath.isAbsolute()) {
            imagePath =
                    requestedPath.toAbsolutePath()
                            .normalize();

            if (projectRoot != null
                    && request.isRestrictToProject()
                    && !imagePath.startsWith(projectRoot)) {

                throw new SecurityException(
                        "프로젝트 루트 외부 이미지는 분석할 수 없습니다.");
            }

        } else {
            if (projectRoot == null) {
                throw new IllegalArgumentException(
                        "상대 이미지 경로를 사용하려면 프로젝트 루트가 필요합니다.");
            }

            imagePath =
                    projectRoot.resolve(requestedPath)
                            .toAbsolutePath()
                            .normalize();

            if (!imagePath.startsWith(projectRoot)) {
                throw new SecurityException(
                        "프로젝트 루트 외부 이미지는 분석할 수 없습니다.");
            }
        }

        return imagePath;
    }

    private void validateImageFile(
            Path imagePath) throws IOException {

        if (!Files.exists(imagePath)) {
            throw new IllegalArgumentException(
                    "이미지 파일이 존재하지 않습니다: "
                            + imagePath);
        }

        if (!Files.isRegularFile(imagePath)) {
            throw new IllegalArgumentException(
                    "이미지 경로가 일반 파일이 아닙니다: "
                            + imagePath);
        }

        if (!Files.isReadable(imagePath)) {
            throw new IllegalArgumentException(
                    "이미지 파일을 읽을 수 없습니다: "
                            + imagePath);
        }

        String extension =
                getExtension(
                        imagePath.getFileName()
                                .toString());

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 형식입니다: "
                            + extension);
        }

        long fileSize =
                Files.size(imagePath);

        if (fileSize <= 0) {
            throw new IllegalArgumentException(
                    "이미지 파일이 비어 있습니다.");
        }

        if (fileSize > maxFileSize) {
            throw new IllegalArgumentException(
                    "이미지 파일 크기가 허용 범위를 초과했습니다. 최대 크기: "
                            + maxFileSize
                            + " bytes");
        }
    }

    private String encodeImage(
            Path imagePath) throws IOException {

        byte[] imageBytes =
                Files.readAllBytes(imagePath);

        return Base64.getEncoder()
                .encodeToString(imageBytes);
    }

    private LlmRequest createLlmRequest(
            AnalyzeImageRequest request,
            Path imagePath,
            String base64Image) {

        String systemPrompt = createSystemPrompt(request);

        String userPrompt =
                createUserPrompt(
                        request,
                        imagePath);

    	LlmMessage systemMessage = LlmMessage.system(systemPrompt);
    	LlmMessage userMessage = LlmMessage.user(userPrompt);

        return LlmRequest.builder()
                .model(visionModel)
                .messages(
                        List.of(
                                systemMessage,
                                userMessage))
                .temperature(0.1)
                .stream(false)
                .build();
    }

    private String createSystemPrompt(
            AnalyzeImageRequest request) {

        int maxAltLength =
                request.getMaxAltLength() > 0
                        ? request.getMaxAltLength()
                        : 100;

        return """
                당신은 EPUB3, WCAG 및 전자책 접근성을 위한 이미지 분석 전문가입니다.

                입력된 이미지를 분석하고 반드시 JSON 객체 하나만 출력하십시오.
                Markdown 코드 블록, 설명문, 주석은 출력하지 마십시오.

                분석 원칙:
                1. 이미지에서 직접 확인할 수 있는 사실만 기술합니다.
                2. 사람의 신원, 감정, 인종, 장애, 관계를 임의로 추정하지 않습니다.
                3. EPUB 본문에서 의미가 없는 장식용 이미지라면 decorative를 true로 설정합니다.
                4. shortAlt는 핵심 정보를 간결하게 전달합니다.
                5. shortAlt를 "이미지", "사진", "그림"이라는 단어로 시작하지 않습니다.
                6. shortAlt는 최대 %d자 이내로 작성합니다.
                7. 복잡한 차트, 표, 지도, 다이어그램은 longDescription에 상세히 설명합니다.
                8. 이미지에 보이는 중요한 문자는 visibleText 배열에 기록합니다.
                9. 이미지가 책 표지라면 제목, 부제, 저자명 등 중요한 문자를 반영합니다.
                10. 장식 이미지라면 shortAlt는 빈 문자열로 반환합니다.
                11. confidence는 0.0 이상 1.0 이하의 숫자로 반환합니다.
                12. 모든 설명은 한국어로 작성합니다.

                출력 JSON 형식:
                {
                  "imageType": "PHOTO|ILLUSTRATION|COVER|CHART|TABLE|DIAGRAM|MAP|SCREENSHOT|DECORATIVE|OTHER",
                  "decorative": false,
                  "shortAlt": "",
                  "longDescription": "",
                  "summary": "",
                  "visibleText": [],
                  "mainSubjects": [],
                  "keywords": [],
                  "confidence": 0.0,
                  "requiresHumanReview": false,
                  "reviewReason": ""
                }
                """
                .formatted(maxAltLength);
    }

    private String createUserPrompt(
            AnalyzeImageRequest request,
            Path imagePath) {

        StringBuilder prompt =
                new StringBuilder();

        appendLine(
                prompt,
                "다음 이미지를 EPUB 접근성 관점에서 분석하십시오.");

        appendField(
                prompt,
                "파일명",
                imagePath.getFileName().toString());

        appendField(
                prompt,
                "분석 목적",
                defaultIfBlank(
                        request.getPurpose(),
                        "EPUB 대체 텍스트 생성"));

        appendField(
                prompt,
                "주변 본문",
                defaultIfBlank(
                        request.getContextText(),
                        "제공되지 않음"));

        if (!isBlank(request.getInstruction())) {
            appendField(
                    prompt,
                    "추가 지시사항",
                    request.getInstruction());
        }

        appendLine(
                prompt,
                "반드시 지정된 JSON 형식으로만 응답하십시오.");

        return prompt.toString();
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


    private ImageAnalysisResult parseAnalysisResult(
            String responseContent) {

        String normalizedJson =
                normalizeJsonResponse(responseContent);

        try {
            ImageAnalysisResult result =
                    jsonMapper.fromJson(
                            normalizedJson,
                            ImageAnalysisResult.class);

            if (result == null) {
                throw new IllegalArgumentException(
                        "이미지 분석 결과가 null입니다.");
            }

            return result;

        } catch (Exception exception) {
            return createFallbackResult(
                    responseContent,
                    exception);
        }
    }

    private String normalizeJsonResponse(
            String responseContent) {

        String normalized =
                removeCodeFence(
                        responseContent.trim());

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

    private ImageAnalysisResult createFallbackResult(
            String responseContent,
            Exception exception) {

        ImageAnalysisResult fallback =
                new ImageAnalysisResult();

        fallback.setImageType("OTHER");
        fallback.setDecorative(false);
        fallback.setShortAlt(
                truncate(
                        responseContent,
                        100));
        fallback.setLongDescription(
                responseContent);
        fallback.setSummary(
                truncate(
                        responseContent,
                        200));
        fallback.setVisibleText(
                new ArrayList<>());
        fallback.setMainSubjects(
                new ArrayList<>());
        fallback.setKeywords(
                new ArrayList<>());
        fallback.setConfidence(0.3);
        fallback.setRequiresHumanReview(true);
        fallback.setReviewReason(
                "모델 응답을 JSON으로 변환하지 못했습니다: "
                        + buildExceptionMessage(exception));

        return fallback;
    }

    private void normalizeAnalysisResult(
            ImageAnalysisResult result,
            Path imagePath,
            AnalyzeImageRequest request) {

        result.setImageType(
                normalizeImageType(
                        result.getImageType()));

        if (result.getVisibleText() == null) {
            result.setVisibleText(
                    new ArrayList<>());
        }

        if (result.getMainSubjects() == null) {
            result.setMainSubjects(
                    new ArrayList<>());
        }

        if (result.getKeywords() == null) {
            result.setKeywords(
                    new ArrayList<>());
        }

        if (result.isDecorative()) {
            result.setImageType("DECORATIVE");
            result.setShortAlt("");
        }

        int maxAltLength =
                request.getMaxAltLength() > 0
                        ? request.getMaxAltLength()
                        : 100;

        result.setShortAlt(
                truncate(
                        defaultIfBlank(
                                result.getShortAlt(),
                                ""),
                        maxAltLength));

        result.setLongDescription(
                defaultIfBlank(
                        result.getLongDescription(),
                        ""));

        result.setSummary(
                defaultIfBlank(
                        result.getSummary(),
                        result.getShortAlt()));

        result.setConfidence(
                clamp(
                        result.getConfidence(),
                        0.0,
                        1.0));

        if (!result.isDecorative()
                && isBlank(result.getShortAlt())) {

            result.setRequiresHumanReview(true);

            if (isBlank(result.getReviewReason())) {
                result.setReviewReason(
                        "비장식 이미지이지만 대체 텍스트가 생성되지 않았습니다.");
            }
        }

        if (result.getConfidence() < 0.6) {
            result.setRequiresHumanReview(true);

            if (isBlank(result.getReviewReason())) {
                result.setReviewReason(
                        "이미지 분석 신뢰도가 낮습니다.");
            }
        }

        result.setSourceFileName(
                imagePath.getFileName()
                        .toString());
    }

    private AnalyzeImageResponse createResponse(
            Path imagePath,
            Path projectRoot,
            ImageAnalysisResult analysisResult)
            throws IOException {

        AnalyzeImageResponse response =
                new AnalyzeImageResponse();

        response.setAnalyzed(true);
        response.setModel(visionModel);
        response.setFileName(
                imagePath.getFileName()
                        .toString());
        response.setAbsolutePath(
                imagePath.toString());
        response.setMimeType(
                resolveMimeType(imagePath));
        response.setFileSize(
                Files.size(imagePath));
        response.setAnalysis(
                analysisResult);

        if (projectRoot != null
                && imagePath.startsWith(projectRoot)) {

            response.setRelativePath(
                    normalizeSeparator(
                            projectRoot.relativize(imagePath)
                                    .toString()));
        }

        return response;
    }

    private String resolveMimeType(
            Path imagePath) {

        try {
            String mimeType =
                    Files.probeContentType(imagePath);

            if (!isBlank(mimeType)) {
                return mimeType;
            }

        } catch (IOException ignored) {
            // 확장자 기반으로 처리한다.
        }

        String extension =
                getExtension(
                        imagePath.getFileName()
                                .toString());

        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private String normalizeImageType(
            String imageType) {

        if (isBlank(imageType)) {
            return "OTHER";
        }

        String normalized =
                imageType.trim()
                        .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "PHOTO",
                 "ILLUSTRATION",
                 "COVER",
                 "CHART",
                 "TABLE",
                 "DIAGRAM",
                 "MAP",
                 "SCREENSHOT",
                 "DECORATIVE",
                 "OTHER" -> normalized;

            default -> "OTHER";
        };
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

    private String getContextString(
            ToolContext context,
            String key) {

        if (context == null
                || key == null
                || key.isBlank()) {

            return null;
        }

        String value =
                context.getAttribute(
                        key,
                        String.class);

        return trimToNull(value);
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

    private String getExtension(
            String fileName) {

        if (isBlank(fileName)) {
            return "";
        }

        int dotIndex =
                fileName.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String truncate(
            String value,
            int maxLength) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim();

        if (maxLength <= 0
                || normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(
                0,
                maxLength);
    }

    private double clamp(
            double value,
            double minimum,
            double maximum) {

        return Math.max(
                minimum,
                Math.min(maximum, value));
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

    private String normalizeSeparator(
            String path) {

        return path.replace('\\', '/');
    }

    private String trimToNull(
            String value) {

        if (isBlank(value)) {
            return null;
        }

        return value.trim();
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
     * 이미지 분석 요청 DTO.
     *
     * 별도 AnalyzeImageRequest.java로 분리해도 된다.
     */
    public static final class AnalyzeImageRequest {

        private String projectRoot;
        private String imagePath;
        private String purpose;
        private String contextText;
        private String instruction;

        private int maxAltLength = 100;
        private boolean restrictToProject = true;

        public AnalyzeImageRequest() {
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(
                String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public String getImagePath() {
            return imagePath;
        }

        public void setImagePath(
                String imagePath) {
            this.imagePath = imagePath;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(
                String purpose) {
            this.purpose = purpose;
        }

        public String getContextText() {
            return contextText;
        }

        public void setContextText(
                String contextText) {
            this.contextText = contextText;
        }

        public String getInstruction() {
            return instruction;
        }

        public void setInstruction(
                String instruction) {
            this.instruction = instruction;
        }

        public int getMaxAltLength() {
            return maxAltLength;
        }

        public void setMaxAltLength(
                int maxAltLength) {
            this.maxAltLength = maxAltLength;
        }

        public boolean isRestrictToProject() {
            return restrictToProject;
        }

        public void setRestrictToProject(
                boolean restrictToProject) {
            this.restrictToProject =
                    restrictToProject;
        }
    }

    /**
     * 이미지 분석 Tool 전체 응답 DTO.
     */
    public static final class AnalyzeImageResponse {

        private boolean analyzed;

        private String model;
        private String fileName;
        private String relativePath;
        private String absolutePath;
        private String mimeType;

        private long fileSize;

        private ImageAnalysisResult analysis;

        public AnalyzeImageResponse() {
        }

        public boolean isAnalyzed() {
            return analyzed;
        }

        public void setAnalyzed(
                boolean analyzed) {
            this.analyzed = analyzed;
        }

        public String getModel() {
            return model;
        }

        public void setModel(
                String model) {
            this.model = model;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(
                String fileName) {
            this.fileName = fileName;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(
                String relativePath) {
            this.relativePath = relativePath;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public void setAbsolutePath(
                String absolutePath) {
            this.absolutePath = absolutePath;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(
                String mimeType) {
            this.mimeType = mimeType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(
                long fileSize) {
            this.fileSize = fileSize;
        }

        public ImageAnalysisResult getAnalysis() {
            return analysis;
        }

        public void setAnalysis(
                ImageAnalysisResult analysis) {
            this.analysis = analysis;
        }
    }

    /**
     * Vision 모델의 구조화된 분석 결과.
     */
    public static final class ImageAnalysisResult {

        private String sourceFileName;
        private String imageType;

        private boolean decorative;

        private String shortAlt;
        private String longDescription;
        private String summary;

        private List<String> visibleText;
        private List<String> mainSubjects;
        private List<String> keywords;

        private double confidence;

        private boolean requiresHumanReview;
        private String reviewReason;

        public ImageAnalysisResult() {
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public void setSourceFileName(
                String sourceFileName) {
            this.sourceFileName = sourceFileName;
        }

        public String getImageType() {
            return imageType;
        }

        public void setImageType(
                String imageType) {
            this.imageType = imageType;
        }

        public boolean isDecorative() {
            return decorative;
        }

        public void setDecorative(
                boolean decorative) {
            this.decorative = decorative;
        }

        public String getShortAlt() {
            return shortAlt;
        }

        public void setShortAlt(
                String shortAlt) {
            this.shortAlt = shortAlt;
        }

        public String getLongDescription() {
            return longDescription;
        }

        public void setLongDescription(
                String longDescription) {
            this.longDescription =
                    longDescription;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(
                String summary) {
            this.summary = summary;
        }

        public List<String> getVisibleText() {
            return visibleText;
        }

        public void setVisibleText(
                List<String> visibleText) {
            this.visibleText = visibleText;
        }

        public List<String> getMainSubjects() {
            return mainSubjects;
        }

        public void setMainSubjects(
                List<String> mainSubjects) {
            this.mainSubjects = mainSubjects;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(
                List<String> keywords) {
            this.keywords = keywords;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(
                double confidence) {
            this.confidence = confidence;
        }

        public boolean isRequiresHumanReview() {
            return requiresHumanReview;
        }

        public void setRequiresHumanReview(
                boolean requiresHumanReview) {
            this.requiresHumanReview =
                    requiresHumanReview;
        }

        public String getReviewReason() {
            return reviewReason;
        }

        public void setReviewReason(
                String reviewReason) {
            this.reviewReason = reviewReason;
        }
    }
}