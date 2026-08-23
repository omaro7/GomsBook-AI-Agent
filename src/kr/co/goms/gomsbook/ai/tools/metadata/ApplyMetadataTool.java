/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.metadata;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.w3c.dom.Node;
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
import kr.co.goms.gomsbook.ai.tools.metadata.GenerateMetadataTool.EpubMetadata;
import kr.co.goms.gomsbook.ai.tools.metadata.GenerateMetadataTool.MetadataCreator;

/**
 * 생성된 EPUB 메타데이터를 package.opf 파일에 적용하는 Tool.
 *
 * <p>기존 manifest, spine 및 기타 패키지 구조는 보존하고
 * metadata 요소만 갱신한다.</p>
 */
public final class ApplyMetadataTool implements AgentTool {

    public static final String TOOL_NAME =
            "apply_metadata";

    private static final String TOOL_DESCRIPTION =
            "생성된 EPUB 메타데이터를 package.opf 파일에 적용합니다.";

    private static final String OPF_NAMESPACE =
            "http://www.idpf.org/2007/opf";

    private static final String DC_NAMESPACE =
            "http://purl.org/dc/elements/1.1/";

    private static final String BACKUP_EXTENSION =
            ".bak";

    private static final String DEFAULT_IDENTIFIER_ID =
            "pub-id";

    private static final String DEFAULT_TITLE_ID =
            "title";

    private static final String DEFAULT_SUBTITLE_ID =
            "subtitle";

    private static final String CREATOR_ID_PREFIX =
            "creator";

    private final JsonMapper jsonMapper;

    public ApplyMetadataTool(
            JsonMapper jsonMapper) {

        this.jsonMapper =
                Objects.requireNonNull(
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
            ApplyMetadataRequest request =
                    parseRequest(toolRequest);

            List<ToolIssue> validationIssues =
                    validateRequest(request);

            if (!validationIssues.isEmpty()) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message("메타데이터 적용 요청이 올바르지 않습니다.")
                        .issues(validationIssues)
                        .build();
            }

            Path projectRoot =
                    resolveProjectRoot(
                            request,
                            toolContext);

            Path packagePath =
                    resolvePackagePath(
                            request,
                            projectRoot);

            validatePackageFile(packagePath);

            Document document =
                    parsePackageDocument(packagePath);

            Element packageElement =
                    validatePackageDocument(document);

            Element metadataElement =
                    findOrCreateMetadataElement(
                            document,
                            packageElement);

            MetadataSnapshot previousSnapshot =
                    createSnapshot(
                            metadataElement);

            applyMetadata(
                    document,
                    packageElement,
                    metadataElement,
                    request);

            Path backupPath = null;

            if (request.isCreateBackup()) {
                backupPath =
                        createBackup(packagePath);
            }

            String serializedPackage =
                    serializePackageDocument(document);

            writePackageDocument(
                    packagePath,
                    serializedPackage);

            ApplyMetadataResponse response =
                    createResponse(
                            projectRoot,
                            packagePath,
                            backupPath,
                            previousSnapshot,
                            request,
                            metadataElement);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put(
                    "applied",
                    response.isApplied());

            data.put(
                    "packageFileName",
                    response.getPackageFileName());

            data.put(
                    "relativePath",
                    response.getRelativePath());

            data.put(
                    "absolutePath",
                    response.getAbsolutePath());

            data.put(
                    "backupCreated",
                    response.isBackupCreated());

            data.put(
                    "backupPath",
                    response.getBackupPath());

            data.put(
                    "identifier",
                    response.getIdentifier());

            data.put(
                    "title",
                    response.getTitle());

            data.put(
                    "creatorCount",
                    response.getCreatorCount());

            data.put(
                    "subjectCount",
                    response.getSubjectCount());

            data.put(
                    "fileSize",
                    response.getFileSize());

            data.put(
                    "charset",
                    response.getCharset());


            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB 메타데이터를 package.opf에 적용했습니다.")
                    .data(data)
                    .build();
            
        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_PACKAGE_PATH",
                    "유효하지 않은 package.opf 경로입니다: "
                            + exception.getInput());

        } catch (ParserConfigurationException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XML_PARSER_CONFIGURATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (SAXException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_PACKAGE_DOCUMENT",
                    "package.opf를 XML 문서로 해석할 수 없습니다: "
                            + buildExceptionMessage(exception));

        } catch (TransformerException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "PACKAGE_SERIALIZATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (IOException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "PACKAGE_FILE_OPERATION_FAILED",
                    buildExceptionMessage(exception));

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "PACKAGE_PATH_ACCESS_DENIED",
                    buildExceptionMessage(exception));

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_METADATA_APPLY_REQUEST",
                    buildExceptionMessage(exception));

        } catch (Exception exception) {
            return failure(
                    ToolStatus.FAILED,
                    "METADATA_APPLY_FAILED",
                    buildExceptionMessage(exception));
        }
    }

    private ApplyMetadataRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "메타데이터 적용 인자가 없습니다.");
        }

        if (arguments instanceof ApplyMetadataRequest) {
            return (ApplyMetadataRequest) arguments;
        }

        String json =
                jsonMapper.toJson(arguments);

        ApplyMetadataRequest request =
                jsonMapper.fromJson(
                        json,
                        ApplyMetadataRequest.class);

        if (request == null) {
            throw new IllegalArgumentException(
                    "메타데이터 적용 요청을 변환할 수 없습니다.");
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            ApplyMetadataRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "메타데이터 적용 요청이 없습니다."));
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(request.getPackagePath())) {
            issues.add(
                    issue(
                            "PACKAGE_PATH_REQUIRED",
                            "package.opf 경로가 필요합니다."));
        }

        if (request.getMetadata() == null) {
            issues.add(
                    issue(
                            "METADATA_REQUIRED",
                            "적용할 EPUB 메타데이터가 없습니다."));

            return issues;
        }

        EpubMetadata metadata =
                request.getMetadata();

        if (isBlank(metadata.getIdentifier())) {
            issues.add(
                    issue(
                            "IDENTIFIER_REQUIRED",
                            "EPUB 식별자는 필수입니다."));
        }

        if (isBlank(metadata.getTitle())) {
            issues.add(
                    issue(
                            "TITLE_REQUIRED",
                            "EPUB 제목은 필수입니다."));
        }

        if (isBlank(metadata.getLanguage())) {
            issues.add(
                    issue(
                            "LANGUAGE_REQUIRED",
                            "EPUB 언어는 필수입니다."));
        }

        if (metadata.getCreators() == null
                || metadata.getCreators().isEmpty()) {

            issues.add(
                    issue(
                            "CREATOR_REQUIRED",
                            "EPUB 저자 정보는 필수입니다."));
        }

        if (!isBlank(metadata.getModified())
                && !isValidModifiedDate(
                        metadata.getModified())) {

            issues.add(
                    issue(
                            "INVALID_MODIFIED_DATE",
                            "수정일은 ISO-8601 형식이어야 합니다."));
        }

        return issues;
    }

    private Path resolveProjectRoot(
            ApplyMetadataRequest request,
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

    private Path resolvePackagePath(
            ApplyMetadataRequest request,
            Path projectRoot) {

        Path requestedPath =
                Path.of(request.getPackagePath().trim());

        Path packagePath;

        if (requestedPath.isAbsolute()) {
            packagePath =
                    requestedPath.toAbsolutePath()
                            .normalize();

            if (projectRoot != null
                    && request.isRestrictToProject()
                    && !packagePath.startsWith(projectRoot)) {

                throw new SecurityException(
                        "프로젝트 루트 외부 package.opf는 수정할 수 없습니다.");
            }

        } else {
            if (projectRoot == null) {
                throw new IllegalArgumentException(
                        "상대 경로를 사용하려면 프로젝트 루트가 필요합니다.");
            }

            packagePath =
                    projectRoot.resolve(requestedPath)
                            .toAbsolutePath()
                            .normalize();

            if (!packagePath.startsWith(projectRoot)) {
                throw new SecurityException(
                        "프로젝트 루트 외부 package.opf는 수정할 수 없습니다.");
            }
        }

        return packagePath;
    }

    private void validatePackageFile(
            Path packagePath) {

        if (!Files.exists(packagePath)) {
            throw new IllegalArgumentException(
                    "package.opf 파일이 존재하지 않습니다: "
                            + packagePath);
        }

        if (!Files.isRegularFile(packagePath)) {
            throw new IllegalArgumentException(
                    "package.opf 경로가 일반 파일이 아닙니다: "
                            + packagePath);
        }

        if (!Files.isReadable(packagePath)) {
            throw new IllegalArgumentException(
                    "package.opf 파일을 읽을 수 없습니다.");
        }

        if (!Files.isWritable(packagePath)) {
            throw new IllegalArgumentException(
                    "package.opf 파일을 수정할 수 없습니다.");
        }

        if (!packagePath.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(".opf")) {

            throw new IllegalArgumentException(
                    "대상 파일의 확장자는 .opf이어야 합니다.");
        }
    }

    private Document parsePackageDocument(
            Path packagePath)
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
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "");
        } catch (IllegalArgumentException ignored) {
        }

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(
                packagePath.toFile());
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
        }
    }

    private Element validatePackageDocument(
            Document document) {

        Element root =
                document.getDocumentElement();

        if (root == null
                || !"package".equals(
                        root.getLocalName() != null
                                ? root.getLocalName()
                                : root.getNodeName())) {

            throw new IllegalArgumentException(
                    "package.opf의 루트 요소가 package가 아닙니다.");
        }

        String version =
                root.getAttribute("version");

        if (isBlank(version)) {
            root.setAttribute(
                    "version",
                    "3.0");
        }

        return root;
    }

    private Element findOrCreateMetadataElement(
            Document document,
            Element packageElement) {

        NodeList metadataNodes =
                packageElement.getElementsByTagNameNS(
                        OPF_NAMESPACE,
                        "metadata");

        if (metadataNodes.getLength() > 0) {
            return (Element) metadataNodes.item(0);
        }

        metadataNodes =
                packageElement.getElementsByTagName(
                        "metadata");

        if (metadataNodes.getLength() > 0) {
            return (Element) metadataNodes.item(0);
        }

        Element metadataElement =
                document.createElementNS(
                        OPF_NAMESPACE,
                        "metadata");

        Node firstElementChild =
                findFirstElementChild(
                        packageElement);

        if (firstElementChild != null) {
            packageElement.insertBefore(
                    metadataElement,
                    firstElementChild);
        } else {
            packageElement.appendChild(
                    metadataElement);
        }

        return metadataElement;
    }

    private Node findFirstElementChild(
            Element parent) {

        Node child =
                parent.getFirstChild();

        while (child != null) {
            if (child.getNodeType()
                    == Node.ELEMENT_NODE) {

                return child;
            }

            child = child.getNextSibling();
        }

        return null;
    }

    private void applyMetadata(
            Document document,
            Element packageElement,
            Element metadataElement,
            ApplyMetadataRequest request) {

        EpubMetadata metadata =
                request.getMetadata();

        List<Element> preservedElements =
                collectPreservedMetadataElements(
                        metadataElement,
                        request);

        clearChildren(metadataElement);

        String identifierId =
                defaultIfBlank(
                        request.getIdentifierElementId(),
                        DEFAULT_IDENTIFIER_ID);

        packageElement.setAttribute(
                "unique-identifier",
                identifierId);

        appendDcElement(
                document,
                metadataElement,
                "identifier",
                metadata.getIdentifier(),
                identifierId);

        if (!isBlank(metadata.getIdentifierType())) {
            appendRefinementMeta(
                    document,
                    metadataElement,
                    identifierId,
                    "identifier-type",
                    metadata.getIdentifierType(),
                    null);
        }

        appendDcElement(
                document,
                metadataElement,
                "title",
                metadata.getTitle(),
                DEFAULT_TITLE_ID);

        appendRefinementMeta(
                document,
                metadataElement,
                DEFAULT_TITLE_ID,
                "title-type",
                "main",
                null);

        if (!isBlank(metadata.getSubtitle())) {
            appendDcElement(
                    document,
                    metadataElement,
                    "title",
                    metadata.getSubtitle(),
                    DEFAULT_SUBTITLE_ID);

            appendRefinementMeta(
                    document,
                    metadataElement,
                    DEFAULT_SUBTITLE_ID,
                    "title-type",
                    "subtitle",
                    null);
        }

        appendCreators(
                document,
                metadataElement,
                metadata.getCreators());

        appendDcElement(
                document,
                metadataElement,
                "language",
                metadata.getLanguage(),
                null);

        appendOptionalDcElement(
                document,
                metadataElement,
                "publisher",
                metadata.getPublisher());

        appendOptionalDcElement(
                document,
                metadataElement,
                "description",
                metadata.getDescription());

        appendListDcElements(
                document,
                metadataElement,
                "subject",
                metadata.getSubjects());

        appendKeywordMetadata(
                document,
                metadataElement,
                metadata.getKeywords());

        appendOptionalDcElement(
                document,
                metadataElement,
                "type",
                metadata.getType());

        appendOptionalDcElement(
                document,
                metadataElement,
                "rights",
                metadata.getRights());

        appendOptionalDcElement(
                document,
                metadataElement,
                "source",
                metadata.getSource());

        appendOptionalDcElement(
                document,
                metadataElement,
                "relation",
                metadata.getRelation());

        appendOptionalDcElement(
                document,
                metadataElement,
                "coverage",
                metadata.getCoverage());

        appendOptionalDcElement(
                document,
                metadataElement,
                "date",
                metadata.getPublicationDate());

        appendOptionalPropertyMeta(
                document,
                metadataElement,
                "dcterms:modified",
                resolveModifiedDate(metadata));

        appendOptionalPropertyMeta(
                document,
                metadataElement,
                "schema:audience",
                metadata.getAudience());

        appendOptionalPropertyMeta(
                document,
                metadataElement,
                "schema:bookFormat",
                metadata.getFormatDescription());

        if (!isBlank(metadata.getAccessibilitySummary())) {
            appendPropertyMeta(
                    document,
                    metadataElement,
                    "schema:accessibilitySummary",
                    metadata.getAccessibilitySummary());
        }

        for (Element preservedElement : preservedElements) {
            Node imported =
                    document.importNode(
                            preservedElement,
                            true);

            metadataElement.appendChild(imported);
        }
    }

    private List<Element> collectPreservedMetadataElements(
            Element metadataElement,
            ApplyMetadataRequest request) {

        List<Element> preserved =
                new ArrayList<>();

        Node child =
                metadataElement.getFirstChild();

        while (child != null) {
            if (child.getNodeType()
                    == Node.ELEMENT_NODE) {

                Element element =
                        (Element) child;

                String property =
                        element.getAttribute("property");

                boolean preserveAccessibility =
                        request.isPreserveAccessibilityMetadata()
                                && property.startsWith(
                                        "schema:accessibility");

                boolean preserveUnknown =
                        request.isPreserveUnknownMetadata()
                                && !isManagedMetadataElement(element);

                if (preserveAccessibility
                        || preserveUnknown) {

                    preserved.add(
                            (Element) element.cloneNode(true));
                }
            }

            child = child.getNextSibling();
        }

        return preserved;
    }

    private boolean isManagedMetadataElement(
            Element element) {

        String localName =
                element.getLocalName();

        if (localName == null) {
            localName =
                    element.getNodeName();
        }

        String namespace =
                element.getNamespaceURI();

        if (DC_NAMESPACE.equals(namespace)) {
            return true;
        }

        if (!"meta".equals(localName)) {
            return false;
        }

        String property =
                element.getAttribute("property");

        if (isBlank(property)) {
            return false;
        }

        return property.equals("dcterms:modified")
                || property.equals("title-type")
                || property.equals("role")
                || property.equals("file-as")
                || property.equals("identifier-type")
                || property.equals("schema:keywords")
                || property.equals("schema:audience")
                || property.equals("schema:bookFormat")
                || property.equals("schema:accessibilitySummary");
    }

    private void appendCreators(
            Document document,
            Element metadataElement,
            List<MetadataCreator> creators) {

        if (creators == null) {
            return;
        }

        int index = 1;

        for (MetadataCreator creator : creators) {
            if (creator == null
                    || isBlank(creator.getName())) {
                continue;
            }

            String creatorId =
                    CREATOR_ID_PREFIX + index;

            appendDcElement(
                    document,
                    metadataElement,
                    "creator",
                    creator.getName(),
                    creatorId);

            appendRefinementMeta(
                    document,
                    metadataElement,
                    creatorId,
                    "role",
                    defaultIfBlank(
                            creator.getRole(),
                            "aut"),
                    "marc:relators");

            if (!isBlank(creator.getFileAs())) {
                appendRefinementMeta(
                        document,
                        metadataElement,
                        creatorId,
                        "file-as",
                        creator.getFileAs(),
                        null);
            }

            index++;
        }
    }

    private void appendKeywordMetadata(
            Document document,
            Element metadataElement,
            List<String> keywords) {

        if (keywords == null
                || keywords.isEmpty()) {
            return;
        }

        Set<String> normalizedKeywords =
                new HashSet<>();

        for (String keyword : keywords) {
            if (!isBlank(keyword)) {
                normalizedKeywords.add(
                        keyword.trim());
            }
        }

        if (normalizedKeywords.isEmpty()) {
            return;
        }

        appendPropertyMeta(
                document,
                metadataElement,
                "schema:keywords",
                String.join(
                        ", ",
                        normalizedKeywords));
    }

    private void appendListDcElements(
            Document document,
            Element metadataElement,
            String localName,
            List<String> values) {

        if (values == null) {
            return;
        }

        Set<String> uniqueValues =
                new HashSet<>();

        for (String value : values) {
            if (!isBlank(value)) {
                uniqueValues.add(
                        value.trim());
            }
        }

        for (String value : uniqueValues) {
            appendDcElement(
                    document,
                    metadataElement,
                    localName,
                    value,
                    null);
        }
    }

    private Element appendDcElement(
            Document document,
            Element parent,
            String localName,
            String value,
            String id) {

        Element element =
                document.createElementNS(
                        DC_NAMESPACE,
                        "dc:" + localName);

        element.setTextContent(
                defaultIfBlank(value, ""));

        if (!isBlank(id)) {
            element.setAttribute(
                    "id",
                    id);
        }

        parent.appendChild(element);

        return element;
    }

    private void appendOptionalDcElement(
            Document document,
            Element parent,
            String localName,
            String value) {

        if (isBlank(value)) {
            return;
        }

        appendDcElement(
                document,
                parent,
                localName,
                value,
                null);
    }

    private void appendRefinementMeta(
            Document document,
            Element parent,
            String targetId,
            String property,
            String value,
            String scheme) {

        if (isBlank(value)) {
            return;
        }

        Element meta =
                document.createElementNS(
                        OPF_NAMESPACE,
                        "meta");

        meta.setAttribute(
                "refines",
                "#" + targetId);

        meta.setAttribute(
                "property",
                property);

        if (!isBlank(scheme)) {
            meta.setAttribute(
                    "scheme",
                    scheme);
        }

        meta.setTextContent(
                value.trim());

        parent.appendChild(meta);
    }

    private void appendOptionalPropertyMeta(
            Document document,
            Element parent,
            String property,
            String value) {

        if (isBlank(value)) {
            return;
        }

        appendPropertyMeta(
                document,
                parent,
                property,
                value);
    }

    private void appendPropertyMeta(
            Document document,
            Element parent,
            String property,
            String value) {

        Element meta =
                document.createElementNS(
                        OPF_NAMESPACE,
                        "meta");

        meta.setAttribute(
                "property",
                property);

        meta.setTextContent(
                value.trim());

        parent.appendChild(meta);
    }

    private String resolveModifiedDate(
            EpubMetadata metadata) {

        if (!isBlank(metadata.getModified())) {
            return metadata.getModified().trim();
        }

        return Instant.now().toString();
    }

    private void clearChildren(
            Element element) {

        while (element.hasChildNodes()) {
            element.removeChild(
                    element.getFirstChild());
        }
    }

    private Path createBackup(
            Path packagePath)
            throws IOException {

        Path backupPath =
                packagePath.resolveSibling(
                        packagePath.getFileName()
                                .toString()
                                + BACKUP_EXTENSION);

        Files.copy(
                packagePath,
                backupPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        return backupPath;
    }

    private String serializePackageDocument(
            Document document)
            throws TransformerException {

        TransformerFactory factory =
                TransformerFactory.newInstance();

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    "");
        } catch (IllegalArgumentException ignored) {
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                    "");
        } catch (IllegalArgumentException ignored) {
        }

        Transformer transformer =
                factory.newTransformer();

        transformer.setOutputProperty(
                OutputKeys.METHOD,
                "xml");

        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name());

        transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION,
                "no");

        transformer.setOutputProperty(
                OutputKeys.INDENT,
                "yes");

        try {
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount",
                    "2");
        } catch (IllegalArgumentException ignored) {
        }

        StringWriter writer =
                new StringWriter();

        transformer.transform(
                new DOMSource(document),
                new StreamResult(writer));

        return writer.toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
                + "\n";
    }

    private void writePackageDocument(
            Path packagePath,
            String content)
            throws IOException {

        Files.writeString(
                packagePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private MetadataSnapshot createSnapshot(
            Element metadataElement) {

        MetadataSnapshot snapshot =
                new MetadataSnapshot();

        snapshot.setIdentifier(
                findFirstDcValue(
                        metadataElement,
                        "identifier"));

        snapshot.setTitle(
                findFirstDcValue(
                        metadataElement,
                        "title"));

        snapshot.setLanguage(
                findFirstDcValue(
                        metadataElement,
                        "language"));

        snapshot.setPublisher(
                findFirstDcValue(
                        metadataElement,
                        "publisher"));

        snapshot.setCreatorCount(
                countDcElements(
                        metadataElement,
                        "creator"));

        snapshot.setSubjectCount(
                countDcElements(
                        metadataElement,
                        "subject"));

        return snapshot;
    }

    private String findFirstDcValue(
            Element metadataElement,
            String localName) {

        NodeList nodes =
                metadataElement.getElementsByTagNameNS(
                        DC_NAMESPACE,
                        localName);

        if (nodes.getLength() == 0) {
            nodes =
                    metadataElement.getElementsByTagName(
                            "dc:" + localName);
        }

        if (nodes.getLength() == 0) {
            return null;
        }

        return trimToNull(
                nodes.item(0)
                        .getTextContent());
    }

    private int countDcElements(
            Element metadataElement,
            String localName) {

        NodeList nodes =
                metadataElement.getElementsByTagNameNS(
                        DC_NAMESPACE,
                        localName);

        if (nodes.getLength() > 0) {
            return nodes.getLength();
        }

        return metadataElement
                .getElementsByTagName(
                        "dc:" + localName)
                .getLength();
    }

    private ApplyMetadataResponse createResponse(
            Path projectRoot,
            Path packagePath,
            Path backupPath,
            MetadataSnapshot previousSnapshot,
            ApplyMetadataRequest request,
            Element metadataElement)
            throws IOException {

        ApplyMetadataResponse response =
                new ApplyMetadataResponse();

        response.setApplied(true);

        response.setPackageFileName(
                packagePath.getFileName()
                        .toString());

        response.setAbsolutePath(
                packagePath.toString());

        if (projectRoot != null
                && packagePath.startsWith(projectRoot)) {

            response.setRelativePath(
                    normalizeSeparator(
                            projectRoot.relativize(packagePath)
                                    .toString()));
        }

        response.setBackupCreated(
                backupPath != null);

        if (backupPath != null) {
            response.setBackupPath(
                    backupPath.toString());
        }

        response.setPreviousMetadata(
                previousSnapshot);

        response.setIdentifier(
                request.getMetadata()
                        .getIdentifier());

        response.setTitle(
                request.getMetadata()
                        .getTitle());

        response.setCreatorCount(
                countDcElements(
                        metadataElement,
                        "creator"));

        response.setSubjectCount(
                countDcElements(
                        metadataElement,
                        "subject"));

        response.setFileSize(
                Files.size(packagePath));

        response.setCharset(
                StandardCharsets.UTF_8.name());

        return response;
    }

    private boolean isValidModifiedDate(
            String value) {

        try {
            Instant.parse(value.trim());
            return true;

        } catch (DateTimeParseException exception) {
            return false;
        }
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

    private String normalizeSeparator(
            String path) {

        return path.replace('\\', '/');
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

    public static final class ApplyMetadataRequest {

        private String projectRoot;
        private String packagePath;

        private EpubMetadata metadata;

        private String identifierElementId =
                DEFAULT_IDENTIFIER_ID;

        private boolean createBackup = true;
        private boolean restrictToProject = true;

        private boolean preserveAccessibilityMetadata = true;
        private boolean preserveUnknownMetadata = true;

        public ApplyMetadataRequest() {
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(
                String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public String getPackagePath() {
            return packagePath;
        }

        public void setPackagePath(
                String packagePath) {
            this.packagePath = packagePath;
        }

        public EpubMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(
                EpubMetadata metadata) {
            this.metadata = metadata;
        }

        public String getIdentifierElementId() {
            return identifierElementId;
        }

        public void setIdentifierElementId(
                String identifierElementId) {
            this.identifierElementId =
                    identifierElementId;
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

        public boolean isPreserveAccessibilityMetadata() {
            return preserveAccessibilityMetadata;
        }

        public void setPreserveAccessibilityMetadata(
                boolean preserveAccessibilityMetadata) {
            this.preserveAccessibilityMetadata =
                    preserveAccessibilityMetadata;
        }

        public boolean isPreserveUnknownMetadata() {
            return preserveUnknownMetadata;
        }

        public void setPreserveUnknownMetadata(
                boolean preserveUnknownMetadata) {
            this.preserveUnknownMetadata =
                    preserveUnknownMetadata;
        }
    }

    public static final class ApplyMetadataResponse {

        private boolean applied;
        private boolean backupCreated;

        private String packageFileName;
        private String relativePath;
        private String absolutePath;
        private String backupPath;

        private String identifier;
        private String title;

        private int creatorCount;
        private int subjectCount;

        private long fileSize;
        private String charset;

        private MetadataSnapshot previousMetadata;

        public ApplyMetadataResponse() {
        }

        public boolean isApplied() {
            return applied;
        }

        public void setApplied(
                boolean applied) {
            this.applied = applied;
        }

        public boolean isBackupCreated() {
            return backupCreated;
        }

        public void setBackupCreated(
                boolean backupCreated) {
            this.backupCreated =
                    backupCreated;
        }

        public String getPackageFileName() {
            return packageFileName;
        }

        public void setPackageFileName(
                String packageFileName) {
            this.packageFileName =
                    packageFileName;
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

        public int getCreatorCount() {
            return creatorCount;
        }

        public void setCreatorCount(
                int creatorCount) {
            this.creatorCount = creatorCount;
        }

        public int getSubjectCount() {
            return subjectCount;
        }

        public void setSubjectCount(
                int subjectCount) {
            this.subjectCount = subjectCount;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(
                long fileSize) {
            this.fileSize = fileSize;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(
                String charset) {
            this.charset = charset;
        }

        public MetadataSnapshot getPreviousMetadata() {
            return previousMetadata;
        }

        public void setPreviousMetadata(
                MetadataSnapshot previousMetadata) {
            this.previousMetadata =
                    previousMetadata;
        }
    }

    public static final class MetadataSnapshot {

        private String identifier;
        private String title;
        private String language;
        private String publisher;

        private int creatorCount;
        private int subjectCount;

        public MetadataSnapshot() {
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

        public String getLanguage() {
            return language;
        }

        public void setLanguage(
                String language) {
            this.language = language;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(
                String publisher) {
            this.publisher = publisher;
        }

        public int getCreatorCount() {
            return creatorCount;
        }

        public void setCreatorCount(
                int creatorCount) {
            this.creatorCount = creatorCount;
        }

        public int getSubjectCount() {
            return subjectCount;
        }

        public void setSubjectCount(
                int subjectCount) {
            this.subjectCount = subjectCount;
        }
    }
}