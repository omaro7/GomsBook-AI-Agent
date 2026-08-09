/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;

/**
 * EPUB NCX 문서인 {@code toc.ncx}를 생성하는 계약입니다.
 *
 * <p>EPUB 2에서는 NCX가 기본 탐색 문서이며,
 * EPUB 3에서는 하위 호환 목적으로 선택적으로 포함할 수 있습니다.</p>
 *
 * <p>일반적인 NCX 구조는 다음과 같습니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"
 *      version="2005-1"
 *      xml:lang="ko">
 *
 *     <head>
 *         <meta name="dtb:uid"
 *               content="urn:isbn:9780000000000"/>
 *         <meta name="dtb:depth"
 *               content="2"/>
 *         <meta name="dtb:totalPageCount"
 *               content="0"/>
 *         <meta name="dtb:maxPageNumber"
 *               content="0"/>
 *     </head>
 *
 *     <docTitle>
 *         <text>도서 제목</text>
 *     </docTitle>
 *
 *     <docAuthor>
 *         <text>저자</text>
 *     </docAuthor>
 *
 *     <navMap>
 *         ...
 *     </navMap>
 * </ncx>
 * }
 * </pre>
 */
public interface EpubNcxDocumentWriter {

    /**
     * NCX 모델을 {@code toc.ncx} XML 문자열로 직렬화합니다.
     *
     * @param ncx     NCX 모델
     * @param options EPUB 생성 옵션
     * @return NCX XML 문자열
     * @throws EpubGenerationException 직렬화 실패 시
     */
    String serialize(
            EpubNcx ncx,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * 기본 생성 옵션을 사용하여 NCX를 직렬화합니다.
     *
     * <p>주의: 기본 생성 옵션이 EPUB 3인 경우에도
     * {@code includeLegacyNcxInEpub3} 또는 {@code generateNcx} 설정이
     * 필요할 수 있으므로 실제 생성 흐름에서는 명시적인 options 사용을
     * 권장합니다.</p>
     *
     * @param ncx NCX 모델
     * @return NCX XML 문자열
     * @throws EpubGenerationException 직렬화 실패 시
     */
    default String serialize(
            EpubNcx ncx
    ) throws EpubGenerationException {

        return serialize(
                ncx,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * NCX를 지정한 Writer에 기록합니다.
     *
     * <p>전달받은 Writer는 닫지 않습니다.</p>
     *
     * @param ncx     NCX 모델
     * @param writer  출력 Writer
     * @param options EPUB 생성 옵션
     * @throws EpubGenerationException 기록 실패 시
     */
    default void write(
            EpubNcx ncx,
            Writer writer,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                writer,
                "EPUB NCX Writer must not be null."
        );

        String xml = serialize(
                ncx,
                options
        );

        try {
            writer.write(xml);
            writer.flush();

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "Failed to write EPUB NCX document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * NCX를 출력 스트림에 기록합니다.
     *
     * <p>문자 인코딩은
     * {@link EpubGenerationOptions#getCharset()}을 사용합니다.</p>
     *
     * <p>전달받은 OutputStream은 닫지 않습니다.</p>
     *
     * @param ncx          NCX 모델
     * @param outputStream 출력 스트림
     * @param options      EPUB 생성 옵션
     * @throws EpubGenerationException 기록 실패 시
     */
    default void write(
            EpubNcx ncx,
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB NCX output stream must not be null."
        );

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        String xml = serialize(
                ncx,
                resolvedOptions
        );

        try {
            outputStream.write(
                    xml.getBytes(
                            resolvedOptions.getCharset()
                    )
            );

            outputStream.flush();

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "Failed to write EPUB NCX document "
                            + "to the output stream."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * NCX를 지정한 파일에 기록합니다.
     *
     * <p>상위 디렉터리가 존재하지 않으면 자동으로 생성합니다.</p>
     *
     * @param ncx        NCX 모델
     * @param outputPath 출력 toc.ncx 경로
     * @param options    EPUB 생성 옵션
     * @return 생성된 NCX 파일 경로
     * @throws EpubGenerationException 파일 생성 실패 시
     */
    default Path write(
            EpubNcx ncx,
            Path outputPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Path normalizedOutputPath =
                normalizeOutputPath(outputPath);

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        validate(
                ncx,
                resolvedOptions
        );

        validateOutputPath(
                normalizedOutputPath
        );

        try {
            Path parent =
                    normalizedOutputPath.getParent();

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
                        ncx,
                        outputStream,
                        resolvedOptions
                );
            }

            return normalizedOutputPath;

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "Failed to create EPUB NCX document: "
                            + normalizedOutputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .filePath(normalizedOutputPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기본 생성 옵션으로 NCX 파일을 생성합니다.
     *
     * @param ncx        NCX 모델
     * @param outputPath 출력 경로
     * @return 생성된 NCX 파일
     * @throws EpubGenerationException 생성 실패 시
     */
    default Path write(
            EpubNcx ncx,
            Path outputPath
    ) throws EpubGenerationException {

        return write(
                ncx,
                outputPath,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * NCX 모델 및 생성 옵션을 검증합니다.
     *
     * @param ncx     NCX 모델
     * @param options 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 경우
     */
    default void validate(
            EpubNcx ncx,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (ncx == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB NCX model must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        /*
         * EPUB 2에서는 NCX가 필수입니다.
         *
         * EPUB 3에서는 generateNcx 또는
         * includeLegacyNcxInEpub3가 활성화된 경우만 생성합니다.
         */
        if (resolvedOptions.getVersion().isEpub3()
                && !resolvedOptions.isGenerateNcx()
                && !resolvedOptions
                        .isIncludeLegacyNcxInEpub3()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB 3 NCX generation is disabled "
                            + "by generation options."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .detail(
                            "epubVersion",
                            resolvedOptions
                                    .getVersion()
                                    .toString()
                    )
                    .build();
        }

        if (resolvedOptions.getVersion().isEpub2()
                && !resolvedOptions.isGenerateNcx()) {

            /*
             * EpubGenerationOptions 구현에서 EPUB 2일 경우
             * generateNcx가 자동 true로 해결되어야 하지만,
             * 방어적으로 다시 검사합니다.
             */
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB 2 requires NCX generation."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }

        try {
            ncx.validate(
                    resolvedOptions.getVersion()
            );

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "EPUB NCX model validation failed."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 현재 Writer가 지정한 NCX 생성 요청을 처리할 수 있는지
     * 확인합니다.
     *
     * @param ncx     NCX 모델
     * @param options EPUB 생성 옵션
     * @return 지원하면 {@code true}
     */
    default boolean supports(
            EpubNcx ncx,
            EpubGenerationOptions options
    ) {
        if (ncx == null || options == null) {
            return false;
        }

        if (options.getVersion().isEpub2()) {
            return true;
        }

        return options.getVersion().isEpub3()
                && (
                        options.isGenerateNcx()
                        || options
                                .isIncludeLegacyNcxInEpub3()
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

    private static EpubGenerationOptions requireOptions(
            EpubGenerationOptions options
    ) {
        return Objects.requireNonNull(
                options,
                "EPUB generation options must not be null."
        );
    }

    private static Path normalizeOutputPath(
            Path outputPath
    ) {
        if (outputPath == null) {
            throw new IllegalArgumentException(
                    "EPUB NCX output path must not be null."
            );
        }

        return outputPath
                .toAbsolutePath()
                .normalize();
    }

    private static void validateOutputPath(
            Path outputPath
    ) throws EpubGenerationException {

        if (outputPath.getFileName() == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB NCX output path must reference a file."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        String fileName =
                outputPath.getFileName().toString();

        if (!fileName
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(".ncx")) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB NCX document must use the .ncx extension: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB NCX output path points to a directory: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }
    }
}