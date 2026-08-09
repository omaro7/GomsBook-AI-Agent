/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcxNavPoint;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.service.EpubResourceWriter.EpubResourceWriteResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubWorkspaceManager.EpubWorkspace;
import kr.co.goms.gomsbook.ai.epub.validation.EpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidator;


/**
 * EPUB 생성 파이프라인의 기본 구현체입니다.
 *
 * <p>EPUB 생성에 필요한 각 컴포넌트를 조합하여 최종
 * {@code .epub} 파일을 생성합니다.</p>
 *
 * <p>기본 생성 순서는 다음과 같습니다.</p>
 *
 * <ol>
 *     <li>생성 요청 및 패키지 검증</li>
 *     <li>작업 디렉터리 준비</li>
 *     <li>일반 manifest 리소스 복사/기록</li>
 *     <li>EPUB 3 Navigation Document 생성</li>
 *     <li>필요한 경우 NCX 생성</li>
 *     <li>OPF 패키지 문서 생성</li>
 *     <li>META-INF/container.xml 생성</li>
 *     <li>mimetype 생성</li>
 *     <li>ZIP EPUB 패키징</li>
 *     <li>생성 결과 기본 검증</li>
 *     <li>작업 디렉터리 정리</li>
 * </ol>
 *
 * <p>{@code nav.xhtml}과 {@code toc.ncx}는 각각의 전용 Writer가
 * 생성하므로 일반 {@link EpubResourceWriter} 처리에서는 제외합니다.</p>
 */
public final class DefaultEpubGenerator
        implements EpubGenerator {

    private final EpubWorkspaceManager workspaceManager;

    private final EpubMimetypeWriter mimetypeWriter;

    private final EpubContainerDocumentWriter
            containerDocumentWriter;

    private final EpubPackageDocumentWriter
            packageDocumentWriter;

    private final EpubResourceWriter resourceWriter;

    private final EpubNavigationBuilder navigationBuilder;

    private final EpubNavigationDocumentWriter
            navigationDocumentWriter;

    private final EpubNcxBuilder ncxBuilder;

    private final EpubNcxDocumentWriter
            ncxDocumentWriter;

    private final EpubArchiveWriter archiveWriter;

    /**
     * 생성 전/후 기본 EPUB 구조 검증기입니다.
     */
    private final EpubValidator epubValidator;

    /**
     * EPUB 접근성 검증기입니다.
     */
    private final EpubAccessibilityValidator
            accessibilityValidator;

    /**
     * 공식 EPUBCheck 검증기입니다.
     */
    private final EpubCheckValidator epubCheckValidator;

    /**
     * 검증기 없이 기본 EPUB 생성 기능만 사용하는 생성자입니다.
     *
     * <p>하위 호환 및 단위 테스트 목적으로 유지합니다.</p>
     */
    public DefaultEpubGenerator() {

        this(
                new DefaultEpubWorkspaceManager(),
                new DefaultEpubMimetypeWriter(),
                new DefaultEpubContainerDocumentWriter(),
                new DefaultEpubPackageDocumentWriter(),
                new DefaultEpubResourceWriter(),
                new DefaultEpubNavigationBuilder(),
                new DefaultEpubNavigationDocumentWriter(),
                new DefaultEpubNcxBuilder(),
                new DefaultEpubNcxDocumentWriter(),
                new DefaultEpubArchiveWriter(),
                null,
                null,
                null
        );
    }

    /**
     * 기존 핵심 생성 컴포넌트만 주입하는 생성자입니다.
     */
    public DefaultEpubGenerator(
            EpubWorkspaceManager workspaceManager,
            EpubMimetypeWriter mimetypeWriter,
            EpubContainerDocumentWriter containerDocumentWriter,
            EpubPackageDocumentWriter packageDocumentWriter,
            EpubResourceWriter resourceWriter,
            EpubNavigationBuilder navigationBuilder,
            EpubNavigationDocumentWriter navigationDocumentWriter,
            EpubNcxBuilder ncxBuilder,
            EpubNcxDocumentWriter ncxDocumentWriter,
            EpubArchiveWriter archiveWriter
    ) {

        this(
                workspaceManager,
                mimetypeWriter,
                containerDocumentWriter,
                packageDocumentWriter,
                resourceWriter,
                navigationBuilder,
                navigationDocumentWriter,
                ncxBuilder,
                ncxDocumentWriter,
                archiveWriter,
                null,
                null,
                null
        );
    }

    /**
     * 전체 EPUB 생성 및 검증 컴포넌트를 주입합니다.
     */
    public DefaultEpubGenerator(
            EpubWorkspaceManager workspaceManager,
            EpubMimetypeWriter mimetypeWriter,
            EpubContainerDocumentWriter containerDocumentWriter,
            EpubPackageDocumentWriter packageDocumentWriter,
            EpubResourceWriter resourceWriter,
            EpubNavigationBuilder navigationBuilder,
            EpubNavigationDocumentWriter navigationDocumentWriter,
            EpubNcxBuilder ncxBuilder,
            EpubNcxDocumentWriter ncxDocumentWriter,
            EpubArchiveWriter archiveWriter,
            EpubValidator epubValidator,
            EpubAccessibilityValidator accessibilityValidator,
            EpubCheckValidator epubCheckValidator
    ) {

        this.workspaceManager =
                Objects.requireNonNull(
                        workspaceManager,
                        "EPUB workspace manager must not be null."
                );

        this.mimetypeWriter =
                Objects.requireNonNull(
                        mimetypeWriter,
                        "EPUB mimetype writer must not be null."
                );

        this.containerDocumentWriter =
                Objects.requireNonNull(
                        containerDocumentWriter,
                        "EPUB container writer must not be null."
                );

        this.packageDocumentWriter =
                Objects.requireNonNull(
                        packageDocumentWriter,
                        "EPUB package writer must not be null."
                );

        this.resourceWriter =
                Objects.requireNonNull(
                        resourceWriter,
                        "EPUB resource writer must not be null."
                );

        this.navigationBuilder =
                Objects.requireNonNull(
                        navigationBuilder,
                        "EPUB navigation builder must not be null."
                );

        this.navigationDocumentWriter =
                Objects.requireNonNull(
                        navigationDocumentWriter,
                        "EPUB navigation writer must not be null."
                );

        this.ncxBuilder =
                Objects.requireNonNull(
                        ncxBuilder,
                        "EPUB NCX builder must not be null."
                );

        this.ncxDocumentWriter =
                Objects.requireNonNull(
                        ncxDocumentWriter,
                        "EPUB NCX writer must not be null."
                );

        this.archiveWriter =
                Objects.requireNonNull(
                        archiveWriter,
                        "EPUB archive writer must not be null."
                );

        this.epubValidator =
                epubValidator;

        this.accessibilityValidator =
                accessibilityValidator;

        this.epubCheckValidator =
                epubCheckValidator;
    }

    /**
     * EPUB을 생성합니다.
     */
    @Override
    public EpubGenerationResult generate(
            EpubGenerationRequest request
    ) throws EpubGenerationException {

        validateSupport(request);
        validateRequest(request);

        Instant startedAt = Instant.now();

        Path projectRoot = request.getPackageDocumentPath();
        
        EpubPackage epubPackage =
                request.getEpubPackage();

        EpubPathConfiguration paths =
                request.getPathConfiguration();

        EpubGenerationOptions options =
                request.getOptions();

        GenerationState state =
                new GenerationState();

        EpubWorkspace workspace = null;

        EpubGenerationException failure = null;

        try {

            /*
             * ------------------------------------------------------
             * 1. 생성 전 내부 검증
             * ------------------------------------------------------
             */
            runPreValidation(
            		projectRoot,
                    epubPackage,
                    paths,
                    options,
                    state
            );

            /*
             * ------------------------------------------------------
             * 2. Workspace 준비
             * ------------------------------------------------------
             */
            workspace =
                    workspaceManager.prepare(
                            paths,
                            options
                    );

            /*
             * ------------------------------------------------------
             * 3. 일반 Manifest 리소스 기록
             * ------------------------------------------------------
             */
            writeManifestResources(
                    epubPackage.getManifest(),
                    paths,
                    options,
                    state
            );

            /*
             * ------------------------------------------------------
             * 4. Navigation Document
             * ------------------------------------------------------
             */
            if (options.requiresNavigationDocument()) {

                writeNavigationDocument(
                        epubPackage,
                        paths,
                        options,
                        state
                );
            }

            /*
             * ------------------------------------------------------
             * 5. NCX
             * ------------------------------------------------------
             */
            if (options.requiresNcx()) {

                writeNcxDocument(
                        epubPackage,
                        paths,
                        options,
                        state
                );
            }

            /*
             * ------------------------------------------------------
             * 6. OPF Package Document
             * ------------------------------------------------------
             */
            if (options.isGeneratePackageDocument()) {

                Path generated =
                        packageDocumentWriter.write(
                                epubPackage,
                                paths.getPackageDocumentPath(),
                                options
                        );

                state.addGeneratedFile(generated);
            }

            /*
             * ------------------------------------------------------
             * 7. META-INF/container.xml
             * ------------------------------------------------------
             */
            if (options.isGenerateContainerDocument()) {

                Path generated =
                        containerDocumentWriter.write(
                                epubPackage,
                                paths.getContainerPath(),
                                options
                        );

                state.addGeneratedFile(generated);
            }

            /*
             * ------------------------------------------------------
             * 8. mimetype
             * ------------------------------------------------------
             */
            if (options.isGenerateMimetypeFile()) {

                Path generated =
                        mimetypeWriter.write(
                                paths.getMimetypePath(),
                                options
                        );

                state.addGeneratedFile(generated);
            }

            /*
             * ------------------------------------------------------
             * 9. Workspace 파일 집계
             * ------------------------------------------------------
             */
            collectWorkspaceFiles(
                    workspace,
                    state
            );

            /*
             * ------------------------------------------------------
             * 10. 접근성 검증
             *
             * nav.xhtml / OPF / XHTML 등이 실제 파일로 생성된
             * 시점에서 검증하는 것이 가장 정확합니다.
             * ------------------------------------------------------
             */
            runAccessibilityValidation(
            		projectRoot,
                    workspace,
                    options,
                    state
            );

            /*
             * ------------------------------------------------------
             * 11. 최종 EPUB ZIP 생성
             * ------------------------------------------------------
             */
            Path outputFile =
                    archiveWriter.write(
                            paths,
                            options
                    );

            state.outputFile =
                    outputFile;

            /*
             * ------------------------------------------------------
             * 12. 생성 후 내부 구조 검증
             * ------------------------------------------------------
             */
            runPostValidation(
            		projectRoot,
                    outputFile,
                    options,
                    state
            );

            /*
             * ------------------------------------------------------
             * 13. 공식 EPUBCheck
             * ------------------------------------------------------
             */
            runEpubCheck(
            		projectRoot,
                    outputFile,
                    options,
                    state
            );

        } catch (EpubGenerationException exception) {

            failure =
                    exception;

        } catch (RuntimeException exception) {

            failure =
                    EpubGenerationException.builder(
                            EpubGenerationException.ErrorCode.UNKNOWN,
                            "Unexpected error during EPUB generation."
                    )
                            .stage(
                                    EpubGenerationException.Stage.UNKNOWN
                            )
                            .requestId(
                                    request.getRequestId()
                            )
                            .cause(exception)
                            .build();

        } finally {

            /*
             * Workspace cleanup 오류가 기존 생성 오류를
             * 덮어쓰지 않도록 처리합니다.
             */
            if (workspace != null) {

                try {

                    workspaceManager.cleanup(
                            workspace,
                            options
                    );

                } catch (EpubGenerationException cleanupException) {

                    if (failure == null) {

                        failure =
                                cleanupException;

                    } else {

                        state.warnings.add(
                                "EPUB workspace cleanup failed: "
                                        + cleanupException
                                                .getMessage()
                        );
                    }
                }
            }
        }

        Instant completedAt =
                Instant.now();

        if (failure != null) {

            return buildFailureResult(
                    request,
                    state,
                    startedAt,
                    completedAt,
                    failure
            );
        }

        return buildSuccessResult(
                request,
                state,
                startedAt,
                completedAt
        );
    }

    /**
     * 생성 전 모델 검증입니다.
     */
    private void runPreValidation(
    		Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration paths,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        if (!options.isValidateBeforeGeneration()) {
            return;
        }

        if (epubValidator == null) {

            state.warnings.add(
                    "Pre-generation validation was requested, "
                            + "but no EPUB validator is configured."
            );

            return;
        }

        EpubValidationResult result =
                epubValidator.validate(
                		projectRoot,
                        epubPackage,
                        paths,
                        options
                );

        state.preValidationResult =
                result;

        if (!result.isPerformed()) {

            state.warnings.add(
                    "EPUB pre-generation validation "
                            + "was not performed."
            );

            return;
        }

        appendValidationWarnings(
                result,
                state
        );

        if (!result.canGenerateEpub()) {

            throw validationException(
                    EpubGenerationException.ErrorCode
                            .VALIDATION_FAILED,
                    EpubGenerationException.Stage
                            .PRE_VALIDATION,
                    "EPUB pre-generation validation failed.",
                    result
            );
        }
    }

    /**
     * Workspace 접근성 검증입니다.
     */
    private void runAccessibilityValidation(
    		Path projectRoot,
            EpubWorkspace workspace,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        if (!options.isValidateAccessibility()) {
            return;
        }

        if (accessibilityValidator == null) {

            state.warnings.add(
                    "Accessibility validation was requested, "
                            + "but no accessibility validator "
                            + "is configured."
            );

            return;
        }

        EpubValidationResult result =
                accessibilityValidator
                        .validateWorkspace(
                        		projectRoot,
                                workspace
                                        .getWorkingDirectory(),
                                workspace
                                        .getPackageDocumentFile(),
                                options
                        );

        state.accessibilityValidationResult =
                result;

        appendValidationWarnings(
                result,
                state
        );

        if (accessibilityValidator
                .shouldBlockGeneration(result)) {

            throw validationException(
                    EpubGenerationException.ErrorCode
                            .ACCESSIBILITY_VALIDATION_FAILED,
                    EpubGenerationException.Stage
                            .ACCESSIBILITY_VALIDATION,
                    "EPUB accessibility validation failed.",
                    result
            );
        }
    }

    /**
     * 최종 생성된 EPUB 내부 구조 검증입니다.
     */
    private void runPostValidation(
    		Path projectRoot,
            Path outputFile,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        if (!options.isValidateAfterGeneration()) {
            return;
        }

        /*
         * ArchiveWriter 자체 구조 검사도 수행합니다.
         */
        archiveWriter.validateArchive(
                outputFile
        );

        if (epubValidator == null) {

            state.warnings.add(
                    "Post-generation validation was requested, "
                            + "but no EPUB validator is configured."
            );

            return;
        }

        EpubValidationResult result =
                epubValidator.validate(
                		projectRoot,
                        outputFile,
                        options
                );

        state.postValidationResult =
                result;

        appendValidationWarnings(
                result,
                state
        );

        if (result.hasBlockingIssues()
                || result.getStatus()
                        == EpubValidationResult.Status.FAILED) {

            throw validationException(
                    EpubGenerationException.ErrorCode
                            .VALIDATION_FAILED,
                    EpubGenerationException.Stage
                            .POST_VALIDATION,
                    "Generated EPUB validation failed.",
                    result
            );
        }
    }

    /**
     * 공식 EPUBCheck를 실행합니다.
     */
    private void runEpubCheck(
    		Path projectRoot,
            Path outputFile,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        if (!options.isRunEpubCheck()) {
            return;
        }

        if (epubCheckValidator == null) {

            state.warnings.add(
                    "EPUBCheck was requested, "
                            + "but no EPUBCheck validator is configured."
            );

            return;
        }

        if (!epubCheckValidator.isAvailable()) {

            EpubCheckValidator.Availability availability =
                    epubCheckValidator
                            .getAvailability();

            String message =
                    availability
                            .getMessage()
                            .orElse(
                                    "EPUBCheck is not available."
                            );

            state.warnings.add(message);

            return;
        }

        EpubValidationResult result =
                epubCheckValidator.validate(
                		projectRoot,
                        outputFile,
                        options
                );

        state.epubCheckValidationResult =
                result;

        appendValidationWarnings(
                result,
                state
        );

        if (epubCheckValidator
                .shouldBlockGeneration(result)) {

            throw validationException(
                    EpubGenerationException.ErrorCode
                            .EPUB_CHECK_FAILED,
                    EpubGenerationException.Stage
                            .EPUB_CHECK,
                    "Generated EPUB did not pass EPUBCheck.",
                    result
            );
        }
    }

    /**
     * Manifest 일반 리소스를 기록합니다.
     */
    private void writeManifestResources(
            EpubManifest manifest,
            EpubPathConfiguration paths,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        for (EpubResource resource :
                manifest.getResources()) {

            if (!resource.isIncluded()) {

                state.skippedResourceCount++;

                continue;
            }

            /*
             * Navigation Document는 전용 Writer가 생성합니다.
             */
            if (resource.isNavigationDocument()
                    && options
                            .requiresNavigationDocument()) {

                continue;
            }

            /*
             * NCX 역시 전용 Writer가 생성합니다.
             */
            if (resource.isNcx()
                    && options.requiresNcx()) {

                continue;
            }

            EpubResourceWriteResult result =
                    resourceWriter.write(
                            resource,
                            paths,
                            options
                    );

            state.resourceResults.add(
                    result
            );

            if (result.isCopied()) {

                state.copiedResourceCount++;
                state.generatedResourceCount++;

            } else if (result.isWritten()) {

                state.writtenResourceCount++;
                state.generatedResourceCount++;

            } else {

                state.skippedResourceCount++;
            }

            if (result.isSuccess()
                    && result.getTargetPath() != null) {

                state.addGeneratedFile(
                        result.getTargetPath()
                );

                state.addGeneratedEpubPath(
                        resource.getHref()
                );
            }

            if (result.isSkipped()
                    && result.getMessage() != null
                    && !result.getMessage()
                            .isBlank()) {

                state.warnings.add(
                        resource.getId()
                                + ": "
                                + result.getMessage()
                );
            }

            String mediaType =
                    resource.getMediaType();

            if ("application/xhtml+xml"
                    .equalsIgnoreCase(mediaType)) {

                state.generatedXhtmlCount++;
            }

            if (mediaType != null
                    && mediaType
                            .toLowerCase()
                            .startsWith("image/")) {

                state.generatedImageCount++;
            }
        }
    }

    /**
     * EPUB 3 Navigation Document를 생성합니다.
     */
    private void writeNavigationDocument(
            EpubPackage epubPackage,
            EpubPathConfiguration paths,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        EpubNavigation navigation =
                navigationBuilder.build(
                        epubPackage
                );

        navigation.validate(
                epubPackage.getManifest()
        );

        Path generated =
                navigationDocumentWriter.write(
                        navigation,
                        paths.getNavigationPath(),
                        options
                );

        state.addGeneratedFile(
                generated
        );

        state.addGeneratedEpubPath(
                paths.getNavigationHref()
        );

        state.generatedResourceCount++;
        state.generatedXhtmlCount++;
    }

    /**
     * NCX를 생성합니다.
     */
    private void writeNcxDocument(
            EpubPackage epubPackage,
            EpubPathConfiguration paths,
            EpubGenerationOptions options,
            GenerationState state
    ) throws EpubGenerationException {

        EpubNcx ncx =
                ncxBuilder.build(
                        epubPackage
                );

        ncx.validate(
                epubPackage.getManifest()
        );

        Path generated =
                ncxDocumentWriter.write(
                        ncx,
                        paths.getNcxPath(),
                        options
                );

        state.addGeneratedFile(
                generated
        );

        state.addGeneratedEpubPath(
                paths.getNcxHref()
        );

        state.generatedResourceCount++;
    }

    /**
     * Workspace의 모든 파일을 집계합니다.
     */
    private void collectWorkspaceFiles(
            EpubWorkspace workspace,
            GenerationState state
    ) throws EpubGenerationException {

        Path root =
                workspace.getWorkingDirectory();

        try (var stream =
                Files.walk(root)) {

            List<Path> files =
                    stream
                            .filter(
                                    Files::isRegularFile
                            )
                            .map(path ->
                                    path
                                            .toAbsolutePath()
                                            .normalize()
                            )
                            .sorted()
                            .toList();

            for (Path file : files) {

                state.addGeneratedFile(
                        file
                );
            }

        } catch (IOException exception) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "Failed to enumerate EPUB workspace files."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .filePath(root)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ValidationResult의 WARNING/INFO를 생성 결과에 반영합니다.
     */
    private void appendValidationWarnings(
            EpubValidationResult result,
            GenerationState state
    ) {

        if (result == null) {
            return;
        }

        result.getIssues()
                .stream()
                .filter(issue ->
                        issue.isWarning()
                                || issue.isInfo()
                )
                .forEach(issue ->
                        state.warnings.add(
                                issue.getDisplayMessage()
                        )
                );
    }

    /**
     * 검증 실패를 EpubGenerationException으로 변환합니다.
     */
    private EpubGenerationException validationException(
            EpubGenerationException.ErrorCode errorCode,
            EpubGenerationException.Stage stage,
            String message,
            EpubValidationResult result
    ) {

        return EpubGenerationException.builder(
                errorCode,
                message
        )
                .stage(stage)
                .detail(
                        "fatal",
                        String.valueOf(
                                result.getFatalCount()
                        )
                )
                .detail(
                        "errors",
                        String.valueOf(
                                result.getErrorCount()
                        )
                )
                .detail(
                        "warnings",
                        String.valueOf(
                                result.getWarningCount()
                        )
                )
                .detail(
                        "info",
                        String.valueOf(
                                result.getInfoCount()
                        )
                )
                .detail(
                        "validator",
                        result.getValidatorName()
                                .orElse("unknown")
                )
                .build();
    }

    /**
     * 요청 유효성을 검사합니다.
     */
    private void validateRequest(
            EpubGenerationRequest request
    ) throws EpubGenerationException {

        try {

            request.validate();

        } catch (RuntimeException exception) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB generation request validation failed."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .REQUEST_VALIDATION
                    )
                    .requestId(
                            request.getRequestId()
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 지원 가능한 요청인지 확인합니다.
     */
    @Override
    public boolean supports(
            EpubGenerationRequest request
    ) {

        if (request == null
                || request.getOptions() == null) {

            return false;
        }

        return request
                .getOptions()
                .getVersion()
                .isEpub2()
                || request
                        .getOptions()
                        .getVersion()
                        .isEpub3();
    }

    /**
     * 성공 결과를 생성합니다.
     */
    private EpubGenerationResult buildSuccessResult(
            EpubGenerationRequest request,
            GenerationState state,
            Instant startedAt,
            Instant completedAt
    ) {

        EpubGenerationResult.Builder builder =
                EpubGenerationResult
                        .builder(request)
                        .startedAt(startedAt)
                        .completedAt(completedAt)
                        .generatedResourceCount(
                                state.generatedResourceCount
                        )
                        .copiedResourceCount(
                                state.copiedResourceCount
                        )
                        .writtenResourceCount(
                                state.writtenResourceCount
                        )
                        .skippedResourceCount(
                                state.skippedResourceCount
                        )
                        .generatedXhtmlCount(
                                state.generatedXhtmlCount
                        )
                        .generatedImageCount(
                                state.generatedImageCount
                        )
                        .generatedFileCount(
                                state.generatedFiles.size()
                        )
                        .outputFileSize(
                                resolveFileSize(
                                        request.getOutputFile()
                                )
                        )
                        .generatedFiles(
                                state.generatedFiles
                        )
                        .generatedEpubPaths(
                                state.generatedEpubPaths
                        )
                        .warnings(
                                state.warnings
                        )
                        .validationSummary(
                                toSummary(
                                        state.postValidationResult
                                                != null
                                                ? state.postValidationResult
                                                : state.preValidationResult
                                )
                        )
                        .accessibilityValidationSummary(
                                toSummary(
                                        state.accessibilityValidationResult
                                )
                        )
                        .epubCheckValidationSummary(
                                toSummary(
                                        state.epubCheckValidationResult
                                )
                        )
                        .message(
                                state.warnings.isEmpty()
                                        ? "EPUB generation completed successfully."
                                        : "EPUB generation completed with warnings."
                        );

        builder.resolveStatus();

        return builder.build();
    }

    /**
     * 실패 결과를 생성합니다.
     */
    private EpubGenerationResult buildFailureResult(
            EpubGenerationRequest request,
            GenerationState state,
            Instant startedAt,
            Instant completedAt,
            EpubGenerationException failure
    ) {

        List<String> errors =
                new ArrayList<>(
                        state.errors
                );

        if (failure.getMessage() != null
                && !failure.getMessage().isBlank()) {

            errors.add(
                    failure.getMessage()
            );
        }

        return EpubGenerationResult
                .builder(request)
                .status(
                        EpubGenerationResult.Status.FAILED
                )
                .startedAt(startedAt)
                .completedAt(completedAt)
                .generatedResourceCount(
                        state.generatedResourceCount
                )
                .copiedResourceCount(
                        state.copiedResourceCount
                )
                .writtenResourceCount(
                        state.writtenResourceCount
                )
                .skippedResourceCount(
                        state.skippedResourceCount
                )
                .generatedXhtmlCount(
                        state.generatedXhtmlCount
                )
                .generatedImageCount(
                        state.generatedImageCount
                )
                .generatedFileCount(
                        state.generatedFiles.size()
                )
                .outputFileSize(
                        resolveFileSize(
                                request.getOutputFile()
                        )
                )
                .generatedFiles(
                        state.generatedFiles
                )
                .generatedEpubPaths(
                        state.generatedEpubPaths
                )
                .warnings(
                        state.warnings
                )
                .errors(errors)
                .validationSummary(
                        toSummary(
                                state.postValidationResult
                                        != null
                                        ? state.postValidationResult
                                        : state.preValidationResult
                        )
                )
                .accessibilityValidationSummary(
                        toSummary(
                                state.accessibilityValidationResult
                        )
                )
                .epubCheckValidationSummary(
                        toSummary(
                                state.epubCheckValidationResult
                        )
                )
                .cause(failure)
                .exceptionType(
                        failure
                                .getClass()
                                .getName()
                )
                .exceptionMessage(
                        failure.getMessage()
                )
                .attribute(
                        "errorCode",
                        failure
                                .getErrorCode()
                                .name()
                )
                .attribute(
                        "failureStage",
                        failure
                                .getStage()
                                .name()
                )
                .message(
                        "EPUB generation failed."
                )
                .build();
    }

    /**
     * EpubValidationResult를 EpubGenerationResult의 요약 정보로
     * 변환합니다.
     */
    private EpubGenerationResult.ValidationSummary
            toSummary(
                    EpubValidationResult result
            ) {

        if (result == null
                || !result.isPerformed()) {

            return EpubGenerationResult
                    .ValidationSummary
                    .notPerformed();
        }

        if (result.hasBlockingIssues()
                || result.getStatus()
                        == EpubValidationResult.Status.FAILED) {

            return EpubGenerationResult
                    .ValidationSummary
                    .failed(
                            result.getValidatorName()
                                    .orElse(
                                            "EPUB Validator"
                                    ),
                            result.getErrorCount()
                                    + result
                                            .getFatalCount(),
                            result.getWarningCount()
                    );
        }

        if (result.hasWarnings()) {

            return EpubGenerationResult
                    .ValidationSummary
                    .passedWithWarnings(
                            result.getValidatorName()
                                    .orElse(
                                            "EPUB Validator"
                                    ),
                            result.getWarningCount()
                    );
        }

        return EpubGenerationResult
                .ValidationSummary
                .passed(
                        result.getValidatorName()
                                .orElse(
                                        "EPUB Validator"
                                )
                );
    }

    private long resolveFileSize(
            Path file
    ) {

        if (file == null
                || !Files.isRegularFile(file)) {

            return 0L;
        }

        try {

            return Files.size(file);

        } catch (IOException exception) {

            return 0L;
        }
    }

    public EpubWorkspaceManager
            getWorkspaceManager() {

        return workspaceManager;
    }

    public EpubValidator getEpubValidator() {
        return epubValidator;
    }

    public EpubAccessibilityValidator
            getAccessibilityValidator() {

        return accessibilityValidator;
    }

    public EpubCheckValidator
            getEpubCheckValidator() {

        return epubCheckValidator;
    }

    public EpubArchiveWriter
            getArchiveWriter() {

        return archiveWriter;
    }

    /**
     * EPUB 생성 과정 내부 상태입니다.
     */
    private static final class GenerationState {

        private int generatedResourceCount;

        private int copiedResourceCount;

        private int writtenResourceCount;

        private int skippedResourceCount;

        private int generatedXhtmlCount;

        private int generatedImageCount;

        private Path outputFile;

        private final List<Path> generatedFiles =
                new ArrayList<>();

        private final List<String>
                generatedEpubPaths =
                new ArrayList<>();

        private final List<EpubResourceWriteResult>
                resourceResults =
                new ArrayList<>();

        private final List<String> warnings =
                new ArrayList<>();

        private final List<String> errors =
                new ArrayList<>();

        private EpubValidationResult
                preValidationResult;

        private EpubValidationResult
                postValidationResult;

        private EpubValidationResult
                accessibilityValidationResult;

        private EpubValidationResult
                epubCheckValidationResult;

        private void addGeneratedFile(
                Path file
        ) {

            if (file == null) {
                return;
            }

            Path normalized =
                    file.toAbsolutePath()
                            .normalize();

            if (!generatedFiles.contains(
                    normalized
            )) {

                generatedFiles.add(
                        normalized
                );
            }
        }

        private void addGeneratedEpubPath(
                String epubPath
        ) {

            if (epubPath == null
                    || epubPath.isBlank()) {

                return;
            }

            String normalized =
                    epubPath.trim()
                            .replace('\\', '/');

            if (!generatedEpubPaths.contains(
                    normalized
            )) {

                generatedEpubPaths.add(
                        normalized
                );
            }
        }
    }
}