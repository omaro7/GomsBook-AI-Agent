/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.accessibility.model.ImageAccessibilityType;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisRequest;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAnalysisResult;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmAttachment;
import kr.co.goms.gomsbook.ai.llm.LlmAttachmentType;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmMessage;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.llm.LlmRole;

/**
 * Vision LLM을 사용하여 이미지 접근성 정보를 분석하는
 * {@link ImageAnalyzer} 구현체.
 *
 * <p>이미지의 접근성 유형, 대체 텍스트, 상세 설명,
 * 이미지 내부의 텍스트 및 분석 신뢰도를 생성한다.</p>
 *
 * <p>이 클래스는 이미지 분석만 수행하며 XHTML 파일을 수정하지 않는다.
 * 분석 결과의 실제 반영은 AltTextApplicator 또는 ApplyAltTextTool이
 * 담당한다.</p>
 */
public final class VisionImageAnalyzer implements ImageAnalyzer {

    public static final long DEFAULT_MAX_FILE_SIZE =
            20L * 1024L * 1024L;

    public static final double DEFAULT_TEMPERATURE = 0.1d;

    public static final int DEFAULT_MAX_CONTEXT_LENGTH = 4_000;

    private static final String SYSTEM_PROMPT =
            """
            You are an EPUB 3 accessibility specialist.

            Analyze the supplied image for use in an accessible EPUB publication.

            Follow these rules:

            1. Describe only information supported by the image and supplied context.
            2. Do not invent names, places, identities, emotions, dates, or events.
            3. Do not begin alternative text with phrases such as
               "image of", "picture of", or equivalent expressions.
            4. Classify the image using exactly one supported accessibility type.
            5. A decorative image conveys no meaningful information in its document context.
            6. Functional image alternative text must describe the action or purpose.
            7. Complex images may require a concise alternative text and a detailed description.
            8. Text visible in the image must be transcribed only when requested.
            9. If the image purpose cannot be determined reliably, use UNKNOWN.
            10. Return only one valid JSON object. Do not return Markdown or explanatory text.
            """;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "png",
            "jpg",
            "jpeg",
            "webp",
            "gif",
            "bmp"
    );

    private static final Map<String, String> EXTENSION_MIME_TYPES =
            createExtensionMimeTypes();

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;
    private final String visionModel;
    private final long maxFileSize;
    private final double temperature;
    private final int maxContextLength;

    public VisionImageAnalyzer(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            String visionModel) {

        this(
                llmClient,
                jsonMapper,
                visionModel,
                DEFAULT_MAX_FILE_SIZE,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_CONTEXT_LENGTH
        );
    }

    public VisionImageAnalyzer(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            String visionModel,
            long maxFileSize,
            double temperature,
            int maxContextLength) {

        this.llmClient = Objects.requireNonNull(
                llmClient,
                "llmClient must not be null"
        );

        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null"
        );

        if (visionModel == null || visionModel.isBlank()) {
            throw new IllegalArgumentException(
                    "visionModel must not be blank"
            );
        }

        if (maxFileSize <= 0L) {
            throw new IllegalArgumentException(
                    "maxFileSize must be greater than zero"
            );
        }

        if (Double.isNaN(temperature)
                || Double.isInfinite(temperature)
                || temperature < 0.0d
                || temperature > 2.0d) {

            throw new IllegalArgumentException(
                    "temperature must be between 0.0 and 2.0"
            );
        }

        if (maxContextLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContextLength must be greater than zero"
            );
        }

        this.visionModel = visionModel.trim();
        this.maxFileSize = maxFileSize;
        this.temperature = temperature;
        this.maxContextLength = maxContextLength;
    }

    @Override
    public ImageAnalysisResult analyze(
            ImageAnalysisRequest request)
            throws ImageAnalysisException {

        validateRequest(request);

        Path imagePath = request.getImagePath();

        validateImageFile(imagePath);

        String mimeType = determineMimeType(imagePath);

        LlmAttachment attachment = createAttachment(
                imagePath,
                mimeType
        );

        String prompt = createUserPrompt(request);

        LlmRequest llmRequest = createLlmRequest(
                prompt,
                attachment
        );

        LlmResponse llmResponse = callModel(
                imagePath,
                llmRequest
        );

        String responseText = extractResponseText(
                llmResponse
        );

        if (responseText == null || responseText.isBlank()) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.MODEL_RESPONSE_EMPTY,
                    imagePath,
                    visionModel,
                    "Vision model returned an empty response.",
                    null
            );
        }

        return parseResult(
                request,
                responseText
        );
    }

    @Override
    public boolean supports(
            ImageAnalysisRequest request) {

        if (request == null
                || request.getImagePath() == null) {

            return false;
        }

        String extension = getExtension(
                request.getImagePath()
        );

        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public String getName() {
        return "VisionImageAnalyzer[" + visionModel + "]";
    }

    /**
     * Vision 모델명을 반환한다.
     *
     * @return Vision 모델명
     */
    public String getVisionModel() {
        return visionModel;
    }

    /**
     * 허용하는 최대 이미지 파일 크기를 반환한다.
     *
     * @return 최대 파일 크기
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    private void validateRequest(
            ImageAnalysisRequest request) {

        if (request == null) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.INVALID_REQUEST,
                    "Image analysis request must not be null."
            );
        }

        if (!request.isProjectImage()) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.INVALID_REQUEST,
                    request.getImagePath(),
                    "Image must be located inside the current project."
            );
        }

        if (!supports(request)) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.UNSUPPORTED_IMAGE_TYPE,
                    request.getImagePath(),
                    "Unsupported image type: "
                            + getExtension(request.getImagePath())
            );
        }
    }

    private void validateImageFile(
            Path imagePath) {

        if (!Files.exists(imagePath)) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.IMAGE_NOT_FOUND,
                    imagePath,
                    "Image file does not exist."
            );
        }

        if (!Files.isRegularFile(imagePath)) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.INVALID_REQUEST,
                    imagePath,
                    "Image path is not a regular file."
            );
        }

        if (!Files.isReadable(imagePath)) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.IMAGE_READ_FAILED,
                    imagePath,
                    "Image file is not readable."
            );
        }

        try {
            long fileSize = Files.size(imagePath);

            if (fileSize <= 0L) {
                throw new ImageAnalysisException(
                        ImageAnalysisErrorCode.IMAGE_READ_FAILED,
                        imagePath,
                        "Image file is empty."
                );
            }

            if (fileSize > maxFileSize) {
                throw new ImageAnalysisException(
                        ImageAnalysisErrorCode.IMAGE_TOO_LARGE,
                        imagePath,
                        "Image file exceeds maximum size: "
                                + fileSize
                                + " > "
                                + maxFileSize
                );
            }

        } catch (IOException exception) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.IMAGE_READ_FAILED,
                    imagePath,
                    visionModel,
                    "Failed to inspect image file.",
                    exception
            );
        }
    }

    private String determineMimeType(
            Path imagePath) {

        try {
            String detectedMimeType =
                    Files.probeContentType(imagePath);

            if (detectedMimeType != null
                    && detectedMimeType
                            .toLowerCase(Locale.ROOT)
                            .startsWith("image/")) {

                return detectedMimeType
                        .toLowerCase(Locale.ROOT);
            }

        } catch (IOException ignored) {
            /*
             * 운영체제 MIME 탐지에 실패하면 확장자 기반으로 판단한다.
             */
        }

        String extension = getExtension(imagePath);

        String mimeType =
                EXTENSION_MIME_TYPES.get(extension);

        if (mimeType == null) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.UNSUPPORTED_IMAGE_TYPE,
                    imagePath,
                    "Unable to determine image MIME type."
            );
        }

        return mimeType;
    }

    private LlmAttachment createAttachment(
            Path imagePath,
            String mimeType) {

        try {

            return new LlmAttachment(
                    LlmAttachmentType.IMAGE,
                    imagePath,
                    mimeType,
                    imagePath
                            .getFileName()
                            .toString(),
                    Map.of()
            );

        } catch (RuntimeException exception) {

            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.ANALYSIS_FAILED,
                    imagePath,
                    visionModel,
                    "Failed to create image attachment.",
                    exception
            );
        }
    }

    private LlmRequest createLlmRequest(
            String prompt,
            LlmAttachment attachment) {

        try {

            LlmMessage systemMessage =
                    LlmMessage.system(
                            SYSTEM_PROMPT
                    );

            LlmMessage userMessage =
                    LlmMessage.user(
                            prompt
                    )
                    .withAttachment(
                            attachment
                    );

            return LlmRequest.builder()
                    .model(
                            visionModel
                    )
                    .message(
                            systemMessage
                    )
                    .message(
                            userMessage
                    )
                    .temperature(
                            temperature
                    )
                    .stream(false)
                    .build();

        } catch (RuntimeException exception) {

            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.PROMPT_BUILD_FAILED,
                    "Failed to build vision model request.",
                    exception
            );
        }
    }

    private LlmResponse callModel(
            Path imagePath,
            LlmRequest request) {

        try {
            LlmResponse response =
                    llmClient.chat(request);

            if (response == null) {
                throw new ImageAnalysisException(
                        ImageAnalysisErrorCode.MODEL_RESPONSE_EMPTY,
                        imagePath,
                        visionModel,
                        "Vision model returned null.",
                        null
                );
            }

            return response;

        } catch (ImageAnalysisException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.ANALYSIS_FAILED,
                    imagePath,
                    visionModel,
                    "Vision model invocation failed.",
                    exception
            );
        }
    }

    private String extractResponseText(
            LlmResponse response) {

        if (response == null) {
            return null;
        }

        String content =
                response.getContent();

        if (content == null
                || content.isBlank()) {

            return null;
        }

        return content.trim();
    }

    private ImageAnalysisResult parseResult(
            ImageAnalysisRequest request,
            String responseText) {

        String json = extractJsonObject(responseText);

        VisionAnalysisResponse response;

        try {
            response = jsonMapper.fromJson(
                    json,
                    VisionAnalysisResponse.class
            );

        } catch (RuntimeException exception) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.RESPONSE_PARSE_FAILED,
                    request.getImagePath(),
                    visionModel,
                    "Failed to parse vision model response.",
                    exception
            );
        }

        if (response == null) {
            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.MODEL_RESPONSE_INVALID,
                    request.getImagePath(),
                    visionModel,
                    "Vision model response could not be parsed.",
                    null
            );
        }

        return createAnalysisResult(
                request,
                response,
                responseText
        );
    }

    private ImageAnalysisResult createAnalysisResult(
            ImageAnalysisRequest request,
            VisionAnalysisResponse response,
            String rawResponse) {

        ImageAccessibilityType accessibilityType =
                ImageAccessibilityType.fromValueOrUnknown(
                        response.accessibilityType
                );

        String altText = normalizeOptionalText(
                response.altText
        );

        String detailedDescription =
                normalizeOptionalText(
                        response.detailedDescription
                );

        String visibleText = normalizeVisibleText(
                response.visibleText
        );

        double confidence = normalizeConfidence(
                response.confidence
        );

        List<String> warnings =
                normalizeWarnings(response.warnings);

        boolean manualReviewRequired =
                response.manualReviewRequired
                        || accessibilityType
                                == ImageAccessibilityType.UNKNOWN
                        || confidence < 0.5d;

        if (accessibilityType.isDecorative()) {
            altText = null;
        }

        if (accessibilityType.isAltTextRequired()
                && altText == null) {

            manualReviewRequired = true;
            warnings.add(
                    "대체 텍스트가 필요한 이미지이지만 "
                            + "대체 텍스트가 생성되지 않았습니다."
            );
        }

        if (altText != null
                && altText.length()
                        > request.getMaxAltTextLength()) {

            altText = truncateAltText(
                    altText,
                    request.getMaxAltTextLength()
            );

            warnings.add(
                    "생성된 대체 텍스트가 최대 길이를 초과하여 "
                            + "자동으로 축약되었습니다."
            );
        }

        if (accessibilityType
                .isDetailedDescriptionRecommended()
                && detailedDescription == null) {

            manualReviewRequired = true;
            warnings.add(
                    "복합 이미지 유형이지만 상세 설명이 "
                            + "생성되지 않았습니다."
            );
        }

        ImageAnalysisResult.Builder builder =
                ImageAnalysisResult.builder()
                        .image(
                                request.getProjectRoot(),
                                request.getImagePath()
                        )
                        .accessibilityType(
                                accessibilityType
                        )
                        .altText(altText)
                        .detailedDescription(
                                detailedDescription
                        )
                        .visibleText(visibleText)
                        .confidence(confidence)
                        .manualReviewRequired(
                                manualReviewRequired
                        )
                        .warnings(warnings)
                        .metadata(request.getMetadata())
                        .model(visionModel)
                        .rawResponse(rawResponse);

        return builder.build();
    }

    private String createUserPrompt(
            ImageAnalysisRequest request) {

        StringBuilder prompt = new StringBuilder();

        prompt.append(
                "Analyze this image for EPUB accessibility."
        );
        prompt.append('\n');
        prompt.append('\n');

        prompt.append("Output language: ");
        prompt.append(
                normalizeLanguage(
                        request.getLanguage()
                )
        );
        prompt.append('\n');

        prompt.append("Maximum alt text length: ");
        prompt.append(
                request.getMaxAltTextLength()
        );
        prompt.append(" characters");
        prompt.append('\n');

        prompt.append("Detect visible text: ");
        prompt.append(
                request.isDetectVisibleText()
        );
        prompt.append('\n');

        prompt.append("Generate detailed description: ");
        prompt.append(
                request.isGenerateDetailedDescription()
        );
        prompt.append('\n');

        prompt.append("Classify accessibility type: ");
        prompt.append(
                request.isClassifyAccessibilityType()
        );
        prompt.append('\n');

        appendContext(
                prompt,
                "Document title",
                request.getDocumentTitle()
        );

        appendContext(
                prompt,
                "Document language",
                request.getDocumentLanguage()
        );

        appendContext(
                prompt,
                "Image purpose",
                request.getPurpose()
        );

        appendContext(
                prompt,
                "Figure caption",
                request.getFigureCaption()
        );

        appendContext(
                prompt,
                "Surrounding document text",
                request.getSurroundingText()
        );

        prompt.append('\n');
        prompt.append(
                "Use exactly one accessibilityType value from:"
        );
        prompt.append('\n');

        for (ImageAccessibilityType type
                : ImageAccessibilityType.values()) {

            prompt.append("- ");
            prompt.append(type.getCode());
            prompt.append('\n');
        }

        prompt.append('\n');
        prompt.append(
                "Return only a JSON object with this structure:"
        );
        prompt.append('\n');

        prompt.append(
                """
                {
                  "accessibilityType": "informative",
                  "altText": "concise alternative text or null",
                  "detailedDescription": "detailed description or null",
                  "visibleText": "visible text or null",
                  "confidence": 0.0,
                  "manualReviewRequired": false,
                  "warnings": []
                }
                """
        );

        prompt.append('\n');
        prompt.append("Additional requirements:");
        prompt.append('\n');
        prompt.append(
                "- accessibilityType must use one of the exact codes above."
        );
        prompt.append('\n');
        prompt.append(
                "- For decorative images, altText must be null."
        );
        prompt.append('\n');
        prompt.append(
                "- For functional images, altText must describe the action."
        );
        prompt.append('\n');
        prompt.append(
                "- For UNKNOWN classification, manualReviewRequired must be true."
        );
        prompt.append('\n');

        if (!request.isDetectVisibleText()) {
            prompt.append(
                    "- visibleText must be null."
            );
            prompt.append('\n');
        }

        if (!request.isGenerateDetailedDescription()) {
            prompt.append(
                    "- detailedDescription must be null."
            );
            prompt.append('\n');
        }

        return prompt.toString();
    }

    private void appendContext(
            StringBuilder prompt,
            String label,
            String value) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return;
        }

        prompt.append('\n');
        prompt.append(label);
        prompt.append(':');
        prompt.append('\n');
        prompt.append(
                truncateContext(normalized)
        );
        prompt.append('\n');
    }

    private String truncateContext(
            String value) {

        if (value.length() <= maxContextLength) {
            return value;
        }

        return value.substring(
                0,
                maxContextLength
        );
    }

    private String extractJsonObject(
            String responseText) {

        String normalized = responseText.trim();

        if (normalized.startsWith("```")) {
            normalized = removeCodeFence(normalized);
        }

        int startIndex = normalized.indexOf('{');
        int endIndex = normalized.lastIndexOf('}');

        if (startIndex < 0
                || endIndex < startIndex) {

            throw new ImageAnalysisException(
                    ImageAnalysisErrorCode.MODEL_RESPONSE_INVALID,
                    "Vision model response does not contain a JSON object."
            );
        }

        return normalized.substring(
                startIndex,
                endIndex + 1
        );
    }

    private String removeCodeFence(
            String value) {

        String normalized = value.trim();

        if (normalized.startsWith("```json")) {
            normalized =
                    normalized.substring(7).trim();

        } else if (normalized.startsWith("```")) {
            normalized =
                    normalized.substring(3).trim();
        }

        if (normalized.endsWith("```")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 3
                    ).trim();
        }

        return normalized;
    }

    private String truncateAltText(
            String altText,
            int maximumLength) {

        if (altText.length() <= maximumLength) {
            return altText;
        }

        String truncated =
                altText.substring(0, maximumLength)
                        .trim();

        int lastSpaceIndex =
                truncated.lastIndexOf(' ');

        if (lastSpaceIndex
                >= maximumLength / 2) {

            truncated =
                    truncated.substring(
                            0,
                            lastSpaceIndex
                    ).trim();
        }

        return truncated;
    }

    private String normalizeVisibleText(
            String value) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        if ("none".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized)
                || "없음".equals(normalized)) {

            return null;
        }

        return normalized;
    }

    private double normalizeConfidence(
            Double value) {

        if (value == null
                || Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0.0d;
        }

        if (value < 0.0d) {
            return 0.0d;
        }

        if (value > 1.0d) {
            return 1.0d;
        }

        return value;
    }

    private List<String> normalizeWarnings(
            List<String> source) {

        List<String> result =
                new ArrayList<>();

        if (source == null) {
            return result;
        }

        for (String warning : source) {
            String normalized =
                    normalizeOptionalText(warning);

            if (normalized != null
                    && !result.contains(normalized)) {

                result.add(normalized);
            }
        }

        return result;
    }

    private String normalizeLanguage(
            String language) {

        if (language == null || language.isBlank()) {
            return "Korean";
        }

        String normalized =
                language.trim()
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "ko", "kor", "korean" -> "Korean";
            case "en", "eng", "english" -> "English";
            case "ja", "jpn", "japanese" -> "Japanese";
            case "zh", "zho", "chinese" -> "Chinese";
            default -> language.trim();
        };
    }

    private String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private String getExtension(
            Path path) {

        if (path == null
                || path.getFileName() == null) {

            return "";
        }

        String fileName =
                path.getFileName()
                        .toString();

        int dotIndex =
                fileName.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex + 1
                        >= fileName.length()) {

            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private static Map<String, String>
            createExtensionMimeTypes() {

        Map<String, String> result =
                new LinkedHashMap<>();

        result.put("png", "image/png");
        result.put("jpg", "image/jpeg");
        result.put("jpeg", "image/jpeg");
        result.put("webp", "image/webp");
        result.put("gif", "image/gif");
        result.put("bmp", "image/bmp");

        return Collections.unmodifiableMap(result);
    }

    /**
     * Vision 모델 JSON 응답 매핑용 내부 DTO.
     */
    private static final class VisionAnalysisResponse {

        private String accessibilityType;
        private String altText;
        private String detailedDescription;
        private String visibleText;
        private Double confidence;
        private boolean manualReviewRequired;
        private List<String> warnings;

        @SuppressWarnings("unused")
        public VisionAnalysisResponse() {
        }
    }
}