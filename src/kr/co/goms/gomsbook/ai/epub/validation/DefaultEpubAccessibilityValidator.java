/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssue;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityLocation;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilitySeverity;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationResult;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue.Severity;

/**
 * 기존 Accessibility 계층을 EPUB 검증 계층과 연결하는
 * 기본 접근성 Validator 구현체입니다.
 *
 * <p>이 클래스는 접근성 규칙을 직접 구현하지 않습니다.</p>
 *
 * <p>기존 {@link AccessibilityValidator}를 이용하여 XHTML 문서를
 * 검사한 후 {@link AccessibilityIssue}를
 * {@link EpubValidationIssue}로 변환합니다.</p>
 *
 * <p>지원하는 검증 방식은 다음과 같습니다.</p>
 *
 * <ul>
 *     <li>EPUB Package + manifest 기반 생성 전 검증</li>
 *     <li>EPUB 작업 디렉터리 전체 검증</li>
 *     <li>개별 XHTML 검증</li>
 *     <li>최종 .epub 아카이브 검증</li>
 * </ul>
 */
public final class DefaultEpubAccessibilityValidator
        implements EpubAccessibilityValidator {

    private static final String VALIDATOR_NAME =
            "GomsBook EPUB Accessibility Validator";

    private static final String VALIDATOR_VERSION =
            "1.0";

    private final AccessibilityValidator accessibilityValidator;

    /**
     * 기존 AccessibilityValidator를 주입받습니다.
     *
     * @param accessibilityValidator 접근성 검증기
     */
    public DefaultEpubAccessibilityValidator(
            AccessibilityValidator accessibilityValidator
    ) {
        this.accessibilityValidator =
                Objects.requireNonNull(
                        accessibilityValidator,
                        "Accessibility validator must not be null."
                );
    }

    /**
     * EPUB Package 모델을 기반으로 접근성을 검증합니다.
     *
     * <p>manifest에 등록된 XHTML 리소스 중 실제 sourcePath가
     * 존재하는 문서를 대상으로 접근성 검증을 수행합니다.</p>
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) {

        Instant startedAt = Instant.now();

        EpubValidationResult.Builder result =
                createResultBuilder()
                        .startedAt(startedAt);

        if (pathConfiguration != null) {
            result.target(
                    pathConfiguration.getOutputFile()
                            .toString()
            );
        }

        try {
            validateInput(
                    epubPackage,
                    pathConfiguration,
                    options
            );

            List<EpubValidationIssue> issues =
                    new ArrayList<>();

            validatePackageAccessibilityMetadata(
                    epubPackage,
                    issues
            );

            validateManifestResources(
            		projectRoot,
                    epubPackage.getManifest(),
                    pathConfiguration,
                    options,
                    issues
            );

            result.issues(issues)
                    .completedAt(Instant.now())
                    .message(
                            createResultMessage(issues)
                    );

        } catch (RuntimeException exception) {
            result.cause(exception)
                    .completedAt(Instant.now())
                    .message(
                            "EPUB accessibility validation "
                                    + "could not be completed."
                    );
        }

        return result.build();
    }

    /**
     * 개별 XHTML 파일의 접근성을 검증합니다.
     */
    @Override
    public EpubValidationResult validateXhtml(
            Path projectRoot,
            Path xhtmlFile,
            String epubPath,
            EpubGenerationOptions options
    ) {

        Instant startedAt =
                Instant.now();

        EpubValidationResult.Builder result =
                createResultBuilder()
                        .target(
                                xhtmlFile == null
                                        ? null
                                        : xhtmlFile
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString()
                        )
                        .startedAt(startedAt);

        try {

            validateXhtmlInput(
                    xhtmlFile,
                    epubPath,
                    options
            );

            Objects.requireNonNull(
                    projectRoot,
                    "Accessibility project root must not be null."
            );

            Path normalizedRoot =
                    projectRoot
                            .toAbsolutePath()
                            .normalize();

            Path normalizedFile =
                    xhtmlFile
                            .toAbsolutePath()
                            .normalize();

            if (!normalizedFile.startsWith(
                    normalizedRoot
            )) {

                throw new IllegalArgumentException(
                        "XHTML file must be located inside "
                                + "the accessibility project root: "
                                + normalizedFile
                );
            }

            AccessibilityValidationResult
                    accessibilityResult =
                    accessibilityValidator.validate(
                            normalizedRoot,
                            normalizedFile
                    );

            List<EpubValidationIssue> issues =
                    convertIssues(
                            accessibilityResult,
                            normalizeEpubPath(epubPath),
                            normalizedFile
                    );

            result.issues(issues)
                    .completedAt(
                            Instant.now()
                    )
                    .message(
                            createResultMessage(
                                    issues
                            )
                    );

        } catch (RuntimeException exception) {

            result.cause(exception)
                    .completedAt(
                            Instant.now()
                    )
                    .message(
                            "XHTML accessibility validation "
                                    + "could not be completed."
                    );
        }

        return result.build();
    }

    /**
     * EPUB 작업 디렉터리에 생성된 실제 XHTML 문서들을 검증합니다.
     */
    @Override
    public EpubValidationResult validateWorkspace(
    		Path projectRoot,
            Path workingDirectory,
            Path packageDocumentPath,
            EpubGenerationOptions options
    ) {

        Instant startedAt = Instant.now();

        EpubValidationResult.Builder result =
                createResultBuilder()
                        .target(
                                workingDirectory == null
                                        ? null
                                        : workingDirectory
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString()
                        )
                        .startedAt(startedAt);

        try {
            validateWorkspaceInput(
                    workingDirectory,
                    packageDocumentPath,
                    options
            );

            Path root =
                    workingDirectory
                            .toAbsolutePath()
                            .normalize();

            List<EpubValidationIssue> issues =
                    new ArrayList<>();

            List<Path> xhtmlFiles =
                    findXhtmlFiles(root);

            if (xhtmlFiles.isEmpty()) {
                issues.add(
                        EpubValidationIssue.builder(
                                "EPUB-A11Y-XHTML-001",
                                EpubValidationIssue.Severity.ERROR,
                                "EPUB workspace contains no XHTML documents."
                        )
                                .category(
                                        EpubValidationIssue.Category.XHTML
                                )
                                .filePath(root)
                                .validator(getName())
                                .build()
                );
            }

            for (Path xhtmlFile : xhtmlFiles) {

                String epubPath =
                        root.relativize(
                                xhtmlFile
                        )
                                .toString()
                                .replace('\\', '/');

                AccessibilityValidationResult
                        accessibilityResult =
                        accessibilityValidator.validate(
                        		projectRoot,
                                xhtmlFile
                        );

                issues.addAll(
                        convertIssues(
                                accessibilityResult,
                                epubPath,
                                xhtmlFile
                        )
                );
            }

            result.issues(issues)
                    .completedAt(Instant.now())
                    .message(
                            createResultMessage(issues)
                    );

        } catch (RuntimeException exception) {
            result.cause(exception)
                    .completedAt(Instant.now())
                    .message(
                            "EPUB workspace accessibility validation "
                                    + "could not be completed."
                    );
        }

        return result.build();
    }

    /**
     * 최종 EPUB ZIP 파일의 접근성을 검증합니다.
     *
     * <p>ZIP 파일 시스템으로 EPUB을 열고 XHTML 파일을 임시 파일로
     * 복사한 뒤 기존 AccessibilityValidator에 전달합니다.</p>
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    ) {

        Instant startedAt = Instant.now();

        EpubValidationResult.Builder result =
                createResultBuilder()
                        .target(
                                epubFile == null
                                        ? null
                                        : epubFile
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString()
                        )
                        .startedAt(startedAt);

        Path temporaryDirectory = null;

        try {
            validateInput(
                    epubFile,
                    options
            );

            Path normalizedEpubFile =
                    epubFile.toAbsolutePath().normalize();

            if (!Files.isRegularFile(normalizedEpubFile)) {
                return result
                        .issue(
                                EpubValidationIssue.builder(
                                        "EPUB-A11Y-ARCHIVE-001",
                                        EpubValidationIssue.Severity.FATAL,
                                        "EPUB archive does not exist."
                                )
                                        .category(
                                                EpubValidationIssue.Category
                                                        .ACCESSIBILITY
                                        )
                                        .filePath(normalizedEpubFile)
                                        .validator(getName())
                                        .build()
                        )
                        .completedAt(Instant.now())
                        .build();
            }

            temporaryDirectory =
                    Files.createTempDirectory(
                            "gomsbook-epub-a11y-"
                    );

            List<EpubValidationIssue> issues =
                    validateArchiveContents(
                    		projectRoot,
                            normalizedEpubFile,
                            temporaryDirectory
                    );

            result.issues(issues)
                    .completedAt(Instant.now())
                    .message(
                            createResultMessage(issues)
                    );

        } catch (Exception exception) {

            result.cause(exception)
                    .completedAt(Instant.now())
                    .message(
                            "EPUB archive accessibility validation "
                                    + "could not be completed."
                    );

        } finally {

            if (temporaryDirectory != null) {
                deleteTemporaryDirectory(
                        temporaryDirectory
                );
            }
        }

        return result.build();
    }

    /**
     * manifest에 등록된 XHTML source 리소스를 검증합니다.
     */
    private void validateManifestResources(
    		Path projectRoot,
            EpubManifest manifest,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options,
            List<EpubValidationIssue> issues
    ) {

        if (manifest == null) {
            issues.add(
                    EpubValidationIssue.builder(
                            "EPUB-A11Y-MANIFEST-001",
                            EpubValidationIssue.Severity.FATAL,
                            "EPUB manifest is missing."
                    )
                            .category(
                                    EpubValidationIssue.Category.MANIFEST
                            )
                            .validator(getName())
                            .build()
            );

            return;
        }

        for (EpubResource resource :
                manifest.getResources()) {

            if (!shouldValidateResource(resource)) {
                continue;
            }

            validateManifestResource(
            		projectRoot,
                    resource,
                    pathConfiguration,
                    options,
                    issues
            );
        }
    }

    /**
     * 단일 manifest XHTML 리소스를 검증합니다.
     */
    private void validateManifestResource(
    		Path projectRoot,
            EpubResource resource,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options,
            List<EpubValidationIssue> issues
    ) {

        String epubPath =
                normalizeEpubPath(
                        resource.getHref()
                );

        if (resource.hasSourcePath()) {

            String source =
                    resource.getSourcePath()
                            .orElse(null);

            if (source != null) {

                Path sourcePath;

                try {
                    sourcePath =
                            Path.of(source)
                                    .toAbsolutePath()
                                    .normalize();

                } catch (RuntimeException exception) {

                    issues.add(
                            EpubValidationIssue.builder(
                                    "EPUB-A11Y-RESOURCE-001",
                                    EpubValidationIssue.Severity.ERROR,
                                    "Invalid XHTML source path."
                            )
                                    .category(
                                            EpubValidationIssue.Category.XHTML
                                    )
                                    .resourceId(
                                            resource.getId()
                                    )
                                    .epubPath(epubPath)
                                    .actualValue(source)
                                    .originalMessage(
                                            exception.getMessage()
                                    )
                                    .validator(getName())
                                    .build()
                    );

                    return;
                }

                if (!Files.isRegularFile(sourcePath)) {

                    issues.add(
                            EpubValidationIssue.builder(
                                    "EPUB-A11Y-RESOURCE-002",
                                    EpubValidationIssue.Severity.ERROR,
                                    "XHTML source file does not exist."
                            )
                                    .category(
                                            EpubValidationIssue.Category.XHTML
                                    )
                                    .resourceId(
                                            resource.getId()
                                    )
                                    .epubPath(epubPath)
                                    .filePath(sourcePath)
                                    .validator(getName())
                                    .build()
                    );

                    return;
                }

                AccessibilityValidationResult
                        accessibilityResult =
                        accessibilityValidator.validate(
                        		projectRoot,
                                sourcePath
                        );

                issues.addAll(
                        convertIssues(
                                accessibilityResult,
                                epubPath,
                                sourcePath
                        )
                );

                return;
            }
        }

        /*
         * 메모리 콘텐츠는 임시 파일로 생성하여 기존 Validator를
         * 그대로 사용할 수 있습니다.
         */
        if (resource.hasContent()) {

            validateEmbeddedXhtml(
                    projectRoot,
                    resource,
                    epubPath,
                    issues
            );

            return;
        }

        /*
         * nav.xhtml 등은 이후 Workspace 단계에서 생성될 수 있습니다.
         * 따라서 생성 전 단계에서는 WARNING만 발생시킵니다.
         */
        if (resource.isNavigationDocument()) {
            return;
        }

        if (options.isFailOnMissingResource()) {

            issues.add(
                    EpubValidationIssue.builder(
                            "EPUB-A11Y-RESOURCE-003",
                            EpubValidationIssue.Severity.WARNING,
                            "XHTML resource could not be accessibility "
                                    + "validated before generation."
                    )
                            .category(
                                    EpubValidationIssue.Category.XHTML
                            )
                            .resourceId(
                                    resource.getId()
                            )
                            .epubPath(epubPath)
                            .validator(getName())
                            .build()
            );
        }
    }

    /**
     * byte[] 기반 XHTML을 임시 파일로 생성하여 검증합니다.
     */
    private void validateEmbeddedXhtml(
    		Path projectRoot,
            EpubResource resource,
            String epubPath,
            List<EpubValidationIssue> issues
    ) {

        Path tempFile = null;

        try {
            tempFile =
                    Files.createTempFile(
                            "gomsbook-xhtml-",
                            ".xhtml"
                    );

            byte[] content =
                    resource.getContent()
                            .orElseThrow();

            Files.write(
                    tempFile,
                    content
            );

            AccessibilityValidationResult
                    accessibilityResult =
                    accessibilityValidator.validate(
                    		projectRoot,
                            tempFile
                    );

            issues.addAll(
                    convertIssues(
                            accessibilityResult,
                            epubPath,
                            tempFile
                    )
            );

        } catch (Exception exception) {

            issues.add(
                    EpubValidationIssue.builder(
                            "EPUB-A11Y-XHTML-002",
                            EpubValidationIssue.Severity.ERROR,
                            "Embedded XHTML accessibility validation failed."
                    )
                            .category(
                                    EpubValidationIssue.Category.XHTML
                            )
                            .resourceId(resource.getId())
                            .epubPath(epubPath)
                            .originalMessage(
                                    exception.getMessage()
                            )
                            .validator(getName())
                            .build()
            );

        } finally {

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 검증 결과에 영향을 주지 않음.
                }
            }
        }
    }

    /**
     * EPUB 접근성 metadata 기본 검증입니다.
     *
     * <p>현재 단계에서는 accessibility metadata의 존재 여부를
     * 경고 수준으로 검사합니다.</p>
     */
    private void validatePackageAccessibilityMetadata(
            EpubPackage epubPackage,
            List<EpubValidationIssue> issues
    ) {

        if (epubPackage.getMetadata() == null) {
            return;
        }

        boolean hasAccessibilityMetadata =
                epubPackage.getMetadata()
                        .getEntries()
                        .stream()
                        .anyMatch(entry -> {

                            String property =
                                    entry.getProperty()
                                            .orElse("");

                            return property.startsWith(
                                    "schema:access"
                            );
                        });

        if (!hasAccessibilityMetadata) {

            issues.add(
                    EpubValidationIssue.builder(
                            "EPUB-A11Y-METADATA-001",
                            EpubValidationIssue.Severity.WARNING,
                            "EPUB accessibility metadata is not defined."
                    )
                            .category(
                                    EpubValidationIssue.Category
                                            .ACCESSIBILITY
                            )
                            .suggestion(
                                    "schema:accessMode, "
                                            + "schema:accessibilityFeature, "
                                            + "schema:accessibilityHazard 등의 "
                                            + "접근성 메타데이터를 검토하십시오."
                            )
                            .validator(getName())
                            .build()
            );
        }
    }

    /**
     * 기존 AccessibilityValidationResult를 EPUB 검증 이슈로 변환합니다.
     */
    private List<EpubValidationIssue> convertIssues(
            AccessibilityValidationResult source,
            String epubPath,
            Path sourceFile
    ) {

        if (source == null
                || source.getIssues() == null
                || source.getIssues().isEmpty()) {

            return List.of();
        }

        List<EpubValidationIssue> result =
                new ArrayList<>();

        for (AccessibilityIssue issue :
                source.getIssues()) {

            if (issue == null) {
                continue;
            }

            result.add(
                    convertIssue(
                            issue,
                            epubPath,
                            sourceFile
                    )
            );
        }

        return List.copyOf(result);
    }

    /**
     * AccessibilityIssue 하나를 EpubValidationIssue로 변환합니다.
     */
    private EpubValidationIssue convertIssue(
            AccessibilityIssue issue,
            String epubPath,
            Path sourceFile
    ) {

        Objects.requireNonNull(
                issue,
                "Accessibility issue must not be null."
        );

        EpubValidationIssue.Builder builder =
                EpubValidationIssue.builder(
                        resolveIssueCode(issue),
                        mapSeverity(
                                issue.getSeverity()
                        ),
                        resolveMessage(issue)
                )
                        .category(
                                mapCategory(issue)
                        )
                        .epubPath(epubPath)
                        .filePath(sourceFile)
                        .validator(getName())
                        .originalMessage(
                                resolveMessage(issue)
                        );

        /*
         * AccessibilityLocation 변환
         */
        if (issue.getLocation() != null) {

            AccessibilityLocation location = issue.getLocation();

            /*
             * 실제 AccessibilityValidationResult에서
             * getLineNumber() API 사용이 확인됨.
             */
            Integer lineNumber =
                    location.getLineNumber();

            if (lineNumber != null
                    && lineNumber >= 0) {

                builder.line(
                        lineNumber
                );
            }

            /*
             * XPath 정보는 EPUB ValidationIssue의 detail로 보존합니다.
             */
            String xpath =
                    location.getXpath();

            if (xpath != null
                    && !xpath.isBlank()) {

                builder.detail(
                        "xpath",
                        xpath
                );
            }

            String projectRelativePath =
                    location.getProjectRelativePath();

            if (projectRelativePath != null
                    && !projectRelativePath.isBlank()) {

                builder.detail(
                        "projectRelativePath",
                        projectRelativePath
                );
            }

            Path documentPath =
                    location.getDocumentPath();

            if (documentPath != null) {

                builder.detail(
                        "documentPath",
                        documentPath.toString()
                );
            }
        }

        /*
         * 실제 AccessibilityIssue API에서
         * isAutomaticallyFixable()이 사용됨.
         */
        if (issue.isAutomaticallyFixable()) {

            builder.autoFixable(true);
        }

        /*
         * 수동 검토 필요 여부도 보존합니다.
         */
        if (issue.isManualReviewRequired()) {

            builder.detail(
                    "manualReviewRequired",
                    "true"
            );
        }

        /*
         * 출판 차단 여부도 보존합니다.
         */
        if (issue.blocksPublication()) {

            builder.detail(
                    "blocksPublication",
                    "true"
            );
        }

        return builder.build();
    }

    /**
     * 접근성 Severity를 EPUB 검증 Severity로 변환합니다.
     */
    private EpubValidationIssue.Severity mapSeverity(
            AccessibilitySeverity severity
    ) {

        if (severity == null) {
            return EpubValidationIssue.Severity.WARNING;
        }

        switch (severity) {

            case INFO:
                return EpubValidationIssue.Severity.INFO;

            case WARNING:
                return EpubValidationIssue.Severity.WARNING;

            case ERROR:
                return EpubValidationIssue.Severity.ERROR;

            default:
                return EpubValidationIssue.Severity.WARNING;
        }
    }

    /**
     * 접근성 IssueCode를 EPUB 검증 Category로 변환합니다.
     */
    private EpubValidationIssue.Category mapCategory(
            AccessibilityIssue issue
    ) {

        if (issue == null
                || issue.getCode() == null) {

            return EpubValidationIssue.Category.ACCESSIBILITY;
        }

        String code =
                issue.getCode()
                        .name()
                        .toUpperCase(Locale.ROOT);

        if (code.contains("ALT")
                || code.contains("IMAGE")) {

            return EpubValidationIssue.Category
                    .ALTERNATIVE_TEXT;
        }

        if (code.contains("LANG")) {
            return EpubValidationIssue.Category.LANGUAGE;
        }

        if (code.contains("HEADING")) {
            return EpubValidationIssue.Category.HEADING;
        }

        if (code.contains("LINK")) {
            return EpubValidationIssue.Category.LINK;
        }

        if (code.contains("TABLE")) {
            return EpubValidationIssue.Category.ACCESSIBILITY;
        }

        if (code.contains("ARIA")) {
            return EpubValidationIssue.Category.ACCESSIBILITY;
        }

        return EpubValidationIssue.Category.ACCESSIBILITY;
    }

    /**
     * EPUB 검증용 오류 코드를 생성합니다.
     */
    private String resolveIssueCode(
            AccessibilityIssue issue
    ) {

        if (issue == null
                || issue.getCode() == null) {

            return "EPUB-A11Y-GENERAL-001";
        }

        return "EPUB-A11Y-"
                + issue.getCode().name();
    }

    private String resolveMessage(
            AccessibilityIssue issue
    ) {

        if (issue == null) {
            return "Unknown accessibility issue.";
        }

        String message =
                issue.getMessage();

        if (message == null || message.isBlank()) {
            return "Accessibility validation issue: "
                    + resolveIssueCode(issue);
        }

        return message.trim();
    }

    /**
     * manifest resource가 접근성 검사 대상인지 확인합니다.
     */
    private boolean shouldValidateResource(
            EpubResource resource
    ) {

        if (resource == null
                || !resource.isIncluded()
                || resource.isRemote()) {

            return false;
        }

        String mediaType =
                resource.getMediaType();

        if (mediaType == null) {
            return false;
        }

        return "application/xhtml+xml"
                .equalsIgnoreCase(mediaType);
    }

    /**
     * Workspace 내 XHTML 파일을 찾습니다.
     */
    private List<Path> findXhtmlFiles(
            Path root
    ) {

        try (var stream = Files.walk(root)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(
                            this::isXhtmlFile
                    )
                    .sorted()
                    .toList();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to enumerate EPUB XHTML files: "
                            + root,
                    exception
            );
        }
    }

    private boolean isXhtmlFile(
            Path path
    ) {

        if (path == null
                || path.getFileName() == null) {

            return false;
        }

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT);

        return name.endsWith(".xhtml")
                || name.endsWith(".html")
                || name.endsWith(".htm");
    }

    /**
     * EPUB ZIP 내부 XHTML을 임시 디렉터리에 복사하여 검증합니다.
     */
    private List<EpubValidationIssue> validateArchiveContents(
    		Path projectRoot,
            Path epubFile,
            Path temporaryDirectory
    ) throws IOException {

        List<EpubValidationIssue> issues =
                new ArrayList<>();

        Map<String, String> environment =
                new HashMap<>();

        environment.put("create", "false");

        try (FileSystem zipFileSystem =
                FileSystems.newFileSystem(
                        epubFile,
                        environment
                )) {

            Path zipRoot =
                    zipFileSystem.getPath("/");

            try (var stream = Files.walk(zipRoot)) {

                List<Path> xhtmlFiles =
                        stream
                                .filter(Files::isRegularFile)
                                .filter(this::isXhtmlFile)
                                .toList();

                for (Path archiveXhtml :
                        xhtmlFiles) {

                    String epubPath =
                            normalizeArchivePath(
                                    archiveXhtml
                            );

                    Path tempFile =
                            resolveTemporaryXhtmlPath(
                                    temporaryDirectory,
                                    epubPath
                            );

                    Path parent =
                            tempFile.getParent();

                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    Files.copy(
                            archiveXhtml,
                            tempFile,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                    AccessibilityValidationResult
                            accessibilityResult =
                            accessibilityValidator.validate(
                            		projectRoot,
                                    tempFile
                            );

                    issues.addAll(
                            convertIssues(
                                    accessibilityResult,
                                    epubPath,
                                    tempFile
                            )
                    );
                }
            }
        }

        return List.copyOf(issues);
    }

    private Path resolveTemporaryXhtmlPath(
            Path temporaryDirectory,
            String epubPath
    ) {

        String normalized =
                epubPath.replace('\\', '/');

        while (normalized.startsWith("/")) {
            normalized =
                    normalized.substring(1);
        }

        Path result =
                temporaryDirectory
                        .resolve(
                                normalized.replace(
                                        '/',
                                        java.io.File.separatorChar
                                )
                        )
                        .normalize();

        if (!result.startsWith(
                temporaryDirectory
                        .toAbsolutePath()
                        .normalize()
        )) {

            throw new IllegalArgumentException(
                    "Invalid EPUB archive path: "
                            + epubPath
            );
        }

        return result;
    }

    private String normalizeArchivePath(
            Path archivePath
    ) {

        String value =
                archivePath.toString()
                        .replace('\\', '/');

        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        return value;
    }

    /**
     * 임시 검증 디렉터리를 삭제합니다.
     */
    private void deleteTemporaryDirectory(
            Path directory
    ) {

        if (directory == null
                || !Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {

            List<Path> paths =
                    stream
                            .sorted(
                                    java.util.Comparator
                                            .reverseOrder()
                            )
                            .toList();

            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 원래 검증 결과를 보존하기 위해 무시합니다.
                }
            }

        } catch (IOException ignored) {
            // 검증 임시 파일 정리 실패는 검증 결과에 영향을 주지 않음.
        }
    }

    private EpubValidationResult.Builder createResultBuilder() {

        return EpubValidationResult.builder()
                .validatorName(getName())
                .validatorVersion(getVersion());
    }

    private String createResultMessage(
            List<EpubValidationIssue> issues
    ) {

        if (issues == null || issues.isEmpty()) {
            return "EPUB accessibility validation completed successfully.";
        }

        long errors =
                issues.stream()
                        .filter(
                                EpubValidationIssue::isBlocking
                        )
                        .count();

        long warnings =
                issues.stream()
                        .filter(
                                EpubValidationIssue::isWarning
                        )
                        .count();

        return "EPUB accessibility validation completed. "
                + "blocking="
                + errors
                + ", warnings="
                + warnings;
    }

    private static String normalizeEpubPath(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim()
                        .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized =
                    normalized.substring(2);
        }

        return normalized;
    }

    @Override
    public boolean supportsPackageValidation() {
        return true;
    }

    @Override
    public boolean supportsArchiveValidation() {
        return true;
    }

    @Override
    public String getName() {
        return VALIDATOR_NAME;
    }

    @Override
    public String getVersion() {
        return VALIDATOR_VERSION;
    }

    @Override
    public Type getType() {
        return Type.ACCESSIBILITY;
    }

    /**
     * 기존 AccessibilityValidator를 반환합니다.
     */
    public AccessibilityValidator getAccessibilityValidator() {
        return accessibilityValidator;
    }
}