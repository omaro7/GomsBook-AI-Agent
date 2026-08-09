/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.Objects;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;

/**
 * EPUB 컨테이너 문서인 {@code META-INF/container.xml}을 생성하는
 * 기본 구현체입니다.
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
 * <p>현재 구현은 하나의 EPUB 패키지 문서(rootfile)를 사용하는
 * 일반적인 EPUB 구조를 대상으로 합니다.</p>
 *
 * <p>인스턴스 상태를 가지지 않으므로 여러 EPUB 생성 요청에서
 * 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubContainerDocumentWriter
        implements EpubContainerDocumentWriter {

    /**
     * OCF container.xml namespace입니다.
     */
    private static final String CONTAINER_NAMESPACE =
            "urn:oasis:names:tc:opendocument:xmlns:container";

    /**
     * container 요소의 버전입니다.
     */
    private static final String CONTAINER_VERSION = "1.0";

    /**
     * OPF 패키지 문서의 media-type입니다.
     */
    private static final String PACKAGE_MEDIA_TYPE =
            "application/oebps-package+xml";

    /**
     * XML 버전입니다.
     */
    private static final String XML_VERSION = "1.0";

    /**
     * EPUB container.xml을 문자열로 직렬화합니다.
     *
     * @param packageDocumentPath EPUB 내부 OPF 패키지 문서 경로
     * @param options             EPUB 생성 옵션
     * @return 직렬화된 container.xml
     * @throws EpubGenerationException container.xml 생성에 실패한 경우
     */
    @Override
    public String serialize(
            String packageDocumentPath,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(packageDocumentPath, options);

        try {
            EpubGenerationOptions resolvedOptions =
                    Objects.requireNonNull(
                            options,
                            "EPUB generation options must not be null."
                    );

            String normalizedPackagePath =
                    normalizePackageDocumentPath(
                            packageDocumentPath
                    );

            SerializationContext context =
                    new SerializationContext(resolvedOptions);

            StringBuilder xml = new StringBuilder(512);

            writeXmlDeclaration(xml, context);

            writeContainerStart(xml, context);

            writeRootfilesStart(xml, context);

            writeRootfile(
                    xml,
                    normalizedPackagePath,
                    context
            );

            writeRootfilesEnd(xml, context);

            writeContainerEnd(xml, context);

            return xml.toString();

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .CONTAINER_GENERATION_FAILED,
                    "Failed to serialize META-INF/container.xml."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .CONTAINER_GENERATION
                    )
                    .epubPath(packageDocumentPath)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * XML 선언을 출력합니다.
     */
    private void writeXmlDeclaration(
            StringBuilder xml,
            SerializationContext context
    ) {
        if (!context.options().isWriteXmlDeclaration()) {
            return;
        }

        xml.append("<?xml version=\"")
                .append(XML_VERSION)
                .append("\" encoding=\"")
                .append(
                        escapeAttribute(
                                context.options()
                                        .getCharset()
                                        .name()
                        )
                )
                .append("\"?>");

        context.newLine(xml);
    }

    /**
     * container 시작 요소를 출력합니다.
     */
    private void writeContainerStart(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 0);

        xml.append("<container");

        writeAttribute(
                xml,
                "version",
                CONTAINER_VERSION
        );

        writeAttribute(
                xml,
                "xmlns",
                CONTAINER_NAMESPACE
        );

        xml.append('>');

        context.newLine(xml);
    }

    /**
     * rootfiles 시작 요소를 출력합니다.
     */
    private void writeRootfilesStart(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 1);

        xml.append("<rootfiles>");

        context.newLine(xml);
    }

    /**
     * rootfile 요소를 출력합니다.
     */
    private void writeRootfile(
            StringBuilder xml,
            String packageDocumentPath,
            SerializationContext context
    ) {
        context.indent(xml, 2);

        xml.append("<rootfile");

        writeAttribute(
                xml,
                "full-path",
                packageDocumentPath
        );

        writeAttribute(
                xml,
                "media-type",
                PACKAGE_MEDIA_TYPE
        );

        xml.append("/>");

        context.newLine(xml);
    }

    /**
     * rootfiles 종료 요소를 출력합니다.
     */
    private void writeRootfilesEnd(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 1);

        xml.append("</rootfiles>");

        context.newLine(xml);
    }

    /**
     * container 종료 요소를 출력합니다.
     */
    private void writeContainerEnd(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 0);

        xml.append("</container>");

        context.newLine(xml);
    }

    /**
     * XML 속성을 출력합니다.
     */
    private static void writeAttribute(
            StringBuilder xml,
            String name,
            String value
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "XML attribute name must not be blank."
            );
        }

        if (value == null) {
            throw new IllegalArgumentException(
                    "XML attribute value must not be null: "
                            + name
            );
        }

        xml.append(' ')
                .append(name)
                .append("=\"")
                .append(escapeAttribute(value))
                .append('"');
    }

    /**
     * XML 속성값을 이스케이프합니다.
     *
     * @param value 원본 문자열
     * @return XML 속성용 이스케이프 문자열
     */
    private static String escapeAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder escaped =
                new StringBuilder(value.length() + 16);

        for (int index = 0;
                index < value.length();
                index++) {

            char character = value.charAt(index);

            switch (character) {

                case '&' ->
                        escaped.append("&amp;");

                case '<' ->
                        escaped.append("&lt;");

                case '>' ->
                        escaped.append("&gt;");

                case '"' ->
                        escaped.append("&quot;");

                case '\'' ->
                        escaped.append("&apos;");

                case '\t' ->
                        escaped.append("&#x9;");

                case '\n' ->
                        escaped.append("&#xA;");

                case '\r' ->
                        escaped.append("&#xD;");

                default ->
                        appendValidXmlCharacter(
                                escaped,
                                character
                        );
            }
        }

        return escaped.toString();
    }

    /**
     * XML 1.0에서 사용할 수 있는 문자인지 검사한 후 추가합니다.
     */
    private static void appendValidXmlCharacter(
            StringBuilder output,
            char character
    ) {
        if (!isValidXmlCharacter(character)) {
            throw new IllegalArgumentException(
                    "Invalid XML 1.0 character: 0x"
                            + Integer.toHexString(character)
            );
        }

        output.append(character);
    }

    /**
     * XML 1.0에서 허용되는 BMP 문자 여부를 확인합니다.
     */
    private static boolean isValidXmlCharacter(
            char character
    ) {
        return character == 0x9
                || character == 0xA
                || character == 0xD
                || character >= 0x20;
    }

    /**
     * EPUB 내부 OPF 경로를 정규화합니다.
     *
     * <p>EPUB 내부 경로는 운영체제와 관계없이 {@code /}를
     * 사용합니다.</p>
     */
    private static String normalizePackageDocumentPath(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB package document path must not be blank."
            );
        }

        String normalized = value.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }

        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException(
                    "EPUB package document path must be relative: "
                            + value
            );
        }

        if (containsParentTraversal(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB package document path must not contain "
                            + "parent traversal: "
                            + value
            );
        }

        if (!normalized
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(".opf")) {

            throw new IllegalArgumentException(
                    "EPUB package document path must end with .opf: "
                            + value
            );
        }

        return normalized;
    }

    /**
     * 상위 디렉터리 탐색(..)이 포함되어 있는지 확인합니다.
     */
    private static boolean containsParentTraversal(
            String value
    ) {
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }

        return false;
    }

    /**
     * XML 직렬화 옵션을 관리합니다.
     */
    private record SerializationContext(
            EpubGenerationOptions options
    ) {

        private SerializationContext {
            Objects.requireNonNull(
                    options,
                    "EPUB generation options must not be null."
            );
        }

        /**
         * XML 들여쓰기를 출력합니다.
         */
        private void indent(
                StringBuilder xml,
                int depth
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            int count =
                    depth * options.getXmlIndentSize();

            for (int index = 0;
                    index < count;
                    index++) {

                xml.append(' ');
            }
        }

        /**
         * XML 줄바꿈을 출력합니다.
         */
        private void newLine(StringBuilder xml) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            xml.append(
                    options.getLineSeparatorValue()
            );
        }
    }
}