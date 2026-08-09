/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;

/**
 * EPUB 생성 작업에 사용할 작업 디렉터리를 준비하고 정리하는 계약입니다.
 *
 * <p>작업 디렉터리는 최종 EPUB ZIP 아카이브를 만들기 전에
 * 다음과 같은 임시 EPUB 파일 구조를 구성하는 공간입니다.</p>
 *
 * <pre>
 * {@code
 * working/
 * ├─ mimetype
 * ├─ META-INF/
 * │  └─ container.xml
 * └─ OEBPS/
 *    ├─ content.opf
 *    ├─ Text/
 *    ├─ Styles/
 *    ├─ Images/
 *    ├─ Fonts/
 *    ├─ Audio/
 *    ├─ Video/
 *    ├─ MediaOverlays/
 *    └─ Misc/
 * }
 * </pre>
 *
 * <p>구현체는 다음 작업을 담당합니다.</p>
 *
 * <ul>
 *     <li>작업 디렉터리 생성</li>
 *     <li>기존 작업 디렉터리 정리</li>
 *     <li>META-INF 및 콘텐츠 디렉터리 생성</li>
 *     <li>리소스별 기본 디렉터리 생성</li>
 *     <li>작업 디렉터리 경로 안전성 검사</li>
 *     <li>EPUB 생성 완료 후 임시 파일 정리</li>
 * </ul>
 */
public interface EpubWorkspaceManager {

    /**
     * EPUB 생성 작업 디렉터리를 준비합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     * @param options 생성 옵션
     * @return 준비된 작업 디렉터리 정보
     * @throws EpubGenerationException 작업 디렉터리 준비 실패 시
     */
    EpubWorkspace prepare(
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * EPUB 작업 디렉터리를 정리합니다.
     *
     * <p>생성 옵션의
     * {@link EpubGenerationOptions#isDeleteWorkingDirectoryAfterGeneration()}
     * 값에 따라 전체 디렉터리를 삭제하거나 유지할 수 있습니다.</p>
     *
     * @param workspace 작업 디렉터리 정보
     * @param options 생성 옵션
     * @throws EpubGenerationException 정리 실패 시
     */
    void cleanup(
            EpubWorkspace workspace,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * 작업 디렉터리 준비 입력값을 검증합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     * @param options 생성 옵션
     * @throws EpubGenerationException 입력값이 잘못된 경우
     */
    default void validate(
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (pathConfiguration == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB path configuration must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .build();
        }

        if (options == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation options must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .build();
        }

        Path workingDirectory =
                pathConfiguration.getWorkingDirectory()
                        .toAbsolutePath()
                        .normalize();

        Path outputFile =
                pathConfiguration.getOutputFile()
                        .toAbsolutePath()
                        .normalize();

        if (workingDirectory.equals(outputFile)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB working directory and output file "
                            + "must not be identical."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(workingDirectory)
                    .build();
        }

        /*
         * 출력 EPUB 파일이 작업 디렉터리 내부에 존재하면
         * clean 작업 중 함께 삭제될 수 있으므로 허용하지 않습니다.
         */
        if (outputFile.startsWith(workingDirectory)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB output file must not be located inside "
                            + "the working directory: "
                            + outputFile
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(outputFile)
                    .detail(
                            "workingDirectory",
                            workingDirectory.toString()
                    )
                    .build();
        }

        /*
         * 루트 디렉터리와 같이 위험한 위치를 작업 디렉터리로
         * 사용하는 것을 차단합니다.
         */
        if (workingDirectory.getParent() == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "Filesystem root must not be used as an "
                            + "EPUB working directory: "
                            + workingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(workingDirectory)
                    .build();
        }
    }

    /**
     * 작업 디렉터리가 EPUB 생성에 필요한 기본 구조를 가지고 있는지
     * 검증합니다.
     *
     * @param workspace 작업 디렉터리
     * @throws EpubGenerationException 구조가 잘못된 경우
     */
    default void validatePreparedWorkspace(
            EpubWorkspace workspace
    ) throws EpubGenerationException {

        if (workspace == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB workspace must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .build();
        }

        requireDirectory(
                workspace.getWorkingDirectory(),
                "working directory"
        );

        requireDirectory(
                workspace.getMetaInfDirectory(),
                "META-INF directory"
        );

        requireDirectory(
                workspace.getContentRootDirectory(),
                "content root directory"
        );
    }

    /**
     * 디렉터리 존재 여부를 검사합니다.
     */
    private static void requireDirectory(
            Path directory,
            String description
    ) throws EpubGenerationException {

        if (directory == null
                || !Files.exists(directory)
                || !Files.isDirectory(directory)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "EPUB " + description + " is not available: "
                            + directory
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(directory)
                    .build();
        }
    }

    /**
     * 구현체 이름을 반환합니다.
     *
     * @return 구현체 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * EPUB 작업 디렉터리의 실제 경로 정보를 표현합니다.
     *
     * <p>생성 준비 과정에서 계산된 경로를 한 번에 전달하기 위한
     * 불변 값 객체입니다.</p>
     */
    final class EpubWorkspace {

        private final Path workingDirectory;

        private final Path mimetypeFile;

        private final Path metaInfDirectory;

        private final Path containerFile;

        private final Path contentRootDirectory;

        private final Path packageDocumentFile;

        private final Path textDirectory;

        private final Path styleDirectory;

        private final Path imageDirectory;

        private final Path fontDirectory;

        private final Path audioDirectory;

        private final Path videoDirectory;

        private final Path mediaOverlayDirectory;

        private final Path miscDirectory;

        private final Path navigationDocumentFile;

        private final Path ncxFile;

        private EpubWorkspace(
                EpubPathConfiguration configuration
        ) {
            Objects.requireNonNull(
                    configuration,
                    "EPUB path configuration must not be null."
            );

            this.workingDirectory =
                    normalize(configuration.getWorkingDirectory());

            this.mimetypeFile =
                    normalize(configuration.getMimetypePath());

            this.metaInfDirectory =
                    normalize(configuration.getMetaInfPath());

            this.containerFile =
                    normalize(configuration.getContainerPath());

            this.contentRootDirectory =
                    normalize(configuration.getContentRootPath());

            this.packageDocumentFile =
                    normalize(configuration.getPackageDocumentPath());

            this.textDirectory =
                    normalize(configuration.getTextPath());

            this.styleDirectory =
                    normalize(configuration.getStylePath());

            this.imageDirectory =
                    normalize(configuration.getImagePath());

            this.fontDirectory =
                    normalize(configuration.getFontPath());

            this.audioDirectory =
                    normalize(configuration.getAudioPath());

            this.videoDirectory =
                    normalize(configuration.getVideoPath());

            this.mediaOverlayDirectory =
                    normalize(configuration.getMediaOverlayPath());

            this.miscDirectory =
                    normalize(configuration.getMiscPath());

            this.navigationDocumentFile =
                    normalize(configuration.getNavigationPath());

            this.ncxFile =
                    normalize(configuration.getNcxPath());
        }

        /**
         * 경로 설정을 기반으로 workspace 값을 생성합니다.
         *
         * @param configuration EPUB 경로 설정
         * @return workspace
         */
        public static EpubWorkspace from(
                EpubPathConfiguration configuration
        ) {
            return new EpubWorkspace(configuration);
        }

        public Path getWorkingDirectory() {
            return workingDirectory;
        }

        public Path getMimetypeFile() {
            return mimetypeFile;
        }

        public Path getMetaInfDirectory() {
            return metaInfDirectory;
        }

        public Path getContainerFile() {
            return containerFile;
        }

        public Path getContentRootDirectory() {
            return contentRootDirectory;
        }

        public Path getPackageDocumentFile() {
            return packageDocumentFile;
        }

        public Path getTextDirectory() {
            return textDirectory;
        }

        public Path getStyleDirectory() {
            return styleDirectory;
        }

        public Path getImageDirectory() {
            return imageDirectory;
        }

        public Path getFontDirectory() {
            return fontDirectory;
        }

        public Path getAudioDirectory() {
            return audioDirectory;
        }

        public Path getVideoDirectory() {
            return videoDirectory;
        }

        public Path getMediaOverlayDirectory() {
            return mediaOverlayDirectory;
        }

        public Path getMiscDirectory() {
            return miscDirectory;
        }

        public Path getNavigationDocumentFile() {
            return navigationDocumentFile;
        }

        public Path getNcxFile() {
            return ncxFile;
        }

        /**
         * EPUB 생성에 필요한 기본 디렉터리 목록을 반환합니다.
         *
         * @return 기본 디렉터리
         */
        public List<Path> getDirectories() {
            List<Path> result = new ArrayList<>();

            result.add(workingDirectory);
            result.add(metaInfDirectory);
            result.add(contentRootDirectory);
            result.add(textDirectory);
            result.add(styleDirectory);
            result.add(imageDirectory);
            result.add(fontDirectory);
            result.add(audioDirectory);
            result.add(videoDirectory);
            result.add(mediaOverlayDirectory);
            result.add(miscDirectory);

            return List.copyOf(result);
        }

        /**
         * 작업 디렉터리 하위의 필수 파일 경로를 반환합니다.
         *
         * <p>파일이 실제 생성되었다는 의미는 아닙니다.</p>
         *
         * @return EPUB 기본 파일 경로
         */
        public List<Path> getCoreFiles() {
            return List.of(
                    mimetypeFile,
                    containerFile,
                    packageDocumentFile
            );
        }

        /**
         * 지정한 경로가 작업 디렉터리 내부인지 확인합니다.
         *
         * @param path 검사할 경로
         * @return 내부이면 {@code true}
         */
        public boolean contains(Path path) {
            if (path == null) {
                return false;
            }

            Path normalized = normalize(path);

            return normalized.startsWith(workingDirectory);
        }

        /**
         * 작업 디렉터리를 기준으로 상대 경로를 반환합니다.
         *
         * @param path 작업 디렉터리 하위 경로
         * @return 상대 경로
         */
        public Path relativize(Path path) {
            if (!contains(path)) {
                throw new IllegalArgumentException(
                        "Path is outside the EPUB workspace: "
                                + path
                );
            }

            return workingDirectory.relativize(
                    normalize(path)
            );
        }

        private static Path normalize(Path value) {
            if (value == null) {
                return null;
            }

            return value.toAbsolutePath().normalize();
        }

        @Override
        public String toString() {
            return "EpubWorkspace{"
                    + "workingDirectory="
                    + workingDirectory
                    + ", contentRootDirectory="
                    + contentRootDirectory
                    + ", packageDocumentFile="
                    + packageDocumentFile
                    + '}';
        }
    }

    /**
     * 디렉터리 트리를 재귀적으로 삭제하는 공통 유틸리티입니다.
     *
     * <p>Default 구현체에서 사용할 수 있도록 인터페이스의 정적
     * 메서드로 제공합니다.</p>
     *
     * @param root 삭제할 루트 경로
     * @throws IOException 삭제 실패 시
     */
    static void deleteRecursively(Path root)
            throws IOException {

        if (root == null || !Files.exists(root)) {
            return;
        }

        /*
         * Files.walk 결과를 역순으로 정렬하여
         * 파일 -> 하위 디렉터리 -> 상위 디렉터리 순으로 삭제합니다.
         */
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 디렉터리 내부의 모든 항목만 제거하고 디렉터리 자체는 유지합니다.
     *
     * @param directory 대상 디렉터리
     * @throws IOException 정리 실패 시
     */
    static void clearDirectory(Path directory)
            throws IOException {

        if (directory == null || !Files.exists(directory)) {
            return;
        }

        if (!Files.isDirectory(directory)) {
            throw new IOException(
                    "Path is not a directory: " + directory
            );
        }

        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(directory)) {

            for (Path child : stream) {
                deleteRecursively(child);
            }
        }
    }
}