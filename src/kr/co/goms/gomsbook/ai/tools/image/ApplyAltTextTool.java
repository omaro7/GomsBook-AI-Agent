/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.image;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * 이미지 분석 결과의 대체 텍스트를 XHTML img 요소에 적용하는 Tool.
 *
 * <p>대상 이미지 검색 우선순위:</p>
 * <ol>
 *     <li>img 요소의 id</li>
 *     <li>img 요소의 src</li>
 *     <li>문서 내 img 요소 순번</li>
 * </ol>
 *
 * <p>장식 이미지인 경우 다음 속성을 적용한다.</p>
 * <pre>
 * alt=""
 * role="presentation"
 * aria-hidden="true"
 * </pre>
 */
public final class ApplyAltTextTool implements AgentTool {

    public static final String TOOL_NAME = "apply_alt_text";

    private static final String TOOL_DESCRIPTION =
            "이미지 분석 결과의 대체 텍스트를 XHTML img 요소에 적용합니다.";

    private static final String XHTML_EXTENSION = ".xhtml";
    private static final String BACKUP_EXTENSION = ".bak";

    private final JsonMapper jsonMapper;

    public ApplyAltTextTool(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null");
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
            ApplyAltTextRequest request =
                    parseRequest(toolRequest);

            List<ToolIssue> validationIssues =
                    validateRequest(request);

            if (!validationIssues.isEmpty()) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message("대체 텍스트 적용 요청이 올바르지 않습니다.")
                        .issues(validationIssues)
                        .build();
            }

            Path projectRoot =
                    resolveProjectRoot(
                            request,
                            toolContext);

            Path xhtmlPath =
                    resolveXhtmlPath(
                            request,
                            projectRoot);

            validateXhtmlFile(xhtmlPath);

            Document document =
                    parseXhtml(xhtmlPath);

            ImageElementMatch imageMatch =
                    findTargetImage(
                            document,
                            request);

            if (imageMatch == null) {
                return failure(
                        ToolStatus.FAILED,
                        "IMAGE_ELEMENT_NOT_FOUND",
                        createImageNotFoundMessage(request));
            }

            Element imageElement =
                    imageMatch.getElement();

            String previousAlt =
                    imageElement.hasAttribute("alt")
                            ? imageElement.getAttribute("alt")
                            : null;

            applyAltText(
                    imageElement,
                    request);

            Path backupPath = null;

            if (request.isCreateBackup()) {
                backupPath =
                        createBackup(xhtmlPath);
            }

            String serializedXhtml =
                    serializeXhtml(document);

            writeXhtml(
                    xhtmlPath,
                    serializedXhtml);

            ApplyAltTextResponse response =
                    createResponse(
                            projectRoot,
                            xhtmlPath,
                            backupPath,
                            imageMatch,
                            previousAlt,
                            request);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put(
                    "response",
                    response);
            
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message("이미지 대체 텍스트를 XHTML에 적용했습니다.")
                    .data(data)
                    .build();

        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_XHTML_PATH",
                    "유효하지 않은 XHTML 경로입니다: "
                            + exception.getInput());

        } catch (ParserConfigurationException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XML_PARSER_CONFIGURATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (SAXException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_XHTML_DOCUMENT",
                    "XHTML 문서를 XML로 해석할 수 없습니다: "
                            + buildExceptionMessage(exception));

        } catch (TransformerException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_SERIALIZATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (IOException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_FILE_OPERATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_PATH_ACCESS_DENIED",
                    buildExceptionMessage(exception));

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_ALT_TEXT_REQUEST",
                    buildExceptionMessage(exception));

        } catch (Exception exception) {
            return failure(
                    ToolStatus.FAILED,
                    "ALT_TEXT_APPLY_FAILED",
                    buildExceptionMessage(exception));
        }
    }

    private ApplyAltTextRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "대체 텍스트 적용 인자가 없습니다.");
        }

        if (arguments instanceof ApplyAltTextRequest) {
            return (ApplyAltTextRequest) arguments;
        }

        String json = jsonMapper.toJson(arguments);

        ApplyAltTextRequest request =
                jsonMapper.fromJson(
                        json,
                        ApplyAltTextRequest.class);

        if (request == null) {
            throw new IllegalArgumentException(
                    "대체 텍스트 적용 요청을 변환할 수 없습니다.");
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            ApplyAltTextRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "대체 텍스트 적용 요청이 없습니다."));
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(request.getXhtmlPath())) {
            issues.add(
                    issue(
                            "XHTML_PATH_REQUIRED",
                            "대상 XHTML 경로가 필요합니다."));
        }

        boolean hasSelector =
                !isBlank(request.getImageElementId())
                        || !isBlank(request.getImageSrc())
                        || request.getImageIndex() >= 0;

        if (!hasSelector) {
            issues.add(
                    issue(
                            "IMAGE_SELECTOR_REQUIRED",
                            "이미지를 찾기 위한 id, src 또는 imageIndex가 필요합니다."));
        }

        if (!request.isDecorative()
                && isBlank(request.getAltText())) {

            issues.add(
                    issue(
                            "ALT_TEXT_REQUIRED",
                            "비장식 이미지에는 대체 텍스트가 필요합니다."));
        }

        if (request.getMaxAltLength() < 0) {
            issues.add(
                    issue(
                            "INVALID_MAX_ALT_LENGTH",
                            "대체 텍스트 최대 길이는 0 이상이어야 합니다."));
        }

        if (!request.isDecorative()
                && request.getMaxAltLength() > 0
                && request.getAltText() != null
                && request.getAltText().trim().length()
                        > request.getMaxAltLength()
                && !request.isTruncateAltText()) {

            issues.add(
                    issue(
                            "ALT_TEXT_TOO_LONG",
                            "대체 텍스트가 최대 길이를 초과했습니다."));
        }

        return issues;
    }

    private Path resolveProjectRoot(
            ApplyAltTextRequest request,
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

    private Path resolveXhtmlPath(
            ApplyAltTextRequest request,
            Path projectRoot) {

        Path requestedPath =
                Path.of(request.getXhtmlPath().trim());

        Path xhtmlPath;

        if (requestedPath.isAbsolute()) {
            xhtmlPath =
                    requestedPath.toAbsolutePath()
                            .normalize();

            if (projectRoot != null
                    && request.isRestrictToProject()
                    && !xhtmlPath.startsWith(projectRoot)) {

                throw new SecurityException(
                        "프로젝트 루트 외부 XHTML은 수정할 수 없습니다.");
            }

        } else {
            if (projectRoot == null) {
                throw new IllegalArgumentException(
                        "상대 XHTML 경로를 사용하려면 프로젝트 루트가 필요합니다.");
            }

            xhtmlPath =
                    projectRoot.resolve(requestedPath)
                            .toAbsolutePath()
                            .normalize();

            if (!xhtmlPath.startsWith(projectRoot)) {
                throw new SecurityException(
                        "프로젝트 루트 외부 XHTML은 수정할 수 없습니다.");
            }
        }

        return xhtmlPath;
    }

    private void validateXhtmlFile(
            Path xhtmlPath) {

        if (!Files.exists(xhtmlPath)) {
            throw new IllegalArgumentException(
                    "XHTML 파일이 존재하지 않습니다: "
                            + xhtmlPath);
        }

        if (!Files.isRegularFile(xhtmlPath)) {
            throw new IllegalArgumentException(
                    "XHTML 경로가 일반 파일이 아닙니다: "
                            + xhtmlPath);
        }

        if (!Files.isReadable(xhtmlPath)) {
            throw new IllegalArgumentException(
                    "XHTML 파일을 읽을 수 없습니다: "
                            + xhtmlPath);
        }

        if (!Files.isWritable(xhtmlPath)) {
            throw new IllegalArgumentException(
                    "XHTML 파일을 수정할 수 없습니다: "
                            + xhtmlPath);
        }

        String fileName =
                xhtmlPath.getFileName()
                        .toString()
                        .toLowerCase();

        if (!fileName.endsWith(XHTML_EXTENSION)) {
            throw new IllegalArgumentException(
                    "대상 파일의 확장자는 .xhtml이어야 합니다.");
        }
    }

    /**
     * 외부 엔티티와 외부 DTD 접근을 차단한 XML 파서를 생성한다.
     */
    private Document parseXhtml(
            Path xhtmlPath)
            throws ParserConfigurationException,
            IOException,
            SAXException {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        setFeature(
                factory,
                "http://apache.org/xml/features/disallow-doctype-decl",
                false);

        setFeature(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false);

        setFeature(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false);

        setFeature(
                factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    "");
        } catch (IllegalArgumentException ignored) {
            // 사용 중인 XML 구현체가 지원하지 않을 수 있다.
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "");
        } catch (IllegalArgumentException ignored) {
            // 사용 중인 XML 구현체가 지원하지 않을 수 있다.
        }

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(
                xhtmlPath.toFile());
    }

    private void setFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean enabled) {

        try {
            factory.setFeature(
                    feature,
                    enabled);
        } catch (ParserConfigurationException ignored) {
            /*
             * XML 구현체별 지원 기능이 다를 수 있다.
             * 지원되는 보안 설정은 계속 적용한다.
             */
        }
    }

    private ImageElementMatch findTargetImage(
            Document document,
            ApplyAltTextRequest request) {

        NodeList imageNodes =
                document.getElementsByTagNameNS(
                        "*",
                        "img");

        if (imageNodes.getLength() == 0) {
            imageNodes =
                    document.getElementsByTagName("img");
        }

        if (!isBlank(request.getImageElementId())) {
            ImageElementMatch match =
                    findByElementId(
                            imageNodes,
                            request.getImageElementId());

            if (match != null) {
                return match;
            }
        }

        if (!isBlank(request.getImageSrc())) {
            ImageElementMatch match =
                    findBySource(
                            imageNodes,
                            request.getImageSrc(),
                            request.isMatchSourceFileNameOnly());

            if (match != null) {
                return match;
            }
        }

        if (request.getImageIndex() >= 0
                && request.getImageIndex()
                        < imageNodes.getLength()) {

            Element element =
                    (Element) imageNodes.item(
                            request.getImageIndex());

            return new ImageElementMatch(
                    element,
                    request.getImageIndex(),
                    "index");
        }

        return null;
    }

    private ImageElementMatch findByElementId(
            NodeList imageNodes,
            String imageElementId) {

        String expectedId =
                imageElementId.trim();

        for (int index = 0;
                index < imageNodes.getLength();
                index++) {

            Element imageElement =
                    (Element) imageNodes.item(index);

            if (expectedId.equals(
                    imageElement.getAttribute("id"))) {

                return new ImageElementMatch(
                        imageElement,
                        index,
                        "id");
            }
        }

        return null;
    }

    private ImageElementMatch findBySource(
            NodeList imageNodes,
            String imageSrc,
            boolean fileNameOnly) {

        String expectedSource =
                normalizePathValue(imageSrc);

        String expectedFileName =
                extractFileName(expectedSource);

        for (int index = 0;
                index < imageNodes.getLength();
                index++) {

            Element imageElement =
                    (Element) imageNodes.item(index);

            String currentSource =
                    normalizePathValue(
                            imageElement.getAttribute("src"));

            boolean matched;

            if (fileNameOnly) {
                matched =
                        expectedFileName.equals(
                                extractFileName(currentSource));
            } else {
                matched =
                        expectedSource.equals(currentSource);
            }

            if (matched) {
                return new ImageElementMatch(
                        imageElement,
                        index,
                        "src");
            }
        }

        return null;
    }

    private void applyAltText(
            Element imageElement,
            ApplyAltTextRequest request) {

        if (request.isDecorative()) {
            applyDecorativeAttributes(
                    imageElement,
                    request);

            return;
        }

        String altText =
                normalizeAltText(
                        request.getAltText(),
                        request.getMaxAltLength(),
                        request.isTruncateAltText());

        imageElement.setAttribute(
                "alt",
                altText);

        /*
         * 이전에 장식 이미지로 설정했던 속성만 제거한다.
         */
        if ("presentation".equalsIgnoreCase(
                imageElement.getAttribute("role"))) {

            imageElement.removeAttribute("role");
        }

        if ("true".equalsIgnoreCase(
                imageElement.getAttribute("aria-hidden"))) {

            imageElement.removeAttribute("aria-hidden");
        }

        if (request.isApplyAriaLabel()) {
            imageElement.setAttribute(
                    "aria-label",
                    altText);
        } else {
            imageElement.removeAttribute(
                    "aria-label");
        }

        if (request.isApplyTitleAttribute()) {
            imageElement.setAttribute(
                    "title",
                    altText);
        }
    }

    private void applyDecorativeAttributes(
            Element imageElement,
            ApplyAltTextRequest request) {

        imageElement.setAttribute(
                "alt",
                "");

        imageElement.removeAttribute(
                "aria-label");

        if (request.isApplyDecorativeAttributes()) {
            imageElement.setAttribute(
                    "role",
                    "presentation");

            imageElement.setAttribute(
                    "aria-hidden",
                    "true");
        }
    }

    private String normalizeAltText(
            String altText,
            int maxLength,
            boolean truncate) {

        String normalized =
                altText == null
                        ? ""
                        : altText.trim()
                                .replaceAll("\\s+", " ");

        if (maxLength <= 0
                || normalized.length() <= maxLength) {

            return normalized;
        }

        if (!truncate) {
            throw new IllegalArgumentException(
                    "대체 텍스트가 최대 길이 "
                            + maxLength
                            + "자를 초과했습니다.");
        }

        return normalized.substring(
                0,
                maxLength);
    }

    private Path createBackup(
            Path xhtmlPath)
            throws IOException {

        Path backupPath =
                xhtmlPath.resolveSibling(
                        xhtmlPath.getFileName().toString()
                                + BACKUP_EXTENSION);

        Files.copy(
                xhtmlPath,
                backupPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        return backupPath;
    }

    private String serializeXhtml(
            Document document)
            throws TransformerException {

        TransformerFactory transformerFactory =
                TransformerFactory.newInstance();

        try {
            transformerFactory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    "");
        } catch (IllegalArgumentException ignored) {
            // Transformer 구현체가 지원하지 않을 수 있다.
        }

        try {
            transformerFactory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                    "");
        } catch (IllegalArgumentException ignored) {
            // Transformer 구현체가 지원하지 않을 수 있다.
        }

        Transformer transformer =
                transformerFactory.newTransformer();

        transformer.setOutputProperty(
                OutputKeys.METHOD,
                "xml");

        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name());

        transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION,
                "yes");

        transformer.setOutputProperty(
                OutputKeys.INDENT,
                "yes");

        try {
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount",
                    "2");
        } catch (IllegalArgumentException ignored) {
            // 구현체별 들여쓰기 설정이다.
        }

        StringWriter writer =
                new StringWriter();

        transformer.transform(
                new DOMSource(document),
                new StreamResult(writer));

        String result =
                writer.toString()
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .trim();

        if (!containsIgnoreCase(
                result,
                "<!DOCTYPE html>")) {

            result =
                    "<!DOCTYPE html>\n"
                            + result;
        }

        return result + "\n";
    }

    private void writeXhtml(
            Path xhtmlPath,
            String xhtml)
            throws IOException {

        Files.writeString(
                xhtmlPath,
                xhtml,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private ApplyAltTextResponse createResponse(
            Path projectRoot,
            Path xhtmlPath,
            Path backupPath,
            ImageElementMatch imageMatch,
            String previousAlt,
            ApplyAltTextRequest request)
            throws IOException {

        Element element =
                imageMatch.getElement();

        ApplyAltTextResponse response =
                new ApplyAltTextResponse();

        response.setApplied(true);
        response.setXhtmlFileName(
                xhtmlPath.getFileName()
                        .toString());

        response.setAbsolutePath(
                xhtmlPath.toString());

        if (projectRoot != null
                && xhtmlPath.startsWith(projectRoot)) {

            response.setRelativePath(
                    normalizeSeparator(
                            projectRoot.relativize(xhtmlPath)
                                    .toString()));
        }

        response.setImageElementId(
                emptyToNull(
                        element.getAttribute("id")));

        response.setImageSrc(
                emptyToNull(
                        element.getAttribute("src")));

        response.setImageIndex(
                imageMatch.getImageIndex());

        response.setMatchedBy(
                imageMatch.getMatchedBy());

        response.setPreviousAlt(
                previousAlt);

        response.setAppliedAlt(
                element.getAttribute("alt"));

        response.setDecorative(
                request.isDecorative());

        response.setBackupCreated(
                backupPath != null);

        if (backupPath != null) {
            response.setBackupPath(
                    backupPath.toString());
        }

        response.setFileSize(
                Files.size(xhtmlPath));

        response.setCharset(
                StandardCharsets.UTF_8.name());

        return response;
    }

    private String createImageNotFoundMessage(
            ApplyAltTextRequest request) {

        StringBuilder builder =
                new StringBuilder(
                        "대상 img 요소를 찾을 수 없습니다.");

        if (!isBlank(request.getImageElementId())) {
            builder.append(" id=")
                    .append(request.getImageElementId());
        }

        if (!isBlank(request.getImageSrc())) {
            builder.append(" src=")
                    .append(request.getImageSrc());
        }

        if (request.getImageIndex() >= 0) {
            builder.append(" index=")
                    .append(request.getImageIndex());
        }

        return builder.toString();
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

    private String normalizePathValue(
            String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .replace('\\', '/');
    }

    private String extractFileName(
            String path) {

        String normalized =
                normalizePathValue(path);

        int slashIndex =
                normalized.lastIndexOf('/');

        if (slashIndex < 0) {
            return normalized;
        }

        return normalized.substring(
                slashIndex + 1);
    }

    private String normalizeSeparator(
            String path) {

        return path.replace('\\', '/');
    }

    private boolean containsIgnoreCase(
            String source,
            String target) {

        if (source == null
                || target == null) {

            return false;
        }

        return source.toLowerCase()
                .contains(
                        target.toLowerCase());
    }

    private String emptyToNull(
            String value) {

        return isBlank(value)
                ? null
                : value;
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

    private static final class ImageElementMatch {

        private final Element element;
        private final int imageIndex;
        private final String matchedBy;

        private ImageElementMatch(
                Element element,
                int imageIndex,
                String matchedBy) {

            this.element =
                    Objects.requireNonNull(element);

            this.imageIndex = imageIndex;
            this.matchedBy = matchedBy;
        }

        public Element getElement() {
            return element;
        }

        public int getImageIndex() {
            return imageIndex;
        }

        public String getMatchedBy() {
            return matchedBy;
        }
    }

    /**
     * 대체 텍스트 적용 요청 DTO.
     *
     * 프로젝트 구조에 따라 별도 파일로 분리할 수 있다.
     */
    public static final class ApplyAltTextRequest {

        private String projectRoot;
        private String xhtmlPath;

        private String imageElementId;
        private String imageSrc;
        private int imageIndex = -1;

        private String altText;
        private boolean decorative;

        private int maxAltLength = 100;
        private boolean truncateAltText = true;

        private boolean createBackup = true;
        private boolean restrictToProject = true;
        private boolean matchSourceFileNameOnly;

        private boolean applyDecorativeAttributes = true;
        private boolean applyAriaLabel;
        private boolean applyTitleAttribute;

        public ApplyAltTextRequest() {
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(
                String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public String getXhtmlPath() {
            return xhtmlPath;
        }

        public void setXhtmlPath(
                String xhtmlPath) {
            this.xhtmlPath = xhtmlPath;
        }

        public String getImageElementId() {
            return imageElementId;
        }

        public void setImageElementId(
                String imageElementId) {
            this.imageElementId =
                    imageElementId;
        }

        public String getImageSrc() {
            return imageSrc;
        }

        public void setImageSrc(
                String imageSrc) {
            this.imageSrc = imageSrc;
        }

        public int getImageIndex() {
            return imageIndex;
        }

        public void setImageIndex(
                int imageIndex) {
            this.imageIndex = imageIndex;
        }

        public String getAltText() {
            return altText;
        }

        public void setAltText(
                String altText) {
            this.altText = altText;
        }

        public boolean isDecorative() {
            return decorative;
        }

        public void setDecorative(
                boolean decorative) {
            this.decorative = decorative;
        }

        public int getMaxAltLength() {
            return maxAltLength;
        }

        public void setMaxAltLength(
                int maxAltLength) {
            this.maxAltLength = maxAltLength;
        }

        public boolean isTruncateAltText() {
            return truncateAltText;
        }

        public void setTruncateAltText(
                boolean truncateAltText) {
            this.truncateAltText =
                    truncateAltText;
        }

        public boolean isCreateBackup() {
            return createBackup;
        }

        public void setCreateBackup(
                boolean createBackup) {
            this.createBackup = createBackup;
        }

        public boolean isRestrictToProject() {
            return restrictToProject;
        }

        public void setRestrictToProject(
                boolean restrictToProject) {
            this.restrictToProject =
                    restrictToProject;
        }

        public boolean isMatchSourceFileNameOnly() {
            return matchSourceFileNameOnly;
        }

        public void setMatchSourceFileNameOnly(
                boolean matchSourceFileNameOnly) {
            this.matchSourceFileNameOnly =
                    matchSourceFileNameOnly;
        }

        public boolean isApplyDecorativeAttributes() {
            return applyDecorativeAttributes;
        }

        public void setApplyDecorativeAttributes(
                boolean applyDecorativeAttributes) {
            this.applyDecorativeAttributes =
                    applyDecorativeAttributes;
        }

        public boolean isApplyAriaLabel() {
            return applyAriaLabel;
        }

        public void setApplyAriaLabel(
                boolean applyAriaLabel) {
            this.applyAriaLabel =
                    applyAriaLabel;
        }

        public boolean isApplyTitleAttribute() {
            return applyTitleAttribute;
        }

        public void setApplyTitleAttribute(
                boolean applyTitleAttribute) {
            this.applyTitleAttribute =
                    applyTitleAttribute;
        }
    }

    /**
     * 대체 텍스트 적용 결과 DTO.
     */
    public static final class ApplyAltTextResponse {

        private boolean applied;
        private boolean decorative;
        private boolean backupCreated;

        private String xhtmlFileName;
        private String relativePath;
        private String absolutePath;
        private String backupPath;

        private String imageElementId;
        private String imageSrc;
        private int imageIndex;
        private String matchedBy;

        private String previousAlt;
        private String appliedAlt;

        private String charset;
        private long fileSize;

        public ApplyAltTextResponse() {
        }

        public boolean isApplied() {
            return applied;
        }

        public void setApplied(
                boolean applied) {
            this.applied = applied;
        }

        public boolean isDecorative() {
            return decorative;
        }

        public void setDecorative(
                boolean decorative) {
            this.decorative = decorative;
        }

        public boolean isBackupCreated() {
            return backupCreated;
        }

        public void setBackupCreated(
                boolean backupCreated) {
            this.backupCreated =
                    backupCreated;
        }

        public String getXhtmlFileName() {
            return xhtmlFileName;
        }

        public void setXhtmlFileName(
                String xhtmlFileName) {
            this.xhtmlFileName =
                    xhtmlFileName;
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

        public String getBackupPath() {
            return backupPath;
        }

        public void setBackupPath(
                String backupPath) {
            this.backupPath = backupPath;
        }

        public String getImageElementId() {
            return imageElementId;
        }

        public void setImageElementId(
                String imageElementId) {
            this.imageElementId =
                    imageElementId;
        }

        public String getImageSrc() {
            return imageSrc;
        }

        public void setImageSrc(
                String imageSrc) {
            this.imageSrc = imageSrc;
        }

        public int getImageIndex() {
            return imageIndex;
        }

        public void setImageIndex(
                int imageIndex) {
            this.imageIndex = imageIndex;
        }

        public String getMatchedBy() {
            return matchedBy;
        }

        public void setMatchedBy(
                String matchedBy) {
            this.matchedBy = matchedBy;
        }

        public String getPreviousAlt() {
            return previousAlt;
        }

        public void setPreviousAlt(
                String previousAlt) {
            this.previousAlt = previousAlt;
        }

        public String getAppliedAlt() {
            return appliedAlt;
        }

        public void setAppliedAlt(
                String appliedAlt) {
            this.appliedAlt = appliedAlt;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(
                String charset) {
            this.charset = charset;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(
                long fileSize) {
            this.fileSize = fileSize;
        }
    }
}