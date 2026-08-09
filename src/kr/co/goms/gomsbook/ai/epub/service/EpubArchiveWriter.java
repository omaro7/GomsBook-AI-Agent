/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;


import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;

/**
 * EPUB 작업 디렉터리의 파일들을 최종 {@code .epub} ZIP 아카이브로
 * 패키징하는 계약입니다.
 *
 * <p>EPUB 아카이브 생성 시 다음 규칙을 반드시 지켜야 합니다.</p>
 *
 * <ul>
 *     <li>{@code mimetype} 파일은 ZIP의 첫 번째 엔트리여야 합니다.</li>
 *     <li>{@code mimetype} 엔트리는 STORED 방식으로 압축하지 않습니다.</li>
 *     <li>{@code mimetype} 엔트리에 extra field를 추가하지 않는 것이
 *     권장됩니다.</li>
 *     <li>나머지 파일은 일반적인 DEFLATED 방식으로 압축할 수 있습니다.</li>
 *     <li>EPUB 내부 경로 구분자는 항상 {@code /}를 사용합니다.</li>
 *     <li>작업 디렉터리 자체가 아니라 그 내부 파일을 아카이브 루트에
 *     배치합니다.</li>
 * </ul>
 *
 * <p>예를 들어 다음 작업 디렉터리는:</p>
 *
 * <pre>
 * {@code
 * working/
 * ├─ mimetype
 * ├─ META-INF/
 * │  └─ container.xml
 * └─ OEBPS/
 *    ├─ content.opf
 *    └─ Text/
 *       └─ chapter01.xhtml
 * }
 * </pre>
 *
 * <p>다음 EPUB 구조로 패키징되어야 합니다.</p>
 *
 * <pre>
 * {@code
 * book.epub
 * ├─ mimetype
 * ├─ META-INF/container.xml
 * ├─ OEBPS/content.opf
 * └─ OEBPS/Text/chapter01.xhtml
 * }
 * </pre>
 */
public interface EpubArchiveWriter {

    /**
     * EPUB 작업 디렉터리를 최종 EPUB 파일로 패키징합니다.
     *
     * @param workingDirectory EPUB 작업 디렉터리
     * @param outputFile       최종 EPUB 출력 파일
     * @param options          EPUB 생성 옵션
     * @return 생성된 EPUB 파일 경로
     * @throws EpubGenerationException 패키징에 실패한 경우
     */
    Path write(
            Path workingDirectory,
            Path outputFile,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * {@link EpubPathConfiguration}을 사용하여 EPUB 파일을
     * 패키징합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     * @param options           EPUB 생성 옵션
     * @return 생성된 EPUB 파일
     * @throws EpubGenerationException 패키징에 실패한 경우
     */
    default Path write(
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (pathConfiguration == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB path configuration must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        return write(
                pathConfiguration.getWorkingDirectory(),
                pathConfiguration.getOutputFile(),
                options
        );
    }

    /**
     * 기본 생성 옵션으로 EPUB 파일을 패키징합니다.
     *
     * @param pathConfiguration EPUB 경로 설정
     * @return 생성된 EPUB 파일
     * @throws EpubGenerationException 패키징에 실패한 경우
     */
    default Path write(
            EpubPathConfiguration pathConfiguration
    ) throws EpubGenerationException {

        return write(
                pathConfiguration,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * EPUB 아카이브 생성 입력값을 검증합니다.
     *
     * @param workingDirectory 작업 디렉터리
     * @param outputFile       출력 EPUB 파일
     * @param options          생성 옵션
     * @throws EpubGenerationException 입력값이 유효하지 않은 경우
     */
    default void validate(
            Path workingDirectory,
            Path outputFile,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (workingDirectory == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB working directory must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        if (outputFile == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB output file must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        if (options == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation options must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        Path normalizedWorkingDirectory =
                workingDirectory.toAbsolutePath().normalize();

        Path normalizedOutputFile =
                outputFile.toAbsolutePath().normalize();

        if (!java.nio.file.Files.exists(
                normalizedWorkingDirectory
        )) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .WORKING_DIRECTORY_PREPARATION_FAILED,
                    "EPUB working directory does not exist: "
                            + normalizedWorkingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedWorkingDirectory)
                    .build();
        }

        if (!java.nio.file.Files.isDirectory(
                normalizedWorkingDirectory
        )) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB working directory is not a directory: "
                            + normalizedWorkingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedWorkingDirectory)
                    .build();
        }

        Path mimetypeFile =
                normalizedWorkingDirectory.resolve(
                        EpubMimetypeWriter.MIMETYPE_FILE_NAME
                );

        if (!java.nio.file.Files.isRegularFile(mimetypeFile)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "EPUB working directory does not contain "
                            + "a valid mimetype file."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(mimetypeFile)
                    .build();
        }

        String outputFileName =
                normalizedOutputFile.getFileName() == null
                        ? ""
                        : normalizedOutputFile
                                .getFileName()
                                .toString();

        if (!outputFileName
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(".epub")) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB output file must end with .epub: "
                            + normalizedOutputFile
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedOutputFile)
                    .build();
        }

        if (normalizedOutputFile.startsWith(
                normalizedWorkingDirectory
        )) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB output file must not be located inside "
                            + "the working directory: "
                            + normalizedOutputFile
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedOutputFile)
                    .build();
        }

        if (java.nio.file.Files.exists(normalizedOutputFile)
                && !options.isOverwriteOutput()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .OUTPUT_WRITE_FAILED,
                    "EPUB output file already exists and overwrite "
                            + "is disabled: "
                            + normalizedOutputFile
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedOutputFile)
                    .build();
        }
    }

    /**
     * 생성된 EPUB 아카이브의 기본 구조를 검증합니다.
     *
     * <p>구현체는 필요하면 ZIP 엔트리 순서, 압축 방식, CRC,
     * mimetype 내용 등을 추가로 검증할 수 있습니다.</p>
     *
     * @param epubFile 생성된 EPUB 파일
     * @throws EpubGenerationException 아카이브가 유효하지 않은 경우
     */
    default void validateArchive(
            Path epubFile
    ) throws EpubGenerationException {

        if (epubFile == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB archive file must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        Path normalized =
                epubFile.toAbsolutePath().normalize();

        if (!java.nio.file.Files.exists(normalized)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Generated EPUB archive does not exist: "
                            + normalized
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalized)
                    .build();
        }

        if (!java.nio.file.Files.isRegularFile(normalized)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Generated EPUB archive is not a regular file: "
                            + normalized
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalized)
                    .build();
        }

        try {
            if (java.nio.file.Files.size(normalized) <= 0L) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .PACKAGING_FAILED,
                        "Generated EPUB archive is empty: "
                                + normalized
                )
                        .stage(
                                EpubGenerationException.Stage.PACKAGING
                        )
                        .filePath(normalized)
                        .build();
            }
        } catch (java.io.IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Failed to inspect generated EPUB archive: "
                            + normalized
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalized)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ZIP 엔트리에 사용할 EPUB 내부 경로를 생성합니다.
     *
     * @param workingDirectory EPUB 작업 루트
     * @param file             작업 루트 하위 파일
     * @return ZIP 엔트리 경로
     * @throws EpubGenerationException 파일이 작업 디렉터리 외부인 경우
     */
    default String resolveEntryName(
            Path workingDirectory,
            Path file
    ) throws EpubGenerationException {

        if (workingDirectory == null || file == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB working directory and archive file "
                            + "must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .build();
        }

        Path root =
                workingDirectory.toAbsolutePath().normalize();

        Path target =
                file.toAbsolutePath().normalize();

        if (!target.startsWith(root)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB archive entry file is outside "
                            + "the working directory: "
                            + target
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(target)
                    .build();
        }

        String entryName = root.relativize(target)
                .toString()
                .replace('\\', '/');

        if (entryName.isBlank()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB archive entry name must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(target)
                    .build();
        }

        return entryName;
    }

    /**
     * 해당 ZIP 엔트리가 mimetype인지 확인합니다.
     *
     * @param entryName ZIP 엔트리명
     * @return mimetype이면 {@code true}
     */
    default boolean isMimetypeEntry(String entryName) {
        return EpubMimetypeWriter.MIMETYPE_FILE_NAME.equals(
                entryName
        );
    }

    /**
     * 구현체 이름을 반환합니다.
     *
     * @return 구현체 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}