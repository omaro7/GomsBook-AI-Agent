/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

import kr.co.goms.gomsbook.ai.accessibility.model.ImageAccessibilityType;

/**
 * XHTML 문서의 이미지 요소에 대체 텍스트와 접근성 속성을 적용하는
 * 기본 {@link AltTextApplicator} 구현체.
 *
 * <p>다음 기능을 제공한다.</p>
 *
 * <ul>
 *   <li>프로젝트 내부 XHTML 파일 검증</li>
 *   <li>이미지 id 또는 src를 이용한 대상 검색</li>
 *   <li>기존 alt 충돌 검증</li>
 *   <li>일반 이미지와 장식 이미지 속성 적용</li>
 *   <li>dry-run 지원</li>
 *   <li>원본 파일 백업</li>
 *   <li>임시 파일과 원자적 이동을 이용한 안전한 저장</li>
 * </ul>
 */
public final class DefaultAltTextApplicator
        implements AltTextApplicator {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("xhtml", "html", "htm");

    private static final DateTimeFormatter BACKUP_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final boolean namespaceAware;
    private final boolean preserveDoctype;

    public DefaultAltTextApplicator() {
        this(true, true);
    }

    public DefaultAltTextApplicator(
            boolean namespaceAware,
            boolean preserveDoctype) {

        this.namespaceAware = namespaceAware;
        this.preserveDoctype = preserveDoctype;
    }

    @Override
    public AltTextApplicationResult apply(
            AltTextApplicationRequest request)
            throws AltTextApplicationException {

        validateRequest(request);

        Path xhtmlPath = request.getXhtmlPath();

        validateXhtmlFile(request);

        Document document = parseDocument(request);

        List<Element> matchedElements =
                findMatchingImages(document, request);

        if (matchedElements.isEmpty()) {
            return createNoMatchResult(request);
        }

        if (matchedElements.size() > 1) {
            return createAmbiguousResult(
                    request,
                    matchedElements
            );
        }

        Element imageElement = matchedElements.get(0);

        String previousAlt = getNullableAttribute(
                imageElement,
                "alt"
        );

        String previousRole = getNullableAttribute(
                imageElement,
                "role"
        );

        String previousAriaHidden = getNullableAttribute(
                imageElement,
                "aria-hidden"
        );

        validateExpectedCurrentAlt(
                request,
                previousAlt
        );

        validateOverwritePolicy(
                request,
                previousAlt
        );

        AttributeChangeSet changeSet =
                applyAccessibilityAttributes(
                        request,
                        imageElement
                );

        if (!changeSet.isChanged()) {
            return AltTextApplicationResult
                    .builder(
                            request.getProjectRoot(),
                            xhtmlPath
                    )
                    .request(request)
                    .matched(true)
                    .matchedElementCount(1)
                    .previousAltText(previousAlt)
                    .appliedAltText(
                            getNullableAttribute(
                                    imageElement,
                                    "alt"
                            )
                    )
                    .previousRole(previousRole)
                    .appliedRole(
                            getNullableAttribute(
                                    imageElement,
                                    "role"
                            )
                    )
                    .previousAriaHidden(
                            previousAriaHidden
                    )
                    .appliedAriaHidden(
                            getNullableAttribute(
                                    imageElement,
                                    "aria-hidden"
                            )
                    )
                    .changed(false)
                    .fileUpdated(false)
                    .dryRun(request.isDryRun())
                    .changedAttributes(
                            Collections.emptyList()
                    )
                    .warning(
                            "요청한 접근성 속성이 이미 적용되어 있습니다."
                    )
                    .build();
        }

        if (request.isDryRun()) {
            return AltTextApplicationResult
                    .builder(
                            request.getProjectRoot(),
                            xhtmlPath
                    )
                    .request(request)
                    .matched(true)
                    .matchedElementCount(1)
                    .previousAltText(previousAlt)
                    .appliedAltText(
                            getNullableAttribute(
                                    imageElement,
                                    "alt"
                            )
                    )
                    .previousRole(previousRole)
                    .appliedRole(
                            getNullableAttribute(
                                    imageElement,
                                    "role"
                            )
                    )
                    .previousAriaHidden(
                            previousAriaHidden
                    )
                    .appliedAriaHidden(
                            getNullableAttribute(
                                    imageElement,
                                    "aria-hidden"
                            )
                    )
                    .changed(true)
                    .fileUpdated(false)
                    .dryRun(true)
                    .changedAttributes(
                            changeSet.getChangedAttributes()
                    )
                    .warning(
                            "Dry-run 모드이므로 XHTML 파일은 저장되지 않았습니다."
                    )
                    .build();
        }

        Path backupPath = null;

        if (request.isCreateBackup()) {
            backupPath = createBackup(xhtmlPath);
        }

        try {
            writeDocumentSafely(
                    document,
                    xhtmlPath
            );

        } catch (AltTextApplicationException exception) {
            restoreBackupIfPossible(
                    backupPath,
                    xhtmlPath
            );

            throw exception;
        }

        return AltTextApplicationResult
                .builder(
                        request.getProjectRoot(),
                        xhtmlPath
                )
                .request(request)
                .matched(true)
                .matchedElementCount(1)
                .previousAltText(previousAlt)
                .appliedAltText(
                        getNullableAttribute(
                                imageElement,
                                "alt"
                        )
                )
                .previousRole(previousRole)
                .appliedRole(
                        getNullableAttribute(
                                imageElement,
                                "role"
                        )
                )
                .previousAriaHidden(
                        previousAriaHidden
                )
                .appliedAriaHidden(
                        getNullableAttribute(
                                imageElement,
                                "aria-hidden"
                        )
                )
                .changed(true)
                .fileUpdated(true)
                .dryRun(false)
                .backup(backupPath)
                .changedAttributes(
                        changeSet.getChangedAttributes()
                )
                .build();
    }

    @Override
    public boolean supports(
            AltTextApplicationRequest request) {

        if (request == null
                || request.getXhtmlPath() == null) {

            return false;
        }

        return SUPPORTED_EXTENSIONS.contains(
                getExtension(
                        request.getXhtmlPath()
                )
        );
    }

    private void validateRequest(
            AltTextApplicationRequest request) {

        if (request == null) {
            throw new AltTextApplicationException(
                    AltTextApplicationErrorCode.INVALID_REQUEST,
                    "Alt text application request must not be null."
            );
        }

        if (!supports(request)) {
            throw createException(
                    request,
                    AltTextApplicationErrorCode.INVALID_REQUEST,
                    "Unsupported XHTML file extension: "
                            + getExtension(
                                    request.getXhtmlPath()
                            )
            );
        }

        if (!request.getXhtmlPath()
                .startsWith(request.getProjectRoot())) {

            throw createException(
                    request,
                    AltTextApplicationErrorCode.INVALID_REQUEST,
                    "XHTML file must be located inside the project."
            );
        }

        if (request.getAccessibilityType()
                == ImageAccessibilityType.UNKNOWN) {

            throw createException(
                    request,
                    AltTextApplicationErrorCode
                            .UNSUPPORTED_ACCESSIBILITY_TYPE,
                    "UNKNOWN accessibility type cannot be applied."
            );
        }
    }

    private void validateXhtmlFile(
            AltTextApplicationRequest request) {

        Path xhtmlPath = request.getXhtmlPath();

        if (!Files.exists(xhtmlPath)) {
            throw createException(
                    request,
                    AltTextApplicationErrorCode.XHTML_NOT_FOUND,
                    "XHTML file does not exist."
            );
        }

        if (!Files.isRegularFile(xhtmlPath)) {
            throw createException(
                    request,
                    AltTextApplicationErrorCode.INVALID_REQUEST,
                    "XHTML path is not a regular file."
            );
        }

        if (!Files.isReadable(xhtmlPath)) {
            throw createException(
                    request,
                    AltTextApplicationErrorCode.XHTML_NOT_READABLE,
                    "XHTML file is not readable."
            );
        }

        if (!request.isDryRun()
                && !Files.isWritable(xhtmlPath)) {

            throw createException(
                    request,
                    AltTextApplicationErrorCode.XHTML_NOT_WRITABLE,
                    "XHTML file is not writable."
            );
        }
    }

    private Document parseDocument(
            AltTextApplicationRequest request) {

        try {
            DocumentBuilderFactory factory =
                    createSecureDocumentBuilderFactory();

            DocumentBuilder builder =
                    factory.newDocumentBuilder();

            Document document =
                    builder.parse(
                            request.getXhtmlPath().toFile()
                    );

            document.normalizeDocument();

            return document;

        } catch (ParserConfigurationException
                | SAXException
                | IOException exception) {

            throw createException(
                    request,
                    AltTextApplicationErrorCode.XHTML_PARSE_FAILED,
                    "Failed to parse XHTML document.",
                    exception
            );
        }
    }

    private DocumentBuilderFactory
            createSecureDocumentBuilderFactory()
            throws ParserConfigurationException {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(namespaceAware);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        setFeature(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        setFeature(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        setFeature(
                factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );

        /*
         * XHTML 문서의 DOCTYPE 자체는 허용하지만,
         * 외부 DTD 로딩은 차단한다.
         */
        setFeature(
                factory,
                "http://apache.org/xml/features/disallow-doctype-decl",
                false
        );

        setAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        setAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );

        return factory;
    }

    private void setFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value)
            throws ParserConfigurationException {

        factory.setFeature(feature, value);
    }

    private void setAttributeIfSupported(
            DocumentBuilderFactory factory,
            String name,
            String value) {

        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
            /*
             * 일부 XML Parser 구현에서는 지원하지 않을 수 있다.
             */
        }
    }

    private List<Element> findMatchingImages(
            Document document,
            AltTextApplicationRequest request) {

        NodeList imageNodes =
                document.getElementsByTagNameNS(
                        "*",
                        "img"
                );

        if (imageNodes.getLength() == 0) {
            imageNodes =
                    document.getElementsByTagName("img");
        }

        List<Element> result = new ArrayList<>();

        for (int index = 0;
                index < imageNodes.getLength();
                index++) {

            Node node = imageNodes.item(index);

            if (!(node instanceof Element element)) {
                continue;
            }

            if (!matchesId(
                    element,
                    request.getImageElementId()
            )) {
                continue;
            }

            if (!matchesSource(
                    element,
                    request.getImageSource()
            )) {
                continue;
            }

            result.add(element);
        }

        return result;
    }

    private boolean matchesId(
            Element element,
            String expectedId) {

        if (expectedId == null) {
            return true;
        }

        return expectedId.equals(
                element.getAttribute("id")
        );
    }

    private boolean matchesSource(
            Element element,
            String expectedSource) {

        if (expectedSource == null) {
            return true;
        }

        String currentSource =
                normalizeReference(
                        element.getAttribute("src")
                );

        String normalizedExpected =
                normalizeReference(expectedSource);

        return normalizedExpected.equals(currentSource);
    }

    private String normalizeReference(
            String value) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim().replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }

    private void validateExpectedCurrentAlt(
            AltTextApplicationRequest request,
            String previousAlt) {

        if (!request.hasExpectedCurrentAlt()) {
            return;
        }

        if (!Objects.equals(
                request.getExpectedCurrentAlt(),
                previousAlt
        )) {

            throw createException(
                    request,
                    AltTextApplicationErrorCode
                            .EXPECTED_ALT_MISMATCH,
                    "Current alt value does not match "
                            + "expectedCurrentAlt."
            );
        }
    }

    private void validateOverwritePolicy(
            AltTextApplicationRequest request,
            String previousAlt) {

        if (request.isOverwriteExisting()) {
            return;
        }

        if (previousAlt == null
                || previousAlt.isBlank()) {

            return;
        }

        String requestedAlt =
                request.getAltText();

        if (Objects.equals(
                previousAlt,
                requestedAlt
        )) {
            return;
        }

        throw createException(
                request,
                AltTextApplicationErrorCode
                        .EXISTING_ALT_CONFLICT,
                "The image already has non-empty alt text. "
                        + "Set overwriteExisting to true "
                        + "to replace it."
        );
    }

    private AttributeChangeSet
            applyAccessibilityAttributes(
                    AltTextApplicationRequest request,
                    Element imageElement) {

        AttributeChangeSet changeSet =
                new AttributeChangeSet();

        if (request.isDecorative()) {
            applyDecorativeAttributes(
                    request,
                    imageElement,
                    changeSet
            );
        } else {
            applyMeaningfulImageAttributes(
                    request,
                    imageElement,
                    changeSet
            );
        }

        if (request.isRemoveTitle()) {
            removeAttribute(
                    imageElement,
                    "title",
                    changeSet
            );
        }

        if (request.isRemoveAriaLabel()) {
            removeAttribute(
                    imageElement,
                    "aria-label",
                    changeSet
            );

            removeAttribute(
                    imageElement,
                    "aria-labelledby",
                    changeSet
            );
        }

        return changeSet;
    }

    private void applyDecorativeAttributes(
            AltTextApplicationRequest request,
            Element imageElement,
            AttributeChangeSet changeSet) {

        setAttribute(
                imageElement,
                "alt",
                "",
                changeSet
        );

        if (request.isApplyPresentationRole()) {
            setAttribute(
                    imageElement,
                    "role",
                    "presentation",
                    changeSet
            );
        } else {
            removePresentationRole(
                    imageElement,
                    changeSet
            );
        }

        if (request.isApplyAriaHidden()) {
            setAttribute(
                    imageElement,
                    "aria-hidden",
                    "true",
                    changeSet
            );
        } else {
            removeAttribute(
                    imageElement,
                    "aria-hidden",
                    changeSet
            );
        }

        /*
         * 장식 이미지에서 접근 가능한 이름을 생성할 수 있는
         * ARIA 속성은 제거한다.
         */
        removeAttribute(
                imageElement,
                "aria-label",
                changeSet
        );

        removeAttribute(
                imageElement,
                "aria-labelledby",
                changeSet
        );
    }

    private void applyMeaningfulImageAttributes(
            AltTextApplicationRequest request,
            Element imageElement,
            AttributeChangeSet changeSet) {

        setAttribute(
                imageElement,
                "alt",
                request.getAltText(),
                changeSet
        );

        removePresentationRole(
                imageElement,
                changeSet
        );

        if ("true".equalsIgnoreCase(
                imageElement.getAttribute(
                        "aria-hidden"
                ))) {

            removeAttribute(
                    imageElement,
                    "aria-hidden",
                    changeSet
            );
        }
    }

    private void removePresentationRole(
            Element element,
            AttributeChangeSet changeSet) {

        String role = element.getAttribute("role");

        if ("presentation".equalsIgnoreCase(role)
                || "none".equalsIgnoreCase(role)) {

            removeAttribute(
                    element,
                    "role",
                    changeSet
            );
        }
    }

    private void setAttribute(
            Element element,
            String name,
            String value,
            AttributeChangeSet changeSet) {

        String previousValue =
                getNullableAttribute(
                        element,
                        name
                );

        if (Objects.equals(
                previousValue,
                value
        )) {
            return;
        }

        element.setAttribute(name, value);
        changeSet.add(name);
    }

    private void removeAttribute(
            Element element,
            String name,
            AttributeChangeSet changeSet) {

        if (!element.hasAttribute(name)) {
            return;
        }

        element.removeAttribute(name);
        changeSet.add(name);
    }

    private String getNullableAttribute(
            Element element,
            String name) {

        if (!element.hasAttribute(name)) {
            return null;
        }

        return element.getAttribute(name);
    }

    private Path createBackup(
            Path xhtmlPath) {

        String timestamp =
                LocalDateTime.now()
                        .format(
                                BACKUP_TIME_FORMATTER
                        );

        Path backupPath =
                xhtmlPath.resolveSibling(
                        xhtmlPath
                                .getFileName()
                                .toString()
                                + "."
                                + timestamp
                                + ".bak"
                );

        try {
            Files.copy(
                    xhtmlPath,
                    backupPath,
                    StandardCopyOption.COPY_ATTRIBUTES
            );

            return backupPath;

        } catch (IOException exception) {
            throw new AltTextApplicationException(
                    AltTextApplicationErrorCode.BACKUP_FAILED,
                    xhtmlPath,
                    null,
                    null,
                    "Failed to create XHTML backup.",
                    exception
            );
        }
    }

    private void restoreBackupIfPossible(
            Path backupPath,
            Path targetPath) {

        if (backupPath == null
                || !Files.exists(backupPath)) {

            return;
        }

        try {
            Files.copy(
                    backupPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
        } catch (IOException ignored) {
            /*
             * 원래 저장 예외를 유지하기 위해 복구 실패는 별도로 던지지 않는다.
             */
        }
    }

    private void writeDocumentSafely(
            Document document,
            Path targetPath) {

        Path parent = targetPath.getParent();

        if (parent == null) {
            throw new AltTextApplicationException(
                    AltTextApplicationErrorCode.XHTML_WRITE_FAILED,
                    targetPath,
                    null,
                    null,
                    "XHTML parent directory is not available."
            );
        }

        Path tempPath = null;

        try {
            tempPath = Files.createTempFile(
                    parent,
                    targetPath
                            .getFileName()
                            .toString(),
                    ".tmp"
            );

            Transformer transformer =
                    createTransformer(document);

            try (OutputStream outputStream =
                    Files.newOutputStream(tempPath)) {

                transformer.transform(
                        new DOMSource(document),
                        new StreamResult(outputStream)
                );
            }

            moveTempFile(
                    tempPath,
                    targetPath
            );

        } catch (IOException
                | TransformerException exception) {

            throw new AltTextApplicationException(
                    AltTextApplicationErrorCode.XHTML_WRITE_FAILED,
                    targetPath,
                    null,
                    null,
                    "Failed to write XHTML document.",
                    exception
            );

        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패는 무시한다.
                }
            }
        }
    }

    private Transformer createTransformer(
            Document document)
            throws TransformerException {

        TransformerFactory factory =
                TransformerFactory.newInstance();

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );
        } catch (IllegalArgumentException ignored) {
            // 구현체가 지원하지 않을 수 있다.
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                    ""
            );
        } catch (IllegalArgumentException ignored) {
            // 구현체가 지원하지 않을 수 있다.
        }

        Transformer transformer =
                factory.newTransformer();

        transformer.setOutputProperty(
                OutputKeys.METHOD,
                "xml"
        );

        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name()
        );

        transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION,
                "no"
        );

        transformer.setOutputProperty(
                OutputKeys.INDENT,
                "no"
        );

        if (preserveDoctype
                && document.getDoctype() != null) {

            String publicId =
                    document.getDoctype()
                            .getPublicId();

            String systemId =
                    document.getDoctype()
                            .getSystemId();

            if (publicId != null) {
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_PUBLIC,
                        publicId
                );
            }

            if (systemId != null) {
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_SYSTEM,
                        systemId
                );
            }
        }

        return transformer;
    }

    private void moveTempFile(
            Path tempPath,
            Path targetPath)
            throws IOException {

        try {
            Files.move(
                    tempPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (AtomicMoveNotSupportedException exception) {

            Files.move(
                    tempPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private AltTextApplicationResult
            createNoMatchResult(
                    AltTextApplicationRequest request) {

        return AltTextApplicationResult
                .builder(
                        request.getProjectRoot(),
                        request.getXhtmlPath()
                )
                .request(request)
                .matched(false)
                .matchedElementCount(0)
                .changed(false)
                .fileUpdated(false)
                .dryRun(request.isDryRun())
                .warning(
                        "조건과 일치하는 img 요소가 없습니다."
                )
                .build();
    }

    private AltTextApplicationResult
            createAmbiguousResult(
                    AltTextApplicationRequest request,
                    List<Element> matchedElements) {

        AltTextApplicationResult.Builder builder =
                AltTextApplicationResult
                        .builder(
                                request.getProjectRoot(),
                                request.getXhtmlPath()
                        )
                        .request(request)
                        .matched(true)
                        .matchedElementCount(
                                matchedElements.size()
                        )
                        .changed(false)
                        .fileUpdated(false)
                        .dryRun(request.isDryRun())
                        .warning(
                                "여러 img 요소가 검색되었습니다. "
                                        + "imageElementId를 사용하여 "
                                        + "대상을 명확히 지정해야 합니다."
                        );

        for (Element element : matchedElements) {
            String id =
                    getNullableAttribute(
                            element,
                            "id"
                    );

            String src =
                    getNullableAttribute(
                            element,
                            "src"
                    );

            builder.warning(
                    "일치 요소: id="
                            + String.valueOf(id)
                            + ", src="
                            + String.valueOf(src)
            );
        }

        return builder.build();
    }

    private AltTextApplicationException
            createException(
                    AltTextApplicationRequest request,
                    AltTextApplicationErrorCode errorCode,
                    String message) {

        return new AltTextApplicationException(
                errorCode,
                request.getXhtmlPath(),
                request.getImageElementId(),
                request.getImageSource(),
                message
        );
    }

    private AltTextApplicationException
            createException(
                    AltTextApplicationRequest request,
                    AltTextApplicationErrorCode errorCode,
                    String message,
                    Throwable cause) {

        return new AltTextApplicationException(
                errorCode,
                request.getXhtmlPath(),
                request.getImageElementId(),
                request.getImageSource(),
                message,
                cause
        );
    }

    private String getExtension(Path path) {

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
                || dotIndex + 1 >= fileName.length()) {

            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * XHTML 요소에 실제로 변경된 속성 목록을 관리한다.
     */
    private static final class AttributeChangeSet {

        private final List<String> changedAttributes =
                new ArrayList<>();

        private void add(String attributeName) {

            if (attributeName == null
                    || attributeName.isBlank()
                    || changedAttributes
                            .contains(attributeName)) {

                return;
            }

            changedAttributes.add(attributeName);
        }

        private boolean isChanged() {
            return !changedAttributes.isEmpty();
        }

        private List<String> getChangedAttributes() {
            return Collections.unmodifiableList(
                    new ArrayList<>(
                            changedAttributes
                    )
            );
        }
    }
}