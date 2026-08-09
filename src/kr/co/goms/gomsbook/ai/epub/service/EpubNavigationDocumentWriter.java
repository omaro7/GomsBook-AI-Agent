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
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;

/**
 * EPUB 3 Navigation Document인 {@code nav.xhtml}을 생성하는 계약입니다.
 *
 * <p>Navigation Document는 EPUB 3에서 필수이며, 최소한
 * {@code epub:type="toc"} 탐색 영역을 포함해야 합니다.</p>
 *
 * <p>일반적인 구조는 다음과 같습니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE html>
 * <html
 *     xmlns="http://www.w3.org/1999/xhtml"
 *     xmlns:epub="http://www.idpf.org/2007/ops"
 *     lang="ko"
 *     xml:lang="ko">
 *
 * <head>
 *     <meta charset="UTF-8"/>
 *     <title>차례</title>
 * </head>
 *
 * <body>
 *     <nav epub:type="toc" id="toc">
 *         <h1>차례</h1>
 *         <ol>
 *             ...
 *         </ol>
 *     </nav>
 * </body>
 * </html>
 * }
 * </pre>
 *
 * <p>구현체는 {@link EpubNavigation} 모델의 TOC, landmarks,
 * page-list 구조를 XHTML로 직렬화해야 합니다.</p>
 */
public interface EpubNavigationDocumentWriter {

    /**
     * Navigation 모델을 nav.xhtml 문자열로 직렬화합니다.
     *
     * @param navigation Navigation 모델
     * @param options    EPUB 생성 옵션
     * @return nav.xhtml 문자열
     * @throws EpubGenerationException 직렬화 실패 시
     */
    String serialize(
            EpubNavigation navigation,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * 기본 생성 옵션으로 Navigation Document를 직렬화합니다.
     *
     * @param navigation Navigation 모델
     * @return nav.xhtml 문자열
     * @throws EpubGenerationException 직렬화 실패 시
     */
    default String serialize(
            EpubNavigation navigation
    ) throws EpubGenerationException {

        return serialize(
                navigation,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * nav.xhtml을 지정한 Writer에 기록합니다.
     *
     * <p>전달받은 Writer는 닫지 않습니다.</p>
     *
     * @param navigation Navigation 모델
     * @param writer     출력 Writer
     * @param options    EPUB 생성 옵션
     * @throws EpubGenerationException 기록 실패 시
     */
    default void write(
            EpubNavigation navigation,
            Writer writer,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                writer,
                "EPUB navigation Writer must not be null."
        );

        String xhtml = serialize(
                navigation,
                options
        );

        try {
            writer.write(xhtml);
            writer.flush();

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to write EPUB Navigation Document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * nav.xhtml을 출력 스트림에 기록합니다.
     *
     * <p>문자 인코딩은
     * {@link EpubGenerationOptions#getCharset()}을 사용합니다.</p>
     *
     * <p>전달받은 OutputStream은 닫지 않습니다.</p>
     *
     * @param navigation  Navigation 모델
     * @param outputStream 출력 스트림
     * @param options     EPUB 생성 옵션
     * @throws EpubGenerationException 기록 실패 시
     */
    default void write(
            EpubNavigation navigation,
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB navigation output stream must not be null."
        );

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        String xhtml = serialize(
                navigation,
                resolvedOptions
        );

        try {
            outputStream.write(
                    xhtml.getBytes(
                            resolvedOptions.getCharset()
                    )
            );
            outputStream.flush();

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to write EPUB Navigation Document "
                            + "to the output stream."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * nav.xhtml을 지정한 파일에 기록합니다.
     *
     * <p>상위 디렉터리가 없으면 자동으로 생성합니다.</p>
     *
     * @param navigation Navigation 모델
     * @param outputPath nav.xhtml 출력 경로
     * @param options    EPUB 생성 옵션
     * @return 기록된 nav.xhtml 경로
     * @throws EpubGenerationException 파일 생성 실패 시
     */
    default Path write(
            EpubNavigation navigation,
            Path outputPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Path normalizedOutputPath =
                normalizeOutputPath(outputPath);

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        validate(
                navigation,
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
                        navigation,
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
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to create EPUB Navigation Document: "
                            + normalizedOutputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .filePath(normalizedOutputPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기본 생성 옵션으로 nav.xhtml 파일을 생성합니다.
     *
     * @param navigation Navigation 모델
     * @param outputPath 출력 경로
     * @return 기록된 nav.xhtml 경로
     * @throws EpubGenerationException 생성 실패 시
     */
    default Path write(
            EpubNavigation navigation,
            Path outputPath
    ) throws EpubGenerationException {

        return write(
                navigation,
                outputPath,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * Navigation Document 입력값을 검증합니다.
     *
     * @param navigation Navigation 모델
     * @param options    EPUB 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 경우
     */
    default void validate(
            EpubNavigation navigation,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (navigation == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB navigation model must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        if (!resolvedOptions.getVersion().isEpub3()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .UNSUPPORTED_REQUEST,
                    "EPUB Navigation Document is only supported "
                            + "for EPUB 3."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .detail(
                            "epubVersion",
                            resolvedOptions
                                    .getVersion()
                                    .toString()
                    )
                    .build();
        }

        if (!resolvedOptions
                .isGenerateNavigationDocument()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB Navigation Document generation "
                            + "is disabled by generation options."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        try {
            navigation.validate(
                    resolvedOptions.getVersion()
            );

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "EPUB navigation model validation failed."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 현재 구현체가 Navigation 모델을 처리할 수 있는지 확인합니다.
     *
     * @param navigation Navigation 모델
     * @param options    생성 옵션
     * @return 지원하면 {@code true}
     */
    default boolean supports(
            EpubNavigation navigation,
            EpubGenerationOptions options
    ) {
        return navigation != null
                && options != null
                && options.getVersion().isEpub3()
                && options.isGenerateNavigationDocument();
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
                    "EPUB navigation output path "
                            + "must not be null."
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
                    "EPUB Navigation Document output path "
                            + "must reference a file."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        String fileName =
                outputPath.getFileName().toString();

        if (!fileName
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(".xhtml")) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB Navigation Document must use "
                            + "the .xhtml extension: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .INVALID_REQUEST,
                    "EPUB Navigation Document output path "
                            + "points to a directory: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }
    }
}