/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB 생성 과정에서 사용할 동작 옵션을 정의합니다.
 *
 * <p>이 클래스는 EPUB 모델 자체가 아니라 실제 파일 생성, XML 직렬화,
 * 리소스 복사, 압축, 검증 등의 생성 정책을 관리합니다.</p>
 *
 * <p>대표적으로 다음 설정을 포함합니다.</p>
 *
 * <ul>
 *     <li>EPUB 버전과 출력 문자 인코딩</li>
 *     <li>OPF, Navigation Document, NCX 생성 여부</li>
 *     <li>리소스 복사와 누락 리소스 처리 정책</li>
 *     <li>ZIP 압축 수준</li>
 *     <li>생성 전후 검증 정책</li>
 *     <li>XML 들여쓰기와 선언 출력 정책</li>
 *     <li>재현 가능한 빌드를 위한 수정 시각</li>
 * </ul>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubGenerationOptions {

    public static final int MIN_COMPRESSION_LEVEL = 0;

    public static final int MAX_COMPRESSION_LEVEL = 9;

    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    public static final String DEFAULT_MIMETYPE =
            "application/epub+zip";

    /**
     * 생성할 EPUB 버전입니다.
     */
    private final EpubVersion version;

    /**
     * XML과 텍스트 리소스의 기본 문자 인코딩입니다.
     */
    private final Charset charset;

    /**
     * XML 선언을 출력할지 여부입니다.
     */
    private final boolean writeXmlDeclaration;

    /**
     * XML 출력 시 들여쓰기를 적용할지 여부입니다.
     */
    private final boolean prettyPrintXml;

    /**
     * XML 들여쓰기 공백 수입니다.
     */
    private final int xmlIndentSize;

    /**
     * OPF 패키지 문서를 생성할지 여부입니다.
     */
    private final boolean generatePackageDocument;

    /**
     * META-INF/container.xml을 생성할지 여부입니다.
     */
    private final boolean generateContainerDocument;

    /**
     * EPUB 루트의 mimetype 파일을 생성할지 여부입니다.
     */
    private final boolean generateMimetypeFile;

    /**
     * EPUB 3 Navigation Document를 생성할지 여부입니다.
     */
    private final boolean generateNavigationDocument;

    /**
     * EPUB 2 또는 하위 호환용 NCX를 생성할지 여부입니다.
     */
    private final boolean generateNcx;

    /**
     * EPUB manifest에 등록된 로컬 리소스를 작업 디렉터리로
     * 복사할지 여부입니다.
     */
    private final boolean copyResources;

    /**
     * 메모리 기반 리소스를 파일로 기록할지 여부입니다.
     */
    private final boolean writeEmbeddedResources;

    /**
     * 원격 리소스를 다운로드하여 EPUB에 포함할지 여부입니다.
     *
     * <p>기본값은 {@code false}입니다. 원격 리소스를 그대로 참조하는
     * 경우 EPUB manifest의 remote-resources 속성 정책을 따라야 합니다.</p>
     */
    private final boolean downloadRemoteResources;

    /**
     * 원격 리소스를 허용할지 여부입니다.
     */
    private final boolean allowRemoteResources;

    /**
     * 누락된 로컬 리소스를 오류로 처리할지 여부입니다.
     */
    private final boolean failOnMissingResource;

    /**
     * 알 수 없는 MIME 타입을 오류로 처리할지 여부입니다.
     */
    private final boolean failOnUnknownMediaType;

    /**
     * manifest에 등록되지 않은 파일을 EPUB에 포함할지 여부입니다.
     */
    private final boolean includeUnmanifestedFiles;

    /**
     * EPUB 생성 전 모델 전체를 검증할지 여부입니다.
     */
    private final boolean validateBeforeGeneration;

    /**
     * EPUB 생성 후 결과 파일을 검증할지 여부입니다.
     */
    private final boolean validateAfterGeneration;

    /**
     * 검증 경고를 오류로 처리할지 여부입니다.
     */
    private final boolean failOnValidationWarning;

    /**
     * 접근성 검증을 수행할지 여부입니다.
     */
    private final boolean validateAccessibility;

    /**
     * 이미지 대체 텍스트 누락을 오류로 처리할지 여부입니다.
     */
    private final boolean failOnMissingAlternativeText;

    /**
     * 작업 디렉터리를 생성 전에 정리할지 여부입니다.
     */
    private final boolean cleanWorkingDirectory;

    /**
     * 생성 완료 후 작업 디렉터리를 삭제할지 여부입니다.
     */
    private final boolean deleteWorkingDirectoryAfterGeneration;

    /**
     * 기존 출력 EPUB 파일을 덮어쓸지 여부입니다.
     */
    private final boolean overwriteOutput;

    /**
     * ZIP 압축 수준입니다.
     *
     * <p>{@code 0}은 압축 없음, {@code 9}는 최대 압축입니다.
     * 단, EPUB의 {@code mimetype} 항목은 항상 압축하지 않고
     * 첫 번째 ZIP 항목으로 기록해야 합니다.</p>
     */
    private final int compressionLevel;

    /**
     * 리소스 파일의 타임스탬프를 정규화할지 여부입니다.
     *
     * <p>동일 입력에서 동일 EPUB 바이너리를 생성하는 재현 가능한
     * 빌드에 사용합니다.</p>
     */
    private final boolean reproducibleBuild;

    /**
     * 생성 결과에 사용할 고정 수정 시각입니다.
     */
    private final Instant buildTimestamp;

    /**
     * EPUB 메타데이터의 dcterms:modified를 자동으로 갱신할지 여부입니다.
     */
    private final boolean updateModifiedMetadata;

    /**
     * EPUB 3에서도 하위 호환용 NCX를 함께 생성할지 여부입니다.
     */
    private final boolean includeLegacyNcxInEpub3;

    /**
     * EPUB 3 rendition 메타데이터를 명시적으로 출력할지 여부입니다.
     */
    private final boolean writeRenditionMetadata;

    /**
     * 기본값인 OPF 속성을 생략할지 여부입니다.
     *
     * <p>예를 들어 {@code linear="yes"},
     * {@code rendition:orientation="auto"} 등을 생략할 수 있습니다.</p>
     */
    private final boolean omitDefaultAttributes;

    /**
     * 구형 독서 시스템을 위한 legacy page-spread 속성을 함께 출력할지
     * 여부입니다.
     */
    private final boolean includeLegacyPageSpreadProperties;

    /**
     * 생성된 파일의 줄바꿈 문자열입니다.
     */
    private final LineSeparator lineSeparator;

    /**
     * 파일명과 EPUB 내부 경로에서 공백을 허용할지 여부입니다.
     */
    private final boolean allowSpacesInPaths;

    /**
     * 파일 경로의 대소문자 충돌을 검사할지 여부입니다.
     */
    private final boolean detectCaseInsensitivePathCollisions;

    /**
     * EPUB에 포함되는 모든 XHTML을 XML 파서로 검증할지 여부입니다.
     */
    private final boolean validateXhtml;

    /**
     * CSS 구문 검증을 수행할지 여부입니다.
     */
    private final boolean validateCss;

    /**
     * 외부 프로세스 또는 EPUBCheck 실행 여부입니다.
     *
     * <p>실제 실행 구현은 생성 계층 또는 검증 계층에서 담당합니다.</p>
     */
    private final boolean runEpubCheck;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubGenerationOptions(Builder builder) {
        this.version = builder.version == null
                ? EpubVersion.defaultVersion()
                : builder.version;

        this.charset = builder.charset == null
                ? StandardCharsets.UTF_8
                : builder.charset;

        this.writeXmlDeclaration = builder.writeXmlDeclaration;
        this.prettyPrintXml = builder.prettyPrintXml;
        this.xmlIndentSize = builder.xmlIndentSize;
        this.generatePackageDocument =
                builder.generatePackageDocument;
        this.generateContainerDocument =
                builder.generateContainerDocument;
        this.generateMimetypeFile =
                builder.generateMimetypeFile;
        this.generateNavigationDocument =
                resolveNavigationGeneration(
                        builder.generateNavigationDocument,
                        this.version
                );
        this.generateNcx = resolveNcxGeneration(
                builder.generateNcx,
                builder.includeLegacyNcxInEpub3,
                this.version
        );
        this.copyResources = builder.copyResources;
        this.writeEmbeddedResources =
                builder.writeEmbeddedResources;
        this.downloadRemoteResources =
                builder.downloadRemoteResources;
        this.allowRemoteResources =
                builder.allowRemoteResources;
        this.failOnMissingResource =
                builder.failOnMissingResource;
        this.failOnUnknownMediaType =
                builder.failOnUnknownMediaType;
        this.includeUnmanifestedFiles =
                builder.includeUnmanifestedFiles;
        this.validateBeforeGeneration =
                builder.validateBeforeGeneration;
        this.validateAfterGeneration =
                builder.validateAfterGeneration;
        this.failOnValidationWarning =
                builder.failOnValidationWarning;
        this.validateAccessibility =
                builder.validateAccessibility;
        this.failOnMissingAlternativeText =
                builder.failOnMissingAlternativeText;
        this.cleanWorkingDirectory =
                builder.cleanWorkingDirectory;
        this.deleteWorkingDirectoryAfterGeneration =
                builder.deleteWorkingDirectoryAfterGeneration;
        this.overwriteOutput = builder.overwriteOutput;
        this.compressionLevel = builder.compressionLevel;
        this.reproducibleBuild = builder.reproducibleBuild;
        this.buildTimestamp = resolveBuildTimestamp(
                builder.buildTimestamp,
                this.reproducibleBuild
        );
        this.updateModifiedMetadata =
                builder.updateModifiedMetadata;
        this.includeLegacyNcxInEpub3 =
                builder.includeLegacyNcxInEpub3;
        this.writeRenditionMetadata =
                builder.writeRenditionMetadata;
        this.omitDefaultAttributes =
                builder.omitDefaultAttributes;
        this.includeLegacyPageSpreadProperties =
                builder.includeLegacyPageSpreadProperties;
        this.lineSeparator = builder.lineSeparator == null
                ? LineSeparator.LF
                : builder.lineSeparator;
        this.allowSpacesInPaths =
                builder.allowSpacesInPaths;
        this.detectCaseInsensitivePathCollisions =
                builder.detectCaseInsensitivePathCollisions;
        this.validateXhtml = builder.validateXhtml;
        this.validateCss = builder.validateCss;
        this.runEpubCheck = builder.runEpubCheck;
        this.description = normalizeOptionalText(
                builder.description
        );

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * GomsBook EPUB 3.3 기본 생성 옵션을 반환합니다.
     *
     * @return EPUB 3.3 기본 옵션
     */
    public static EpubGenerationOptions defaultOptions() {
        return builder().build();
    }

    /**
     * 접근성 EPUB 생성에 권장되는 옵션을 반환합니다.
     *
     * @return 접근성 검증이 강화된 옵션
     */
    public static EpubGenerationOptions accessibleOptions() {
        return builder()
                .validateAccessibility(true)
                .failOnMissingAlternativeText(true)
                .validateBeforeGeneration(true)
                .validateAfterGeneration(true)
                .validateXhtml(true)
                .runEpubCheck(true)
                .build();
    }

    /**
     * 재현 가능한 빌드용 옵션을 반환합니다.
     *
     * @param timestamp 고정 생성 시각
     * @return 재현 가능한 생성 옵션
     */
    public static EpubGenerationOptions reproducible(
            Instant timestamp
    ) {
        return builder()
                .reproducibleBuild(true)
                .buildTimestamp(timestamp)
                .build();
    }

    public EpubVersion getVersion() {
        return version;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isWriteXmlDeclaration() {
        return writeXmlDeclaration;
    }

    public boolean isPrettyPrintXml() {
        return prettyPrintXml;
    }

    public int getXmlIndentSize() {
        return xmlIndentSize;
    }

    public boolean isGeneratePackageDocument() {
        return generatePackageDocument;
    }

    public boolean isGenerateContainerDocument() {
        return generateContainerDocument;
    }

    public boolean isGenerateMimetypeFile() {
        return generateMimetypeFile;
    }

    public boolean isGenerateNavigationDocument() {
        return generateNavigationDocument;
    }

    public boolean isGenerateNcx() {
        return generateNcx;
    }

    public boolean isCopyResources() {
        return copyResources;
    }

    public boolean isWriteEmbeddedResources() {
        return writeEmbeddedResources;
    }

    public boolean isDownloadRemoteResources() {
        return downloadRemoteResources;
    }

    public boolean isAllowRemoteResources() {
        return allowRemoteResources;
    }

    public boolean isFailOnMissingResource() {
        return failOnMissingResource;
    }

    public boolean isFailOnUnknownMediaType() {
        return failOnUnknownMediaType;
    }

    public boolean isIncludeUnmanifestedFiles() {
        return includeUnmanifestedFiles;
    }

    public boolean isValidateBeforeGeneration() {
        return validateBeforeGeneration;
    }

    public boolean isValidateAfterGeneration() {
        return validateAfterGeneration;
    }

    public boolean isFailOnValidationWarning() {
        return failOnValidationWarning;
    }

    public boolean isValidateAccessibility() {
        return validateAccessibility;
    }

    public boolean isFailOnMissingAlternativeText() {
        return failOnMissingAlternativeText;
    }

    public boolean isCleanWorkingDirectory() {
        return cleanWorkingDirectory;
    }

    public boolean isDeleteWorkingDirectoryAfterGeneration() {
        return deleteWorkingDirectoryAfterGeneration;
    }

    public boolean isOverwriteOutput() {
        return overwriteOutput;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public boolean isReproducibleBuild() {
        return reproducibleBuild;
    }

    public Optional<Instant> getBuildTimestamp() {
        return Optional.ofNullable(buildTimestamp);
    }

    /**
     * 실제 생성에 사용할 타임스탬프를 반환합니다.
     *
     * <p>고정 타임스탬프가 설정되지 않았다면 현재 시각을 반환합니다.</p>
     *
     * @return 생성 시각
     */
    public Instant resolveBuildTimestamp() {
        return buildTimestamp == null
                ? Instant.now()
                : buildTimestamp;
    }

    public boolean isUpdateModifiedMetadata() {
        return updateModifiedMetadata;
    }

    public boolean isIncludeLegacyNcxInEpub3() {
        return includeLegacyNcxInEpub3;
    }

    public boolean isWriteRenditionMetadata() {
        return writeRenditionMetadata;
    }

    public boolean isOmitDefaultAttributes() {
        return omitDefaultAttributes;
    }

    public boolean isIncludeLegacyPageSpreadProperties() {
        return includeLegacyPageSpreadProperties;
    }

    public LineSeparator getLineSeparator() {
        return lineSeparator;
    }

    public String getLineSeparatorValue() {
        return lineSeparator.getValue();
    }

    public boolean isAllowSpacesInPaths() {
        return allowSpacesInPaths;
    }

    public boolean isDetectCaseInsensitivePathCollisions() {
        return detectCaseInsensitivePathCollisions;
    }

    public boolean isValidateXhtml() {
        return validateXhtml;
    }

    public boolean isValidateCss() {
        return validateCss;
    }

    public boolean isRunEpubCheck() {
        return runEpubCheck;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * EPUB 3 Navigation Document가 필요한지 확인합니다.
     *
     * @return EPUB 3이고 Navigation Document 생성이 활성화되면
     *         {@code true}
     */
    public boolean requiresNavigationDocument() {
        return version.isEpub3()
                && generateNavigationDocument;
    }

    /**
     * NCX가 필요한지 확인합니다.
     *
     * @return EPUB 2이거나 EPUB 3 하위 호환 NCX가 활성화되면
     *         {@code true}
     */
    public boolean requiresNcx() {
        return version.isEpub2()
                || generateNcx
                || includeLegacyNcxInEpub3;
    }

    /**
     * 기본 EPUB 구조 파일을 모두 생성하는지 확인합니다.
     *
     * @return mimetype, container.xml, OPF를 모두 생성하면
     *         {@code true}
     */
    public boolean generatesCompletePackageStructure() {
        return generateMimetypeFile
                && generateContainerDocument
                && generatePackageDocument;
    }

    /**
     * 엄격 검증 모드인지 확인합니다.
     *
     * @return 검증 경고나 리소스 오류를 실패로 처리하면 {@code true}
     */
    public boolean isStrictValidation() {
        return failOnValidationWarning
                && failOnMissingResource
                && failOnUnknownMediaType;
    }

    /**
     * 접근성 관련 엄격 검증 모드인지 확인합니다.
     *
     * @return 접근성 검증과 alt 누락 실패가 모두 활성화되면
     *         {@code true}
     */
    public boolean isStrictAccessibilityValidation() {
        return validateAccessibility
                && failOnMissingAlternativeText;
    }

    /**
     * 리소스를 실제 EPUB 파일에 기록하는 옵션인지 확인합니다.
     *
     * @return 파일 또는 메모리 리소스 기록이 활성화되면 {@code true}
     */
    public boolean writesResources() {
        return copyResources || writeEmbeddedResources;
    }

    /**
     * 지정한 경로 설정과 생성 옵션의 일관성을 검증합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     */
    public void validate(
            EpubPathConfiguration pathConfiguration
    ) {
        Objects.requireNonNull(
                pathConfiguration,
                "EPUB path configuration must not be null."
        );

        if (cleanWorkingDirectory
                && !pathConfiguration.isCleanWorkingDirectory()) {
            throw new IllegalStateException(
                    "Generation options require working directory "
                            + "cleanup, but path configuration disables it."
            );
        }

        if (!allowSpacesInPaths
                && pathConfiguration.isAllowSpaces()) {
            throw new IllegalStateException(
                    "EPUB generation options disallow spaces, but "
                            + "path configuration allows them."
            );
        }
    }

    /**
     * 지정한 EPUB 패키지와 생성 옵션의 일관성을 검증합니다.
     *
     * @param epubPackage EPUB 패키지
     */
    public void validate(EpubPackage epubPackage) {
        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        if (epubPackage.getVersion() != version) {
            throw new IllegalStateException(
                    "EPUB package version and generation option "
                            + "version do not match: "
                            + epubPackage.getVersion()
                            + " != "
                            + version
            );
        }

        if (requiresNavigationDocument()
                && epubPackage.getNavigationDocument().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB generation requires a Navigation Document."
            );
        }

        if (version.isEpub2()
                && epubPackage.getNcxResource().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB 2 generation requires an NCX resource."
            );
        }

        if (!allowRemoteResources
                && !epubPackage.getManifest()
                        .getRemoteResources()
                        .isEmpty()) {
            throw new IllegalStateException(
                    "Remote EPUB resources are not allowed by "
                            + "generation options."
            );
        }

        if (failOnUnknownMediaType) {
            boolean unknownResourceExists =
                    epubPackage.getManifest()
                            .getResources()
                            .stream()
                            .anyMatch(resource ->
                                    resource.getResourceType()
                                            .isUnknown()
                            );

            if (unknownResourceExists) {
                throw new IllegalStateException(
                        "EPUB manifest contains an unknown "
                                + "resource media type."
                );
            }
        }
    }

    /**
     * 지정한 패키지와 경로 설정을 함께 검증합니다.
     *
     * @param epubPackage      EPUB 패키지
     * @param pathConfiguration 경로 설정
     */
    public void validate(
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration
    ) {
        validate(epubPackage);
        validate(pathConfiguration);
    }

    /**
     * 현재 옵션을 기반으로 Builder를 생성합니다.
     *
     * @return 복사된 Builder
     */
    public Builder toBuilder() {
        return new Builder()
                .version(version)
                .charset(charset)
                .writeXmlDeclaration(writeXmlDeclaration)
                .prettyPrintXml(prettyPrintXml)
                .xmlIndentSize(xmlIndentSize)
                .generatePackageDocument(generatePackageDocument)
                .generateContainerDocument(generateContainerDocument)
                .generateMimetypeFile(generateMimetypeFile)
                .generateNavigationDocument(
                        generateNavigationDocument
                )
                .generateNcx(generateNcx)
                .copyResources(copyResources)
                .writeEmbeddedResources(writeEmbeddedResources)
                .downloadRemoteResources(downloadRemoteResources)
                .allowRemoteResources(allowRemoteResources)
                .failOnMissingResource(failOnMissingResource)
                .failOnUnknownMediaType(failOnUnknownMediaType)
                .includeUnmanifestedFiles(
                        includeUnmanifestedFiles
                )
                .validateBeforeGeneration(
                        validateBeforeGeneration
                )
                .validateAfterGeneration(validateAfterGeneration)
                .failOnValidationWarning(
                        failOnValidationWarning
                )
                .validateAccessibility(validateAccessibility)
                .failOnMissingAlternativeText(
                        failOnMissingAlternativeText
                )
                .cleanWorkingDirectory(cleanWorkingDirectory)
                .deleteWorkingDirectoryAfterGeneration(
                        deleteWorkingDirectoryAfterGeneration
                )
                .overwriteOutput(overwriteOutput)
                .compressionLevel(compressionLevel)
                .reproducibleBuild(reproducibleBuild)
                .buildTimestamp(buildTimestamp)
                .updateModifiedMetadata(updateModifiedMetadata)
                .includeLegacyNcxInEpub3(
                        includeLegacyNcxInEpub3
                )
                .writeRenditionMetadata(writeRenditionMetadata)
                .omitDefaultAttributes(omitDefaultAttributes)
                .includeLegacyPageSpreadProperties(
                        includeLegacyPageSpreadProperties
                )
                .lineSeparator(lineSeparator)
                .allowSpacesInPaths(allowSpacesInPaths)
                .detectCaseInsensitivePathCollisions(
                        detectCaseInsensitivePathCollisions
                )
                .validateXhtml(validateXhtml)
                .validateCss(validateCss)
                .runEpubCheck(runEpubCheck)
                .description(description);
    }

    private void validate() {
        if (!StandardCharsets.UTF_8.equals(charset)) {
            throw new IllegalArgumentException(
                    "EPUB XML and text generation must use UTF-8: "
                            + charset
            );
        }

        if (xmlIndentSize < 0 || xmlIndentSize > 16) {
            throw new IllegalArgumentException(
                    "XML indent size must be between 0 and 16: "
                            + xmlIndentSize
            );
        }

        if (!prettyPrintXml && xmlIndentSize != 0) {
            /*
             * 들여쓰기 출력이 비활성화된 경우 들여쓰기 크기는
             * 생성기에 영향을 주지 않지만 설정 오류는 아닙니다.
             */
        }

        if (compressionLevel < MIN_COMPRESSION_LEVEL
                || compressionLevel > MAX_COMPRESSION_LEVEL) {
            throw new IllegalArgumentException(
                    "EPUB compression level must be between "
                            + MIN_COMPRESSION_LEVEL
                            + " and "
                            + MAX_COMPRESSION_LEVEL
                            + ": "
                            + compressionLevel
            );
        }

        if (version.isEpub3()
                && !generateNavigationDocument) {
            throw new IllegalArgumentException(
                    "EPUB 3 generation requires a Navigation Document."
            );
        }

        if (version.isEpub2() && !generateNcx) {
            throw new IllegalArgumentException(
                    "EPUB 2 generation requires an NCX document."
            );
        }

        if (version.isEpub2()
                && includeLegacyNcxInEpub3) {
            throw new IllegalArgumentException(
                    "includeLegacyNcxInEpub3 is only valid for EPUB 3."
            );
        }

        if (downloadRemoteResources
                && !allowRemoteResources) {
            throw new IllegalArgumentException(
                    "Remote resource download requires "
                            + "allowRemoteResources=true."
            );
        }

        if (failOnMissingAlternativeText
                && !validateAccessibility) {
            throw new IllegalArgumentException(
                    "Missing alternative text validation requires "
                            + "accessibility validation."
            );
        }

        if (deleteWorkingDirectoryAfterGeneration
                && !cleanWorkingDirectory) {
            /*
             * 두 옵션은 독립적으로 사용할 수 있으므로 오류가 아닙니다.
             */
        }

        if (reproducibleBuild && buildTimestamp == null) {
            throw new IllegalArgumentException(
                    "Reproducible EPUB generation requires "
                            + "a fixed build timestamp."
            );
        }

        if (runEpubCheck && !validateAfterGeneration) {
            throw new IllegalArgumentException(
                    "EPUBCheck execution requires post-generation "
                            + "validation."
            );
        }

        if (!generatePackageDocument
                || !generateContainerDocument
                || !generateMimetypeFile) {
            throw new IllegalArgumentException(
                    "A complete EPUB file requires mimetype, "
                            + "container.xml, and package document generation."
            );
        }
    }

    private static boolean resolveNavigationGeneration(
            boolean requested,
            EpubVersion version
    ) {
        if (version.isEpub3()) {
            return true;
        }

        return requested;
    }

    private static boolean resolveNcxGeneration(
            boolean requested,
            boolean includeLegacyNcxInEpub3,
            EpubVersion version
    ) {
        if (version.isEpub2()) {
            return true;
        }

        return requested || includeLegacyNcxInEpub3;
    }

    private static Instant resolveBuildTimestamp(
            Instant buildTimestamp,
            boolean reproducibleBuild
    ) {
        if (buildTimestamp != null) {
            return buildTimestamp;
        }

        if (reproducibleBuild) {
            return null;
        }

        return null;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubGenerationOptions{"
                + "version=" + version
                + ", charset=" + charset
                + ", prettyPrintXml=" + prettyPrintXml
                + ", generateNavigationDocument="
                + generateNavigationDocument
                + ", generateNcx=" + generateNcx
                + ", copyResources=" + copyResources
                + ", validateBeforeGeneration="
                + validateBeforeGeneration
                + ", validateAfterGeneration="
                + validateAfterGeneration
                + ", validateAccessibility="
                + validateAccessibility
                + ", compressionLevel=" + compressionLevel
                + ", reproducibleBuild=" + reproducibleBuild
                + ", runEpubCheck=" + runEpubCheck
                + '}';
    }

    /**
     * 생성 파일의 줄바꿈 형식입니다.
     */
    public enum LineSeparator {

        /**
         * Unix, Linux 및 EPUB 표준 출력에 권장되는 LF입니다.
         */
        LF("\n"),

        /**
         * Windows 줄바꿈입니다.
         */
        CRLF("\r\n");

        private final String value;

        LineSeparator(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * {@link EpubGenerationOptions} 생성 Builder입니다.
     */
    public static final class Builder {

        private EpubVersion version =
                EpubVersion.defaultVersion();

        private Charset charset =
                StandardCharsets.UTF_8;

        private boolean writeXmlDeclaration = true;

        private boolean prettyPrintXml = true;

        private int xmlIndentSize = 4;

        private boolean generatePackageDocument = true;

        private boolean generateContainerDocument = true;

        private boolean generateMimetypeFile = true;

        private boolean generateNavigationDocument = true;

        private boolean generateNcx;

        private boolean copyResources = true;

        private boolean writeEmbeddedResources = true;

        private boolean downloadRemoteResources;

        private boolean allowRemoteResources;

        private boolean failOnMissingResource = true;

        private boolean failOnUnknownMediaType = true;

        private boolean includeUnmanifestedFiles;

        private boolean validateBeforeGeneration = true;

        private boolean validateAfterGeneration = true;

        private boolean failOnValidationWarning;

        private boolean validateAccessibility = true;

        private boolean failOnMissingAlternativeText;

        private boolean cleanWorkingDirectory = true;

        private boolean deleteWorkingDirectoryAfterGeneration;

        private boolean overwriteOutput = true;

        private int compressionLevel =
                DEFAULT_COMPRESSION_LEVEL;

        private boolean reproducibleBuild;

        private Instant buildTimestamp;

        private boolean updateModifiedMetadata = true;

        private boolean includeLegacyNcxInEpub3;

        private boolean writeRenditionMetadata = true;

        private boolean omitDefaultAttributes = true;

        private boolean includeLegacyPageSpreadProperties;

        private LineSeparator lineSeparator =
                LineSeparator.LF;

        private boolean allowSpacesInPaths;

        private boolean detectCaseInsensitivePathCollisions = true;

        private boolean validateXhtml = true;

        private boolean validateCss = true;

        private boolean runEpubCheck;

        private String description;

        private Builder() {
        }

        public Builder version(EpubVersion version) {
            this.version = version;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder writeXmlDeclaration(
                boolean writeXmlDeclaration
        ) {
            this.writeXmlDeclaration = writeXmlDeclaration;
            return this;
        }

        public Builder prettyPrintXml(boolean prettyPrintXml) {
            this.prettyPrintXml = prettyPrintXml;
            return this;
        }

        public Builder xmlIndentSize(int xmlIndentSize) {
            this.xmlIndentSize = xmlIndentSize;
            return this;
        }

        public Builder generatePackageDocument(
                boolean generatePackageDocument
        ) {
            this.generatePackageDocument =
                    generatePackageDocument;
            return this;
        }

        public Builder generateContainerDocument(
                boolean generateContainerDocument
        ) {
            this.generateContainerDocument =
                    generateContainerDocument;
            return this;
        }

        public Builder generateMimetypeFile(
                boolean generateMimetypeFile
        ) {
            this.generateMimetypeFile = generateMimetypeFile;
            return this;
        }

        public Builder generateNavigationDocument(
                boolean generateNavigationDocument
        ) {
            this.generateNavigationDocument =
                    generateNavigationDocument;
            return this;
        }

        public Builder generateNcx(boolean generateNcx) {
            this.generateNcx = generateNcx;
            return this;
        }

        public Builder copyResources(boolean copyResources) {
            this.copyResources = copyResources;
            return this;
        }

        public Builder writeEmbeddedResources(
                boolean writeEmbeddedResources
        ) {
            this.writeEmbeddedResources =
                    writeEmbeddedResources;
            return this;
        }

        public Builder downloadRemoteResources(
                boolean downloadRemoteResources
        ) {
            this.downloadRemoteResources =
                    downloadRemoteResources;
            return this;
        }

        public Builder allowRemoteResources(
                boolean allowRemoteResources
        ) {
            this.allowRemoteResources = allowRemoteResources;
            return this;
        }

        public Builder failOnMissingResource(
                boolean failOnMissingResource
        ) {
            this.failOnMissingResource = failOnMissingResource;
            return this;
        }

        public Builder failOnUnknownMediaType(
                boolean failOnUnknownMediaType
        ) {
            this.failOnUnknownMediaType =
                    failOnUnknownMediaType;
            return this;
        }

        public Builder includeUnmanifestedFiles(
                boolean includeUnmanifestedFiles
        ) {
            this.includeUnmanifestedFiles =
                    includeUnmanifestedFiles;
            return this;
        }

        public Builder validateBeforeGeneration(
                boolean validateBeforeGeneration
        ) {
            this.validateBeforeGeneration =
                    validateBeforeGeneration;
            return this;
        }

        public Builder validateAfterGeneration(
                boolean validateAfterGeneration
        ) {
            this.validateAfterGeneration =
                    validateAfterGeneration;
            return this;
        }

        public Builder failOnValidationWarning(
                boolean failOnValidationWarning
        ) {
            this.failOnValidationWarning =
                    failOnValidationWarning;
            return this;
        }

        public Builder validateAccessibility(
                boolean validateAccessibility
        ) {
            this.validateAccessibility =
                    validateAccessibility;
            return this;
        }

        public Builder failOnMissingAlternativeText(
                boolean failOnMissingAlternativeText
        ) {
            this.failOnMissingAlternativeText =
                    failOnMissingAlternativeText;
            return this;
        }

        public Builder cleanWorkingDirectory(
                boolean cleanWorkingDirectory
        ) {
            this.cleanWorkingDirectory =
                    cleanWorkingDirectory;
            return this;
        }

        public Builder deleteWorkingDirectoryAfterGeneration(
                boolean deleteWorkingDirectoryAfterGeneration
        ) {
            this.deleteWorkingDirectoryAfterGeneration =
                    deleteWorkingDirectoryAfterGeneration;
            return this;
        }

        public Builder overwriteOutput(boolean overwriteOutput) {
            this.overwriteOutput = overwriteOutput;
            return this;
        }

        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public Builder reproducibleBuild(
                boolean reproducibleBuild
        ) {
            this.reproducibleBuild = reproducibleBuild;
            return this;
        }

        public Builder buildTimestamp(Instant buildTimestamp) {
            this.buildTimestamp = buildTimestamp;
            return this;
        }

        public Builder updateModifiedMetadata(
                boolean updateModifiedMetadata
        ) {
            this.updateModifiedMetadata =
                    updateModifiedMetadata;
            return this;
        }

        public Builder includeLegacyNcxInEpub3(
                boolean includeLegacyNcxInEpub3
        ) {
            this.includeLegacyNcxInEpub3 =
                    includeLegacyNcxInEpub3;
            return this;
        }

        public Builder writeRenditionMetadata(
                boolean writeRenditionMetadata
        ) {
            this.writeRenditionMetadata =
                    writeRenditionMetadata;
            return this;
        }

        public Builder omitDefaultAttributes(
                boolean omitDefaultAttributes
        ) {
            this.omitDefaultAttributes =
                    omitDefaultAttributes;
            return this;
        }

        public Builder includeLegacyPageSpreadProperties(
                boolean includeLegacyPageSpreadProperties
        ) {
            this.includeLegacyPageSpreadProperties =
                    includeLegacyPageSpreadProperties;
            return this;
        }

        public Builder lineSeparator(
                LineSeparator lineSeparator
        ) {
            this.lineSeparator = lineSeparator;
            return this;
        }

        public Builder allowSpacesInPaths(
                boolean allowSpacesInPaths
        ) {
            this.allowSpacesInPaths = allowSpacesInPaths;
            return this;
        }

        public Builder detectCaseInsensitivePathCollisions(
                boolean detectCaseInsensitivePathCollisions
        ) {
            this.detectCaseInsensitivePathCollisions =
                    detectCaseInsensitivePathCollisions;
            return this;
        }

        public Builder validateXhtml(boolean validateXhtml) {
            this.validateXhtml = validateXhtml;
            return this;
        }

        public Builder validateCss(boolean validateCss) {
            this.validateCss = validateCss;
            return this;
        }

        public Builder runEpubCheck(boolean runEpubCheck) {
            this.runEpubCheck = runEpubCheck;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 엄격한 검증 옵션을 한 번에 적용합니다.
         *
         * @return 현재 Builder
         */
        public Builder strictValidation() {
            this.validateBeforeGeneration = true;
            this.validateAfterGeneration = true;
            this.failOnValidationWarning = true;
            this.failOnMissingResource = true;
            this.failOnUnknownMediaType = true;
            this.validateXhtml = true;
            this.validateCss = true;
            this.runEpubCheck = true;

            return this;
        }

        /**
         * 접근성 검증 옵션을 한 번에 적용합니다.
         *
         * @return 현재 Builder
         */
        public Builder strictAccessibility() {
            this.validateAccessibility = true;
            this.failOnMissingAlternativeText = true;

            return this;
        }

        /**
         * 재현 가능한 빌드 옵션을 적용합니다.
         *
         * @param timestamp 고정 생성 시각
         * @return 현재 Builder
         */
        public Builder reproducibleBuild(Instant timestamp) {
            this.reproducibleBuild = true;
            this.buildTimestamp = Objects.requireNonNull(
                    timestamp,
                    "Reproducible build timestamp must not be null."
            );

            return this;
        }

        /**
         * EPUB 3 하위 호환 옵션을 적용합니다.
         *
         * @return 현재 Builder
         */
        public Builder epub3BackwardCompatibility() {
            this.includeLegacyNcxInEpub3 = true;
            this.generateNcx = true;
            this.includeLegacyPageSpreadProperties = true;

            return this;
        }

        public EpubGenerationOptions build() {
            return new EpubGenerationOptions(this);
        }
    }
}