/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * EPUB 생성 작업에 필요한 전체 입력값을 표현합니다.
 *
 * <p>생성 대상 EPUB 패키지, 경로 설정, 생성 옵션 및 요청 단위의
 * 실행 정보를 하나의 객체로 묶습니다.</p>
 *
 * <pre>
 * {@code
 * EpubGenerationRequest request =
 *         EpubGenerationRequest.builder()
 *                 .epubPackage(epubPackage)
 *                 .pathConfiguration(pathConfiguration)
 *                 .options(options)
 *                 .build();
 * }
 * </pre>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubGenerationRequest {

    /**
     * 생성 요청을 식별하는 고유 ID입니다.
     */
    private final String requestId;

    /**
     * 원본 GomsBook 프로젝트의 루트 경로입니다.
     *
     * <p>접근성 검증 및 프로젝트 상대 경로 계산 등에 사용합니다.</p>
     */
    private final Path projectRoot;
    
    /**
     * 생성할 EPUB 패키지 모델입니다.
     */
    private final EpubPackage epubPackage;

    /**
     * 작업 디렉터리와 출력 파일 경로 설정입니다.
     */
    private final EpubPathConfiguration pathConfiguration;

    /**
     * EPUB 생성 동작 옵션입니다.
     */
    private final EpubGenerationOptions options;

    /**
     * 요청 생성 시각입니다.
     */
    private final Instant requestedAt;

    /**
     * 요청을 생성한 사용자 또는 시스템 식별자입니다.
     */
    private final String requestedBy;

    /**
     * 기존 출력 파일을 대체할 때 사용할 임시 출력 경로입니다.
     *
     * <p>지정하지 않으면 생성 구현체가 자체적으로 결정합니다.</p>
     */
    private final Path temporaryOutputFile;

    /**
     * 요청 단위의 추가 속성입니다.
     */
    private final Map<String, String> attributes;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubGenerationRequest(Builder builder) {
        this.requestId = normalizeRequestId(builder.requestId);
        
        this.projectRoot =
                Objects.requireNonNull(
                        builder.projectRoot,
                        "EPUB project root must not be null."
                )
                .toAbsolutePath()
                .normalize();

        this.epubPackage = Objects.requireNonNull(
                builder.epubPackage,
                "EPUB package must not be null."
        );

        this.pathConfiguration = Objects.requireNonNull(
                builder.pathConfiguration,
                "EPUB path configuration must not be null."
        );

        this.options = builder.options == null
                ? EpubGenerationOptions.defaultOptions()
                : builder.options;

        this.requestedAt = builder.requestedAt == null
                ? Instant.now()
                : builder.requestedAt;

        this.requestedBy = normalizeOptionalText(
                builder.requestedBy
        );

        this.temporaryOutputFile = normalizeOptionalPath(
                builder.temporaryOutputFile
        );

        this.attributes = immutableAttributes(
                builder.attributes
        );

        this.description = normalizeOptionalText(
                builder.description
        );

        if (builder.validateOnBuild) {
            validate();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 필수 입력값으로 EPUB 생성 요청을 생성합니다.
     *
     * @param epubPackage       EPUB 패키지
     * @param pathConfiguration 경로 설정
     * @return EPUB 생성 요청
     */
    public static EpubGenerationRequest of(
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration
    ) {
        return builder()
                .epubPackage(epubPackage)
                .pathConfiguration(pathConfiguration)
                .build();
    }

    /**
     * 필수 입력값과 생성 옵션으로 요청을 생성합니다.
     *
     * @param epubPackage       EPUB 패키지
     * @param pathConfiguration 경로 설정
     * @param options           생성 옵션
     * @return EPUB 생성 요청
     */
    public static EpubGenerationRequest of(
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) {
        return builder()
                .epubPackage(epubPackage)
                .pathConfiguration(pathConfiguration)
                .options(options)
                .build();
    }

    public String getRequestId() {
        return requestId;
    }
    
    public Path getProjectRoot() {
    	return projectRoot;
    }

    public EpubPackage getEpubPackage() {
        return epubPackage;
    }

    public EpubPathConfiguration getPathConfiguration() {
        return pathConfiguration;
    }

    public EpubGenerationOptions getOptions() {
        return options;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Optional<String> getRequestedBy() {
        return Optional.ofNullable(requestedBy);
    }

    public Optional<Path> getTemporaryOutputFile() {
        return Optional.ofNullable(temporaryOutputFile);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * 최종 출력 EPUB 파일 경로를 반환합니다.
     *
     * @return 출력 EPUB 파일
     */
    public Path getOutputFile() {
        return pathConfiguration.getOutputFile();
    }

    /**
     * EPUB 생성 작업 디렉터리를 반환합니다.
     *
     * @return 작업 디렉터리
     */
    public Path getWorkingDirectory() {
        return pathConfiguration.getWorkingDirectory();
    }

    /**
     * EPUB OPF 패키지 문서의 로컬 출력 경로를 반환합니다.
     *
     * @return content.opf 경로
     */
    public Path getPackageDocumentPath() {
        return pathConfiguration.getPackageDocumentPath();
    }

    /**
     * EPUB 내부 OPF 패키지 경로를 반환합니다.
     *
     * @return 예: {@code OEBPS/content.opf}
     */
    public String getPackageDocumentEpubPath() {
        return pathConfiguration.getPackageDocumentEpubPath();
    }

    /**
     * 생성할 EPUB 버전을 반환합니다.
     *
     * @return EPUB 버전
     */
    public EpubVersion getVersion() {
        return epubPackage.getVersion();
    }

    /**
     * 출판물 제목을 반환합니다.
     *
     * @return 제목
     */
    public Optional<String> getTitle() {
        return epubPackage.getTitle();
    }

    /**
     * 출판물 고유 식별자를 반환합니다.
     *
     * @return ISBN, UUID 또는 기타 식별자
     */
    public String getUniqueIdentifier() {
        return epubPackage.getUniqueIdentifierValue();
    }

    /**
     * 요청 속성을 이름으로 조회합니다.
     *
     * @param name 속성 이름
     * @return 속성값
     */
    public Optional<String> getAttribute(String name) {
        String normalizedName = normalizeAttributeName(name);

        if (normalizedName == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                attributes.get(normalizedName)
        );
    }

    /**
     * 요청 속성이 존재하는지 확인합니다.
     *
     * @param name 속성 이름
     * @return 존재하면 {@code true}
     */
    public boolean hasAttribute(String name) {
        return getAttribute(name).isPresent();
    }

    /**
     * 최종 출력 파일이 이미 존재할 때 덮어쓸 수 있는지 확인합니다.
     *
     * @return 덮어쓰기 가능하면 {@code true}
     */
    public boolean isOverwriteOutput() {
        return options.isOverwriteOutput();
    }

    /**
     * 생성 전 검증을 수행해야 하는지 확인합니다.
     *
     * @return 생성 전 검증 여부
     */
    public boolean shouldValidateBeforeGeneration() {
        return options.isValidateBeforeGeneration();
    }

    /**
     * 생성 후 검증을 수행해야 하는지 확인합니다.
     *
     * @return 생성 후 검증 여부
     */
    public boolean shouldValidateAfterGeneration() {
        return options.isValidateAfterGeneration();
    }

    /**
     * EPUBCheck를 실행해야 하는지 확인합니다.
     *
     * @return EPUBCheck 실행 여부
     */
    public boolean shouldRunEpubCheck() {
        return options.isRunEpubCheck();
    }

    /**
     * 접근성 검증을 수행해야 하는지 확인합니다.
     *
     * @return 접근성 검증 여부
     */
    public boolean shouldValidateAccessibility() {
        return options.isValidateAccessibility();
    }

    /**
     * 재현 가능한 빌드 요청인지 확인합니다.
     *
     * @return 재현 가능한 빌드이면 {@code true}
     */
    public boolean isReproducibleBuild() {
        return options.isReproducibleBuild();
    }

    /**
     * 생성 작업에 사용할 기준 시각을 반환합니다.
     *
     * @return 생성 기준 시각
     */
    public Instant resolveBuildTimestamp() {
        return options.resolveBuildTimestamp();
    }

    /**
     * 요청 전체의 입력값 일관성을 검증합니다.
     */
    public void validate() {
        validateVersionConsistency();
        validatePackagePathConsistency();
        validateOutputPath();
        validateTemporaryOutputPath();

        if (options.isValidateBeforeGeneration()) {
            epubPackage.validate();
        }

        options.validate(
                epubPackage,
                pathConfiguration
        );
    }

    /**
     * EPUB 패키지와 생성 옵션의 버전이 일치하는지 검증합니다.
     */
    private void validateVersionConsistency() {
        if (epubPackage.getVersion() != options.getVersion()) {
            throw new IllegalStateException(
                    "EPUB package version and generation option "
                            + "version do not match: "
                            + epubPackage.getVersion()
                            + " != "
                            + options.getVersion()
            );
        }
    }

    /**
     * EPUB 패키지의 OPF 경로와 경로 설정의 OPF 경로가
     * 일치하는지 검증합니다.
     */
    private void validatePackagePathConsistency() {
        String packagePath = normalizeEpubPath(
                epubPackage.getPackageDocumentPath()
        );

        String configuredPath = normalizeEpubPath(
                pathConfiguration.getPackageDocumentEpubPath()
        );

        if (!packagePath.equals(configuredPath)) {
            throw new IllegalStateException(
                    "EPUB package document path and path configuration "
                            + "do not match: "
                            + packagePath
                            + " != "
                            + configuredPath
            );
        }
    }

    private void validateOutputPath() {
        Path outputFile = pathConfiguration.getOutputFile();
        Path workingDirectory =
                pathConfiguration.getWorkingDirectory();

        if (outputFile.equals(workingDirectory)) {
            throw new IllegalStateException(
                    "EPUB output file must not be the working directory: "
                            + outputFile
            );
        }

        if (outputFile.startsWith(workingDirectory)) {
            throw new IllegalStateException(
                    "EPUB output file should not be located inside "
                            + "the working directory: "
                            + outputFile
            );
        }
    }

    private void validateTemporaryOutputPath() {
        if (temporaryOutputFile == null) {
            return;
        }

        Path outputFile = pathConfiguration.getOutputFile();

        if (temporaryOutputFile.equals(outputFile)) {
            throw new IllegalStateException(
                    "Temporary EPUB output file must differ from "
                            + "the final output file: "
                            + outputFile
            );
        }

        if (!temporaryOutputFile
                .getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".epub")) {
            throw new IllegalStateException(
                    "Temporary EPUB output file must end with .epub: "
                            + temporaryOutputFile
            );
        }
    }

    /**
     * 현재 요청을 기반으로 Builder를 생성합니다.
     *
     * <p>EPUB 패키지는 독립 복사본으로 전달합니다.</p>
     *
     * @return 복사된 Builder
     */
    public Builder toBuilder() {
        return new Builder()
                .requestId(requestId)
                .projectRoot(projectRoot)
                .epubPackage(epubPackage.copy())
                .pathConfiguration(pathConfiguration)
                .options(options)
                .requestedAt(requestedAt)
                .requestedBy(requestedBy)
                .temporaryOutputFile(temporaryOutputFile)
                .attributes(attributes)
                .description(description);
    }

    /**
     * 새로운 요청 ID를 가진 복사본을 생성합니다.
     *
     * @return 복제된 생성 요청
     */
    public EpubGenerationRequest copyAsNewRequest() {
        return toBuilder()
                .requestId(null)
                .requestedAt(Instant.now())
                .build();
    }

    private static String normalizeRequestId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String normalized = value.trim();

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB generation request id must not contain "
                            + "whitespace: "
                            + value
            );
        }

        return normalized;
    }

    private static Path normalizeOptionalPath(Path value) {
        if (value == null) {
            return null;
        }

        return value.toAbsolutePath().normalize();
    }

    private static Map<String, String> immutableAttributes(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = requireAttributeName(entry.getKey());
            String value = requireAttributeValue(
                    entry.getValue(),
                    name
            );

            if (result.put(name, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate EPUB generation request attribute: "
                                + name
                );
            }
        }

        return Map.copyOf(result);
    }

    private static String normalizeAttributeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String requireAttributeName(String value) {
        String normalized = normalizeAttributeName(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "EPUB generation request attribute name "
                            + "must not be blank."
            );
        }

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB generation request attribute name must not "
                            + "contain whitespace: "
                            + value
            );
        }

        return normalized;
    }

    private static String requireAttributeValue(
            String value,
            String attributeName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB generation request attribute value "
                            + "must not be blank: "
                            + attributeName
            );
        }

        return value.trim();
    }

    private static String normalizeEpubPath(String value) {
        String normalized = value.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }

        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "EpubGenerationRequest{"
                + "requestId='" + requestId + '\''
                + ", requestedAt=" + requestedAt
                + ", requestedBy='" + requestedBy + '\''
                + ", version=" + getVersion()
                + ", title='" + getTitle().orElse(null) + '\''
                + ", outputFile=" + getOutputFile()
                + ", workingDirectory=" + getWorkingDirectory()
                + ", reproducibleBuild="
                + isReproducibleBuild()
                + '}';
    }

    /**
     * {@link EpubGenerationRequest} 생성 Builder입니다.
     */
    public static final class Builder {

        private String requestId;
        
        private Path projectRoot;

        private EpubPackage epubPackage;

        private EpubPathConfiguration pathConfiguration;

        private EpubGenerationOptions options;

        private Instant requestedAt;

        private String requestedBy;

        private Path temporaryOutputFile;

        private final Map<String, String> attributes =
                new LinkedHashMap<>();

        private String description;

        private boolean validateOnBuild = true;

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder epubPackage(EpubPackage epubPackage) {
            this.epubPackage = epubPackage;
            return this;
        }

        public Builder pathConfiguration(
                EpubPathConfiguration pathConfiguration
        ) {
            this.pathConfiguration = pathConfiguration;
            return this;
        }

        public Builder options(EpubGenerationOptions options) {
            this.options = options;
            return this;
        }

        public Builder requestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public Builder requestedBy(String requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }

        public Builder temporaryOutputFile(
                Path temporaryOutputFile
        ) {
            this.temporaryOutputFile = temporaryOutputFile;
            return this;
        }

        public Builder temporaryOutputFile(
                String temporaryOutputFile
        ) {
            this.temporaryOutputFile =
                    temporaryOutputFile == null
                            || temporaryOutputFile.isBlank()
                            ? null
                            : Path.of(temporaryOutputFile.trim());

            return this;
        }

        public Builder attribute(
                String name,
                String value
        ) {
            attributes.put(name, value);
            return this;
        }

        public Builder attributes(
                Map<String, String> attributes
        ) {
            if (attributes == null) {
                return this;
            }

            this.attributes.putAll(attributes);
            return this;
        }

        public Builder clearAttributes() {
            attributes.clear();
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder validateOnBuild(
                boolean validateOnBuild
        ) {
            this.validateOnBuild = validateOnBuild;
            return this;
        }

        /**
         * EPUB 패키지의 버전에 맞는 기본 생성 옵션을 설정합니다.
         *
         * @return 현재 Builder
         */
        public Builder defaultOptions() {
            EpubVersion version = epubPackage == null
                    ? EpubVersion.defaultVersion()
                    : epubPackage.getVersion();

            this.options = EpubGenerationOptions.builder()
                    .version(version)
                    .build();

            return this;
        }

        /**
         * 경로 설정의 OPF 경로를 EPUB 패키지에 반영한 복사본을
         * 설정합니다.
         *
         * @return 현재 Builder
         */
        public Builder synchronizePackagePath() {
            if (epubPackage == null) {
                throw new IllegalStateException(
                        "EPUB package must be configured before "
                                + "synchronizing its package path."
                );
            }

            if (pathConfiguration == null) {
                throw new IllegalStateException(
                        "EPUB path configuration must be configured "
                                + "before synchronizing the package path."
                );
            }

            this.epubPackage = epubPackage.toBuilder()
                    .packageDocumentPath(
                            pathConfiguration
                                    .getPackageDocumentEpubPath()
                    )
                    .build();

            return this;
        }

        public EpubGenerationRequest build() {
            return new EpubGenerationRequest(this);
        }
    }
}