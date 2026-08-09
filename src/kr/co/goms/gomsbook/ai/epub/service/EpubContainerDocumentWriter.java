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
 * EPUB 컨테이너 문서인 {@code META-INF/container.xml}을
 * 생성하는 계약입니다.
 *
 * <p>container.xml은 EPUB 아카이브 내부에서 OPF 패키지 문서의
 * 위치를 독서 시스템에 알려주는 필수 파일입니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <container
 *     version="1.0"
 *     xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
 *     <rootfiles>
 *         <rootfile
 *             full-path="OEBPS/content.opf"
 *             media-type="application/oebps-package+xml"/>
 *     </rootfiles>
 * </container>
 * }
 * </pre>
 *
 * <p>구현체는 최소 하나의 {@code rootfile} 요소를 생성해야 하며,
 * 기본 구현에서는 하나의 OPF 패키지 문서만 지원합니다.</p>
 */
public interface EpubContainerDocumentWriter {

    /**
     * EPUB container.xml을 문자열로 직렬화합니다.
     *
     * @param packageDocumentPath EPUB 아카이브 내부 OPF 경로
     * @param options             EPUB 생성 옵션
     * @return container.xml 문자열
     * @throws EpubGenerationException 직렬화에 실패한 경우
     */
    String serialize(
            String packageDocumentPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * EPUB 패키지 모델의 OPF 경로를 사용하여 container.xml을
     * 문자열로 직렬화합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param options     EPUB 생성 옵션
     * @return container.xml 문자열
     * @throws EpubGenerationException 직렬화에 실패한 경우
     */
    default String serialize(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        return serialize(
                epubPackage.getPackageDocumentPath(),
                options
        );
    }

    /**
     * 기본 생성 옵션을 사용하여 container.xml을 직렬화합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @return container.xml 문자열
     * @throws EpubGenerationException 직렬화에 실패한 경우
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
     * container.xml을 지정한 Writer에 기록합니다.
     *
     * <p>전달받은 Writer는 닫지 않습니다.</p>
     *
     * @param packageDocumentPath EPUB 아카이브 내부 OPF 경로
     * @param writer              XML 출력 Writer
     * @param options             EPUB 생성 옵션
     * @throws EpubGenerationException 기록에 실패한 경우
     */
    default void write(
            String packageDocumentPath,
            Writer writer,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                writer,
                "EPUB container document Writer must not be null."
        );

        String xml = serialize(
                packageDocumentPath,
                options
        );

        try {
            writer.write(xml);
            writer.flush();
        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .CONTAINER_GENERATION_FAILED,
                    "Failed to write META-INF/container.xml."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * EPUB 패키지 모델을 사용하여 container.xml을 Writer에 기록합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param writer      XML 출력 Writer
     * @param options     EPUB 생성 옵션
     * @throws EpubGenerationException 기록에 실패한 경우
     */
    default void write(
            EpubPackage epubPackage,
            Writer writer,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        write(
                epubPackage.getPackageDocumentPath(),
                writer,
                options
        );
    }

    /**
     * container.xml을 OutputStream에 기록합니다.
     *
     * <p>문자 인코딩은
     * {@link EpubGenerationOptions#getCharset()}을 사용합니다.</p>
     *
     * <p>전달받은 OutputStream은 닫지 않습니다.</p>
     *
     * @param packageDocumentPath EPUB 아카이브 내부 OPF 경로
     * @param outputStream        출력 스트림
     * @param options             EPUB 생성 옵션
     * @throws EpubGenerationException 기록에 실패한 경우
     */
    default void write(
            String packageDocumentPath,
            OutputStream outputStream,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                outputStream,
                "EPUB container output stream must not be null."
        );

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

        String xml = serialize(
                packageDocumentPath,
                resolvedOptions
        );

        try {
            outputStream.write(
                    xml.getBytes(resolvedOptions.getCharset())
            );
            outputStream.flush();
        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .CONTAINER_GENERATION_FAILED,
                    "Failed to write META-INF/container.xml "
                            + "to the output stream."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * container.xml을 지정한 파일에 기록합니다.
     *
     * <p>상위 디렉터리가 존재하지 않으면 자동으로 생성합니다.</p>
     *
     * @param packageDocumentPath EPUB 아카이브 내부 OPF 경로
     * @param outputPath          container.xml 출력 경로
     * @param options             EPUB 생성 옵션
     * @return 기록된 파일 경로
     * @throws EpubGenerationException 파일 생성에 실패한 경우
     */
    default Path write(
            String packageDocumentPath,
            Path outputPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        Path normalizedOutputPath =
                normalizeOutputPath(outputPath);

        EpubGenerationOptions resolvedOptions =
                requireOptions(options);

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
                        packageDocumentPath,
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
                            .CONTAINER_GENERATION_FAILED,
                    "Failed to create META-INF/container.xml: "
                            + normalizedOutputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .filePath(normalizedOutputPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * EPUB 패키지 모델을 사용하여 container.xml 파일을 생성합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param outputPath  출력 파일 경로
     * @param options     EPUB 생성 옵션
     * @return 기록된 파일 경로
     * @throws EpubGenerationException 파일 생성에 실패한 경우
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

        return write(
                epubPackage.getPackageDocumentPath(),
                outputPath,
                options
        );
    }

    /**
     * 기본 생성 옵션으로 container.xml 파일을 생성합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param outputPath  출력 파일 경로
     * @return 기록된 파일 경로
     * @throws EpubGenerationException 파일 생성에 실패한 경우
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
     * container.xml 생성 입력값을 검증합니다.
     *
     * @param packageDocumentPath EPUB 아카이브 내부 OPF 경로
     * @param options             EPUB 생성 옵션
     * @throws EpubGenerationException 입력값이 유효하지 않은 경우
     */
    default void validate(
            String packageDocumentPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        requireOptions(options);

        if (packageDocumentPath == null
                || packageDocumentPath.isBlank()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package document path must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .build();
        }

        String normalized = normalizeEpubPath(
                packageDocumentPath
        );

        if (normalized.startsWith("/")) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package document path must be relative: "
                            + packageDocumentPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .epubPath(normalized)
                    .build();
        }

        if (containsParentTraversal(normalized)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package document path must not contain "
                            + "parent traversal: "
                            + packageDocumentPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .epubPath(normalized)
                    .build();
        }

        if (!normalized.toLowerCase().endsWith(".opf")) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package document path must end with .opf: "
                            + packageDocumentPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .epubPath(normalized)
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
                    "EPUB container output path must not be null."
            );
        }

        return outputPath.toAbsolutePath().normalize();
    }

    private static void validateOutputPath(Path outputPath)
            throws EpubGenerationException {

        String fileName = outputPath.getFileName() == null
                ? ""
                : outputPath.getFileName().toString();

        if (!"container.xml".equalsIgnoreCase(fileName)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB container document file name must be "
                            + "container.xml: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
        }

        if (Files.exists(outputPath)
                && Files.isDirectory(outputPath)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB container output path points to a directory: "
                            + outputPath
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .filePath(outputPath)
                    .build();
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

        return normalized;
    }

    private static boolean containsParentTraversal(String value) {
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }

        return false;
    }
}