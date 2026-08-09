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
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;

/**
 * EPUB OPF 패키지 문서를 생성하는 계약입니다.
 *
 * <p>{@link EpubPackage} 모델을 XML로 직렬화하여 EPUB 패키지 문서인
 * {@code content.opf}를 생성합니다.</p>
 *
 * <p>구현체는 다음 요소를 순서대로 출력해야 합니다.</p>
 *
 * <ol>
 *     <li>{@code package}</li>
 *     <li>{@code metadata}</li>
 *     <li>{@code manifest}</li>
 *     <li>{@code spine}</li>
 * </ol>
 *
 * <p>EPUB 3 패키지 문서의 기본 구조는 다음과 같습니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <package
 *     xmlns="http://www.idpf.org/2007/opf"
 *     version="3.0"
 *     unique-identifier="book-id"
 *     xml:lang="ko">
 *
 *     <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
 *         ...
 *     </metadata>
 *
 *     <manifest>
 *         ...
 *     </manifest>
 *
 *     <spine page-progression-direction="ltr">
 *         ...
 *     </spine>
 * </package>
 * }
 * </pre>
 */
public interface EpubPackageDocumentWriter {

    /**
     * EPUB 패키지 모델을 OPF XML 문자열로 직렬화합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param options     EPUB 생성 옵션
     * @return 직렬화된 OPF XML
     * @throws EpubGenerationException 패키지 문서 직렬화에 실패한 경우
     */
    String serialize(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * 기본 생성 옵션을 사용하여 EPUB 패키지 모델을
     * OPF XML 문자열로 직렬화합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @return 직렬화된 OPF XML
     * @throws EpubGenerationException 패키지 문서 직렬화에 실패한 경우
     */
    default String serialize(EpubPackage epubPackage)
            throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        EpubGenerationOptions options =
                EpubGenerationOptions.builder()
                        .version(epubPackage.getVersion())
                        .build();

        return serialize(epubPackage, options);
    }

    /**
     * EPUB 패키지 문서를 지정한 {@link Writer}에 기록합니다.
     *
     * <p>이 메서드는 전달받은 Writer를 닫지 않습니다.</p>
     *
     * @param epubPackage EPUB 패키지 모델
     * @param writer      XML 출력 Writer
     * @param options     EPUB 생성 옵션
     * @throws EpubGenerationException 패키지 문서 생성에 실패한 경우
     */
    default void write(
            EpubPackage epubPackage,
            Writer writer,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                writer,
                "EPUB package document writer must not be null."
        );

        String xml = serialize(epubPackage, options);

        try {
            writer.write(xml);
            writer.flush();
        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .PACKAGE_DOCUMENT_GENERATION_FAILED,
                    "Failed to write the EPUB package document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기본 생성 옵션을 사용하여 EPUB 패키지 문서를
     * 지정한 Writer에 기록합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param writer      XML 출력 Writer
     * @throws EpubGenerationException 패키지 문서 생성에 실패한 경우
     */
    default void write(
            EpubPackage epubPackage,
            Writer writer
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        EpubGenerationOptions options =
                EpubGenerationOptions.builder()
                        .version(epubPackage.getVersion())
                        .build();

        write(epubPackage, writer, options);
    }

    /**
     * EPUB 패키지 문서를 지정한 출력 스트림에 기록합니다.
     *
     * <p>출력 문자 인코딩은
     * {@link EpubGenerationOptions#getCharset()}을 사용합니다.</p>
     *
     * <p>이 메서드는 전달받은 OutputStream을 닫지 않습니다.</p>
     *
     * @param epubPackage EPUB 패키지 모델
     * @param outputStream XML 출력 스트림
     * @param options EPUB 생성 옵션
     * @throws EpubGenerationException 패키지 문서 생성에 실패한 경우
     */
    default void write(
            EpubPackage epubPackage,
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB package output stream must not be null."
        );

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        String xml = serialize(epubPackage, resolvedOptions);

        try {
            outputStream.write(
                    xml.getBytes(resolvedOptions.getCharset())
            );
            outputStream.flush();
        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .PACKAGE_DOCUMENT_GENERATION_FAILED,
                    "Failed to write the EPUB package document "
                            + "to the output stream."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * EPUB 패키지 문서를 지정한 파일에 기록합니다.
     *
     * <p>상위 디렉터리가 존재하지 않으면 자동으로 생성합니다.</p>
     *
     * @param epubPackage EPUB 패키지 모델
     * @param outputPath  OPF 출력 파일 경로
     * @param options     EPUB 생성 옵션
     * @return 기록된 OPF 파일 경로
     * @throws EpubGenerationException 패키지 문서 생성에 실패한 경우
     */
    default Path write(
            EpubPackage epubPackage,
            Path outputPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        Path normalizedOutputPath = normalizeOutputPath(outputPath);
        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        validateOutputFile(normalizedOutputPath);

        try {
            Path parent = normalizedOutputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (OutputStream outputStream = Files.newOutputStream(
                    normalizedOutputPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                write(
                        epubPackage,
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
                            .PACKAGE_DOCUMENT_GENERATION_FAILED,
                    "Failed to create the EPUB package document: "
                            + normalizedOutputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .filePath(normalizedOutputPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 기본 생성 옵션으로 EPUB 패키지 문서를 파일에 기록합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param outputPath  OPF 출력 경로
     * @return 기록된 파일 경로
     * @throws EpubGenerationException 패키지 문서 생성에 실패한 경우
     */
    default Path write(
            EpubPackage epubPackage,
            Path outputPath
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        EpubGenerationOptions options =
                EpubGenerationOptions.builder()
                        .version(epubPackage.getVersion())
                        .build();

        return write(epubPackage, outputPath, options);
    }

    /**
     * EPUB 패키지 문서의 출력 가능 여부를 검증합니다.
     *
     * <p>구현체는 필요에 따라 추가 검증을 수행할 수 있습니다.</p>
     *
     * @param epubPackage EPUB 패키지 모델
     * @param options     EPUB 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 패키지인 경우
     */
    default void validate(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (epubPackage == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .build();
        }

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        if (epubPackage.getVersion()
                != resolvedOptions.getVersion()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package version and generation option "
                            + "version do not match: "
                            + epubPackage.getVersion()
                            + " != "
                            + resolvedOptions.getVersion()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .detail(
                            "packageVersion",
                            epubPackage.getVersion().toString()
                    )
                    .detail(
                            "optionVersion",
                            resolvedOptions.getVersion().toString()
                    )
                    .build();
        }

        try {
            epubPackage.validate();
        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .PACKAGE_DOCUMENT_GENERATION_FAILED,
                    "EPUB package validation failed before "
                            + "OPF serialization."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 현재 구현체가 지정한 EPUB 버전을 지원하는지 확인합니다.
     *
     * @param epubPackage EPUB 패키지
     * @return 지원하면 {@code true}
     */
    default boolean supports(EpubPackage epubPackage) {
        return epubPackage != null;
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

    private static Path normalizeOutputPath(Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException(
                    "EPUB package output path must not be null."
            );
        }

        return outputPath.toAbsolutePath().normalize();
    }

    private static void validateOutputFile(Path outputPath)
            throws EpubGenerationException {

        String fileName = outputPath.getFileName() == null
                ? ""
                : outputPath.getFileName().toString();

        if (!fileName.toLowerCase().endsWith(".opf")) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package document must use the .opf "
                            + "file extension: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package output path points to a directory: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }
    }
}