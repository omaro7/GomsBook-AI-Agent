/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
/**
 * EPUB 작업 디렉터리를 실제로 생성, 초기화 및 정리하는
 * 기본 구현체입니다.
 *
 * <p>생성 전에는 다음 기본 구조를 준비합니다.</p>
 *
 * <pre>
 * working/
 * ├─ META-INF/
 * └─ OEBPS/
 *    ├─ Text/
 *    ├─ Styles/
 *    ├─ Images/
 *    ├─ Fonts/
 *    ├─ Audio/
 *    ├─ Video/
 *    ├─ MediaOverlays/
 *    └─ Misc/
 * </pre>
 *
 * <p>{@code mimetype}, {@code container.xml}, {@code content.opf} 등의
 * 실제 파일 생성은 각각의 Writer 구현체가 담당합니다.</p>
 *
 * <p>이 구현체는 상태를 가지지 않으므로 여러 EPUB 생성 요청에서
 * 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubWorkspaceManager
        implements EpubWorkspaceManager {

    /**
     * EPUB 생성 작업 디렉터리를 준비합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     * @param options           EPUB 생성 옵션
     * @return 준비된 작업 공간
     * @throws EpubGenerationException 작업 공간 준비 실패 시
     */
    @Override
    public EpubWorkspace prepare(
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(pathConfiguration, options);

        Path workingDirectory =
                pathConfiguration.getWorkingDirectory()
                        .toAbsolutePath()
                        .normalize();

        try {
            prepareWorkingDirectory(
                    workingDirectory,
                    pathConfiguration,
                    options
            );

            EpubWorkspace workspace =
                    EpubWorkspace.from(pathConfiguration);

            createRequiredDirectories(
                    workspace,
                    pathConfiguration,
                    options
            );

            validatePreparedWorkspace(workspace);

            return workspace;

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "Failed to prepare EPUB working directory: "
                            + workingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(workingDirectory)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * EPUB 생성 작업 완료 후 작업 공간을 정리합니다.
     *
     * <p>{@code deleteWorkingDirectoryAfterGeneration=true}인 경우
     * 작업 디렉터리 전체를 삭제합니다.</p>
     *
     * <p>해당 옵션이 false이면 작업 디렉터리를 그대로 유지하여
     * 생성된 OPF, XHTML, 이미지 등의 중간 결과를 확인할 수 있도록
     * 합니다.</p>
     *
     * @param workspace 작업 공간
     * @param options   EPUB 생성 옵션
     * @throws EpubGenerationException 정리 실패 시
     */
    @Override
    public void cleanup(
            EpubWorkspace workspace,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (workspace == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB workspace must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.CLEANUP
                    )
                    .build();
        }

        if (options == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation options must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.CLEANUP
                    )
                    .build();
        }

        if (!options
                .isDeleteWorkingDirectoryAfterGeneration()) {

            return;
        }

        Path workingDirectory =
                workspace.getWorkingDirectory();

        validateSafeCleanupPath(workingDirectory);

        try {
            EpubWorkspaceManager.deleteRecursively(
                    workingDirectory
            );

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.CLEANUP_FAILED,
                    "Failed to clean up EPUB working directory: "
                            + workingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage.CLEANUP
                    )
                    .filePath(workingDirectory)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기존 작업 디렉터리를 생성 옵션에 맞게 준비합니다.
     */
    private void prepareWorkingDirectory(
            Path workingDirectory,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws IOException, EpubGenerationException {

        if (Files.exists(workingDirectory)
                && !Files.isDirectory(workingDirectory)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "EPUB working path exists but is not a directory: "
                            + workingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(workingDirectory)
                    .build();
        }

        if (Files.exists(workingDirectory)
                && options.isCleanWorkingDirectory()) {

            validateSafeCleanupPath(workingDirectory);

            EpubWorkspaceManager.clearDirectory(
                    workingDirectory
            );
        }

        /*
         * EpubPathConfiguration의 createDirectories 설정도 존중합니다.
         *
         * 이미 작업 디렉터리가 존재하면 false여도 사용할 수 있지만,
         * 존재하지 않는 경우 자동 생성이 금지되어 있으면 오류입니다.
         */
        if (!Files.exists(workingDirectory)) {
            if (!pathConfiguration.isCreateDirectories()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .WORKING_DIRECTORY_PREPARATION_FAILED,
                        "EPUB working directory does not exist and "
                                + "automatic directory creation "
                                + "is disabled: "
                                + workingDirectory
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .WORKING_DIRECTORY_PREPARATION
                        )
                        .filePath(workingDirectory)
                        .build();
            }

            Files.createDirectories(workingDirectory);
        }

        if (!Files.isWritable(workingDirectory)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "EPUB working directory is not writable: "
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
     * EPUB 기본 디렉터리 구조를 생성합니다.
     */
    private void createRequiredDirectories(
            EpubWorkspace workspace,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws IOException, EpubGenerationException {

        List<Path> directories =
                workspace.getDirectories();

        for (Path directory : directories) {
            if (directory == null) {
                continue;
            }

            validateDirectoryInsideWorkspace(
                    workspace,
                    directory
            );

            if (Files.exists(directory)) {
                if (!Files.isDirectory(directory)) {
                    throw EpubGenerationException.builder(
                            EpubGenerationException.ErrorCode
                                    .WORKING_DIRECTORY_PREPARATION_FAILED,
                            "EPUB workspace path exists but "
                                    + "is not a directory: "
                                    + directory
                    )
                            .stage(
                                    EpubGenerationException.Stage
                                            .WORKING_DIRECTORY_PREPARATION
                            )
                            .filePath(directory)
                            .build();
                }

                continue;
            }

            if (!pathConfiguration.isCreateDirectories()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .WORKING_DIRECTORY_PREPARATION_FAILED,
                        "Required EPUB directory does not exist and "
                                + "directory creation is disabled: "
                                + directory
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .WORKING_DIRECTORY_PREPARATION
                        )
                        .filePath(directory)
                        .build();
            }

            Files.createDirectories(directory);
        }

        /*
         * 최종적으로 생성된 각 디렉터리의 쓰기 권한을 확인합니다.
         */
        for (Path directory : directories) {
            if (directory == null) {
                continue;
            }

            if (!Files.isWritable(directory)) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .WORKING_DIRECTORY_PREPARATION_FAILED,
                        "EPUB workspace directory is not writable: "
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
    }

    /**
     * 생성 대상 디렉터리가 작업 루트 내부인지 확인합니다.
     */
    private void validateDirectoryInsideWorkspace(
            EpubWorkspace workspace,
            Path directory
    ) throws EpubGenerationException {

        Path workingDirectory =
                workspace.getWorkingDirectory()
                        .toAbsolutePath()
                        .normalize();

        Path normalizedDirectory =
                directory.toAbsolutePath()
                        .normalize();

        if (!normalizedDirectory.startsWith(
                workingDirectory
        )) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "EPUB directory is outside the working directory: "
                            + normalizedDirectory
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .WORKING_DIRECTORY_PREPARATION
                    )
                    .filePath(normalizedDirectory)
                    .detail(
                            "workingDirectory",
                            workingDirectory.toString()
                    )
                    .build();
        }
    }

    /**
     * 삭제 가능한 안전한 작업 경로인지 검증합니다.
     *
     * <p>작업 공간 정리 과정에서 잘못된 경로 설정으로 사용자 파일을
     * 삭제하는 사고를 방지하기 위한 방어 코드입니다.</p>
     */
    private void validateSafeCleanupPath(
            Path directory
    ) throws EpubGenerationException {

        if (directory == null) {
            throw cleanupSafetyException(
                    null,
                    "Cleanup directory must not be null."
            );
        }

        Path normalized =
                directory.toAbsolutePath()
                        .normalize();

        /*
         * Windows의 C:\, Unix의 / 등 파일 시스템 루트 삭제 금지.
         */
        if (normalized.getParent() == null) {
            throw cleanupSafetyException(
                    normalized,
                    "Filesystem root must never be used "
                            + "as an EPUB cleanup directory."
            );
        }

        /*
         * 홈 디렉터리 자체 삭제 방지.
         */
        Path userHome = resolveUserHome();

        if (userHome != null
                && normalized.equals(userHome)) {

            throw cleanupSafetyException(
                    normalized,
                    "User home directory must never be used "
                            + "as an EPUB cleanup directory."
            );
        }

        /*
         * 현재 작업 디렉터리 자체를 잘못 지정한 경우도 방어합니다.
         */
        Path currentDirectory =
                Path.of("")
                        .toAbsolutePath()
                        .normalize();

        if (normalized.equals(currentDirectory)) {
            throw cleanupSafetyException(
                    normalized,
                    "Current application directory must not be "
                            + "used as an EPUB cleanup directory."
            );
        }
    }

    /**
     * 사용자 홈 디렉터리를 안전하게 조회합니다.
     */
    private Path resolveUserHome() {
        String userHome =
                System.getProperty("user.home");

        if (userHome == null
                || userHome.isBlank()) {

            return null;
        }

        try {
            return Path.of(userHome)
                    .toAbsolutePath()
                    .normalize();

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private EpubGenerationException cleanupSafetyException(
            Path path,
            String message
    ) {
        EpubGenerationException.Builder builder =
                EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .CLEANUP_FAILED,
                        message
                )
                        .stage(
                                EpubGenerationException.Stage.CLEANUP
                        );

        if (path != null) {
            builder.filePath(path);
        }

        return builder.build();
    }
}