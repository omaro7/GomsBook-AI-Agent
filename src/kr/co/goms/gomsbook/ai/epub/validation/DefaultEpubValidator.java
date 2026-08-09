/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcxNavPoint;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue.Category;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue.Severity;

/**
 * EPUB 내부 구조를 검증하는 기본 Validator 구현체입니다.
 *
 * <p>외부 EPUBCheck에 의존하지 않고 GomsBook EPUB 계층 자체에서
 * 확인할 수 있는 기본적인 EPUB 무결성을 검증합니다.</p>
 *
 * <p>생성 전 검증 항목:</p>
 *
 * <ul>
 *     <li>EPUB Package 기본 무결성</li>
 *     <li>Metadata 필수 정보</li>
 *     <li>Manifest ID/HREF 중복</li>
 *     <li>Spine → Manifest 참조</li>
 *     <li>EPUB 3 Navigation Document 존재</li>
 *     <li>NCX 설정 일관성</li>
 *     <li>리소스 원본 파일 존재 여부</li>
 *     <li>작업 경로 안전성</li>
 * </ul>
 *
 * <p>생성 후 검증 항목:</p>
 *
 * <ul>
 *     <li>EPUB ZIP 파일 존재 및 크기</li>
 *     <li>첫 번째 ZIP 엔트리가 mimetype인지 확인</li>
 *     <li>mimetype이 STORED 방식인지 확인</li>
 *     <li>mimetype 내용 정확성 확인</li>
 *     <li>META-INF/container.xml 존재</li>
 *     <li>container.xml에서 OPF 경로 추출</li>
 *     <li>OPF 패키지 문서 존재</li>
 *     <li>ZIP 엔트리 경로 기본 안전성</li>
 * </ul>
 *
 * <p>EPUBCheck 및 접근성 검증은 별도의 Validator 구현체가
 * 담당합니다.</p>
 */
public final class DefaultEpubValidator
        implements EpubValidator {

    private static final String VALIDATOR_NAME =
            "GomsBook EPUB Validator";

    private static final String VALIDATOR_VERSION =
            "1.0";

    private static final String MIMETYPE_ENTRY =
            "mimetype";

    private static final String MIMETYPE_VALUE =
            "application/epub+zip";

    private static final String CONTAINER_ENTRY =
            "META-INF/container.xml";

    /**
     * 생성 전 EPUB Package 모델을 검증합니다.
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
                EpubValidationResult.builder()
                        .validatorName(getName())
                        .validatorVersion(getVersion())
                        .startedAt(startedAt);

        if (pathConfiguration != null) {
            result.target(
                    pathConfiguration.getOutputFile().toString()
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

            validatePackage(
                    epubPackage,
                    issues
            );

            validateMetadata(
                    epubPackage,
                    issues
            );

            validateManifest(
                    epubPackage.getManifest(),
                    options,
                    issues
            );

            validateSpine(
                    epubPackage.getManifest(),
                    epubPackage.getSpine(),
                    issues
            );

            validateNavigation(
                    epubPackage,
                    options,
                    issues
            );

            validateNcx(
                    epubPackage,
                    options,
                    issues
            );

            validatePaths(
                    pathConfiguration,
                    issues
            );

            result.issues(issues)
                    .completedAt(Instant.now())
                    .message(
                            issues.isEmpty()
                                    ? "EPUB package validation completed successfully."
                                    : "EPUB package validation completed."
                    );

        } catch (RuntimeException exception) {

            result.cause(exception)
                    .completedAt(Instant.now())
                    .message(
                            "EPUB package validation could not be completed."
                    );
        }

        return result.build();
    }

    /**
     * 생성된 실제 EPUB ZIP 파일을 검증합니다.
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    ) {

        Instant startedAt = Instant.now();

        EpubValidationResult.Builder result =
                EpubValidationResult.builder()
                        .validatorName(getName())
                        .validatorVersion(getVersion())
                        .startedAt(startedAt);

        if (epubFile != null) {
            result.target(
                    epubFile.toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        try {
            validateInput(
                    epubFile,
                    options
            );

            Path normalized =
                    epubFile.toAbsolutePath().normalize();

            List<EpubValidationIssue> issues =
                    new ArrayList<>();

            validateArchiveFile(
                    normalized,
                    issues
            );

            if (Files.isRegularFile(normalized)) {
                validateZipArchive(
                        normalized,
                        issues
                );
            }

            result.issues(issues)
                    .completedAt(Instant.now())
                    .message(
                            issues.isEmpty()
                                    ? "EPUB archive validation completed successfully."
                                    : "EPUB archive validation completed."
                    );

        } catch (RuntimeException exception) {

            result.cause(exception)
                    .completedAt(Instant.now())
                    .message(
                            "EPUB archive validation could not be completed."
                    );
        }

        return result.build();
    }

    /**
     * EpubPackage 자체의 모델 검증을 수행합니다.
     */
    private void validatePackage(
            EpubPackage epubPackage,
            List<EpubValidationIssue> issues
    ) {

        try {
            epubPackage.validate();

        } catch (RuntimeException exception) {

            issues.add(
                    issue(
                            "EPUB-PACKAGE-001",
                            Severity.ERROR,
                            Category.PACKAGE_DOCUMENT,
                            "EPUB package model validation failed."
                    )
                            .originalMessage(
                                    exception.getMessage()
                            )
                            .build()
            );
        }

        if (epubPackage.getUniqueIdentifierValue() == null
                || epubPackage.getUniqueIdentifierValue()
                        .isBlank()) {

            issues.add(
                    issue(
                            "EPUB-METADATA-IDENTIFIER-001",
                            Severity.ERROR,
                            Category.METADATA,
                            "EPUB package requires a unique identifier."
                    )
                            .suggestion(
                                    "dc:identifier를 설정하십시오."
                            )
                            .build()
            );
        }

        if (epubPackage.getTitle().isEmpty()) {
            issues.add(
                    issue(
                            "EPUB-METADATA-TITLE-001",
                            Severity.ERROR,
                            Category.METADATA,
                            "EPUB package requires a title."
                    )
                            .suggestion(
                                    "dc:title을 설정하십시오."
                            )
                            .build()
            );
        }

        if (epubPackage.getLanguage() == null
                || epubPackage.getLanguage().isBlank()) {

            issues.add(
                    issue(
                            "EPUB-METADATA-LANGUAGE-001",
                            Severity.ERROR,
                            Category.LANGUAGE,
                            "EPUB package requires a language."
                    )
                            .suggestion(
                                    "BCP 47 형식의 dc:language를 설정하십시오."
                            )
                            .build()
            );
        }
    }

    /**
     * Metadata 기본 필수값을 검사합니다.
     */
    private void validateMetadata(
            EpubPackage epubPackage,
            List<EpubValidationIssue> issues
    ) {

        if (epubPackage.getMetadata() == null) {
            issues.add(
                    issue(
                            "EPUB-METADATA-001",
                            Severity.FATAL,
                            Category.METADATA,
                            "EPUB metadata is missing."
                    ).build()
            );

            return;
        }

        if (epubPackage.getMetadata()
                .getEntries()
                .isEmpty()) {

            issues.add(
                    issue(
                            "EPUB-METADATA-002",
                            Severity.ERROR,
                            Category.METADATA,
                            "EPUB metadata contains no entries."
                    ).build()
            );
        }
    }

    /**
     * Manifest의 ID, href 및 실제 리소스를 검증합니다.
     */
    private void validateManifest(
            EpubManifest manifest,
            EpubGenerationOptions options,
            List<EpubValidationIssue> issues
    ) {

        if (manifest == null) {
            issues.add(
                    issue(
                            "EPUB-MANIFEST-001",
                            Severity.FATAL,
                            Category.MANIFEST,
                            "EPUB manifest is missing."
                    ).build()
            );

            return;
        }

        if (manifest.getResources().isEmpty()) {
            issues.add(
                    issue(
                            "EPUB-MANIFEST-002",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "EPUB manifest contains no resources."
                    ).build()
            );

            return;
        }

        Set<String> ids =
                new HashSet<>();

        Set<String> hrefs =
                new HashSet<>();

        for (EpubResource resource :
                manifest.getResources()) {

            if (resource == null) {
                issues.add(
                        issue(
                                "EPUB-MANIFEST-003",
                                Severity.ERROR,
                                Category.MANIFEST,
                                "EPUB manifest contains a null resource."
                        ).build()
                );

                continue;
            }

            validateManifestResource(
                    resource,
                    options,
                    ids,
                    hrefs,
                    issues
            );
        }
    }

    private void validateManifestResource(
            EpubResource resource,
            EpubGenerationOptions options,
            Set<String> ids,
            Set<String> hrefs,
            List<EpubValidationIssue> issues
    ) {

        String id = resource.getId();

        String href = normalizeEpubPath(
                resource.getHref()
        );

        if (id == null || id.isBlank()) {
            issues.add(
                    issue(
                            "EPUB-MANIFEST-ID-001",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "Manifest resource has no id."
                    )
                            .epubPath(href)
                            .build()
            );

        } else if (!ids.add(id)) {

            issues.add(
                    issue(
                            "EPUB-MANIFEST-ID-002",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "Duplicate manifest resource id: "
                                    + id
                    )
                            .resourceId(id)
                            .epubPath(href)
                            .build()
            );
        }

        if (href == null || href.isBlank()) {

            issues.add(
                    issue(
                            "EPUB-MANIFEST-HREF-001",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "Manifest resource has no href."
                    )
                            .resourceId(id)
                            .build()
            );

        } else if (!hrefs.add(href)) {

            issues.add(
                    issue(
                            "EPUB-MANIFEST-HREF-002",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "Duplicate manifest href: "
                                    + href
                    )
                            .resourceId(id)
                            .epubPath(href)
                            .build()
            );
        }

        if (resource.getMediaType() == null
                || resource.getMediaType().isBlank()) {

            issues.add(
                    issue(
                            "EPUB-MANIFEST-MEDIATYPE-001",
                            Severity.ERROR,
                            Category.MANIFEST,
                            "Manifest resource has no media-type."
                    )
                            .resourceId(id)
                            .epubPath(href)
                            .build()
            );
        }

        if (!resource.isIncluded()) {
            return;
        }

        if (resource.isRemote()) {

            if (!options.isAllowRemoteResources()) {
                issues.add(
                        issue(
                                "EPUB-RESOURCE-REMOTE-001",
                                Severity.ERROR,
                                Category.RESOURCE,
                                "Remote resource is not permitted by "
                                        + "the current generation options."
                        )
                                .resourceId(id)
                                .epubPath(href)
                                .actualValue(
                                        resource.getHref()
                                )
                                .build()
                );
            }

            return;
        }

        /*
         * nav.xhtml과 toc.ncx는 전용 Writer가 생성할 수 있으므로
         * source/content가 없어도 여기서는 missing으로 처리하지 않습니다.
         */
        if (resource.isNavigationDocument()
                || resource.isNcx()) {
            return;
        }

        if (resource.hasContent()) {
            return;
        }

        if (resource.hasSourcePath()) {

            resource.getSourcePath().ifPresent(
                    source -> validateSourceFile(
                            resource,
                            source,
                            issues
                    )
            );

            return;
        }

        if (options.isFailOnMissingResource()) {

            issues.add(
                    issue(
                            "EPUB-RESOURCE-MISSING-001",
                            Severity.ERROR,
                            Category.RESOURCE,
                            "Manifest resource has no available source."
                    )
                            .resourceId(id)
                            .epubPath(href)
                            .build()
            );
        } else {

            issues.add(
                    issue(
                            "EPUB-RESOURCE-MISSING-002",
                            Severity.WARNING,
                            Category.RESOURCE,
                            "Manifest resource has no available source "
                                    + "and will be skipped."
                    )
                            .resourceId(id)
                            .epubPath(href)
                            .build()
            );
        }
    }

    /**
     * 리소스 sourcePath의 실제 파일을 확인합니다.
     */
    private void validateSourceFile(
            EpubResource resource,
            String source,
            List<EpubValidationIssue> issues
    ) {

        try {
            Path sourcePath =
                    Path.of(source)
                            .toAbsolutePath()
                            .normalize();

            if (!Files.exists(sourcePath)) {

                issues.add(
                        issue(
                                "EPUB-RESOURCE-FILE-001",
                                Severity.ERROR,
                                Category.RESOURCE,
                                "EPUB source file does not exist."
                        )
                                .resourceId(
                                        resource.getId()
                                )
                                .epubPath(
                                        resource.getHref()
                                )
                                .filePath(sourcePath)
                                .build()
                );

                return;
            }

            if (!Files.isRegularFile(sourcePath)) {

                issues.add(
                        issue(
                                "EPUB-RESOURCE-FILE-002",
                                Severity.ERROR,
                                Category.RESOURCE,
                                "EPUB resource source is not "
                                        + "a regular file."
                        )
                                .resourceId(
                                        resource.getId()
                                )
                                .epubPath(
                                        resource.getHref()
                                )
                                .filePath(sourcePath)
                                .build()
                );
            }

            if (!Files.isReadable(sourcePath)) {

                issues.add(
                        issue(
                                "EPUB-RESOURCE-FILE-003",
                                Severity.ERROR,
                                Category.RESOURCE,
                                "EPUB resource source is not readable."
                        )
                                .resourceId(
                                        resource.getId()
                                )
                                .epubPath(
                                        resource.getHref()
                                )
                                .filePath(sourcePath)
                                .build()
                );
            }

        } catch (RuntimeException exception) {

            issues.add(
                    issue(
                            "EPUB-RESOURCE-FILE-004",
                            Severity.ERROR,
                            Category.RESOURCE,
                            "Invalid EPUB resource source path."
                    )
                            .resourceId(
                                    resource.getId()
                            )
                            .epubPath(
                                    resource.getHref()
                            )
                            .actualValue(source)
                            .originalMessage(
                                    exception.getMessage()
                            )
                            .build()
            );
        }
    }

    /**
     * Spine의 idref가 manifest를 올바르게 참조하는지 검증합니다.
     */
    private void validateSpine(
            EpubManifest manifest,
            EpubSpine spine,
            List<EpubValidationIssue> issues
    ) {

        if (spine == null) {
            issues.add(
                    issue(
                            "EPUB-SPINE-001",
                            Severity.FATAL,
                            Category.SPINE,
                            "EPUB spine is missing."
                    ).build()
            );

            return;
        }

        if (spine.getItems().isEmpty()) {

            issues.add(
                    issue(
                            "EPUB-SPINE-002",
                            Severity.ERROR,
                            Category.SPINE,
                            "EPUB spine contains no reading-order items."
                    ).build()
            );

            return;
        }

        Set<String> idrefs =
                new HashSet<>();

        int linearCount = 0;

        for (EpubSpineItem spineItem :
                spine.getItems()) {

            if (spineItem == null) {
                issues.add(
                        issue(
                                "EPUB-SPINE-003",
                                Severity.ERROR,
                                Category.SPINE,
                                "EPUB spine contains a null item."
                        ).build()
                );

                continue;
            }

            String idref =
                    spineItem.getIdref();

            if (idref == null || idref.isBlank()) {

                issues.add(
                        issue(
                                "EPUB-SPINE-IDREF-001",
                                Severity.ERROR,
                                Category.SPINE,
                                "Spine item has no idref."
                        ).build()
                );

                continue;
            }

            if (!idrefs.add(idref)) {

                issues.add(
                        issue(
                                "EPUB-SPINE-IDREF-002",
                                Severity.WARNING,
                                Category.SPINE,
                                "Manifest resource is referenced "
                                        + "multiple times by spine: "
                                        + idref
                        )
                                .resourceId(idref)
                                .build()
                );
            }

            if (manifest.findById(idref).isEmpty()) {

                issues.add(
                        issue(
                                "EPUB-SPINE-IDREF-003",
                                Severity.ERROR,
                                Category.SPINE,
                                "Spine references a resource "
                                        + "that does not exist in manifest: "
                                        + idref
                        )
                                .resourceId(idref)
                                .build()
                );

                continue;
            }

            if (spineItem.isLinear()) {
                linearCount++;
            }
        }

        if (linearCount == 0) {

            issues.add(
                    issue(
                            "EPUB-SPINE-LINEAR-001",
                            Severity.ERROR,
                            Category.SPINE,
                            "EPUB spine has no linear reading-order item."
                    ).build()
            );
        }
    }

    /**
     * EPUB 3 Navigation Document 설정을 검증합니다.
     */
    private void validateNavigation(
            EpubPackage epubPackage,
            EpubGenerationOptions options,
            List<EpubValidationIssue> issues
    ) {

        if (!epubPackage.getVersion().isEpub3()) {
            return;
        }

        if (!options.requiresNavigationDocument()) {

            issues.add(
                    issue(
                            "EPUB-NAV-001",
                            Severity.ERROR,
                            Category.NAVIGATION,
                            "EPUB 3 requires a Navigation Document."
                    )
                            .suggestion(
                                    "nav.xhtml 생성 옵션을 활성화하십시오."
                            )
                            .build()
            );

            return;
        }

        if (epubPackage.getManifest()
                .getNavigationDocument()
                .isEmpty()) {

            issues.add(
                    issue(
                            "EPUB-NAV-002",
                            Severity.ERROR,
                            Category.NAVIGATION,
                            "EPUB 3 manifest does not contain "
                                    + "a resource with the nav property."
                    )
                            .suggestion(
                                    "nav.xhtml을 manifest에 등록하고 "
                                            + "properties=\"nav\"를 지정하십시오."
                            )
                            .build()
            );
        }
    }

    /**
     * NCX 설정을 검증합니다.
     */
    private void validateNcx(
            EpubPackage epubPackage,
            EpubGenerationOptions options,
            List<EpubValidationIssue> issues
    ) {

        boolean requiresNcx =
                options.requiresNcx();

        if (!requiresNcx) {
            return;
        }

        boolean hasNcx =
                epubPackage.getManifest()
                        .getResources()
                        .stream()
                        .anyMatch(
                                EpubResource::isNcx
                        );

        if (!hasNcx) {

            issues.add(
                    issue(
                            "EPUB-NCX-001",
                            Severity.ERROR,
                            Category.NCX,
                            "NCX generation is enabled but "
                                    + "toc.ncx is not registered "
                                    + "in the manifest."
                    )
                            .suggestion(
                                    "application/x-dtbncx+xml 리소스를 "
                                            + "manifest에 등록하십시오."
                            )
                            .build()
            );
        }
    }

    /**
     * 생성 작업에 사용하는 실제 경로를 검사합니다.
     */
    private void validatePaths(
            EpubPathConfiguration paths,
            List<EpubValidationIssue> issues
    ) {

        Path working =
                paths.getWorkingDirectory()
                        .toAbsolutePath()
                        .normalize();

        Path output =
                paths.getOutputFile()
                        .toAbsolutePath()
                        .normalize();

        if (working.equals(output)) {

            issues.add(
                    issue(
                            "EPUB-PATH-001",
                            Severity.FATAL,
                            Category.RESOURCE,
                            "Working directory and output file "
                                    + "must not be identical."
                    )
                            .filePath(output)
                            .build()
            );
        }

        if (output.startsWith(working)) {

            issues.add(
                    issue(
                            "EPUB-PATH-002",
                            Severity.ERROR,
                            Category.RESOURCE,
                            "EPUB output file must not be located "
                                    + "inside the working directory."
                    )
                            .filePath(output)
                            .detail(
                                    "workingDirectory",
                                    working.toString()
                            )
                            .build()
            );
        }

        String outputName =
                output.getFileName() == null
                        ? ""
                        : output.getFileName().toString();

        if (!outputName
                .toLowerCase(Locale.ROOT)
                .endsWith(".epub")) {

            issues.add(
                    issue(
                            "EPUB-PATH-003",
                            Severity.ERROR,
                            Category.RESOURCE,
                            "EPUB output file must use "
                                    + "the .epub extension."
                    )
                            .filePath(output)
                            .build()
            );
        }
    }

    /**
     * 실제 EPUB 출력 파일의 기본 상태를 검사합니다.
     */
    private void validateArchiveFile(
            Path epubFile,
            List<EpubValidationIssue> issues
    ) {

        if (!Files.exists(epubFile)) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-001",
                            Severity.FATAL,
                            Category.ARCHIVE,
                            "EPUB archive does not exist."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return;
        }

        if (!Files.isRegularFile(epubFile)) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-002",
                            Severity.FATAL,
                            Category.ARCHIVE,
                            "EPUB archive is not a regular file."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return;
        }

        if (!Files.isReadable(epubFile)) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-003",
                            Severity.FATAL,
                            Category.ARCHIVE,
                            "EPUB archive is not readable."
                    )
                            .filePath(epubFile)
                            .build()
            );
        }

        try {
            if (Files.size(epubFile) == 0L) {

                issues.add(
                        issue(
                                "EPUB-ARCHIVE-004",
                                Severity.FATAL,
                                Category.ARCHIVE,
                                "EPUB archive is empty."
                        )
                                .filePath(epubFile)
                                .build()
                );
            }

        } catch (IOException exception) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-005",
                            Severity.ERROR,
                            Category.ARCHIVE,
                            "Unable to determine EPUB archive size."
                    )
                            .filePath(epubFile)
                            .originalMessage(
                                    exception.getMessage()
                            )
                            .build()
            );
        }
    }

    /**
     * EPUB ZIP 내부 구조를 검사합니다.
     */
    private void validateZipArchive(
            Path epubFile,
            List<EpubValidationIssue> issues
    ) {

        try (ZipFile zipFile =
                new ZipFile(epubFile.toFile())) {

            validateZipEntries(
                    zipFile,
                    epubFile,
                    issues
            );

            validateMimetype(
                    zipFile,
                    epubFile,
                    issues
            );

            String packagePath =
                    validateContainer(
                            zipFile,
                            epubFile,
                            issues
                    );

            if (packagePath != null) {
                validatePackageDocumentEntry(
                        zipFile,
                        packagePath,
                        epubFile,
                        issues
                );
            }

        } catch (IOException exception) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-ZIP-001",
                            Severity.FATAL,
                            Category.ARCHIVE,
                            "EPUB file is not a readable ZIP archive."
                    )
                            .filePath(epubFile)
                            .originalMessage(
                                    exception.getMessage()
                            )
                            .build()
            );
        }
    }

    /**
     * 첫 번째 ZIP 엔트리와 경로를 검사합니다.
     */
    private void validateZipEntries(
            ZipFile zipFile,
            Path epubFile,
            List<EpubValidationIssue> issues
    ) {

        Enumeration<? extends ZipEntry> entries =
                zipFile.entries();

        if (!entries.hasMoreElements()) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-ZIP-002",
                            Severity.FATAL,
                            Category.ARCHIVE,
                            "EPUB ZIP archive contains no entries."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return;
        }

        ZipEntry first =
                entries.nextElement();

        if (!MIMETYPE_ENTRY.equals(first.getName())) {

            issues.add(
                    issue(
                            "EPUB-MIMETYPE-001",
                            Severity.ERROR,
                            Category.MIMETYPE,
                            "The first EPUB ZIP entry must be mimetype."
                    )
                            .filePath(epubFile)
                            .actualValue(
                                    first.getName()
                            )
                            .expectedValue(
                                    MIMETYPE_ENTRY
                            )
                            .build()
            );
        }

        if (MIMETYPE_ENTRY.equals(first.getName())
                && first.getMethod() != ZipEntry.STORED) {

            issues.add(
                    issue(
                            "EPUB-MIMETYPE-002",
                            Severity.ERROR,
                            Category.MIMETYPE,
                            "The EPUB mimetype entry must not "
                                    + "be compressed."
                    )
                            .filePath(epubFile)
                            .actualValue(
                                    zipMethodName(
                                            first.getMethod()
                                    )
                            )
                            .expectedValue("STORED")
                            .build()
            );
        }

        Set<String> names =
                new HashSet<>();

        names.add(first.getName());

        validateZipEntryName(
                first,
                epubFile,
                issues
        );

        while (entries.hasMoreElements()) {

            ZipEntry entry =
                    entries.nextElement();

            if (!names.add(entry.getName())) {

                issues.add(
                        issue(
                                "EPUB-ARCHIVE-ZIP-003",
                                Severity.ERROR,
                                Category.ARCHIVE,
                                "Duplicate ZIP entry: "
                                        + entry.getName()
                        )
                                .filePath(epubFile)
                                .epubPath(
                                        entry.getName()
                                )
                                .build()
                );
            }

            validateZipEntryName(
                    entry,
                    epubFile,
                    issues
            );
        }
    }

    private void validateZipEntryName(
            ZipEntry entry,
            Path epubFile,
            List<EpubValidationIssue> issues
    ) {

        String name =
                entry.getName();

        if (name == null
                || name.isBlank()) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-PATH-001",
                            Severity.ERROR,
                            Category.ARCHIVE,
                            "EPUB ZIP contains an entry "
                                    + "with an empty name."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return;
        }

        if (name.startsWith("/")) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-PATH-002",
                            Severity.ERROR,
                            Category.ARCHIVE,
                            "EPUB ZIP entry must use a relative path."
                    )
                            .filePath(epubFile)
                            .epubPath(name)
                            .build()
            );
        }

        if (name.indexOf('\\') >= 0) {

            issues.add(
                    issue(
                            "EPUB-ARCHIVE-PATH-003",
                            Severity.ERROR,
                            Category.ARCHIVE,
                            "EPUB ZIP entry path must use '/'."
                    )
                            .filePath(epubFile)
                            .epubPath(name)
                            .build()
            );
        }

        for (String segment :
                name.split("/")) {

            if ("..".equals(segment)) {

                issues.add(
                        issue(
                                "EPUB-ARCHIVE-PATH-004",
                                Severity.ERROR,
                                Category.ARCHIVE,
                                "EPUB ZIP entry contains parent traversal."
                        )
                                .filePath(epubFile)
                                .epubPath(name)
                                .build()
                );

                break;
            }
        }
    }

    /**
     * ZIP 내부 mimetype 데이터를 검사합니다.
     */
    private void validateMimetype(
            ZipFile zipFile,
            Path epubFile,
            List<EpubValidationIssue> issues
    ) throws IOException {

        ZipEntry mimetype =
                zipFile.getEntry(MIMETYPE_ENTRY);

        if (mimetype == null) {

            issues.add(
                    issue(
                            "EPUB-MIMETYPE-003",
                            Severity.FATAL,
                            Category.MIMETYPE,
                            "EPUB archive does not contain mimetype."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return;
        }

        byte[] expected =
                MIMETYPE_VALUE.getBytes(
                        StandardCharsets.US_ASCII
                );

        byte[] actual;

        try (InputStream input =
                zipFile.getInputStream(mimetype)) {

            actual = input.readAllBytes();
        }

        if (!java.util.Arrays.equals(
                expected,
                actual
        )) {

            issues.add(
                    issue(
                            "EPUB-MIMETYPE-004",
                            Severity.ERROR,
                            Category.MIMETYPE,
                            "Invalid EPUB mimetype content."
                    )
                            .filePath(epubFile)
                            .expectedValue(
                                    MIMETYPE_VALUE
                            )
                            .actualValue(
                                    new String(
                                            actual,
                                            StandardCharsets.UTF_8
                                    )
                            )
                            .build()
            );
        }

        if (mimetype.getMethod()
                != ZipEntry.STORED) {

            issues.add(
                    issue(
                            "EPUB-MIMETYPE-005",
                            Severity.ERROR,
                            Category.MIMETYPE,
                            "EPUB mimetype must use STORED ZIP method."
                    )
                            .filePath(epubFile)
                            .build()
            );
        }
    }

    /**
     * container.xml의 존재 및 OPF full-path를 확인합니다.
     *
     * @return OPF 경로 또는 null
     */
    private String validateContainer(
            ZipFile zipFile,
            Path epubFile,
            List<EpubValidationIssue> issues
    ) throws IOException {

        ZipEntry container =
                zipFile.getEntry(
                        CONTAINER_ENTRY
                );

        if (container == null) {

            issues.add(
                    issue(
                            "EPUB-CONTAINER-001",
                            Severity.FATAL,
                            Category.CONTAINER,
                            "EPUB archive does not contain "
                                    + "META-INF/container.xml."
                    )
                            .filePath(epubFile)
                            .build()
            );

            return null;
        }

        String xml;

        try (InputStream input =
                zipFile.getInputStream(container)) {

            xml = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        if (!xml.contains(
                "urn:oasis:names:tc:opendocument:xmlns:container"
        )) {

            issues.add(
                    issue(
                            "EPUB-CONTAINER-002",
                            Severity.ERROR,
                            Category.CONTAINER,
                            "container.xml does not contain "
                                    + "the required container namespace."
                    )
                            .filePath(epubFile)
                            .epubPath(CONTAINER_ENTRY)
                            .build()
            );
        }

        String packagePath =
                extractAttributeValue(
                        xml,
                        "full-path"
                );

        if (packagePath == null
                || packagePath.isBlank()) {

            issues.add(
                    issue(
                            "EPUB-CONTAINER-003",
                            Severity.ERROR,
                            Category.CONTAINER,
                            "container.xml does not define "
                                    + "a rootfile full-path."
                    )
                            .filePath(epubFile)
                            .epubPath(CONTAINER_ENTRY)
                            .build()
            );

            return null;
        }

        packagePath =
                normalizeEpubPath(
                        packagePath
                );

        if (!packagePath
                .toLowerCase(Locale.ROOT)
                .endsWith(".opf")) {

            issues.add(
                    issue(
                            "EPUB-CONTAINER-004",
                            Severity.ERROR,
                            Category.CONTAINER,
                            "container.xml rootfile must reference "
                                    + "an OPF document."
                    )
                            .filePath(epubFile)
                            .epubPath(CONTAINER_ENTRY)
                            .actualValue(packagePath)
                            .build()
            );
        }

        return packagePath;
    }

    /**
     * container.xml이 가리키는 OPF 파일이 ZIP에 존재하는지 확인합니다.
     */
    private void validatePackageDocumentEntry(
            ZipFile zipFile,
            String packagePath,
            Path epubFile,
            List<EpubValidationIssue> issues
    ) throws IOException {

        ZipEntry opf =
                zipFile.getEntry(packagePath);

        if (opf == null) {

            issues.add(
                    issue(
                            "EPUB-PACKAGE-ARCHIVE-001",
                            Severity.FATAL,
                            Category.PACKAGE_DOCUMENT,
                            "container.xml references an OPF document "
                                    + "that is not present in the archive."
                    )
                            .filePath(epubFile)
                            .epubPath(packagePath)
                            .build()
            );

            return;
        }

        /*
         * 여기서는 전체 XML Schema 검증까지 수행하지 않고
         * package / metadata / manifest / spine의 존재 여부만
         * 빠르게 확인합니다.
         */
        String xml;

        try (InputStream input =
                zipFile.getInputStream(opf)) {

            xml = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        if (!containsElement(xml, "package")) {

            issues.add(
                    packageStructureIssue(
                            epubFile,
                            packagePath,
                            "EPUB-PACKAGE-ARCHIVE-002",
                            "OPF document does not contain "
                                    + "a package element."
                    )
            );
        }

        if (!containsElement(xml, "metadata")) {

            issues.add(
                    packageStructureIssue(
                            epubFile,
                            packagePath,
                            "EPUB-PACKAGE-ARCHIVE-003",
                            "OPF document does not contain metadata."
                    )
            );
        }

        if (!containsElement(xml, "manifest")) {

            issues.add(
                    packageStructureIssue(
                            epubFile,
                            packagePath,
                            "EPUB-PACKAGE-ARCHIVE-004",
                            "OPF document does not contain manifest."
                    )
            );
        }

        if (!containsElement(xml, "spine")) {

            issues.add(
                    packageStructureIssue(
                            epubFile,
                            packagePath,
                            "EPUB-PACKAGE-ARCHIVE-005",
                            "OPF document does not contain spine."
                    )
            );
        }
    }

    private EpubValidationIssue packageStructureIssue(
            Path epubFile,
            String packagePath,
            String code,
            String message
    ) {

        return issue(
                code,
                Severity.ERROR,
                Category.PACKAGE_DOCUMENT,
                message
        )
                .filePath(epubFile)
                .epubPath(packagePath)
                .build();
    }

    /**
     * 간단한 XML attribute 값을 추출합니다.
     *
     * <p>여기서는 container.xml 구조가 작고 full-path 값 하나만
     * 필요하므로 별도의 DOM 파서를 사용하지 않습니다.</p>
     */
    private String extractAttributeValue(
            String xml,
            String attributeName
    ) {

        if (xml == null
                || attributeName == null) {
            return null;
        }

        String prefix =
                attributeName + "=\"";

        int start =
                xml.indexOf(prefix);

        char quote = '"';

        if (start < 0) {

            prefix =
                    attributeName + "='";

            start =
                    xml.indexOf(prefix);

            quote = '\'';
        }

        if (start < 0) {
            return null;
        }

        start += prefix.length();

        int end =
                xml.indexOf(
                        quote,
                        start
                );

        if (end < 0) {
            return null;
        }

        return xml.substring(
                start,
                end
        ).trim();
    }

    private boolean containsElement(
            String xml,
            String elementName
    ) {

        if (xml == null
                || elementName == null) {
            return false;
        }

        return xml.contains(
                "<" + elementName
        );
    }

    private static EpubValidationIssue.Builder issue(
            String code,
            Severity severity,
            Category category,
            String message
    ) {

        return EpubValidationIssue.builder(
                code,
                severity,
                message
        )
                .category(category)
                .validator(
                        VALIDATOR_NAME
                );
    }

    private static String normalizeEpubPath(
            String value
    ) {

        if (value == null
                || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim()
                        .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized =
                    normalized.substring(2);
        }

        while (normalized.contains("//")) {
            normalized =
                    normalized.replace("//", "/");
        }

        return normalized;
    }

    private static String zipMethodName(
            int method
    ) {

        return switch (method) {
            case ZipEntry.STORED ->
                    "STORED";

            case ZipEntry.DEFLATED ->
                    "DEFLATED";

            default ->
                    String.valueOf(method);
        };
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
        return Type.INTERNAL;
    }
}