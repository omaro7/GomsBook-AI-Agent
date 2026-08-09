/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcxNavPoint;

/**
 * EPUB NCX 문서(toc.ncx)를 XML로 직렬화하는 기본 구현체입니다.
 *
 * <p>EPUB 2의 표준 탐색 문서와 EPUB 3 하위 호환용 NCX 생성을
 * 지원합니다.</p>
 */
public final class DefaultEpubNcxDocumentWriter
        implements EpubNcxDocumentWriter {

    private static final String XML_VERSION = "1.0";

    /**
     * NCX 모델을 XML 문자열로 직렬화합니다.
     *
     * @param ncx NCX 모델
     * @param options EPUB 생성 옵션
     * @return toc.ncx XML
     * @throws EpubGenerationException 직렬화 실패 시
     */
    @Override
    public String serialize(
            EpubNcx ncx,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(ncx, options);

        try {
            /*
             * playOrder가 지정되지 않은 navPoint는
             * 직렬화 전에 순차 번호를 부여합니다.
             */
            EpubNcx resolvedNcx =
                    ncx.withResolvedPlayOrders();

            SerializationContext context =
                    new SerializationContext(options);

            StringBuilder xml =
                    new StringBuilder(8192);

            writeXmlDeclaration(
                    xml,
                    context
            );

            writeNcxStart(
                    xml,
                    resolvedNcx,
                    context
            );

            writeHead(
                    xml,
                    resolvedNcx,
                    context
            );

            writeDocTitle(
                    xml,
                    resolvedNcx,
                    context
            );

            if (resolvedNcx.hasAuthor()) {
                writeDocAuthor(
                        xml,
                        resolvedNcx,
                        context
                );
            }

            writeNavMap(
                    xml,
                    resolvedNcx,
                    context
            );

            writeNcxEnd(
                    xml,
                    context
            );

            return xml.toString();

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "Failed to serialize EPUB NCX document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    private void writeXmlDeclaration(
            StringBuilder xml,
            SerializationContext context
    ) {
        if (!context.options()
                .isWriteXmlDeclaration()) {
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

    private void writeNcxStart(
            StringBuilder xml,
            EpubNcx ncx,
            SerializationContext context
    ) {
        context.indent(xml, 0);

        xml.append("<ncx");

        writeAttribute(
                xml,
                "xmlns",
                EpubNcx.NCX_NAMESPACE
        );

        writeAttribute(
                xml,
                "version",
                ncx.getVersion()
        );

        ncx.getLanguage().ifPresent(language ->
                writeAttribute(
                        xml,
                        "xml:lang",
                        language
                )
        );

        xml.append('>');
        context.newLine(xml);
    }

    private void writeNcxEnd(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 0);
        xml.append("</ncx>");
        context.newLine(xml);
    }

    /**
     * NCX head 영역을 출력합니다.
     */
    private void writeHead(
            StringBuilder xml,
            EpubNcx ncx,
            SerializationContext context
    ) {
        context.indent(xml, 1);
        xml.append("<head>");
        context.newLine(xml);

        for (Map.Entry<String, String> entry :
                ncx.getResolvedHeadMetadata().entrySet()) {

            context.indent(xml, 2);

            xml.append("<meta");

            writeAttribute(
                    xml,
                    "name",
                    entry.getKey()
            );

            writeAttribute(
                    xml,
                    "content",
                    entry.getValue()
            );

            xml.append("/>");
            context.newLine(xml);
        }

        context.indent(xml, 1);
        xml.append("</head>");
        context.newLine(xml);
    }

    /**
     * docTitle을 출력합니다.
     */
    private void writeDocTitle(
            StringBuilder xml,
            EpubNcx ncx,
            SerializationContext context
    ) {
        context.indent(xml, 1);
        xml.append("<docTitle>");
        context.newLine(xml);

        context.indent(xml, 2);
        xml.append("<text>")
                .append(
                        escapeText(
                                ncx.getTitle()
                        )
                )
                .append("</text>");
        context.newLine(xml);

        context.indent(xml, 1);
        xml.append("</docTitle>");
        context.newLine(xml);
    }

    /**
     * docAuthor를 출력합니다.
     */
    private void writeDocAuthor(
            StringBuilder xml,
            EpubNcx ncx,
            SerializationContext context
    ) {
        String author = ncx.getAuthor()
                .orElseThrow();

        context.indent(xml, 1);
        xml.append("<docAuthor>");
        context.newLine(xml);

        context.indent(xml, 2);
        xml.append("<text>")
                .append(
                        escapeText(author)
                )
                .append("</text>");
        context.newLine(xml);

        context.indent(xml, 1);
        xml.append("</docAuthor>");
        context.newLine(xml);
    }

    /**
     * navMap을 출력합니다.
     */
    private void writeNavMap(
            StringBuilder xml,
            EpubNcx ncx,
            SerializationContext context
    ) throws EpubGenerationException {

        context.indent(xml, 1);
        xml.append("<navMap>");
        context.newLine(xml);

        for (EpubNcxNavPoint navPoint :
                ncx.getNavPoints()) {

            writeNavPoint(
                    xml,
                    navPoint,
                    2,
                    context
            );
        }

        context.indent(xml, 1);
        xml.append("</navMap>");
        context.newLine(xml);
    }

    /**
     * navPoint를 재귀적으로 출력합니다.
     */
    private void writeNavPoint(
            StringBuilder xml,
            EpubNcxNavPoint navPoint,
            int depth,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                navPoint,
                "NCX navPoint must not be null."
        );

        if (!navPoint.isIncluded()) {
            return;
        }

        validateNavPoint(navPoint);

        context.indent(xml, depth);

        xml.append("<navPoint");

        writeAttribute(
                xml,
                "id",
                navPoint.getId()
        );

        writeAttribute(
                xml,
                "playOrder",
                String.valueOf(
                        navPoint.getPlayOrder()
                )
        );

        xml.append('>');
        context.newLine(xml);

        writeNavLabel(
                xml,
                navPoint,
                depth + 1,
                context
        );

        writeContent(
                xml,
                navPoint,
                depth + 1,
                context
        );

        for (EpubNcxNavPoint child :
                navPoint.getChildren()) {

            writeNavPoint(
                    xml,
                    child,
                    depth + 1,
                    context
            );
        }

        context.indent(xml, depth);
        xml.append("</navPoint>");
        context.newLine(xml);
    }

    private void writeNavLabel(
            StringBuilder xml,
            EpubNcxNavPoint navPoint,
            int depth,
            SerializationContext context
    ) {
        context.indent(xml, depth);
        xml.append("<navLabel>");
        context.newLine(xml);

        context.indent(xml, depth + 1);
        xml.append("<text>")
                .append(
                        escapeText(
                                navPoint.getLabel()
                        )
                )
                .append("</text>");
        context.newLine(xml);

        context.indent(xml, depth);
        xml.append("</navLabel>");
        context.newLine(xml);
    }

    private void writeContent(
            StringBuilder xml,
            EpubNcxNavPoint navPoint,
            int depth,
            SerializationContext context
    ) {
        context.indent(xml, depth);

        xml.append("<content");

        writeAttribute(
                xml,
                "src",
                navPoint.getSrc()
        );

        xml.append("/>");
        context.newLine(xml);
    }

    private void validateNavPoint(
            EpubNcxNavPoint navPoint
    ) throws EpubGenerationException {

        if (navPoint.getId() == null
                || navPoint.getId().isBlank()) {

            throw ncxException(
                    "NCX navPoint id must not be blank."
            );
        }

        if (navPoint.getLabel() == null
                || navPoint.getLabel().isBlank()) {

            throw ncxException(
                    "NCX navPoint label must not be blank: "
                            + navPoint.getId()
            );
        }

        if (navPoint.getSrc() == null
                || navPoint.getSrc().isBlank()) {

            throw ncxException(
                    "NCX navPoint src must not be blank: "
                            + navPoint.getId()
            );
        }

        if (navPoint.getPlayOrder() <= 0) {
            throw ncxException(
                    "NCX navPoint playOrder must be greater than zero: "
                            + navPoint.getId()
            );
        }
    }

    private EpubGenerationException ncxException(
            String message
    ) {
        return EpubGenerationException.builder(
                EpubGenerationException.ErrorCode
                        .NCX_GENERATION_FAILED,
                message
        )
                .stage(
                        EpubGenerationException.Stage
                                .NCX_GENERATION
                )
                .build();
    }

    private static void writeAttribute(
            StringBuilder xml,
            String name,
            String value
    ) {
        if (name == null
                || name.isBlank()
                || value == null) {
            return;
        }

        xml.append(' ')
                .append(name)
                .append("=\"")
                .append(
                        escapeAttribute(value)
                )
                .append('"');
    }

    private static String escapeText(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder(
                        value.length() + 16
                );

        for (int index = 0;
                index < value.length();
                index++) {

            char character =
                    value.charAt(index);

            switch (character) {
                case '&' ->
                        result.append("&amp;");

                case '<' ->
                        result.append("&lt;");

                case '>' ->
                        result.append("&gt;");

                default ->
                        appendValidXmlCharacter(
                                result,
                                character
                        );
            }
        }

        return result.toString();
    }

    private static String escapeAttribute(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder(
                        value.length() + 16
                );

        for (int index = 0;
                index < value.length();
                index++) {

            char character =
                    value.charAt(index);

            switch (character) {
                case '&' ->
                        result.append("&amp;");

                case '<' ->
                        result.append("&lt;");

                case '>' ->
                        result.append("&gt;");

                case '"' ->
                        result.append("&quot;");

                case '\'' ->
                        result.append("&apos;");

                case '\t' ->
                        result.append("&#x9;");

                case '\n' ->
                        result.append("&#xA;");

                case '\r' ->
                        result.append("&#xD;");

                default ->
                        appendValidXmlCharacter(
                                result,
                                character
                        );
            }
        }

        return result.toString();
    }

    private static void appendValidXmlCharacter(
            StringBuilder output,
            char character
    ) {
        if (!isValidXmlCharacter(character)) {
            throw new IllegalArgumentException(
                    "Invalid XML 1.0 character: 0x"
                            + Integer.toHexString(
                                    character
                            )
            );
        }

        output.append(character);
    }

    private static boolean isValidXmlCharacter(
            char character
    ) {
        return character == 0x9
                || character == 0xA
                || character == 0xD
                || character >= 0x20;
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

        private void indent(
                StringBuilder xml,
                int depth
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            int count =
                    depth
                            * options.getXmlIndentSize();

            for (int index = 0;
                    index < count;
                    index++) {

                xml.append(' ');
            }
        }

        private void newLine(
                StringBuilder xml
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            xml.append(
                    options.getLineSeparatorValue()
            );
        }
    }
}