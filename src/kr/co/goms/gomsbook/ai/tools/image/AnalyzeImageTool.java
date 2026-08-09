/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.image;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.analysis.ImageAnalysisException;
import kr.co.goms.gomsbook.ai.accessibility.analysis.ImageAnalyzer;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisRequest;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisResult;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * 이미지 파일을 분석하여 EPUB 접근성 정보를 생성하는 Agent Tool.
 *
 * <p>
 * 실제 이미지 분석, Vision 모델 호출, 프롬프트 생성,
 * 응답 파싱은 {@link ImageAnalyzer} 구현체에 위임합니다.
 * </p>
 *
 * <p>
 * 일반적인 실행 구조:
 * </p>
 *
 * <pre>
 * AnalyzeImageTool
 *      ↓
 * ImageAnalysisRequest
 *      ↓
 * ImageAnalyzer
 *      ↓
 * VisionImageAnalyzer
 *      ↓
 * LlmClient
 * </pre>
 *
 * <p>
 * 이 Tool은 프로젝트 파일을 수정하지 않습니다.
 * 생성된 대체 텍스트를 XHTML에 적용하는 작업은
 * {@code ApplyAltTextTool}이 담당합니다.
 * </p>
 */
public final class AnalyzeImageTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "analyze_image";

    private static final String TOOL_DESCRIPTION =
            "이미지를 분석하여 EPUB 접근성 유형, "
                    + "대체 텍스트, 상세 설명 및 분석 신뢰도를 생성합니다.";

    private static final String DEFAULT_LANGUAGE =
            "ko";

    private static final int DEFAULT_MAX_ALT_TEXT_LENGTH =
            100;

    private static final int MAX_ALT_TEXT_LENGTH =
            2000;

    private final ImageAnalyzer imageAnalyzer;

    /**
     * ImageAnalyzer 기반 Tool을 생성합니다.
     *
     * @param imageAnalyzer 이미지 분석 서비스
     */
    public AnalyzeImageTool(
            ImageAnalyzer imageAnalyzer) {

        this.imageAnalyzer =
                Objects.requireNonNull(
                        imageAnalyzer,
                        "imageAnalyzer must not be null"
                );
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
                    "ToolRequest가 없습니다."
            );
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
                        .message(
                                "이미지 분석 요청이 올바르지 않습니다."
                        )
                        .issues(validationIssues)
                        .build();
            }

            Path projectRoot =
                    resolveProjectRoot(
                            request,
                            toolContext
                    );

            Path imagePath =
                    resolveImagePath(
                            request,
                            projectRoot
                    );

            ImageAnalysisRequest analysisRequest =
                    createAnalysisRequest(
                            request,
                            projectRoot,
                            imagePath
                    );

            /*
             * 실제 Vision 모델 호출은 ImageAnalyzer 구현체,
             * 일반적으로 VisionImageAnalyzer에서 수행합니다.
             */
            ImageAnalysisResult analysisResult =
                    imageAnalyzer.analyze(
                            analysisRequest
                    );

            if (analysisResult == null) {
                return failure(
                        ToolStatus.FAILED,
                        "IMAGE_ANALYSIS_EMPTY",
                        "이미지 분석 결과가 없습니다."
                );
            }

            Map<String, Object> data =
                    createOutput(
                            request,
                            projectRoot,
                            imagePath,
                            analysisResult
                    );

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message(
                            createSuccessMessage(
                                    analysisResult
                            )
                    )
                    .data(data)
                    .build();

        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_IMAGE_PATH",
                    "유효하지 않은 이미지 경로입니다: "
                            + exception.getInput()
            );

        } catch (ImageAnalysisException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "이미지 분석에 실패했습니다: "
                                    + safeMessage(exception)
                    )
                    .cause(exception)
                    .build();

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "IMAGE_PATH_ACCESS_DENIED",
                    safeMessage(exception)
            );

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_IMAGE_REQUEST",
                    safeMessage(exception)
            );

        } catch (RuntimeException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "이미지 분석 중 예상하지 못한 오류가 발생했습니다: "
                                    + safeMessage(exception)
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ToolRequest의 arguments를 Tool 요청 DTO로 변환합니다.
     *
     * <p>
     * JsonMapper 의존성을 제거하기 위해 Map 기반으로 직접 읽습니다.
     * </p>
     */
    private AnalyzeImageRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "이미지 분석 인자가 없습니다."
            );
        }

        if (arguments
                instanceof AnalyzeImageRequest request) {

            return request;
        }

        if (!(arguments
                instanceof Map<?, ?> map)) {

            throw new IllegalArgumentException(
                    "이미지 분석 인자는 Object 또는 Map 형식이어야 합니다."
            );
        }

        AnalyzeImageRequest request =
                new AnalyzeImageRequest();

        request.setProjectRoot(
                readString(
                        map,
                        "projectRoot"
                )
        );

        request.setImagePath(
                readString(
                        map,
                        "imagePath"
                )
        );

        request.setLanguage(
                readString(
                        map,
                        "language"
                )
        );

        request.setPurpose(
                readString(
                        map,
                        "purpose"
                )
        );

        request.setContextText(
                firstNonBlank(
                        readString(
                                map,
                                "contextText"
                        ),
                        readString(
                                map,
                                "surroundingText"
                        )
                )
        );

        request.setFigureCaption(
                readString(
                        map,
                        "figureCaption"
                )
        );

        request.setDocumentTitle(
                readString(
                        map,
                        "documentTitle"
                )
        );

        request.setDocumentLanguage(
                readString(
                        map,
                        "documentLanguage"
                )
        );

        request.setInstruction(
                readString(
                        map,
                        "instruction"
                )
        );

        Integer maxAltLength =
                firstNonNull(
                        readInteger(
                                map,
                                "maxAltLength"
                        ),
                        readInteger(
                                map,
                                "maxAltTextLength"
                        )
                );

        if (maxAltLength != null) {
            request.setMaxAltLength(
                    maxAltLength
            );
        }

        Boolean restrictToProject =
                readBoolean(
                        map,
                        "restrictToProject"
                );

        if (restrictToProject != null) {
            request.setRestrictToProject(
                    restrictToProject
            );
        }

        Boolean detectVisibleText =
                readBoolean(
                        map,
                        "detectVisibleText"
                );

        if (detectVisibleText != null) {
            request.setDetectVisibleText(
                    detectVisibleText
            );
        }

        Boolean generateDetailedDescription =
                readBoolean(
                        map,
                        "generateDetailedDescription"
                );

        if (generateDetailedDescription != null) {
            request.setGenerateDetailedDescription(
                    generateDetailedDescription
            );
        }

        Boolean classifyAccessibilityType =
                readBoolean(
                        map,
                        "classifyAccessibilityType"
                );

        if (classifyAccessibilityType != null) {
            request.setClassifyAccessibilityType(
                    classifyAccessibilityType
            );
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            AnalyzeImageRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "이미지 분석 요청이 없습니다."
                    )
            );
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(request.getImagePath())) {
            issues.add(
                    issue(
                            "IMAGE_PATH_REQUIRED",
                            "분석할 이미지 경로가 필요합니다."
                    )
            );
        }

        if (request.getMaxAltLength() < 0) {
            issues.add(
                    issue(
                            "INVALID_MAX_ALT_LENGTH",
                            "대체 텍스트 최대 길이는 0 이상이어야 합니다."
                    )
            );
        }

        if (request.getMaxAltLength()
                > MAX_ALT_TEXT_LENGTH) {

            issues.add(
                    issue(
                            "INVALID_MAX_ALT_LENGTH",
                            "대체 텍스트 최대 길이는 "
                                    + MAX_ALT_TEXT_LENGTH
                                    + "자를 초과할 수 없습니다."
                    )
            );
        }

        return issues;
    }

    private Path resolveProjectRoot(
            AnalyzeImageRequest request,
            ToolContext toolContext) {

        String projectRootValue =
                trimToNull(
                        request.getProjectRoot()
                );

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectRoot"
                    );
        }

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectPath"
                    );
        }

        if (projectRootValue == null) {
            throw new IllegalArgumentException(
                    "프로젝트 루트가 필요합니다."
            );
        }

        return Path.of(projectRootValue)
                .toAbsolutePath()
                .normalize();
    }

    private Path resolveImagePath(
            AnalyzeImageRequest request,
            Path projectRoot) {

        Path requestedPath =
                Path.of(
                        request
                                .getImagePath()
                                .trim()
                );

        Path imagePath;

        if (requestedPath.isAbsolute()) {
            imagePath =
                    requestedPath
                            .toAbsolutePath()
                            .normalize();

        } else {
            imagePath =
                    projectRoot
                            .resolve(requestedPath)
                            .toAbsolutePath()
                            .normalize();
        }

        if (request.isRestrictToProject()
                && !imagePath.startsWith(
                        projectRoot)) {

            throw new SecurityException(
                    "프로젝트 루트 외부 이미지는 분석할 수 없습니다."
            );
        }

        return imagePath;
    }

    private ImageAnalysisRequest createAnalysisRequest(
            AnalyzeImageRequest request,
            Path projectRoot,
            Path imagePath) {

        String language =
                defaultIfBlank(
                        request.getLanguage(),
                        DEFAULT_LANGUAGE
                );

        int maxAltLength =
                request.getMaxAltLength() > 0
                        ? request.getMaxAltLength()
                        : DEFAULT_MAX_ALT_TEXT_LENGTH;

        ImageAnalysisRequest.Builder builder =
                ImageAnalysisRequest.builder()
                        .projectRoot(
                                projectRoot
                        )
                        .imagePath(
                                imagePath
                        )
                        .language(
                                language
                        )
                        .purpose(
                                trimToNull(
                                        request.getPurpose()
                                )
                        )
                        .surroundingText(
                                trimToNull(
                                        request.getContextText()
                                )
                        )
                        .figureCaption(
                                trimToNull(
                                        request.getFigureCaption()
                                )
                        )
                        .documentTitle(
                                trimToNull(
                                        request.getDocumentTitle()
                                )
                        )
                        .documentLanguage(
                                trimToNull(
                                        request.getDocumentLanguage()
                                )
                        )
                        .maxAltTextLength(
                                maxAltLength
                        )
                        .detectVisibleText(
                                request.isDetectVisibleText()
                        )
                        .generateDetailedDescription(
                                request
                                        .isGenerateDetailedDescription()
                        )
                        .classifyAccessibilityType(
                                request
                                        .isClassifyAccessibilityType()
                        )
                        .metadata(
                                "toolName",
                                TOOL_NAME
                        );

        if (!isBlank(
                request.getInstruction())) {

            builder.metadata(
                    "instruction",
                    request
                            .getInstruction()
                            .trim()
            );
        }

        return builder.build();
    }

    private Map<String, Object> createOutput(
            AnalyzeImageRequest request,
            Path projectRoot,
            Path imagePath,
            ImageAnalysisResult result) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "imagePath",
                normalizeRelativePath(
                        projectRoot,
                        imagePath
                )
        );

        output.put(
                "fileName",
                imagePath
                        .getFileName()
                        .toString()
        );

        if (result.getAccessibilityType()
                != null) {

            output.put(
                    "accessibilityType",
                    result
                            .getAccessibilityType()
                            .getCode()
            );

            output.put(
                    "accessibilityTypeDisplayName",
                    result
                            .getAccessibilityType()
                            .getDisplayName()
            );

            output.put(
                    "altTextRequired",
                    result
                            .getAccessibilityType()
                            .isAltTextRequired()
            );

            output.put(
                    "emptyAltRecommended",
                    result
                            .getAccessibilityType()
                            .isEmptyAltRecommended()
            );

            output.put(
                    "detailedDescriptionRecommended",
                    result
                            .getAccessibilityType()
                            .isDetailedDescriptionRecommended()
            );
        }

        output.put(
                "altText",
                result.isDecorative()
                        ? ""
                        : result.getAltText()
        );

        output.put(
                "detailedDescription",
                result.getDetailedDescription()
        );

        output.put(
                "visibleText",
                result.getVisibleText()
        );

        output.put(
                "confidence",
                result.getConfidence()
        );

        output.put(
                "decorative",
                result.isDecorative()
        );

        output.put(
                "requiresDetailedDescription",
                result.requiresDetailedDescription()
        );

        output.put(
                "manualReviewRequired",
                result.isManualReviewRequired()
        );

        output.put(
                "applicable",
                result.isApplicable()
        );

        output.put(
                "warnings",
                result.getWarnings()
        );

        output.put(
                "model",
                result.getModel()
        );

        output.put(
                "metadata",
                result.getMetadata()
        );

        if (!isBlank(request.getPurpose())) {
            output.put(
                    "purpose",
                    request.getPurpose()
            );
        }

        return Collections.unmodifiableMap(
                output
        );
    }

    private String createSuccessMessage(
            ImageAnalysisResult result) {

        if (result.isManualReviewRequired()) {
            return "이미지 분석을 완료했습니다. "
                    + "사용자 검토가 필요합니다.";
        }

        if (result.isDecorative()) {
            return "이미지를 장식용 이미지로 분석했습니다.";
        }

        return "이미지 접근성 분석을 완료했습니다.";
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
                                issue(
                                        code,
                                        message
                                )
                        )
                )
                .build();
    }

    private ToolIssue issue(
            String code,
            String message) {

        return ToolIssue.builder()
                .code(code)
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .message(message)
                .build();
    }

    private String getContextString(
            ToolContext context,
            String key) {

        if (context == null
                || isBlank(key)) {

            return null;
        }

        try {
            String value =
                    context.getAttribute(
                            key,
                            String.class
                    );

            return trimToNull(value);

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeRelativePath(
            Path projectRoot,
            Path path) {

        if (path == null) {
            return null;
        }

        Path normalized =
                path.toAbsolutePath()
                        .normalize();

        if (projectRoot != null) {

            Path normalizedRoot =
                    projectRoot
                            .toAbsolutePath()
                            .normalize();

            if (normalized.startsWith(
                    normalizedRoot)) {

                return normalizedRoot
                        .relativize(normalized)
                        .toString()
                        .replace('\\', '/');
            }
        }

        return normalized
                .toString()
                .replace('\\', '/');
    }

    private String readString(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private Integer readInteger(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(
                    String.valueOf(value)
                            .trim()
            );

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean readBoolean(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String text =
                String.valueOf(value)
                        .trim();

        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }

        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }

        return null;
    }

    private String firstNonBlank(
            String first,
            String second) {

        if (!isBlank(first)) {
            return first;
        }

        return second;
    }

    private Integer firstNonNull(
            Integer first,
            Integer second) {

        return first != null
                ? first
                : second;
    }

    private String defaultIfBlank(
            String value,
            String defaultValue) {

        return isBlank(value)
                ? defaultValue
                : value.trim();
    }

    private String trimToNull(
            String value) {

        return isBlank(value)
                ? null
                : value.trim();
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }

    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable
                        .getMessage()
                        .isBlank()) {

            return "알 수 없는 오류";
        }

        return throwable
                .getMessage()
                .trim();
    }

    /**
     * AnalyzeImageTool 입력 DTO.
     *
     * <p>
     * Tool 외부 계약을 유지하기 위해 Tool 내부 DTO로 둡니다.
     * 실제 Vision 분석 모델은 {@link ImageAnalysisRequest}를 사용합니다.
     * </p>
     */
    public static final class AnalyzeImageRequest {

        private String projectRoot;
        private String imagePath;

        private String language =
                DEFAULT_LANGUAGE;

        private String purpose;
        private String contextText;
        private String figureCaption;
        private String documentTitle;
        private String documentLanguage;
        private String instruction;

        private int maxAltLength =
                DEFAULT_MAX_ALT_TEXT_LENGTH;

        private boolean restrictToProject =
                true;

        private boolean detectVisibleText =
                true;

        private boolean generateDetailedDescription =
                true;

        private boolean classifyAccessibilityType =
                true;

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

        public String getLanguage() {
            return language;
        }

        public void setLanguage(
                String language) {

            this.language = language;
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

        public String getFigureCaption() {
            return figureCaption;
        }

        public void setFigureCaption(
                String figureCaption) {

            this.figureCaption =
                    figureCaption;
        }

        public String getDocumentTitle() {
            return documentTitle;
        }

        public void setDocumentTitle(
                String documentTitle) {

            this.documentTitle =
                    documentTitle;
        }

        public String getDocumentLanguage() {
            return documentLanguage;
        }

        public void setDocumentLanguage(
                String documentLanguage) {

            this.documentLanguage =
                    documentLanguage;
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

        public boolean isDetectVisibleText() {
            return detectVisibleText;
        }

        public void setDetectVisibleText(
                boolean detectVisibleText) {

            this.detectVisibleText =
                    detectVisibleText;
        }

        public boolean isGenerateDetailedDescription() {
            return generateDetailedDescription;
        }

        public void setGenerateDetailedDescription(
                boolean value) {

            this.generateDetailedDescription =
                    value;
        }

        public boolean isClassifyAccessibilityType() {
            return classifyAccessibilityType;
        }

        public void setClassifyAccessibilityType(
                boolean value) {

            this.classifyAccessibilityType =
                    value;
        }
    }
}