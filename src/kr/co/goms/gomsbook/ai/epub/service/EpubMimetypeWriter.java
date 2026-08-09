/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;

/**
 * EPUB 루트의 {@code mimetype} 파일을 생성하는 계약입니다.
 *
 * <p>EPUB의 mimetype 파일은 다음 요구사항을 만족해야 합니다.</p>
 *
 * <ul>
 *     <li>EPUB ZIP 아카이브의 루트에 위치해야 합니다.</li>
 *     <li>파일명은 반드시 {@code mimetype}이어야 합니다.</li>
 *     <li>내용은 정확히 {@code application/epub+zip}이어야 합니다.</li>
 *     <li>불필요한 줄바꿈이나 BOM을 포함하면 안 됩니다.</li>
 *     <li>ZIP 패키징 시 첫 번째 항목으로 기록해야 합니다.</li>
 *     <li>ZIP 패키징 시 압축하지 않은 STORED 방식으로 기록해야 합니다.</li>
 * </ul>
 *
 * <p>이 인터페이스는 mimetype 파일의 내용 생성과 파일 기록만 담당합니다.
 * ZIP 내부에서 첫 번째 항목으로 배치하고 압축하지 않는 처리는
 * EPUB 패키징 구현체가 담당해야 합니다.</p>
 */
public interface EpubMimetypeWriter {

    /**
     * EPUB mimetype 파일의 표준 내용을 반환합니다.
     */
    String EPUB_MIMETYPE = "application/epub+zip";

    /**
     * EPUB mimetype 파일의 표준 파일명입니다.
     */
    String MIMETYPE_FILE_NAME = "mimetype";

    /**
     * mimetype 내용을 문자열로 반환합니다.
     *
     * <p>반환 문자열에는 줄바꿈이 포함되지 않습니다.</p>
     *
     * @return {@code application/epub+zip}
     */
    default String serialize() {
        return EPUB_MIMETYPE;
    }

    /**
     * EPUB 생성 옵션을 받아 mimetype 내용을 반환합니다.
     *
     * <p>현재 EPUB 규격상 mimetype 값은 버전과 관계없이 동일하므로
     * 옵션은 유효성 검증 목적으로만 사용합니다.</p>
     *
     * @param options EPUB 생성 옵션
     * @return {@code application/epub+zip}
     * @throws EpubGenerationException 옵션이 유효하지 않은 경우
     */
    default String serialize(
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(options);

        return EPUB_MIMETYPE;
    }

    /**
     * mimetype 파일의 정확한 바이트 데이터를 반환합니다.
     *
     * <p>EPUB mimetype 파일은 ASCII 문자만 사용하므로 UTF-8로
     * 변환해도 동일한 바이트 결과를 생성합니다.</p>
     *
     * @return mimetype 바이트 데이터
     */
    default byte[] getBytes() {
        return EPUB_MIMETYPE.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * EPUB 생성 옵션을 검증한 후 mimetype 바이트를 반환합니다.
     *
     * @param options EPUB 생성 옵션
     * @return mimetype 바이트 데이터
     * @throws EpubGenerationException 옵션이 유효하지 않은 경우
     */
    default byte[] getBytes(
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(options);

        return getBytes();
    }

    /**
     * mimetype 내용을 지정한 출력 스트림에 기록합니다.
     *
     * <p>전달받은 OutputStream은 닫지 않습니다.</p>
     *
     * @param outputStream 출력 스트림
     * @param options      EPUB 생성 옵션
     * @throws EpubGenerationException 기록에 실패한 경우
     */
    default void write(
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB mimetype output stream must not be null."
        );

        validate(options);

        try {
            outputStream.write(getBytes());
            outputStream.flush();

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "Failed to write EPUB mimetype content."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * mimetype 파일을 지정한 경로에 생성합니다.
     *
     * <p>상위 디렉터리가 존재하지 않으면 자동으로 생성합니다.</p>
     *
     * @param outputPath mimetype 파일 출력 경로
     * @param options    EPUB 생성 옵션
     * @return 생성된 mimetype 파일 경로
     * @throws EpubGenerationException 파일 생성에 실패한 경우
     */
    default Path write(
            Path outputPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Path normalizedOutputPath =
                normalizeOutputPath(outputPath);

        validate(options);
        validateOutputPath(normalizedOutputPath);

        try {
            Path parent = normalizedOutputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (OutputStream outputStream =
                    Files.newOutputStream(
                            normalizedOutputPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    )) {

                write(outputStream, options);
            }

            validateWrittenFile(normalizedOutputPath);

            return normalizedOutputPath;

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "Failed to create EPUB mimetype file: "
                            + normalizedOutputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(normalizedOutputPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기본 생성 옵션으로 mimetype 파일을 생성합니다.
     *
     * @param outputPath mimetype 출력 경로
     * @return 생성된 mimetype 파일 경로
     * @throws EpubGenerationException 생성에 실패한 경우
     */
    default Path write(
            Path outputPath
    ) throws EpubGenerationException {

        return write(
                outputPath,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * 생성 옵션이 mimetype 파일 생성에 적합한지 검증합니다.
     *
     * @param options EPUB 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 경우
     */
    default void validate(
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (options == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation options must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .build();
        }

        if (!options.isGenerateMimetypeFile()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB mimetype generation is disabled "
                            + "by generation options."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .build();
        }
    }

    /**
     * 생성된 mimetype 파일의 내용이 정확한지 검증합니다.
     *
     * @param mimetypeFile mimetype 파일
     * @throws EpubGenerationException 파일이 잘못된 경우
     */
    default void validateWrittenFile(
            Path mimetypeFile
    ) throws EpubGenerationException {

        Path normalizedPath =
                normalizeOutputPath(mimetypeFile);

        if (!Files.exists(normalizedPath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "EPUB mimetype file was not created: "
                            + normalizedPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(normalizedPath)
                    .build();
        }

        if (!Files.isRegularFile(normalizedPath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "EPUB mimetype path is not a regular file: "
                            + normalizedPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(normalizedPath)
                    .build();
        }

        try {
            byte[] actual = Files.readAllBytes(normalizedPath);
            byte[] expected = getBytes();

            if (!java.util.Arrays.equals(actual, expected)) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .MIMETYPE_GENERATION_FAILED,
                        "Invalid EPUB mimetype file content."
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .MIMETYPE_GENERATION
                        )
                        .filePath(normalizedPath)
                        .detail(
                                "expected",
                                EPUB_MIMETYPE
                        )
                        .detail(
                                "actualSize",
                                String.valueOf(actual.length)
                        )
                        .build();
            }

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "Failed to validate EPUB mimetype file: "
                            + normalizedPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(normalizedPath)
                    .cause(exception)
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

    private static Path normalizeOutputPath(
            Path outputPath
    ) {
        if (outputPath == null) {
            throw new IllegalArgumentException(
                    "EPUB mimetype output path must not be null."
            );
        }

        return outputPath
                .toAbsolutePath()
                .normalize();
    }

    private static void validateOutputPath(
            Path outputPath
    ) throws EpubGenerationException {

        String fileName = outputPath.getFileName() == null
                ? ""
                : outputPath.getFileName().toString();

        if (!MIMETYPE_FILE_NAME.equals(fileName)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB mimetype file name must be exactly "
                            + "'mimetype': "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB mimetype output path points to a directory: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }
    }
}