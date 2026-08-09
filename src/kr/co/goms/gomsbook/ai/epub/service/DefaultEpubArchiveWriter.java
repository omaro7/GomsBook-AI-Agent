/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;

/**
 * EPUB 작업 디렉터리를 최종 {@code .epub} ZIP 아카이브로 생성하는
 * 기본 구현체입니다.
 *
 * <p>EPUB ZIP 패키징에서 가장 중요한 다음 규칙을 보장합니다.</p>
 *
 * <ul>
 *     <li>{@code mimetype} 엔트리를 반드시 첫 번째로 기록합니다.</li>
 *     <li>{@code mimetype}은 STORED 방식으로 기록합니다.</li>
 *     <li>{@code mimetype}에는 정확한 CRC와 크기를 미리 설정합니다.</li>
 *     <li>나머지 파일은 DEFLATED 방식으로 압축합니다.</li>
 *     <li>ZIP 엔트리 경로는 항상 {@code /}를 사용합니다.</li>
 *     <li>디렉터리 엔트리는 생성하지 않고 실제 파일만 기록합니다.</li>
 * </ul>
 *
 * <p>재현 가능한 빌드 옵션이 활성화된 경우 모든 ZIP 엔트리의
 * 타임스탬프를 동일한 값으로 설정하고 파일 정렬 순서를 고정합니다.</p>
 */
public final class DefaultEpubArchiveWriter
        implements EpubArchiveWriter {

    private static final int BUFFER_SIZE = 16 * 1024;

    private final EpubMimetypeWriter mimetypeWriter;

    /**
     * 기본 mimetype writer를 사용하는 ArchiveWriter를 생성합니다.
     */
    public DefaultEpubArchiveWriter() {
        this(new DefaultEpubMimetypeWriter());
    }

    /**
     * 지정한 mimetype writer를 사용하는 ArchiveWriter를 생성합니다.
     *
     * @param mimetypeWriter mimetype 검증에 사용할 writer
     */
    public DefaultEpubArchiveWriter(
            EpubMimetypeWriter mimetypeWriter
    ) {
        this.mimetypeWriter = Objects.requireNonNull(
                mimetypeWriter,
                "EPUB mimetype writer must not be null."
        );
    }

    /**
     * EPUB 작업 디렉터리를 최종 EPUB 파일로 패키징합니다.
     *
     * @param workingDirectory EPUB 작업 디렉터리
     * @param outputFile       최종 EPUB 파일
     * @param options          생성 옵션
     * @return 생성된 EPUB 파일
     * @throws EpubGenerationException 패키징 실패 시
     */
    @Override
    public Path write(
            Path workingDirectory,
            Path outputFile,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(
                workingDirectory,
                outputFile,
                options
        );

        Path normalizedWorkingDirectory =
                workingDirectory.toAbsolutePath().normalize();

        Path normalizedOutputFile =
                outputFile.toAbsolutePath().normalize();

        Path mimetypeFile =
                normalizedWorkingDirectory.resolve(
                        EpubMimetypeWriter.MIMETYPE_FILE_NAME
                );

        /*
         * ZIP 생성 전에 mimetype 내용까지 정확하게 검사합니다.
         */
        mimetypeWriter.validateWrittenFile(mimetypeFile);

        List<Path> resourceFiles =
                collectArchiveFiles(
                        normalizedWorkingDirectory,
                        mimetypeFile
                );

        prepareOutputFile(
                normalizedOutputFile,
                options
        );

        try {
            Path parent = normalizedOutputFile.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (
                    OutputStream fileOutputStream =
                            Files.newOutputStream(
                                    normalizedOutputFile,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE
                            );

                    BufferedOutputStream bufferedOutputStream =
                            new BufferedOutputStream(
                                    fileOutputStream,
                                    BUFFER_SIZE
                            );

                    ZipOutputStream zipOutputStream =
                            new ZipOutputStream(
                                    bufferedOutputStream
                            )
            ) {
                zipOutputStream.setLevel(
                        options.getCompressionLevel()
                );

                /*
                 * EPUB 규격 핵심:
                 *
                 * 첫 번째 ZIP 엔트리는 반드시 mimetype이고,
                 * STORED 방식으로 기록합니다.
                 */
                writeMimetypeEntry(
                        zipOutputStream,
                        mimetypeFile,
                        options
                );

                /*
                 * 나머지 파일은 안정적인 순서로 DEFLATED 처리합니다.
                 */
                for (Path file : resourceFiles) {
                    writeCompressedEntry(
                            zipOutputStream,
                            normalizedWorkingDirectory,
                            file,
                            options
                    );
                }

                zipOutputStream.finish();
            }

            validateArchive(normalizedOutputFile);

            return normalizedOutputFile;

        } catch (EpubGenerationException exception) {
            deleteFailedOutput(normalizedOutputFile);
            throw exception;

        } catch (IOException exception) {
            deleteFailedOutput(normalizedOutputFile);

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Failed to create EPUB archive: "
                            + normalizedOutputFile
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(normalizedOutputFile)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ZIP 첫 번째 엔트리로 mimetype을 STORED 방식으로 기록합니다.
     */
    private void writeMimetypeEntry(
            ZipOutputStream zipOutputStream,
            Path mimetypeFile,
            EpubGenerationOptions options
    ) throws IOException, EpubGenerationException {

        byte[] data = Files.readAllBytes(mimetypeFile);

        byte[] expected =
                mimetypeWriter.getBytes(options);

        if (!java.util.Arrays.equals(data, expected)) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .MIMETYPE_GENERATION_FAILED,
                    "Invalid EPUB mimetype content before packaging."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(mimetypeFile)
                    .build();
        }

        CRC32 crc = new CRC32();
        crc.update(data);

        ZipEntry entry = new ZipEntry(
                EpubMimetypeWriter.MIMETYPE_FILE_NAME
        );

        entry.setMethod(ZipEntry.STORED);

        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());

        applyTimestamp(
                entry,
                mimetypeFile,
                options
        );

        /*
         * mimetype에 불필요한 extra field를 명시적으로 추가하지 않습니다.
         */
        entry.setExtra(null);

        zipOutputStream.putNextEntry(entry);

        zipOutputStream.write(data);

        zipOutputStream.closeEntry();
    }

    /**
     * 일반 EPUB 리소스를 DEFLATED 방식으로 기록합니다.
     */
    private void writeCompressedEntry(
            ZipOutputStream zipOutputStream,
            Path workingDirectory,
            Path file,
            EpubGenerationOptions options
    ) throws IOException, EpubGenerationException {

        String entryName = resolveEntryName(
                workingDirectory,
                file
        );

        if (isMimetypeEntry(entryName)) {
            /*
             * mimetype은 이미 첫 번째 엔트리로 기록했습니다.
             */
            return;
        }

        validateEntryName(entryName, file);

        ZipEntry entry = new ZipEntry(entryName);

        entry.setMethod(ZipEntry.DEFLATED);

        applyTimestamp(
                entry,
                file,
                options
        );

        zipOutputStream.putNextEntry(entry);

        try (InputStream inputStream =
                Files.newInputStream(file)) {

            byte[] buffer = new byte[BUFFER_SIZE];

            int read;

            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }

                zipOutputStream.write(
                        buffer,
                        0,
                        read
                );
            }
        }

        zipOutputStream.closeEntry();
    }

    /**
     * EPUB 작업 디렉터리의 모든 일반 파일을 수집합니다.
     *
     * <p>mimetype은 별도 처리하므로 제외합니다.</p>
     */
    private List<Path> collectArchiveFiles(
            Path workingDirectory,
            Path mimetypeFile
    ) throws EpubGenerationException {

        try {
            List<Path> files = new ArrayList<>();

            try (var stream = Files.walk(workingDirectory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path ->
                                !samePath(path, mimetypeFile)
                        )
                        .forEach(files::add);
            }

            /*
             * ZIP 엔트리 생성 순서를 항상 동일하게 유지합니다.
             *
             * META-INF/container.xml 등을 특정 순서로 강제할 필요는 없지만
             * 정렬하면 재현 가능한 빌드와 테스트에 유리합니다.
             */
            files.sort(
                    Comparator.comparing(
                            path -> normalizeArchivePath(
                                    workingDirectory.relativize(path)
                            )
                    )
            );

            return files;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Failed to enumerate EPUB working directory: "
                            + workingDirectory
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(workingDirectory)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 출력 파일 생성 전 상태를 준비합니다.
     */
    private void prepareOutputFile(
            Path outputFile,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        try {
            if (!Files.exists(outputFile)) {
                return;
            }

            if (Files.isDirectory(outputFile)) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .OUTPUT_WRITE_FAILED,
                        "EPUB output path is a directory: "
                                + outputFile
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .OUTPUT_WRITE
                        )
                        .filePath(outputFile)
                        .build();
            }

            if (!options.isOverwriteOutput()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .OUTPUT_WRITE_FAILED,
                        "EPUB output file already exists "
                                + "and overwrite is disabled: "
                                + outputFile
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .OUTPUT_WRITE
                        )
                        .filePath(outputFile)
                        .build();
            }

            Files.delete(outputFile);

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .OUTPUT_WRITE_FAILED,
                    "Failed to prepare EPUB output file: "
                            + outputFile
            )
                    .stage(
                            EpubGenerationException.Stage.OUTPUT_WRITE
                    )
                    .filePath(outputFile)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ZIP 엔트리에 타임스탬프를 적용합니다.
     */
    private void applyTimestamp(
            ZipEntry entry,
            Path sourceFile,
            EpubGenerationOptions options
    ) throws IOException {

        if (options.isReproducibleBuild()) {
            Instant timestamp = options.getBuildTimestamp()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Reproducible EPUB build "
                                            + "requires a timestamp."
                            )
                    );

            /*
             * setLastModifiedTime을 사용하면 Instant 기반으로
             * 안정적인 시간을 기록할 수 있습니다.
             */
            entry.setLastModifiedTime(
                    FileTime.from(timestamp)
            );

            return;
        }

        FileTime sourceTimestamp =
                Files.getLastModifiedTime(sourceFile);

        entry.setLastModifiedTime(sourceTimestamp);
    }

    /**
     * ZIP 엔트리 경로를 검증합니다.
     */
    private void validateEntryName(
            String entryName,
            Path sourceFile
    ) throws EpubGenerationException {

        if (entryName == null || entryName.isBlank()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "EPUB ZIP entry name must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(sourceFile)
                    .build();
        }

        if (entryName.startsWith("/")) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "EPUB ZIP entry must use a relative path: "
                            + entryName
            )
                    .stage(
                            EpubGenerationException.Stage.PACKAGING
                    )
                    .filePath(sourceFile)
                    .epubPath(entryName)
                    .build();
        }

        for (String segment : entryName.split("/")) {
            if ("..".equals(segment)) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                        "EPUB ZIP entry must not contain "
                                + "parent traversal: "
                                + entryName
                )
                        .stage(
                                EpubGenerationException.Stage.PACKAGING
                        )
                        .filePath(sourceFile)
                        .epubPath(entryName)
                        .build();
            }
        }
    }

    /**
     * 생성된 EPUB ZIP 구조를 상세 검증합니다.
     *
     * <p>기본 인터페이스 검증에 더해 다음을 확인합니다.</p>
     *
     * <ul>
     *     <li>첫 번째 엔트리가 mimetype인지 확인</li>
     *     <li>mimetype이 STORED 방식인지 확인</li>
     *     <li>mimetype 내용이 정확한지 확인</li>
     *     <li>META-INF/container.xml 존재 여부 확인</li>
     * </ul>
     */
    @Override
    public void validateArchive(
            Path epubFile
    ) throws EpubGenerationException {

        EpubArchiveWriter.super.validateArchive(epubFile);

        Path normalized =
                epubFile.toAbsolutePath().normalize();

        try (ZipFile zipFile = new ZipFile(normalized.toFile())) {

            var entries = zipFile.entries();

            if (!entries.hasMoreElements()) {
                throw archiveValidationException(
                        normalized,
                        "EPUB archive contains no ZIP entries."
                );
            }

            ZipEntry firstEntry = entries.nextElement();

            if (!EpubMimetypeWriter.MIMETYPE_FILE_NAME
                    .equals(firstEntry.getName())) {

                throw archiveValidationException(
                        normalized,
                        "The first EPUB ZIP entry must be 'mimetype', "
                                + "but was: "
                                + firstEntry.getName()
                );
            }

            if (firstEntry.getMethod() != ZipEntry.STORED) {
                throw archiveValidationException(
                        normalized,
                        "EPUB mimetype ZIP entry must use STORED method."
                );
            }

            validateArchivedMimetype(
                    zipFile,
                    firstEntry,
                    normalized
            );

            ZipEntry containerEntry =
                    zipFile.getEntry(
                            "META-INF/container.xml"
                    );

            if (containerEntry == null) {
                throw archiveValidationException(
                        normalized,
                        "EPUB archive does not contain "
                                + "META-INF/container.xml."
                );
            }

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (IOException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                    "Failed to validate generated EPUB archive: "
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
     * ZIP 내부 mimetype 데이터를 검사합니다.
     */
    private void validateArchivedMimetype(
            ZipFile zipFile,
            ZipEntry entry,
            Path epubFile
    ) throws IOException, EpubGenerationException {

        byte[] actual;

        try (InputStream inputStream =
                zipFile.getInputStream(entry)) {

            actual = inputStream.readAllBytes();
        }

        byte[] expected = mimetypeWriter.getBytes();

        if (!java.util.Arrays.equals(actual, expected)) {
            throw archiveValidationException(
                    epubFile,
                    "EPUB archive contains invalid mimetype data."
            );
        }

        if (entry.getSize() != expected.length) {
            throw archiveValidationException(
                    epubFile,
                    "EPUB mimetype entry size is invalid: "
                            + entry.getSize()
                            + ", expected: "
                            + expected.length
            );
        }

        if (entry.getCompressedSize() != expected.length) {
            throw archiveValidationException(
                    epubFile,
                    "EPUB mimetype entry must not be compressed."
            );
        }
    }

    private EpubGenerationException archiveValidationException(
            Path epubFile,
            String message
    ) {
        return EpubGenerationException.builder(
                EpubGenerationException.ErrorCode.PACKAGING_FAILED,
                message
        )
                .stage(
                        EpubGenerationException.Stage.PACKAGING
                )
                .filePath(epubFile)
                .build();
    }

    /**
     * 실패한 EPUB 출력 파일을 삭제합니다.
     */
    private void deleteFailedOutput(Path outputFile) {
        if (outputFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException ignored) {
            /*
             * 원래 패키징 오류를 유지해야 하므로 정리 실패는 무시합니다.
             * 별도 로깅 구현이 있다면 이 위치에서 기록합니다.
             */
        }
    }

    private static boolean samePath(
            Path first,
            Path second
    ) {
        return first.toAbsolutePath()
                .normalize()
                .equals(
                        second.toAbsolutePath().normalize()
                );
    }

    private static String normalizeArchivePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}