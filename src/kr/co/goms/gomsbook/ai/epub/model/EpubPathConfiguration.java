/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB 생성 과정에서 사용하는 디렉터리 및 파일 경로 규칙을 정의합니다.
 *
 * <p>기본 EPUB 구조는 다음과 같습니다.</p>
 *
 * <pre>
 * {@code
 * book.epub
 * ├─ mimetype
 * ├─ META-INF
 * │  └─ container.xml
 * └─ OEBPS
 *    ├─ content.opf
 *    ├─ Text
 *    ├─ Styles
 *    ├─ Images
 *    ├─ Fonts
 *    ├─ Audio
 *    ├─ Video
 *    ├─ MediaOverlays
 *    └─ Misc
 * }
 * </pre>
 *
 * <p>모든 EPUB 내부 경로는 운영체제와 관계없이 슬래시({@code /})를
 * 사용합니다. 로컬 작업 디렉터리는 {@link Path}로 관리합니다.</p>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubPathConfiguration {

    public static final String DEFAULT_MIMETYPE_FILE = "mimetype";

    public static final String DEFAULT_META_INF_DIRECTORY = "META-INF";

    public static final String DEFAULT_CONTAINER_FILE = "container.xml";

    public static final String DEFAULT_CONTENT_ROOT_DIRECTORY = "OEBPS";

    public static final String DEFAULT_PACKAGE_DOCUMENT_FILE = "content.opf";

    public static final String DEFAULT_TEXT_DIRECTORY = "Text";

    public static final String DEFAULT_STYLE_DIRECTORY = "Styles";

    public static final String DEFAULT_IMAGE_DIRECTORY = "Images";

    public static final String DEFAULT_FONT_DIRECTORY = "Fonts";

    public static final String DEFAULT_AUDIO_DIRECTORY = "Audio";

    public static final String DEFAULT_VIDEO_DIRECTORY = "Video";

    public static final String DEFAULT_MEDIA_OVERLAY_DIRECTORY =
            "MediaOverlays";

    public static final String DEFAULT_MISC_DIRECTORY = "Misc";

    public static final String DEFAULT_NAVIGATION_FILE = "nav.xhtml";

    public static final String DEFAULT_NCX_FILE = "toc.ncx";

    /**
     * EPUB을 조립할 로컬 작업 루트 디렉터리입니다.
     */
    private final Path workingDirectory;

    /**
     * 최종 EPUB 파일을 기록할 로컬 경로입니다.
     */
    private final Path outputFile;

    /**
     * EPUB 루트의 mimetype 파일명입니다.
     */
    private final String mimetypeFileName;

    /**
     * EPUB 루트의 META-INF 디렉터리명입니다.
     */
    private final String metaInfDirectory;

    /**
     * META-INF 내부 container.xml 파일명입니다.
     */
    private final String containerFileName;

    /**
     * OPF 패키지와 콘텐츠가 위치하는 루트 디렉터리입니다.
     */
    private final String contentRootDirectory;

    /**
     * OPF 패키지 문서 파일명입니다.
     */
    private final String packageDocumentFileName;

    private final String textDirectory;

    private final String styleDirectory;

    private final String imageDirectory;

    private final String fontDirectory;

    private final String audioDirectory;

    private final String videoDirectory;

    private final String mediaOverlayDirectory;

    private final String miscDirectory;

    private final String navigationFileName;

    private final String ncxFileName;

    /**
     * EPUB 리소스 경로에 공백을 허용할지 여부입니다.
     */
    private final boolean allowSpaces;

    /**
     * 생성 시 기본 디렉터리를 자동으로 만들지 여부입니다.
     */
    private final boolean createDirectories;

    /**
     * 기존 작업 디렉터리 내용을 제거할지 여부입니다.
     */
    private final boolean cleanWorkingDirectory;

    private EpubPathConfiguration(Builder builder) {
        this.workingDirectory = normalizeLocalPath(
                builder.workingDirectory,
                "workingDirectory",
                false
        );
        this.outputFile = normalizeLocalPath(
                builder.outputFile,
                "outputFile",
                true
        );
        this.mimetypeFileName = normalizeFileName(
                builder.mimetypeFileName,
                DEFAULT_MIMETYPE_FILE
        );
        this.metaInfDirectory = normalizeDirectoryName(
                builder.metaInfDirectory,
                DEFAULT_META_INF_DIRECTORY
        );
        this.containerFileName = normalizeFileName(
                builder.containerFileName,
                DEFAULT_CONTAINER_FILE
        );
        this.contentRootDirectory = normalizeDirectoryName(
                builder.contentRootDirectory,
                DEFAULT_CONTENT_ROOT_DIRECTORY
        );
        this.packageDocumentFileName = normalizeFileName(
                builder.packageDocumentFileName,
                DEFAULT_PACKAGE_DOCUMENT_FILE
        );
        this.textDirectory = normalizeDirectoryName(
                builder.textDirectory,
                DEFAULT_TEXT_DIRECTORY
        );
        this.styleDirectory = normalizeDirectoryName(
                builder.styleDirectory,
                DEFAULT_STYLE_DIRECTORY
        );
        this.imageDirectory = normalizeDirectoryName(
                builder.imageDirectory,
                DEFAULT_IMAGE_DIRECTORY
        );
        this.fontDirectory = normalizeDirectoryName(
                builder.fontDirectory,
                DEFAULT_FONT_DIRECTORY
        );
        this.audioDirectory = normalizeDirectoryName(
                builder.audioDirectory,
                DEFAULT_AUDIO_DIRECTORY
        );
        this.videoDirectory = normalizeDirectoryName(
                builder.videoDirectory,
                DEFAULT_VIDEO_DIRECTORY
        );
        this.mediaOverlayDirectory = normalizeDirectoryName(
                builder.mediaOverlayDirectory,
                DEFAULT_MEDIA_OVERLAY_DIRECTORY
        );
        this.miscDirectory = normalizeDirectoryName(
                builder.miscDirectory,
                DEFAULT_MISC_DIRECTORY
        );
        this.navigationFileName = normalizeFileName(
                builder.navigationFileName,
                DEFAULT_NAVIGATION_FILE
        );
        this.ncxFileName = normalizeFileName(
                builder.ncxFileName,
                DEFAULT_NCX_FILE
        );
        this.allowSpaces = builder.allowSpaces;
        this.createDirectories = builder.createDirectories;
        this.cleanWorkingDirectory = builder.cleanWorkingDirectory;

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 EPUB 경로 설정을 생성합니다.
     *
     * @param workingDirectory 작업 디렉터리
     * @param outputFile       최종 EPUB 파일
     * @return 경로 설정
     */
    public static EpubPathConfiguration of(
            Path workingDirectory,
            Path outputFile
    ) {
        return builder()
                .workingDirectory(workingDirectory)
                .outputFile(outputFile)
                .build();
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public String getMimetypeFileName() {
        return mimetypeFileName;
    }

    public String getMetaInfDirectory() {
        return metaInfDirectory;
    }

    public String getContainerFileName() {
        return containerFileName;
    }

    public String getContentRootDirectory() {
        return contentRootDirectory;
    }

    public String getPackageDocumentFileName() {
        return packageDocumentFileName;
    }

    public String getTextDirectory() {
        return textDirectory;
    }

    public String getStyleDirectory() {
        return styleDirectory;
    }

    public String getImageDirectory() {
        return imageDirectory;
    }

    public String getFontDirectory() {
        return fontDirectory;
    }

    public String getAudioDirectory() {
        return audioDirectory;
    }

    public String getVideoDirectory() {
        return videoDirectory;
    }

    public String getMediaOverlayDirectory() {
        return mediaOverlayDirectory;
    }

    public String getMiscDirectory() {
        return miscDirectory;
    }

    public String getNavigationFileName() {
        return navigationFileName;
    }

    public String getNcxFileName() {
        return ncxFileName;
    }

    public boolean isAllowSpaces() {
        return allowSpaces;
    }

    public boolean isCreateDirectories() {
        return createDirectories;
    }

    public boolean isCleanWorkingDirectory() {
        return cleanWorkingDirectory;
    }

    /**
     * 로컬 mimetype 파일 경로를 반환합니다.
     */
    public Path getMimetypePath() {
        return workingDirectory.resolve(mimetypeFileName);
    }

    /**
     * 로컬 META-INF 디렉터리 경로를 반환합니다.
     */
    public Path getMetaInfPath() {
        return workingDirectory.resolve(metaInfDirectory);
    }

    /**
     * 로컬 container.xml 경로를 반환합니다.
     */
    public Path getContainerPath() {
        return getMetaInfPath().resolve(containerFileName);
    }

    /**
     * 로컬 콘텐츠 루트 디렉터리 경로를 반환합니다.
     */
    public Path getContentRootPath() {
        return workingDirectory.resolve(contentRootDirectory);
    }

    /**
     * 로컬 OPF 패키지 문서 경로를 반환합니다.
     */
    public Path getPackageDocumentPath() {
        return getContentRootPath().resolve(packageDocumentFileName);
    }

    public Path getTextPath() {
        return getContentRootPath().resolve(textDirectory);
    }

    public Path getStylePath() {
        return getContentRootPath().resolve(styleDirectory);
    }

    public Path getImagePath() {
        return getContentRootPath().resolve(imageDirectory);
    }

    public Path getFontPath() {
        return getContentRootPath().resolve(fontDirectory);
    }

    public Path getAudioPath() {
        return getContentRootPath().resolve(audioDirectory);
    }

    public Path getVideoPath() {
        return getContentRootPath().resolve(videoDirectory);
    }

    public Path getMediaOverlayPath() {
        return getContentRootPath().resolve(mediaOverlayDirectory);
    }

    public Path getMiscPath() {
        return getContentRootPath().resolve(miscDirectory);
    }

    public Path getNavigationPath() {
        return getTextPath().resolve(navigationFileName);
    }

    public Path getNcxPath() {
        return getContentRootPath().resolve(ncxFileName);
    }

    /**
     * container.xml의 full-path 속성에 사용할 OPF 경로를 반환합니다.
     *
     * @return 예: {@code OEBPS/content.opf}
     */
    public String getPackageDocumentEpubPath() {
        return joinEpubPath(
                contentRootDirectory,
                packageDocumentFileName
        );
    }

    /**
     * manifest href의 기준 디렉터리에서 사용할 Text 경로를 반환합니다.
     *
     * @return {@code Text}
     */
    public String getTextEpubDirectory() {
        return textDirectory;
    }

    public String getStyleEpubDirectory() {
        return styleDirectory;
    }

    public String getImageEpubDirectory() {
        return imageDirectory;
    }

    public String getFontEpubDirectory() {
        return fontDirectory;
    }

    public String getAudioEpubDirectory() {
        return audioDirectory;
    }

    public String getVideoEpubDirectory() {
        return videoDirectory;
    }

    public String getMediaOverlayEpubDirectory() {
        return mediaOverlayDirectory;
    }

    public String getMiscEpubDirectory() {
        return miscDirectory;
    }

    /**
     * OPF manifest에 사용할 Navigation Document href를 반환합니다.
     *
     * @return 예: {@code Text/nav.xhtml}
     */
    public String getNavigationHref() {
        return joinEpubPath(
                textDirectory,
                navigationFileName
        );
    }

    /**
     * OPF manifest에 사용할 NCX href를 반환합니다.
     *
     * @return 예: {@code toc.ncx}
     */
    public String getNcxHref() {
        return ncxFileName;
    }

    /**
     * 지정한 XHTML 파일의 manifest href를 생성합니다.
     *
     * @param fileName XHTML 파일명
     * @return 예: {@code Text/chapter01.xhtml}
     */
    public String resolveTextHref(String fileName) {
        return resolveResourceHref(textDirectory, fileName);
    }

    public String resolveStyleHref(String fileName) {
        return resolveResourceHref(styleDirectory, fileName);
    }

    public String resolveImageHref(String fileName) {
        return resolveResourceHref(imageDirectory, fileName);
    }

    public String resolveFontHref(String fileName) {
        return resolveResourceHref(fontDirectory, fileName);
    }

    public String resolveAudioHref(String fileName) {
        return resolveResourceHref(audioDirectory, fileName);
    }

    public String resolveVideoHref(String fileName) {
        return resolveResourceHref(videoDirectory, fileName);
    }

    public String resolveMediaOverlayHref(String fileName) {
        return resolveResourceHref(
                mediaOverlayDirectory,
                fileName
        );
    }

    public String resolveMiscHref(String fileName) {
        return resolveResourceHref(miscDirectory, fileName);
    }

    /**
     * 리소스 유형에 맞는 manifest href를 생성합니다.
     *
     * @param resourceType 리소스 유형
     * @param fileName     파일명
     * @return manifest href
     */
    public String resolveHref(
            EpubResourceType resourceType,
            String fileName
    ) {
        Objects.requireNonNull(
                resourceType,
                "EPUB resource type must not be null."
        );

        return switch (resourceType.getCategory()) {
            case DOCUMENT, NAVIGATION ->
                    resolveTextHref(fileName);

            case STYLE ->
                    resolveStyleHref(fileName);

            case IMAGE ->
                    resolveImageHref(fileName);

            case FONT ->
                    resolveFontHref(fileName);

            case AUDIO ->
                    resolveAudioHref(fileName);

            case VIDEO ->
                    resolveVideoHref(fileName);

            case MEDIA_OVERLAY ->
                    resolveMediaOverlayHref(fileName);

            case SCRIPT, TRACK, DATA, UNKNOWN ->
                    resolveMiscHref(fileName);
        };
    }

    /**
     * 리소스 유형에 맞는 로컬 출력 경로를 생성합니다.
     *
     * @param resourceType 리소스 유형
     * @param fileName     파일명
     * @return 로컬 출력 경로
     */
    public Path resolveLocalPath(
            EpubResourceType resourceType,
            String fileName
    ) {
        String normalizedFileName = validateRelativeResourcePath(
                fileName
        );

        Objects.requireNonNull(
                resourceType,
                "EPUB resource type must not be null."
        );

        Path directory = switch (resourceType.getCategory()) {
            case DOCUMENT, NAVIGATION -> getTextPath();
            case STYLE -> getStylePath();
            case IMAGE -> getImagePath();
            case FONT -> getFontPath();
            case AUDIO -> getAudioPath();
            case VIDEO -> getVideoPath();
            case MEDIA_OVERLAY -> getMediaOverlayPath();
            case SCRIPT, TRACK, DATA, UNKNOWN -> getMiscPath();
        };

        return directory.resolve(
                normalizedFileName.replace('/', java.io.File.separatorChar)
        );
    }

    /**
     * manifest href에 해당하는 로컬 파일 경로를 반환합니다.
     *
     * <p>href는 OPF 패키지 문서를 기준으로 해석합니다.</p>
     *
     * @param href manifest href
     * @return 로컬 파일 경로
     */
    public Path resolveHrefToLocalPath(String href) {
        String normalized = validateRelativeResourcePath(href);

        return getContentRootPath().resolve(
                normalized.replace('/', java.io.File.separatorChar)
        );
    }

    /**
     * 로컬 콘텐츠 파일을 OPF 기준 manifest href로 변환합니다.
     *
     * @param localPath 콘텐츠 루트 하위의 로컬 파일
     * @return manifest href
     */
    public String relativizeToHref(Path localPath) {
        Objects.requireNonNull(
                localPath,
                "Local resource path must not be null."
        );

        Path normalizedRoot = getContentRootPath()
                .toAbsolutePath()
                .normalize();

        Path normalizedResource = localPath
                .toAbsolutePath()
                .normalize();

        if (!normalizedResource.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                    "Resource path is outside the EPUB content root: "
                            + localPath
            );
        }

        return normalizedRoot
                .relativize(normalizedResource)
                .toString()
                .replace('\\', '/');
    }

    /**
     * href가 콘텐츠 루트 내부의 안전한 상대 경로인지 확인합니다.
     *
     * @param href manifest href
     * @return 안전하면 {@code true}
     */
    public boolean isSafeResourceHref(String href) {
        try {
            validateRelativeResourcePath(href);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 현재 설정을 EpubPackage의 패키지 경로에 적용합니다.
     *
     * @param packageBuilder EPUB 패키지 Builder
     * @return 전달받은 Builder
     */
    public EpubPackage.Builder applyTo(
            EpubPackage.Builder packageBuilder
    ) {
        Objects.requireNonNull(
                packageBuilder,
                "EPUB package builder must not be null."
        );

        return packageBuilder.packageDocumentPath(
                getPackageDocumentEpubPath()
        );
    }

    public Builder toBuilder() {
        return new Builder()
                .workingDirectory(workingDirectory)
                .outputFile(outputFile)
                .mimetypeFileName(mimetypeFileName)
                .metaInfDirectory(metaInfDirectory)
                .containerFileName(containerFileName)
                .contentRootDirectory(contentRootDirectory)
                .packageDocumentFileName(packageDocumentFileName)
                .textDirectory(textDirectory)
                .styleDirectory(styleDirectory)
                .imageDirectory(imageDirectory)
                .fontDirectory(fontDirectory)
                .audioDirectory(audioDirectory)
                .videoDirectory(videoDirectory)
                .mediaOverlayDirectory(mediaOverlayDirectory)
                .miscDirectory(miscDirectory)
                .navigationFileName(navigationFileName)
                .ncxFileName(ncxFileName)
                .allowSpaces(allowSpaces)
                .createDirectories(createDirectories)
                .cleanWorkingDirectory(cleanWorkingDirectory);
    }

    private String resolveResourceHref(
            String directory,
            String fileName
    ) {
        String normalizedFileName = validateRelativeResourcePath(
                fileName
        );

        return joinEpubPath(directory, normalizedFileName);
    }

    private String validateRelativeResourcePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB resource path must not be blank."
            );
        }

        String normalized = normalizeEpubPath(value);

        if (isAbsoluteOrRemote(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB resource path must be relative: " + value
            );
        }

        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        "EPUB resource path must not contain "
                                + "parent traversal: "
                                + value
                );
            }

            if (!allowSpaces && segment.contains(" ")) {
                throw new IllegalArgumentException(
                        "EPUB resource path must not contain spaces: "
                                + value
                );
            }
        }

        return normalized;
    }

    private void validate() {
        validateDistinctDirectories();

        if (!outputFile.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(".epub")) {
            throw new IllegalArgumentException(
                    "EPUB output file must end with .epub: "
                            + outputFile
            );
        }

        if (!packageDocumentFileName
                .toLowerCase(Locale.ROOT)
                .endsWith(".opf")) {
            throw new IllegalArgumentException(
                    "Package document file must end with .opf: "
                            + packageDocumentFileName
            );
        }

        if (!containerFileName
                .equalsIgnoreCase("container.xml")) {
            /*
             * EPUB 표준 위치의 파일명은 container.xml이어야 합니다.
             */
            throw new IllegalArgumentException(
                    "EPUB container file name must be container.xml: "
                            + containerFileName
            );
        }

        if (!mimetypeFileName.equals("mimetype")) {
            throw new IllegalArgumentException(
                    "EPUB mimetype file name must be mimetype: "
                            + mimetypeFileName
            );
        }
    }

    private void validateDistinctDirectories() {
        String[] directories = {
                textDirectory,
                styleDirectory,
                imageDirectory,
                fontDirectory,
                audioDirectory,
                videoDirectory,
                mediaOverlayDirectory,
                miscDirectory
        };

        for (int first = 0; first < directories.length; first++) {
            for (int second = first + 1;
                    second < directories.length;
                    second++) {
                if (directories[first]
                        .equalsIgnoreCase(directories[second])) {
                    throw new IllegalArgumentException(
                            "EPUB resource directories must be distinct: "
                                    + directories[first]
                    );
                }
            }
        }
    }

    private static Path normalizeLocalPath(
            Path value,
            String fieldName,
            boolean fileRequired
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "EPUB " + fieldName + " must not be null."
            );
        }

        Path normalized = value.toAbsolutePath().normalize();

        if (fileRequired && normalized.getFileName() == null) {
            throw new IllegalArgumentException(
                    "EPUB " + fieldName + " must reference a file: "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeDirectoryName(
            String value,
            String defaultValue
    ) {
        String normalized = value == null || value.isBlank()
                ? defaultValue
                : normalizeEpubPath(value);

        if (normalized.contains("/")) {
            throw new IllegalArgumentException(
                    "EPUB directory name must be a single path segment: "
                            + value
            );
        }

        validatePathSegment(normalized);

        return normalized;
    }

    private static String normalizeFileName(
            String value,
            String defaultValue
    ) {
        String normalized = value == null || value.isBlank()
                ? defaultValue
                : normalizeEpubPath(value);

        if (normalized.contains("/")) {
            throw new IllegalArgumentException(
                    "EPUB file name must not contain directories: "
                            + value
            );
        }

        validatePathSegment(normalized);

        return normalized;
    }

    private static void validatePathSegment(String value) {
        if (value == null || value.isBlank()
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB path segment: " + value
            );
        }

        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "EPUB path segment contains a null character."
            );
        }
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

        if (normalized.endsWith("/")
                && normalized.length() > 1) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }

        return normalized;
    }

    private static String joinEpubPath(
            String first,
            String second
    ) {
        String normalizedFirst = normalizeEpubPath(first);
        String normalizedSecond = normalizeEpubPath(second);

        if (normalizedFirst.isBlank()) {
            return normalizedSecond;
        }

        if (normalizedSecond.isBlank()) {
            return normalizedFirst;
        }

        return normalizedFirst + "/" + normalizedSecond;
    }

    private static boolean isAbsoluteOrRemote(String value) {
        if (value.startsWith("/")) {
            return true;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("file:")
                || lower.startsWith("jar:")) {
            return true;
        }

        /*
         * Windows 드라이브 문자 경로를 검사합니다.
         */
        return value.length() >= 2
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':';
    }

    @Override
    public String toString() {
        return "EpubPathConfiguration{"
                + "workingDirectory=" + workingDirectory
                + ", outputFile=" + outputFile
                + ", packageDocumentPath='"
                + getPackageDocumentEpubPath() + '\''
                + ", textDirectory='" + textDirectory + '\''
                + ", styleDirectory='" + styleDirectory + '\''
                + ", imageDirectory='" + imageDirectory + '\''
                + ", createDirectories=" + createDirectories
                + ", cleanWorkingDirectory="
                + cleanWorkingDirectory
                + '}';
    }

    /**
     * {@link EpubPathConfiguration} 생성 Builder입니다.
     */
    public static final class Builder {

        private Path workingDirectory;

        private Path outputFile;

        private String mimetypeFileName =
                DEFAULT_MIMETYPE_FILE;

        private String metaInfDirectory =
                DEFAULT_META_INF_DIRECTORY;

        private String containerFileName =
                DEFAULT_CONTAINER_FILE;

        private String contentRootDirectory =
                DEFAULT_CONTENT_ROOT_DIRECTORY;

        private String packageDocumentFileName =
                DEFAULT_PACKAGE_DOCUMENT_FILE;

        private String textDirectory =
                DEFAULT_TEXT_DIRECTORY;

        private String styleDirectory =
                DEFAULT_STYLE_DIRECTORY;

        private String imageDirectory =
                DEFAULT_IMAGE_DIRECTORY;

        private String fontDirectory =
                DEFAULT_FONT_DIRECTORY;

        private String audioDirectory =
                DEFAULT_AUDIO_DIRECTORY;

        private String videoDirectory =
                DEFAULT_VIDEO_DIRECTORY;

        private String mediaOverlayDirectory =
                DEFAULT_MEDIA_OVERLAY_DIRECTORY;

        private String miscDirectory =
                DEFAULT_MISC_DIRECTORY;

        private String navigationFileName =
                DEFAULT_NAVIGATION_FILE;

        private String ncxFileName =
                DEFAULT_NCX_FILE;

        private boolean allowSpaces;

        private boolean createDirectories = true;

        private boolean cleanWorkingDirectory = true;

        private Builder() {
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = toPath(workingDirectory);
            return this;
        }

        public Builder outputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder outputFile(String outputFile) {
            this.outputFile = toPath(outputFile);
            return this;
        }

        public Builder mimetypeFileName(String value) {
            this.mimetypeFileName = value;
            return this;
        }

        public Builder metaInfDirectory(String value) {
            this.metaInfDirectory = value;
            return this;
        }

        public Builder containerFileName(String value) {
            this.containerFileName = value;
            return this;
        }

        public Builder contentRootDirectory(String value) {
            this.contentRootDirectory = value;
            return this;
        }

        public Builder packageDocumentFileName(String value) {
            this.packageDocumentFileName = value;
            return this;
        }

        public Builder textDirectory(String value) {
            this.textDirectory = value;
            return this;
        }

        public Builder styleDirectory(String value) {
            this.styleDirectory = value;
            return this;
        }

        public Builder imageDirectory(String value) {
            this.imageDirectory = value;
            return this;
        }

        public Builder fontDirectory(String value) {
            this.fontDirectory = value;
            return this;
        }

        public Builder audioDirectory(String value) {
            this.audioDirectory = value;
            return this;
        }

        public Builder videoDirectory(String value) {
            this.videoDirectory = value;
            return this;
        }

        public Builder mediaOverlayDirectory(String value) {
            this.mediaOverlayDirectory = value;
            return this;
        }

        public Builder miscDirectory(String value) {
            this.miscDirectory = value;
            return this;
        }

        public Builder navigationFileName(String value) {
            this.navigationFileName = value;
            return this;
        }

        public Builder ncxFileName(String value) {
            this.ncxFileName = value;
            return this;
        }

        public Builder allowSpaces(boolean allowSpaces) {
            this.allowSpaces = allowSpaces;
            return this;
        }

        public Builder createDirectories(
                boolean createDirectories
        ) {
            this.createDirectories = createDirectories;
            return this;
        }

        public Builder cleanWorkingDirectory(
                boolean cleanWorkingDirectory
        ) {
            this.cleanWorkingDirectory = cleanWorkingDirectory;
            return this;
        }

        public EpubPathConfiguration build() {
            return new EpubPathConfiguration(this);
        }

        private static Path toPath(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }

            return Paths.get(value.trim());
        }
    }
}