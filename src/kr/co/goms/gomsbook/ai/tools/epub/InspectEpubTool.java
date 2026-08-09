/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.epub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 생성된 EPUB 파일의 구조와 주요 정보를 조회하는 Agent Tool입니다.
 *
 * <p>EPUB 파일을 수정하지 않고 ZIP 내부 구조와 주요 문서를
 * 읽어 Agent가 현재 EPUB 상태를 파악할 수 있도록 합니다.</p>
 *
 * <p>주요 조회 항목:</p>
 *
 * <ul>
 *     <li>EPUB 파일 크기</li>
 *     <li>ZIP 엔트리 수</li>
 *     <li>mimetype 존재 및 저장 방식</li>
 *     <li>META-INF/container.xml 존재 여부</li>
 *     <li>OPF package document 경로</li>
 *     <li>EPUB 버전</li>
 *     <li>제목 / 언어 / 저자 / 식별자</li>
 *     <li>manifest / spine item 수</li>
 *     <li>Navigation Document 존재 여부</li>
 *     <li>NCX 존재 여부</li>
 *     <li>XHTML / CSS / Image / Font 리소스 수</li>
 *     <li>기본 접근성 metadata 존재 여부</li>
 * </ul>
 *
 * <p>정식 EPUB 규격 및 접근성 검증은
 * {@link ValidateEpubTool}에서 수행합니다.</p>
 */
public final class InspectEpubTool
        implements AgentTool {

    public static final String NAME =
            "inspect_epub";

    public static final String TOOL_NAME =
            NAME;

    public static final String DESCRIPTION =
            "Inspects an EPUB file and returns its structure, "
                    + "metadata, manifest, spine, navigation, "
                    + "and resource summary without modifying it.";

    private static final String EPUB_FILE_ARGUMENT =
            "epubFile";

    private static final String MIMETYPE_ENTRY =
            "mimetype";

    private static final String CONTAINER_ENTRY =
            "META-INF/container.xml";

    private static final String EXPECTED_MIMETYPE =
            "application/epub+zip";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * EPUB inspection 요청을 검증합니다.
     */
    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context
    ) {

        ToolValidationResult.Builder result =
                ToolValidationResult.builder();

        if (request == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_REQUEST_NULL",
                                    "Tool request must not be null."
                            )
                    )
                    .build();
        }

        Path epubFile;

        try {

            epubFile =
                    resolveEpubFile(
                            request,
                            context
                    );

        } catch (RuntimeException exception) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_ARGUMENT_INVALID",
                                    safeMessage(exception)
                            )
                    )
                    .build();
        }

        if (epubFile == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_FILE_MISSING",
                                    "EPUB file was not provided."
                            )
                    )
                    .build();
        }

        Path normalized =
                epubFile
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_FILE_NOT_FOUND",
                                    "EPUB file does not exist: "
                                            + normalized
                            )
                    )
                    .build();
        }

        if (!Files.isRegularFile(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_NOT_FILE",
                                    "EPUB path is not a regular file: "
                                            + normalized
                            )
                    )
                    .build();
        }

        if (!Files.isReadable(normalized)) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_NOT_READABLE",
                                    "EPUB file is not readable: "
                                            + normalized
                            )
                    )
                    .build();
        }

        String fileName =
                normalized.getFileName() == null
                        ? ""
                        : normalized
                                .getFileName()
                                .toString()
                                .toLowerCase(
                                        Locale.ROOT
                                );

        if (!fileName.endsWith(".epub")) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_INSPECT_EXTENSION_INVALID",
                                    "Inspection target must use "
                                            + "the .epub extension."
                            )
                    )
                    .build();
        }

        return result
                .valid(true)
                .build();
    }

    /**
     * EPUB 파일을 조회합니다.
     */
    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context
    ) {

        ToolValidationResult validation =
                validate(
                        request,
                        context
                );

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.VALIDATION_FAILED
                    )
                    .validationResult(
                            validation
                    )
                    .message(
                            "EPUB inspection request is invalid."
                    )
                    .build();
        }

        Path epubFile;

        try {

            epubFile =
                    Objects.requireNonNull(
                            resolveEpubFile(
                                    request,
                                    context
                            ),
                            "EPUB file must not be null."
                    )
                            .toAbsolutePath()
                            .normalize();

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_INSPECT_ARGUMENT_INVALID",
                    safeMessage(exception),
                    null,
                    exception
            );
        }

        try {

            EpubInspectionResult inspectionResult =
                    inspect(
                            epubFile
                    );

            return convertResult(
                    inspectionResult
            );

        } catch (IOException exception) {

            return failure(
                    "EPUB_INSPECT_IO_ERROR",
                    "Failed to inspect EPUB file: "
                            + safeMessage(exception),
                    epubFile,
                    exception
            );

        } catch (SecurityException exception) {

            return failure(
                    "EPUB_INSPECT_ACCESS_DENIED",
                    "Access to EPUB file was denied: "
                            + safeMessage(exception),
                    epubFile,
                    exception
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_INSPECT_UNEXPECTED_ERROR",
                    "Unexpected EPUB inspection error: "
                            + safeMessage(exception),
                    epubFile,
                    exception
            );
        }
    }

    /**
     * EPUB 내부 구조를 조회합니다.
     */
    private EpubInspectionResult inspect(
            Path epubFile
    ) throws IOException {

        EpubInspectionResult.Builder result =
                EpubInspectionResult.builder()
                        .epubFile(
                                epubFile
                        )
                        .fileSize(
                                Files.size(
                                        epubFile
                                )
                        );

        try (ZipFile zipFile =
                new ZipFile(
                        epubFile.toFile()
                )) {

            List<? extends ZipEntry> entries =
                    java.util.Collections.list(
                            zipFile.entries()
                    );

            result.entryCount(
                    entries.size()
            );

            inspectEntryStatistics(
                    entries,
                    result
            );

            inspectMimetype(
                    zipFile,
                    entries,
                    result
            );

            String packagePath =
                    inspectContainer(
                            zipFile,
                            result
                    );

            if (packagePath != null) {

                inspectPackageDocument(
                        zipFile,
                        packagePath,
                        result
                );
            }
        }

        return result.build();
    }

    /**
     * ZIP 엔트리 유형별 개수를 집계합니다.
     */
    private void inspectEntryStatistics(
            List<? extends ZipEntry> entries,
            EpubInspectionResult.Builder result
    ) {

        int xhtmlCount = 0;
        int cssCount = 0;
        int imageCount = 0;
        int fontCount = 0;
        int audioCount = 0;
        int videoCount = 0;

        Set<String> paths =
                new TreeSet<>();

        for (ZipEntry entry : entries) {

            if (entry == null
                    || entry.isDirectory()) {

                continue;
            }

            String name =
                    normalizeEpubPath(
                            entry.getName()
                    );

            paths.add(name);

            String lower =
                    name.toLowerCase(
                            Locale.ROOT
                    );

            if (lower.endsWith(".xhtml")
                    || lower.endsWith(".html")
                    || lower.endsWith(".htm")) {

                xhtmlCount++;

            } else if (lower.endsWith(".css")) {

                cssCount++;

            } else if (isImageFile(lower)) {

                imageCount++;

            } else if (isFontFile(lower)) {

                fontCount++;

            } else if (isAudioFile(lower)) {

                audioCount++;

            } else if (isVideoFile(lower)) {

                videoCount++;
            }
        }

        result.xhtmlCount(
                xhtmlCount
        )
                .cssCount(
                        cssCount
                )
                .imageCount(
                        imageCount
                )
                .fontCount(
                        fontCount
                )
                .audioCount(
                        audioCount
                )
                .videoCount(
                        videoCount
                )
                .entryPaths(
                        paths
                );
    }

    /**
     * EPUB mimetype 정보를 조회합니다.
     */
    private void inspectMimetype(
            ZipFile zipFile,
            List<? extends ZipEntry> entries,
            EpubInspectionResult.Builder result
    ) throws IOException {

        ZipEntry mimetype =
                zipFile.getEntry(
                        MIMETYPE_ENTRY
                );

        result.mimetypePresent(
                mimetype != null
        );

        if (mimetype == null) {
            return;
        }

        String value =
                readTextEntry(
                        zipFile,
                        mimetype
                );

        result.mimetype(
                value
        );

        result.mimetypeValid(
                EXPECTED_MIMETYPE.equals(
                        value
                )
        );

        result.mimetypeStored(
                mimetype.getMethod()
                        == ZipEntry.STORED
        );

        if (!entries.isEmpty()) {

            result.mimetypeFirstEntry(
                    MIMETYPE_ENTRY.equals(
                            entries.get(0)
                                    .getName()
                    )
            );
        }
    }

    /**
     * container.xml에서 OPF Package Document 경로를 조회합니다.
     */
    private String inspectContainer(
            ZipFile zipFile,
            EpubInspectionResult.Builder result
    ) throws IOException {

        ZipEntry container =
                zipFile.getEntry(
                        CONTAINER_ENTRY
                );

        result.containerPresent(
                container != null
        );

        if (container == null) {
            return null;
        }

        String xml =
                readTextEntry(
                        zipFile,
                        container
                );

        String packagePath =
                extractAttributeValue(
                        xml,
                        "full-path"
                );

        packagePath =
                normalizeOptionalEpubPath(
                        packagePath
                );

        result.packageDocumentPath(
                packagePath
        );

        if (packagePath != null) {

            result.packageDocumentPresent(
                    zipFile.getEntry(
                            packagePath
                    ) != null
            );
        }

        return packagePath;
    }

    /**
     * OPF Package Document의 주요 정보를 조회합니다.
     */
    private void inspectPackageDocument(
            ZipFile zipFile,
            String packagePath,
            EpubInspectionResult.Builder result
    ) throws IOException {

        ZipEntry packageEntry =
                zipFile.getEntry(
                        packagePath
                );

        if (packageEntry == null) {
            return;
        }

        String xml =
                readTextEntry(
                        zipFile,
                        packageEntry
                );

        result.epubVersion(
                extractAttributeValueFromElement(
                        xml,
                        "package",
                        "version"
                )
        );

        String uniqueIdentifierId =
                extractAttributeValueFromElement(
                        xml,
                        "package",
                        "unique-identifier"
                );

        result.uniqueIdentifierId(
                uniqueIdentifierId
        );

        result.title(
                extractElementText(
                        xml,
                        "dc:title"
                )
        );

        result.language(
                extractElementText(
                        xml,
                        "dc:language"
                )
        );

        result.creator(
                extractElementText(
                        xml,
                        "dc:creator"
                )
        );

        if (uniqueIdentifierId != null) {

            result.identifier(
                    extractElementTextById(
                            xml,
                            "dc:identifier",
                            uniqueIdentifierId
                    )
            );

        } else {

            result.identifier(
                    extractElementText(
                            xml,
                            "dc:identifier"
                    )
            );
        }

        List<String> manifestItems =
                extractElements(
                        xml,
                        "item"
                );

        result.manifestItemCount(
                manifestItems.size()
        );

        int navigationItemCount = 0;
        int ncxItemCount = 0;

        for (String item : manifestItems) {

            String properties =
                    extractAttributeValue(
                            item,
                            "properties"
                    );

            String mediaType =
                    extractAttributeValue(
                            item,
                            "media-type"
                    );

            if (containsToken(
                    properties,
                    "nav"
            )) {

                navigationItemCount++;
            }

            if ("application/x-dtbncx+xml"
                    .equalsIgnoreCase(
                            mediaType
                    )) {

                ncxItemCount++;
            }
        }

        result.navigationDocumentPresent(
                navigationItemCount > 0
        );

        result.ncxPresent(
                ncxItemCount > 0
        );

        List<String> spineItems =
                extractElements(
                        xml,
                        "itemref"
                );

        result.spineItemCount(
                spineItems.size()
        );

        result.linearSpineItemCount(
                countLinearSpineItems(
                        spineItems
                )
        );

        String spineElement =
                extractStartTag(
                        xml,
                        "spine"
                );

        if (spineElement != null) {

            result.spineToc(
                    extractAttributeValue(
                            spineElement,
                            "toc"
                    )
            );

            result.pageProgressionDirection(
                    extractAttributeValue(
                            spineElement,
                            "page-progression-direction"
                    )
            );
        }

        inspectAccessibilityMetadata(
                xml,
                result
        );
    }

    /**
     * OPF 접근성 metadata 존재 여부를 조회합니다.
     */
    private void inspectAccessibilityMetadata(
            String xml,
            EpubInspectionResult.Builder result
    ) {

        if (xml == null) {
            return;
        }

        result.accessModePresent(
                xml.contains(
                        "schema:accessMode"
                )
        );

        result.accessibilityFeaturePresent(
                xml.contains(
                        "schema:accessibilityFeature"
                )
        );

        result.accessibilityHazardPresent(
                xml.contains(
                        "schema:accessibilityHazard"
                )
        );

        result.accessibilitySummaryPresent(
                xml.contains(
                        "schema:accessibilitySummary"
                )
        );
    }

    private int countLinearSpineItems(
            List<String> spineItems
    ) {

        int count = 0;

        for (String item : spineItems) {

            String linear =
                    extractAttributeValue(
                            item,
                            "linear"
                    );

            /*
             * EPUB의 linear 기본값은 yes입니다.
             */
            if (linear == null
                    || !"no".equalsIgnoreCase(
                            linear
                    )) {

                count++;
            }
        }

        return count;
    }

    /**
     * Inspection 결과를 공통 ToolResult로 변환합니다.
     */
    private ToolResult convertResult(
            EpubInspectionResult result
    ) {

        Objects.requireNonNull(
                result,
                "EPUB inspection result must not be null."
        );

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        /*
                         * 구조 경고가 있더라도 inspection 자체가
                         * 정상적으로 수행되었다면 SUCCESS입니다.
                         */
                        .status(
                                ToolStatus.SUCCESS
                        )
                        .message(
                                result.createSummary()
                        )
                        .data(
                                "inspectionResult",
                                result
                        )
                        .data(
                                "epubFile",
                                result.getEpubFile()
                                        .toString()
                        )
                        .data(
                                "fileSize",
                                result.getFileSize()
                        )
                        .data(
                                "entryCount",
                                result.getEntryCount()
                        )
                        .data(
                                "xhtmlCount",
                                result.getXhtmlCount()
                        )
                        .data(
                                "cssCount",
                                result.getCssCount()
                        )
                        .data(
                                "imageCount",
                                result.getImageCount()
                        )
                        .data(
                                "fontCount",
                                result.getFontCount()
                        )
                        .data(
                                "audioCount",
                                result.getAudioCount()
                        )
                        .data(
                                "videoCount",
                                result.getVideoCount()
                        )
                        .data(
                                "manifestItemCount",
                                result.getManifestItemCount()
                        )
                        .data(
                                "spineItemCount",
                                result.getSpineItemCount()
                        )
                        .data(
                                "linearSpineItemCount",
                                result.getLinearSpineItemCount()
                        )
                        .data(
                                "navigationDocumentPresent",
                                result.isNavigationDocumentPresent()
                        )
                        .data(
                                "ncxPresent",
                                result.isNcxPresent()
                        )
                        .data(
                                "mimetypePresent",
                                result.isMimetypePresent()
                        )
                        .data(
                                "mimetypeValid",
                                result.isMimetypeValid()
                        )
                        .data(
                                "mimetypeStored",
                                result.isMimetypeStored()
                        )
                        .data(
                                "mimetypeFirstEntry",
                                result.isMimetypeFirstEntry()
                        )
                        .data(
                                "containerPresent",
                                result.isContainerPresent()
                        )
                        .data(
                                "packageDocumentPresent",
                                result.isPackageDocumentPresent()
                        )
                        .data(
                                "accessModePresent",
                                result.isAccessModePresent()
                        )
                        .data(
                                "accessibilityFeaturePresent",
                                result.isAccessibilityFeaturePresent()
                        )
                        .data(
                                "accessibilityHazardPresent",
                                result.isAccessibilityHazardPresent()
                        )
                        .data(
                                "accessibilitySummaryPresent",
                                result.isAccessibilitySummaryPresent()
                        )
                        .data(
                                "basicAccessibilityMetadata",
                                result.hasBasicAccessibilityMetadata()
                        )
                        .data(
                                "structuralWarnings",
                                result.hasStructuralWarnings()
                        )
                        .data(
                                "entryPaths",
                                result.getEntryPaths()
                        );

        result.getMimetype()
                .ifPresent(
                        value ->
                                builder.data(
                                        "mimetype",
                                        value
                                )
                );

        result.getEpubVersion()
                .ifPresent(
                        value ->
                                builder.data(
                                        "epubVersion",
                                        value
                                )
                );

        result.getTitle()
                .ifPresent(
                        value ->
                                builder.data(
                                        "title",
                                        value
                                )
                );

        result.getLanguage()
                .ifPresent(
                        value ->
                                builder.data(
                                        "language",
                                        value
                                )
                );

        result.getCreator()
                .ifPresent(
                        value ->
                                builder.data(
                                        "creator",
                                        value
                                )
                );

        result.getIdentifier()
                .ifPresent(
                        value ->
                                builder.data(
                                        "identifier",
                                        value
                                )
                );

        result.getUniqueIdentifierId()
                .ifPresent(
                        value ->
                                builder.data(
                                        "uniqueIdentifierId",
                                        value
                                )
                );

        result.getPackageDocumentPath()
                .ifPresent(
                        value ->
                                builder.data(
                                        "packageDocumentPath",
                                        value
                                )
                );

        result.getSpineToc()
                .ifPresent(
                        value ->
                                builder.data(
                                        "spineToc",
                                        value
                                )
                );

        result.getPageProgressionDirection()
                .ifPresent(
                        value ->
                                builder.data(
                                        "pageProgressionDirection",
                                        value
                                )
                );

        appendStructuralIssues(
                result,
                builder
        );

        return builder.build();
    }

    /**
     * 조회만으로 확인할 수 있는 구조 경고를 추가합니다.
     *
     * <p>정식 유효성 판단은 ValidateEpubTool의 책임입니다.</p>
     */
    private void appendStructuralIssues(
            EpubInspectionResult result,
            ToolResult.Builder builder
    ) {

        if (!result.isMimetypePresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_MIMETYPE_MISSING",
                            "The EPUB archive does not contain mimetype."
                    )
            );

        } else {

            if (!result.isMimetypeValid()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_INVALID",
                                "The EPUB mimetype value is invalid."
                        )
                );
            }

            if (!result.isMimetypeStored()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_COMPRESSED",
                                "The EPUB mimetype entry is compressed."
                        )
                );
            }

            if (!result.isMimetypeFirstEntry()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_ORDER",
                                "The mimetype entry is not "
                                        + "the first ZIP entry."
                        )
                );
            }
        }

        if (!result.isContainerPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_CONTAINER_MISSING",
                            "META-INF/container.xml is missing."
                    )
            );
        }

        if (result.getPackageDocumentPath()
                .isEmpty()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_PACKAGE_PATH_MISSING",
                            "The package document path "
                                    + "could not be determined."
                    )
            );

        } else if (!result.isPackageDocumentPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_PACKAGE_MISSING",
                            "The package document referenced "
                                    + "by container.xml is missing."
                    )
            );
        }

        if (result.getEpubVersion()
                .map(
                        value ->
                                value.startsWith("3")
                )
                .orElse(false)
                && !result.isNavigationDocumentPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_NAV_MISSING",
                            "EPUB 3 package does not appear "
                                    + "to contain a Navigation Document."
                    )
            );
        }

        if (result.getSpineItemCount() == 0) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_SPINE_EMPTY",
                            "The EPUB spine contains no itemref elements."
                    )
            );
        }
    }

    /**
     * ToolRequest 또는 ToolContext에서 EPUB 파일 경로를 조회합니다.
     *
     * <p>우선순위:</p>
     *
     * <ol>
     *     <li>ToolRequest.arguments</li>
     *     <li>ToolContext.epubFile</li>
     * </ol>
     */
    private Path resolveEpubFile(
            ToolRequest request,
            ToolContext context
    ) {

        if (request == null) {
            return null;
        }

        /*
         * 1. ToolRequest.arguments
         */
        Path path =
                resolvePathFromArguments(
                        request.getArguments()
                );

        if (path != null) {
            return path;
        }

        /*
         * 2. ToolContext
         */
        if (context != null) {

            Object value =
                    context.getAttribute(
                            EPUB_FILE_ARGUMENT
                    );

            path =
                    toPath(
                            value
                    );

            if (path != null) {
                return path;
            }
        }

        return null;
    }

    /**
     * ToolRequest.arguments에서 EPUB 경로를 조회합니다.
     */
    private Path resolvePathFromArguments(
            Object arguments
    ) {

        if (arguments == null) {
            return null;
        }

        Path directPath =
                toPath(
                        arguments
                );

        if (directPath != null) {
            return directPath;
        }

        /*
         * LLM Tool 호출의 arguments는 일반적으로 Map 구조입니다.
         */
        if (arguments instanceof Map<?, ?> map) {

            Object value =
                    map.get(
                            EPUB_FILE_ARGUMENT
                    );

            return toPath(
                    value
            );
        }

        return null;
    }

    /**
     * 객체를 Path로 변환합니다.
     */
    private Path toPath(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Path path) {
            return path;
        }

        if (value instanceof String text) {

            String normalized =
                    text.trim();

            if (normalized.isEmpty()) {
                return null;
            }

            return Path.of(
                    normalized
            );
        }

        return null;
    }

    /**
     * Tool 입력 Schema입니다.
     */
    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                Map.of(
                        EPUB_FILE_ARGUMENT,
                        Map.of(
                                "type",
                                "string",
                                "description",
                                "Path of the EPUB file to inspect."
                        )
                )
        );

        schema.put(
                "required",
                List.of(
                        EPUB_FILE_ARGUMENT
                )
        );

        return Map.copyOf(
                schema
        );
    }

    /**
     * 실패 ToolResult를 생성합니다.
     */
    private ToolResult failure(
            String errorCode,
            String errorMessage,
            Path epubFile,
            Throwable cause
    ) {

        String code =
                errorCode == null
                        || errorCode.isBlank()
                                ? "EPUB_INSPECT_FAILED"
                                : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                                ? "EPUB inspection failed."
                                : errorMessage.trim();

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.FAILED
                        )
                        .message(
                                message
                        )
                        .errorCode(
                                code
                        )
                        .errorMessage(
                                message
                        )
                        .issue(
                                errorIssue(
                                        code,
                                        message
                                )
                        );

        if (epubFile != null) {

            builder.data(
                    "epubFile",
                    epubFile
                            .toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        if (cause != null) {

            builder.cause(
                    cause
            );

            builder.data(
                    "exceptionType",
                    cause.getClass()
                            .getName()
            );
        }

        return builder.build();
    }

    private ToolIssue errorIssue(
            String code,
            String message
    ) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .code(
                        code
                )
                .message(
                        message
                )
                .build();
    }

    private ToolIssue warningIssue(
            String code,
            String message
    ) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.WARNING
                )
                .code(
                        code
                )
                .message(
                        message
                )
                .build();
    }

    private static String readTextEntry(
            ZipFile zipFile,
            ZipEntry entry
    ) throws IOException {

        try (InputStream input =
                zipFile.getInputStream(
                        entry
                )) {

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
        }
    }

    /**
     * XML 시작 태그의 속성값을 추출합니다.
     *
     * <p>Inspection 용도의 경량 파서이며,
     * 정식 XML/EPUB 검증은 Validator 계층에서 수행합니다.</p>
     */
    private static String extractAttributeValue(
            String text,
            String attributeName
    ) {

        if (text == null
                || attributeName == null) {

            return null;
        }

        String doubleQuote =
                attributeName + "=\"";

        int start =
                text.indexOf(
                        doubleQuote
                );

        char quote = '"';

        if (start < 0) {

            String singleQuote =
                    attributeName + "='";

            start =
                    text.indexOf(
                            singleQuote
                    );

            if (start < 0) {
                return null;
            }

            quote = '\'';

            start +=
                    singleQuote.length();

        } else {

            start +=
                    doubleQuote.length();
        }

        int end =
                text.indexOf(
                        quote,
                        start
                );

        if (end < 0) {
            return null;
        }

        return decodeXmlEntities(
                text.substring(
                        start,
                        end
                ).trim()
        );
    }

    private static String extractAttributeValueFromElement(
            String xml,
            String elementName,
            String attributeName
    ) {

        String startTag =
                extractStartTag(
                        xml,
                        elementName
                );

        if (startTag == null) {
            return null;
        }

        return extractAttributeValue(
                startTag,
                attributeName
        );
    }

    private static String extractStartTag(
            String xml,
            String elementName
    ) {

        if (xml == null
                || elementName == null) {

            return null;
        }

        int start =
                findElementStart(
                        xml,
                        elementName,
                        0
                );

        if (start < 0) {
            return null;
        }

        int end =
                xml.indexOf(
                        '>',
                        start
                );

        if (end < 0) {
            return null;
        }

        return xml.substring(
                start,
                end + 1
        );
    }

    private static String extractElementText(
            String xml,
            String elementName
    ) {

        if (xml == null
                || elementName == null) {

            return null;
        }

        int start =
                findElementStart(
                        xml,
                        elementName,
                        0
                );

        if (start < 0) {
            return null;
        }

        int contentStart =
                xml.indexOf(
                        '>',
                        start
                );

        if (contentStart < 0) {
            return null;
        }

        String localName =
                localName(
                        elementName
                );

        int end =
                findClosingElement(
                        xml,
                        elementName,
                        localName,
                        contentStart + 1
                );

        if (end < 0) {
            return null;
        }

        String value =
                stripXmlTags(
                        xml.substring(
                                contentStart + 1,
                                end
                        )
                );

        return normalizeOptionalText(
                decodeXmlEntities(
                        value
                )
        );
    }

    private static String extractElementTextById(
            String xml,
            String elementName,
            String id
    ) {

        if (xml == null
                || id == null) {

            return null;
        }

        int offset = 0;

        while (true) {

            int start =
                    findElementStart(
                            xml,
                            elementName,
                            offset
                    );

            if (start < 0) {
                return null;
            }

            int tagEnd =
                    xml.indexOf(
                            '>',
                            start
                    );

            if (tagEnd < 0) {
                return null;
            }

            String tag =
                    xml.substring(
                            start,
                            tagEnd + 1
                    );

            String currentId =
                    extractAttributeValue(
                            tag,
                            "id"
                    );

            if (id.equals(
                    currentId
            )) {

                int close =
                        findClosingElement(
                                xml,
                                elementName,
                                localName(
                                        elementName
                                ),
                                tagEnd + 1
                        );

                if (close < 0) {
                    return null;
                }

                return normalizeOptionalText(
                        decodeXmlEntities(
                                stripXmlTags(
                                        xml.substring(
                                                tagEnd + 1,
                                                close
                                        )
                                )
                        )
                );
            }

            offset =
                    tagEnd + 1;
        }
    }

    /**
     * 지정한 element의 시작 태그 목록을 반환합니다.
     */
    private static List<String> extractElements(
            String xml,
            String elementName
    ) {

        List<String> result =
                new ArrayList<>();

        if (xml == null
                || elementName == null) {

            return result;
        }

        int offset = 0;

        while (true) {

            int start =
                    findElementStart(
                            xml,
                            elementName,
                            offset
                    );

            if (start < 0) {
                break;
            }

            int end =
                    xml.indexOf(
                            '>',
                            start
                    );

            if (end < 0) {
                break;
            }

            result.add(
                    xml.substring(
                            start,
                            end + 1
                    )
            );

            offset =
                    end + 1;
        }

        return result;
    }

    private static int findElementStart(
            String xml,
            String elementName,
            int offset
    ) {

        String direct =
                "<" + elementName;

        int index =
                xml.indexOf(
                        direct,
                        offset
                );

        if (index >= 0) {
            return index;
        }

        /*
         * namespace prefix가 다른 일반 element도 지원합니다.
         */
        if (!elementName.contains(":")) {

            String suffix =
                    ":" + elementName;

            int scan =
                    xml.indexOf(
                            '<',
                            offset
                    );

            while (scan >= 0) {

                int end =
                        xml.indexOf(
                                '>',
                                scan
                        );

                if (end < 0) {
                    break;
                }

                String tag =
                        xml.substring(
                                scan,
                                end + 1
                        );

                int nameEnd =
                        findNameEnd(
                                tag
                        );

                if (nameEnd > 1) {

                    String name =
                            tag.substring(
                                    1,
                                    nameEnd
                            );

                    if (name.equals(
                            elementName
                    )
                            || name.endsWith(
                                    suffix
                            )) {

                        return scan;
                    }
                }

                scan =
                        xml.indexOf(
                                '<',
                                end + 1
                        );
            }
        }

        return -1;
    }

    private static int findClosingElement(
            String xml,
            String elementName,
            String localName,
            int offset
    ) {

        int direct =
                xml.indexOf(
                        "</"
                                + elementName
                                + ">",
                        offset
                );

        if (direct >= 0) {
            return direct;
        }

        int scan =
                xml.indexOf(
                        "</",
                        offset
                );

        while (scan >= 0) {

            int end =
                    xml.indexOf(
                            '>',
                            scan
                    );

            if (end < 0) {
                return -1;
            }

            String name =
                    xml.substring(
                            scan + 2,
                            end
                    ).trim();

            if (name.equals(
                    elementName
            )
                    || localName(
                            name
                    ).equals(
                            localName
                    )) {

                return scan;
            }

            scan =
                    xml.indexOf(
                            "</",
                            end + 1
                    );
        }

        return -1;
    }

    private static int findNameEnd(
            String tag
    ) {

        for (int index = 1;
                index < tag.length();
                index++) {

            char character =
                    tag.charAt(
                            index
                    );

            if (Character.isWhitespace(
                    character
            )
                    || character == '>'
                    || character == '/') {

                return index;
            }
        }

        return -1;
    }

    private static String localName(
            String value
    ) {

        if (value == null) {
            return "";
        }

        int index =
                value.indexOf(':');

        return index < 0
                ? value
                : value.substring(
                        index + 1
                );
    }

    private static boolean containsToken(
            String values,
            String target
    ) {

        if (values == null
                || target == null) {

            return false;
        }

        for (String value :
                values.trim()
                        .split("\\s+")) {

            if (target.equals(
                    value
            )) {

                return true;
            }
        }

        return false;
    }

    private static String stripXmlTags(
            String value
    ) {

        if (value == null) {
            return null;
        }

        return value.replaceAll(
                "<[^>]+>",
                ""
        );
    }

    private static String decodeXmlEntities(
            String value
    ) {

        if (value == null) {
            return null;
        }

        return value
                .replace(
                        "&lt;",
                        "<"
                )
                .replace(
                        "&gt;",
                        ">"
                )
                .replace(
                        "&quot;",
                        "\""
                )
                .replace(
                        "&apos;",
                        "'"
                )
                .replace(
                        "&amp;",
                        "&"
                );
    }

    private static String normalizeEpubPath(
            String value
    ) {

        if (value == null) {
            return "";
        }

        String normalized =
                value.trim()
                        .replace(
                                '\\',
                                '/'
                        );

        while (normalized.startsWith("/")) {

            normalized =
                    normalized.substring(
                            1
                    );
        }

        while (normalized.startsWith("./")) {

            normalized =
                    normalized.substring(
                            2
                    );
        }

        return normalized;
    }

    private static String normalizeOptionalEpubPath(
            String value
    ) {

        String normalized =
                normalizeOptionalText(
                        value
                );

        return normalized == null
                ? null
                : normalizeEpubPath(
                        normalized
                );
    }

    private static String normalizeOptionalText(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    private static boolean isImageFile(
            String value
    ) {

        return value.endsWith(".png")
                || value.endsWith(".jpg")
                || value.endsWith(".jpeg")
                || value.endsWith(".gif")
                || value.endsWith(".svg")
                || value.endsWith(".webp");
    }

    private static boolean isFontFile(
            String value
    ) {

        return value.endsWith(".ttf")
                || value.endsWith(".otf")
                || value.endsWith(".woff")
                || value.endsWith(".woff2");
    }

    private static boolean isAudioFile(
            String value
    ) {

        return value.endsWith(".mp3")
                || value.endsWith(".m4a")
                || value.endsWith(".aac")
                || value.endsWith(".ogg")
                || value.endsWith(".wav");
    }

    private static boolean isVideoFile(
            String value
    ) {

        return value.endsWith(".mp4")
                || value.endsWith(".webm")
                || value.endsWith(".m4v");
    }

    private static String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {

            return "Unknown EPUB inspection error.";
        }

        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        return message.trim();
    }

    /**
     * EPUB 구조 조회 결과입니다.
     */
    public static final class EpubInspectionResult {

        private final Path epubFile;

        private final long fileSize;

        private final int entryCount;

        private final boolean mimetypePresent;

        private final boolean mimetypeValid;

        private final boolean mimetypeStored;

        private final boolean mimetypeFirstEntry;

        private final String mimetype;

        private final boolean containerPresent;

        private final String packageDocumentPath;

        private final boolean packageDocumentPresent;

        private final String epubVersion;

        private final String title;

        private final String language;

        private final String creator;

        private final String identifier;

        private final String uniqueIdentifierId;

        private final int manifestItemCount;

        private final int spineItemCount;

        private final int linearSpineItemCount;

        private final String spineToc;

        private final String pageProgressionDirection;

        private final boolean navigationDocumentPresent;

        private final boolean ncxPresent;

        private final int xhtmlCount;

        private final int cssCount;

        private final int imageCount;

        private final int fontCount;

        private final int audioCount;

        private final int videoCount;

        private final boolean accessModePresent;

        private final boolean accessibilityFeaturePresent;

        private final boolean accessibilityHazardPresent;

        private final boolean accessibilitySummaryPresent;

        private final List<String> entryPaths;

        private EpubInspectionResult(
                Builder builder
        ) {

            this.epubFile =
                    Objects.requireNonNull(
                            builder.epubFile,
                            "EPUB file must not be null."
                    )
                            .toAbsolutePath()
                            .normalize();

            this.fileSize =
                    builder.fileSize;

            this.entryCount =
                    builder.entryCount;

            this.mimetypePresent =
                    builder.mimetypePresent;

            this.mimetypeValid =
                    builder.mimetypeValid;

            this.mimetypeStored =
                    builder.mimetypeStored;

            this.mimetypeFirstEntry =
                    builder.mimetypeFirstEntry;

            this.mimetype =
                    normalizeOptionalText(
                            builder.mimetype
                    );

            this.containerPresent =
                    builder.containerPresent;

            this.packageDocumentPath =
                    normalizeOptionalEpubPath(
                            builder.packageDocumentPath
                    );

            this.packageDocumentPresent =
                    builder.packageDocumentPresent;

            this.epubVersion =
                    normalizeOptionalText(
                            builder.epubVersion
                    );

            this.title =
                    normalizeOptionalText(
                            builder.title
                    );

            this.language =
                    normalizeOptionalText(
                            builder.language
                    );

            this.creator =
                    normalizeOptionalText(
                            builder.creator
                    );

            this.identifier =
                    normalizeOptionalText(
                            builder.identifier
                    );

            this.uniqueIdentifierId =
                    normalizeOptionalText(
                            builder.uniqueIdentifierId
                    );

            this.manifestItemCount =
                    builder.manifestItemCount;

            this.spineItemCount =
                    builder.spineItemCount;

            this.linearSpineItemCount =
                    builder.linearSpineItemCount;

            this.spineToc =
                    normalizeOptionalText(
                            builder.spineToc
                    );

            this.pageProgressionDirection =
                    normalizeOptionalText(
                            builder.pageProgressionDirection
                    );

            this.navigationDocumentPresent =
                    builder.navigationDocumentPresent;

            this.ncxPresent =
                    builder.ncxPresent;

            this.xhtmlCount =
                    builder.xhtmlCount;

            this.cssCount =
                    builder.cssCount;

            this.imageCount =
                    builder.imageCount;

            this.fontCount =
                    builder.fontCount;

            this.audioCount =
                    builder.audioCount;

            this.videoCount =
                    builder.videoCount;

            this.accessModePresent =
                    builder.accessModePresent;

            this.accessibilityFeaturePresent =
                    builder.accessibilityFeaturePresent;

            this.accessibilityHazardPresent =
                    builder.accessibilityHazardPresent;

            this.accessibilitySummaryPresent =
                    builder.accessibilitySummaryPresent;

            this.entryPaths =
                    List.copyOf(
                            builder.entryPaths
                    );
        }

        public static Builder builder() {
            return new Builder();
        }

        public Path getEpubFile() {
            return epubFile;
        }

        public long getFileSize() {
            return fileSize;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public boolean isMimetypePresent() {
            return mimetypePresent;
        }

        public boolean isMimetypeValid() {
            return mimetypeValid;
        }

        public boolean isMimetypeStored() {
            return mimetypeStored;
        }

        public boolean isMimetypeFirstEntry() {
            return mimetypeFirstEntry;
        }

        public Optional<String> getMimetype() {

            return Optional.ofNullable(
                    mimetype
            );
        }

        public boolean isContainerPresent() {
            return containerPresent;
        }

        public Optional<String>
                getPackageDocumentPath() {

            return Optional.ofNullable(
                    packageDocumentPath
            );
        }

        public boolean isPackageDocumentPresent() {
            return packageDocumentPresent;
        }

        public Optional<String> getEpubVersion() {

            return Optional.ofNullable(
                    epubVersion
            );
        }

        public Optional<String> getTitle() {

            return Optional.ofNullable(
                    title
            );
        }

        public Optional<String> getLanguage() {

            return Optional.ofNullable(
                    language
            );
        }

        public Optional<String> getCreator() {

            return Optional.ofNullable(
                    creator
            );
        }

        public Optional<String> getIdentifier() {

            return Optional.ofNullable(
                    identifier
            );
        }

        public Optional<String>
                getUniqueIdentifierId() {

            return Optional.ofNullable(
                    uniqueIdentifierId
            );
        }

        public int getManifestItemCount() {
            return manifestItemCount;
        }

        public int getSpineItemCount() {
            return spineItemCount;
        }

        public int getLinearSpineItemCount() {
            return linearSpineItemCount;
        }

        public Optional<String> getSpineToc() {

            return Optional.ofNullable(
                    spineToc
            );
        }

        public Optional<String>
                getPageProgressionDirection() {

            return Optional.ofNullable(
                    pageProgressionDirection
            );
        }

        public boolean
                isNavigationDocumentPresent() {

            return navigationDocumentPresent;
        }

        public boolean isNcxPresent() {
            return ncxPresent;
        }

        public int getXhtmlCount() {
            return xhtmlCount;
        }

        public int getCssCount() {
            return cssCount;
        }

        public int getImageCount() {
            return imageCount;
        }

        public int getFontCount() {
            return fontCount;
        }

        public int getAudioCount() {
            return audioCount;
        }

        public int getVideoCount() {
            return videoCount;
        }

        public boolean isAccessModePresent() {
            return accessModePresent;
        }

        public boolean
                isAccessibilityFeaturePresent() {

            return accessibilityFeaturePresent;
        }

        public boolean
                isAccessibilityHazardPresent() {

            return accessibilityHazardPresent;
        }

        public boolean
                isAccessibilitySummaryPresent() {

            return accessibilitySummaryPresent;
        }

        public List<String> getEntryPaths() {
            return entryPaths;
        }

        /**
         * 조회만으로 확인 가능한 기본 구조 경고가 있는지 확인합니다.
         */
        public boolean hasStructuralWarnings() {

            if (!mimetypePresent
                    || !mimetypeValid
                    || !mimetypeStored
                    || !mimetypeFirstEntry
                    || !containerPresent
                    || packageDocumentPath == null
                    || !packageDocumentPresent
                    || spineItemCount == 0) {

                return true;
            }

            return epubVersion != null
                    && epubVersion.startsWith("3")
                    && !navigationDocumentPresent;
        }

        /**
         * EPUB 접근성 metadata 기본 구성이 존재하는지 확인합니다.
         */
        public boolean
                hasBasicAccessibilityMetadata() {

            return accessModePresent
                    && accessibilityFeaturePresent
                    && accessibilityHazardPresent;
        }

        /**
         * Inspection 요약 문자열을 생성합니다.
         */
        public String createSummary() {

            StringBuilder result =
                    new StringBuilder();

            result.append(
                    "EPUB inspection completed"
            );

            if (title != null) {

                result.append(": ")
                        .append(
                                title
                        );
            }

            result.append(" [version=")
                    .append(
                            epubVersion == null
                                    ? "unknown"
                                    : epubVersion
                    )
                    .append(", manifest=")
                    .append(
                            manifestItemCount
                    )
                    .append(", spine=")
                    .append(
                            spineItemCount
                    )
                    .append(", xhtml=")
                    .append(
                            xhtmlCount
                    )
                    .append(", images=")
                    .append(
                            imageCount
                    )
                    .append(", nav=")
                    .append(
                            navigationDocumentPresent
                    )
                    .append(", ncx=")
                    .append(
                            ncxPresent
                    )
                    .append(']');

            return result.toString();
        }

        @Override
        public String toString() {
            return createSummary();
        }

        /**
         * EpubInspectionResult Builder입니다.
         */
        public static final class Builder {

            private Path epubFile;

            private long fileSize;

            private int entryCount;

            private boolean mimetypePresent;

            private boolean mimetypeValid;

            private boolean mimetypeStored;

            private boolean mimetypeFirstEntry;

            private String mimetype;

            private boolean containerPresent;

            private String packageDocumentPath;

            private boolean packageDocumentPresent;

            private String epubVersion;

            private String title;

            private String language;

            private String creator;

            private String identifier;

            private String uniqueIdentifierId;

            private int manifestItemCount;

            private int spineItemCount;

            private int linearSpineItemCount;

            private String spineToc;

            private String pageProgressionDirection;

            private boolean navigationDocumentPresent;

            private boolean ncxPresent;

            private int xhtmlCount;

            private int cssCount;

            private int imageCount;

            private int fontCount;

            private int audioCount;

            private int videoCount;

            private boolean accessModePresent;

            private boolean accessibilityFeaturePresent;

            private boolean accessibilityHazardPresent;

            private boolean accessibilitySummaryPresent;

            private final List<String> entryPaths =
                    new ArrayList<>();

            private Builder() {
            }

            public Builder epubFile(
                    Path value
            ) {

                this.epubFile = value;
                return this;
            }

            public Builder fileSize(
                    long value
            ) {

                this.fileSize = value;
                return this;
            }

            public Builder entryCount(
                    int value
            ) {

                this.entryCount = value;
                return this;
            }

            public Builder mimetypePresent(
                    boolean value
            ) {

                this.mimetypePresent = value;
                return this;
            }

            public Builder mimetypeValid(
                    boolean value
            ) {

                this.mimetypeValid = value;
                return this;
            }

            public Builder mimetypeStored(
                    boolean value
            ) {

                this.mimetypeStored = value;
                return this;
            }

            public Builder mimetypeFirstEntry(
                    boolean value
            ) {

                this.mimetypeFirstEntry = value;
                return this;
            }

            public Builder mimetype(
                    String value
            ) {

                this.mimetype = value;
                return this;
            }

            public Builder containerPresent(
                    boolean value
            ) {

                this.containerPresent = value;
                return this;
            }

            public Builder packageDocumentPath(
                    String value
            ) {

                this.packageDocumentPath = value;
                return this;
            }

            public Builder packageDocumentPresent(
                    boolean value
            ) {

                this.packageDocumentPresent = value;
                return this;
            }

            public Builder epubVersion(
                    String value
            ) {

                this.epubVersion = value;
                return this;
            }

            public Builder title(
                    String value
            ) {

                this.title = value;
                return this;
            }

            public Builder language(
                    String value
            ) {

                this.language = value;
                return this;
            }

            public Builder creator(
                    String value
            ) {

                this.creator = value;
                return this;
            }

            public Builder identifier(
                    String value
            ) {

                this.identifier = value;
                return this;
            }

            public Builder uniqueIdentifierId(
                    String value
            ) {

                this.uniqueIdentifierId = value;
                return this;
            }

            public Builder manifestItemCount(
                    int value
            ) {

                this.manifestItemCount = value;
                return this;
            }

            public Builder spineItemCount(
                    int value
            ) {

                this.spineItemCount = value;
                return this;
            }

            public Builder linearSpineItemCount(
                    int value
            ) {

                this.linearSpineItemCount = value;
                return this;
            }

            public Builder spineToc(
                    String value
            ) {

                this.spineToc = value;
                return this;
            }

            public Builder pageProgressionDirection(
                    String value
            ) {

                this.pageProgressionDirection =
                        value;

                return this;
            }

            public Builder navigationDocumentPresent(
                    boolean value
            ) {

                this.navigationDocumentPresent =
                        value;

                return this;
            }

            public Builder ncxPresent(
                    boolean value
            ) {

                this.ncxPresent = value;
                return this;
            }

            public Builder xhtmlCount(
                    int value
            ) {

                this.xhtmlCount = value;
                return this;
            }

            public Builder cssCount(
                    int value
            ) {

                this.cssCount = value;
                return this;
            }

            public Builder imageCount(
                    int value
            ) {

                this.imageCount = value;
                return this;
            }

            public Builder fontCount(
                    int value
            ) {

                this.fontCount = value;
                return this;
            }

            public Builder audioCount(
                    int value
            ) {

                this.audioCount = value;
                return this;
            }

            public Builder videoCount(
                    int value
            ) {

                this.videoCount = value;
                return this;
            }

            public Builder accessModePresent(
                    boolean value
            ) {

                this.accessModePresent = value;
                return this;
            }

            public Builder accessibilityFeaturePresent(
                    boolean value
            ) {

                this.accessibilityFeaturePresent =
                        value;

                return this;
            }

            public Builder accessibilityHazardPresent(
                    boolean value
            ) {

                this.accessibilityHazardPresent =
                        value;

                return this;
            }

            public Builder accessibilitySummaryPresent(
                    boolean value
            ) {

                this.accessibilitySummaryPresent =
                        value;

                return this;
            }

            public Builder entryPath(
                    String value
            ) {

                if (value != null
                        && !value.isBlank()) {

                    entryPaths.add(
                            value
                    );
                }

                return this;
            }

            public Builder entryPaths(
                    Iterable<String> values
            ) {

                if (values == null) {
                    return this;
                }

                for (String value : values) {

                    entryPath(
                            value
                    );
                }

                return this;
            }

            public EpubInspectionResult build() {

                return new EpubInspectionResult(
                        this
                );
            }
        }
    }
}