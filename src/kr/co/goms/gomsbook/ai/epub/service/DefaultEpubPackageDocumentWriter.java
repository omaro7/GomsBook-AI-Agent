/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadata;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadataEntry;

/**
 * EPUB OPF 패키지 문서를 XML로 직렬화하는 기본 구현체입니다.
 *
 * <p>다음 요소를 생성합니다.</p>
 *
 * <ol>
 *     <li>{@code package}</li>
 *     <li>{@code metadata}</li>
 *     <li>{@code manifest}</li>
 *     <li>{@code spine}</li>
 * </ol>
 *
 * <p>이 구현체는 외부 XML 라이브러리에 의존하지 않으며,
 * XML 특수문자를 직접 이스케이프하여 문자열을 생성합니다.</p>
 *
 * <p>인스턴스 상태를 가지지 않으므로 여러 생성 요청에서
 * 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubPackageDocumentWriter
        implements EpubPackageDocumentWriter {

    private static final String XML_VERSION = "1.0";

    private static final String PACKAGE_ELEMENT = "package";

    private static final String METADATA_ELEMENT = "metadata";

    private static final String MANIFEST_ELEMENT = "manifest";

    private static final String ITEM_ELEMENT = "item";

    private static final String SPINE_ELEMENT = "spine";

    private static final String ITEMREF_ELEMENT = "itemref";

    /**
     * OPF XML을 직렬화합니다.
     *
     * @param epubPackage EPUB 패키지
     * @param options     EPUB 생성 옵션
     * @return OPF XML 문자열
     * @throws EpubGenerationException 직렬화에 실패한 경우
     */
    @Override
    public String serialize(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(epubPackage, options);

        try {
            SerializationContext context =
                    new SerializationContext(options);

            StringBuilder xml = new StringBuilder(8192);

            writeXmlDeclaration(xml, context);
            writePackageStart(xml, epubPackage, context);
            writeMetadata(xml, epubPackage.getMetadata(), context);
            writeManifest(xml, epubPackage.getManifest(), context);
            writeSpine(xml, epubPackage.getSpine(), context);
            writePackageEnd(xml, context);

            return xml.toString();
        } catch (EpubGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .PACKAGE_DOCUMENT_GENERATION_FAILED,
                    "Failed to serialize the EPUB package document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .PACKAGE_DOCUMENT_GENERATION
                    )
                    .detail(
                            "packagePath",
                            epubPackage.getPackageDocumentPath()
                    )
                    .detail(
                            "epubVersion",
                            epubPackage.getVersion().toString()
                    )
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
     * package 시작 요소를 출력합니다.
     */
    private void writePackageStart(
            StringBuilder xml,
            EpubPackage epubPackage,
            SerializationContext context
    ) {
        context.indent(xml, 0);

        xml.append('<').append(PACKAGE_ELEMENT);

        writeAttribute(
                xml,
                "xmlns",
                EpubPackage.OPF_NAMESPACE
        );

        writeAttribute(
                xml,
                "version",
                epubPackage.getPackageVersion()
        );

        writeAttribute(
                xml,
                "unique-identifier",
                epubPackage.getUniqueIdentifierId()
        );

        if (epubPackage.shouldWriteLanguageAttribute()) {
            writeAttribute(
                    xml,
                    "xml:lang",
                    epubPackage.getLanguage()
            );
        }

        if (epubPackage.shouldWriteDirectionAttribute()) {
            writeAttribute(
                    xml,
                    "dir",
                    epubPackage.getDirection().getOpfValue()
            );
        }

        if (epubPackage.shouldWritePrefixAttribute()) {
            writeAttribute(
                    xml,
                    "prefix",
                    epubPackage.getPrefixAttributeValue()
            );
        }

        xml.append('>');
        context.newLine(xml);
    }

    /**
     * package 종료 요소를 출력합니다.
     */
    private void writePackageEnd(
            StringBuilder xml,
            SerializationContext context
    ) {
        context.indent(xml, 0);
        xml.append("</").append(PACKAGE_ELEMENT).append('>');
        context.newLine(xml);
    }

    /**
     * metadata 요소를 출력합니다.
     */
    private void writeMetadata(
            StringBuilder xml,
            EpubMetadata metadata,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                metadata,
                "EPUB metadata must not be null."
        );

        context.indent(xml, 1);

        xml.append('<').append(METADATA_ELEMENT);

        /*
         * dc 접두사는 Dublin Core 요소를 출력하기 위해 필요합니다.
         */
        writeAttribute(
                xml,
                "xmlns:dc",
                EpubPackage.DC_NAMESPACE
        );

        xml.append('>');
        context.newLine(xml);

        for (EpubMetadataEntry entry : metadata.getEntries()) {
            writeMetadataEntry(xml, entry, context);
        }

        context.indent(xml, 1);
        xml.append("</").append(METADATA_ELEMENT).append('>');
        context.newLine(xml);
    }

    /**
     * 개별 메타데이터 요소를 출력합니다.
     */
    private void writeMetadataEntry(
            StringBuilder xml,
            EpubMetadataEntry entry,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                entry,
                "EPUB metadata entry must not be null."
        );

        validateMetadataEntry(entry);

        context.indent(xml, 2);

        String elementName = entry.getElementName();

        xml.append('<').append(elementName);

        for (Map.Entry<String, String> attribute
                : entry.toXmlAttributes().entrySet()) {

            writeAttribute(
                    xml,
                    attribute.getKey(),
                    attribute.getValue()
            );
        }

        xml.append('>');
        xml.append(escapeText(entry.getValue()));
        xml.append("</").append(elementName).append('>');

        context.newLine(xml);
    }

    /**
     * manifest 요소를 출력합니다.
     */
    private void writeManifest(
            StringBuilder xml,
            EpubManifest manifest,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        context.indent(xml, 1);
        xml.append('<').append(MANIFEST_ELEMENT).append('>');
        context.newLine(xml);

        for (EpubResource resource : manifest.getResources()) {
            writeManifestItem(xml, resource, context);
        }

        context.indent(xml, 1);
        xml.append("</").append(MANIFEST_ELEMENT).append('>');
        context.newLine(xml);
    }

    /**
     * manifest의 개별 item 요소를 출력합니다.
     */
    private void writeManifestItem(
            StringBuilder xml,
            EpubResource resource,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                resource,
                "EPUB manifest resource must not be null."
        );

        validateManifestResource(resource);

        context.indent(xml, 2);

        xml.append('<').append(ITEM_ELEMENT);

        writeAttribute(xml, "id", resource.getId());
        writeAttribute(xml, "href", resource.getHref());
        writeAttribute(xml, "media-type", resource.getMediaType());

        if (!resource.getProperties().isEmpty()) {
            writeAttribute(
                    xml,
                    "properties",
                    resource.getPropertiesValue()
            );
        }

        resource.getFallbackId().ifPresent(value ->
                writeAttribute(xml, "fallback", value)
        );

        resource.getMediaOverlayId().ifPresent(value ->
                writeAttribute(xml, "media-overlay", value)
        );

        xml.append("/>");
        context.newLine(xml);
    }

    /**
     * spine 요소를 출력합니다.
     */
    private void writeSpine(
            StringBuilder xml,
            EpubSpine spine,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                spine,
                "EPUB spine must not be null."
        );

        context.indent(xml, 1);

        xml.append('<').append(SPINE_ELEMENT);

        if (spine.shouldWriteTocAttribute()) {
            spine.getTocId().ifPresent(value ->
                    writeAttribute(xml, "toc", value)
            );
        }

        if (spine.shouldWritePageProgressionDirection()) {
            writeAttribute(
                    xml,
                    "page-progression-direction",
                    spine.getPageProgressionDirection()
                            .getOpfValue()
            );
        }

        xml.append('>');
        context.newLine(xml);

        for (EpubSpineItem item : spine.getItems()) {
            writeSpineItem(xml, item, context);
        }

        context.indent(xml, 1);
        xml.append("</").append(SPINE_ELEMENT).append('>');
        context.newLine(xml);
    }

    /**
     * spine의 개별 itemref 요소를 출력합니다.
     */
    private void writeSpineItem(
            StringBuilder xml,
            EpubSpineItem item,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                item,
                "EPUB spine item must not be null."
        );

        validateSpineItem(item);

        context.indent(xml, 2);

        xml.append('<').append(ITEMREF_ELEMENT);

        item.getId().ifPresent(value ->
                writeAttribute(xml, "id", value)
        );

        writeAttribute(xml, "idref", item.getIdref());

        /*
         * linear="yes"는 기본값이므로 omitDefaultAttributes가
         * 활성화된 경우 생략합니다.
         */
        if (item.shouldWriteLinearAttribute()
                || !context.options().isOmitDefaultAttributes()) {

            writeAttribute(
                    xml,
                    "linear",
                    item.getLinearValue()
            );
        }

        String properties = resolveSpineProperties(
                item,
                context.options()
        );

        if (!properties.isBlank()) {
            writeAttribute(
                    xml,
                    "properties",
                    properties
            );
        }

        xml.append("/>");
        context.newLine(xml);
    }

    /**
     * itemref properties 값을 생성합니다.
     */
    private String resolveSpineProperties(
            EpubSpineItem item,
            EpubGenerationOptions options
    ) {
        Set<String> properties = item.getProperties();

        if (properties.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (String property : properties) {
            appendToken(result, property);

            if (options.isIncludeLegacyPageSpreadProperties()) {
                appendLegacyPageSpreadProperty(
                        result,
                        property
                );
            }
        }

        return result.toString();
    }

    /**
     * EPUB 3 rendition page-spread 속성에 대응하는 구형 속성을 추가합니다.
     */
    private void appendLegacyPageSpreadProperty(
            StringBuilder result,
            String property
    ) {
        if ("rendition:page-spread-left".equals(property)) {
            appendToken(result, "page-spread-left");
            return;
        }

        if ("rendition:page-spread-right".equals(property)) {
            appendToken(result, "page-spread-right");
            return;
        }

        if ("rendition:page-spread-center".equals(property)) {
            appendToken(result, "spread-none");
        }
    }

    private void appendToken(
            StringBuilder result,
            String token
    ) {
        if (token == null || token.isBlank()) {
            return;
        }

        String normalized = token.trim();

        if (containsToken(result, normalized)) {
            return;
        }

        if (result.length() > 0) {
            result.append(' ');
        }

        result.append(normalized);
    }

    private boolean containsToken(
            StringBuilder value,
            String token
    ) {
        if (value.length() == 0) {
            return false;
        }

        String[] tokens = value.toString().split("\\s+");

        for (String existing : tokens) {
            if (existing.equals(token)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 메타데이터 항목의 직렬화 가능 여부를 검증합니다.
     */
    private void validateMetadataEntry(
            EpubMetadataEntry entry
    ) throws EpubGenerationException {

        if (entry.getElementName() == null
                || entry.getElementName().isBlank()) {
            throw packageDocumentException(
                    "EPUB metadata element name must not be blank.",
                    null
            );
        }

        if (entry.getValue() == null
                || entry.getValue().isBlank()) {
            throw packageDocumentException(
                    "EPUB metadata value must not be blank: "
                            + entry.getElementName(),
                    null
            );
        }

        if (entry.isMetaProperty()
                && entry.getProperty().isEmpty()) {
            throw packageDocumentException(
                    "EPUB meta element requires a property attribute.",
                    null
            );
        }
    }

    /**
     * manifest 리소스의 직렬화 가능 여부를 검증합니다.
     */
    private void validateManifestResource(
            EpubResource resource
    ) throws EpubGenerationException {

        if (resource.getId() == null
                || resource.getId().isBlank()) {
            throw packageDocumentException(
                    "EPUB manifest item id must not be blank.",
                    null
            );
        }

        if (resource.getHref() == null
                || resource.getHref().isBlank()) {
            throw packageDocumentException(
                    "EPUB manifest item href must not be blank: "
                            + resource.getId(),
                    null
            );
        }

        if (resource.getMediaType() == null
                || resource.getMediaType().isBlank()) {
            throw packageDocumentException(
                    "EPUB manifest item media-type must not be blank: "
                            + resource.getId(),
                    null
            );
        }
    }

    /**
     * spine 항목의 직렬화 가능 여부를 검증합니다.
     */
    private void validateSpineItem(
            EpubSpineItem item
    ) throws EpubGenerationException {

        if (item.getIdref() == null
                || item.getIdref().isBlank()) {
            throw packageDocumentException(
                    "EPUB spine item idref must not be blank.",
                    null
            );
        }
    }

    /**
     * XML 속성을 출력합니다.
     */
    private static void writeAttribute(
            StringBuilder xml,
            String name,
            String value
    ) {
        if (name == null || name.isBlank()
                || value == null) {
            return;
        }

        xml.append(' ')
                .append(name)
                .append("=\"")
                .append(escapeAttribute(value))
                .append('"');
    }

    /**
     * XML 텍스트 노드 값을 이스케이프합니다.
     */
    private static String escapeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder escaped =
                new StringBuilder(value.length() + 16);

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                default -> appendValidXmlCharacter(
                        escaped,
                        character
                );
            }
        }

        return escaped.toString();
    }

    /**
     * XML 속성값을 이스케이프합니다.
     */
    private static String escapeAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder escaped =
                new StringBuilder(value.length() + 16);

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                case '\t' -> escaped.append("&#x9;");
                case '\n' -> escaped.append("&#xA;");
                case '\r' -> escaped.append("&#xD;");
                default -> appendValidXmlCharacter(
                        escaped,
                        character
                );
            }
        }

        return escaped.toString();
    }

    /**
     * XML 1.0에서 허용되는 문자를 추가합니다.
     *
     * <p>허용되지 않는 제어 문자는 제거하지 않고 예외를 발생시켜
     * 데이터 손실을 방지합니다.</p>
     */
    private static void appendValidXmlCharacter(
            StringBuilder output,
            char character
    ) {
        if (isValidXmlCharacter(character)) {
            output.append(character);
            return;
        }

        throw new IllegalArgumentException(
                "Invalid XML 1.0 character: 0x"
                        + Integer.toHexString(character)
        );
    }

    /**
     * XML 1.0에서 허용되는 BMP 문자인지 확인합니다.
     */
    private static boolean isValidXmlCharacter(char character) {
        return character == 0x9
                || character == 0xA
                || character == 0xD
                || character >= 0x20;
    }

    private EpubGenerationException packageDocumentException(
            String message,
            Throwable cause
    ) {
        EpubGenerationException.Builder builder =
                EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .PACKAGE_DOCUMENT_GENERATION_FAILED,
                        message
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .PACKAGE_DOCUMENT_GENERATION
                        );

        if (cause != null) {
            builder.cause(cause);
        }

        return builder.build();
    }

    /**
     * XML 직렬화에 필요한 옵션을 관리합니다.
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
         * 지정한 깊이만큼 들여쓰기를 출력합니다.
         */
        private void indent(
                StringBuilder xml,
                int depth
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            int count = depth * options.getXmlIndentSize();

            for (int index = 0; index < count; index++) {
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

            xml.append(options.getLineSeparatorValue());
        }
    }
}