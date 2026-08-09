/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
/**
 * EPUB 루트의 {@code mimetype} 파일을 생성하는 기본 구현체입니다.
 *
 * <p>EPUB mimetype 파일은 다음 규칙을 만족해야 합니다.</p>
 *
 * <ul>
 *     <li>파일명은 정확히 {@code mimetype}이어야 합니다.</li>
 *     <li>내용은 정확히 {@code application/epub+zip}이어야 합니다.</li>
 *     <li>UTF-8 BOM 또는 기타 BOM을 포함하지 않습니다.</li>
 *     <li>파일 끝에 줄바꿈을 추가하지 않습니다.</li>
 *     <li>ZIP 패키징 시 첫 번째 엔트리로 기록해야 합니다.</li>
 *     <li>ZIP 패키징 시 STORED 방식으로 압축하지 않아야 합니다.</li>
 * </ul>
 *
 * <p>이 클래스는 mimetype 데이터 생성과 파일 기록까지만 담당합니다.
 * ZIP 엔트리 순서 및 압축 방식은 별도의 EPUB 패키저가 담당합니다.</p>
 *
 * <p>인스턴스 상태를 가지지 않으므로 여러 EPUB 생성 요청에서
 * 안전하게 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubMimetypeWriter
        implements EpubMimetypeWriter {

    /**
     * EPUB 표준 mimetype 바이트입니다.
     *
     * <p>ASCII 문자열이므로 UTF-8과 동일한 바이트 표현을 갖습니다.</p>
     */
    private static final byte[] MIMETYPE_BYTES = {
            'a', 'p', 'p', 'l', 'i', 'c', 'a', 't', 'i', 'o', 'n',
            '/', 'e', 'p', 'u', 'b', '+', 'z', 'i', 'p'
    };

    /**
     * EPUB mimetype 내용을 문자열로 반환합니다.
     *
     * @return {@code application/epub+zip}
     */
    @Override
    public String serialize() {
        return EPUB_MIMETYPE;
    }

    /**
     * EPUB 생성 옵션을 검증한 뒤 mimetype 문자열을 반환합니다.
     *
     * @param options EPUB 생성 옵션
     * @return EPUB mimetype 문자열
     * @throws EpubGenerationException 옵션이 유효하지 않은 경우
     */
    @Override
    public String serialize(
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(options);

        return EPUB_MIMETYPE;
    }

    /**
     * EPUB mimetype 바이트를 반환합니다.
     *
     * <p>내부 배열 보호를 위해 복사본을 반환합니다.</p>
     *
     * @return mimetype 바이트
     */
    @Override
    public byte[] getBytes() {
        return MIMETYPE_BYTES.clone();
    }

    /**
     * 생성 옵션 검증 후 mimetype 바이트를 반환합니다.
     *
     * @param options EPUB 생성 옵션
     * @return mimetype 바이트
     * @throws EpubGenerationException 옵션이 유효하지 않은 경우
     */
    @Override
    public byte[] getBytes(
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(options);

        return getBytes();
    }

    /**
     * mimetype 내용을 출력 스트림에 기록합니다.
     *
     * <p>전달받은 OutputStream은 닫지 않습니다.</p>
     *
     * @param outputStream 출력 스트림
     * @param options EPUB 생성 옵션
     * @throws EpubGenerationException 기록에 실패한 경우
     */
    @Override
    public void write(
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB mimetype output stream must not be null."
        );

        validate(options);

        try {
            /*
             * Writer 또는 Charset 변환을 사용하지 않습니다.
             *
             * mimetype은 정확한 20바이트를 기록해야 하기 때문에
             * 미리 정의된 ASCII 바이트를 그대로 기록합니다.
             */
            outputStream.write(MIMETYPE_BYTES);
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
                    .detail(
                            "expectedContent",
                            EPUB_MIMETYPE
                    )
                    .detail(
                            "expectedSize",
                            String.valueOf(MIMETYPE_BYTES.length)
                    )
                    .build();
        }
    }

    /**
     * mimetype 파일을 지정한 경로에 생성합니다.
     *
     * @param outputPath mimetype 출력 경로
     * @param options EPUB 생성 옵션
     * @return 생성된 파일 경로
     * @throws EpubGenerationException 파일 생성에 실패한 경우
     */
    @Override
    public Path write(
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

                write(
                        outputStream,
                        options
                );
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
     * @return 생성된 파일
     * @throws EpubGenerationException 생성에 실패한 경우
     */
    @Override
    public Path write(
            Path outputPath
    ) throws EpubGenerationException {

        return write(
                outputPath,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * mimetype 생성 옵션을 검증합니다.
     *
     * @param options EPUB 생성 옵션
     * @throws EpubGenerationException 잘못된 옵션인 경우
     */
    @Override
    public void validate(
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
     * 생성된 mimetype 파일이 EPUB 규격에 맞는지 검증합니다.
     *
     * <p>단순 문자열 비교가 아니라 파일 전체 바이트를 비교하여
     * BOM, 줄바꿈, 공백 등 불필요한 데이터가 포함되었는지도
     * 검출합니다.</p>
     *
     * @param mimetypeFile 생성된 mimetype 파일
     * @throws EpubGenerationException 잘못된 mimetype 파일인 경우
     */
    @Override
    public void validateWrittenFile(
            Path mimetypeFile
    ) throws EpubGenerationException {

        Path normalizedPath =
                normalizeOutputPath(mimetypeFile);

        validateOutputPath(normalizedPath);

        if (!Files.exists(normalizedPath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "EPUB mimetype file does not exist: "
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
            long actualSize = Files.size(normalizedPath);

            if (actualSize != MIMETYPE_BYTES.length) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .MIMETYPE_GENERATION_FAILED,
                        "Invalid EPUB mimetype file size: "
                                + actualSize
                                + ", expected: "
                                + MIMETYPE_BYTES.length
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .MIMETYPE_GENERATION
                        )
                        .filePath(normalizedPath)
                        .detail(
                                "expectedSize",
                                String.valueOf(
                                        MIMETYPE_BYTES.length
                                )
                        )
                        .detail(
                                "actualSize",
                                String.valueOf(actualSize)
                        )
                        .build();
            }

            byte[] actualBytes =
                    Files.readAllBytes(normalizedPath);

            if (!Arrays.equals(
                    MIMETYPE_BYTES,
                    actualBytes
            )) {
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
                                "expectedContent",
                                EPUB_MIMETYPE
                        )
                        .detail(
                                "expectedSize",
                                String.valueOf(
                                        MIMETYPE_BYTES.length
                                )
                        )
                        .detail(
                                "actualSize",
                                String.valueOf(
                                        actualBytes.length
                                )
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
     * EPUB mimetype 데이터가 정확한 20바이트인지 확인합니다.
     *
     * @return 항상 정상 구현이면 {@code true}
     */
    public boolean isStandardMimetype() {
        return MIMETYPE_BYTES.length == 20
                && EPUB_MIMETYPE.equals(
                        new String(
                                MIMETYPE_BYTES,
                                java.nio.charset.StandardCharsets.US_ASCII
                        )
                );
    }

    /**
     * 출력 경로를 정규화합니다.
     */
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

    /**
     * mimetype 출력 경로를 검증합니다.
     */
    private static void validateOutputPath(
            Path outputPath
    ) throws EpubGenerationException {

        if (outputPath.getFileName() == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB mimetype output path must reference a file."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .MIMETYPE_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        String fileName =
                outputPath.getFileName().toString();

        /*
         * EPUB mimetype 파일명은 대소문자까지 정확히
         * "mimetype"이어야 합니다.
         */
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
                    .detail(
                            "expectedFileName",
                            MIMETYPE_FILE_NAME
                    )
                    .detail(
                            "actualFileName",
                            fileName
                    )
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB mimetype output path points "
                            + "to a directory: "
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