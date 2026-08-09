/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;

/**
 * EPUB 3 Navigation Document(nav.xhtml)를 생성하는 기본 구현체입니다.
 *
 * <p>다음 navigation 영역을 지원합니다.</p>
 *
 * <ul>
 *     <li>toc</li>
 *     <li>landmarks</li>
 *     <li>page-list</li>
 * </ul>
 *
 * <p>{@link EpubNavigationItem}의 href는 OPF 패키지 문서를 기준으로
 * 저장된다고 가정합니다.</p>
 *
 * <p>예를 들어 Navigation Document가 {@code Text/nav.xhtml}이고,
 * Navigation Item의 href가 {@code Text/chapter01.xhtml}이면
 * 실제 nav.xhtml에는 다음과 같이 기록합니다.</p>
 *
 * <pre>
 * {@code
 * <a href="chapter01.xhtml">1장</a>
 * }
 * </pre>
 *
 * <p>이를 통해 잘못된 {@code Text/Text/chapter01.xhtml} 참조가
 * 발생하지 않도록 합니다.</p>
 *
 * <p>이 구현체는 상태를 가지지 않으므로 여러 EPUB 생성 요청에서
 * 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubNavigationDocumentWriter
        implements EpubNavigationDocumentWriter {

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    private static final String EPUB_NAMESPACE =
            "http://www.idpf.org/2007/ops";

    private static final String XML_VERSION = "1.0";

    /**
     * 현재 프로젝트의 기본 Navigation Document manifest href입니다.
     *
     * <p>{@code OEBPS/content.opf} 기준 경로입니다.</p>
     */
    private final String navigationDocumentHref;

    /**
     * 기본 {@code Text/nav.xhtml} 경로를 사용하는 Writer를 생성합니다.
     */
    public DefaultEpubNavigationDocumentWriter() {
        this("Text/nav.xhtml");
    }

    /**
     * Navigation Document의 OPF 기준 href를 지정합니다.
     *
     * @param navigationDocumentHref 예: {@code Text/nav.xhtml}
     */
    public DefaultEpubNavigationDocumentWriter(
            String navigationDocumentHref
    ) {
        this.navigationDocumentHref =
                normalizeRelativeHref(
                        Objects.requireNonNull(
                                navigationDocumentHref,
                                "Navigation document href must not be null."
                        )
                );

        if (!this.navigationDocumentHref
                .toLowerCase(Locale.ROOT)
                .endsWith(".xhtml")) {

            throw new IllegalArgumentException(
                    "EPUB Navigation Document href must end with .xhtml: "
                            + navigationDocumentHref
            );
        }
    }

    /**
     * Navigation 모델을 nav.xhtml 문자열로 직렬화합니다.
     *
     * @param navigation Navigation 모델
     * @param options EPUB 생성 옵션
     * @return nav.xhtml
     * @throws EpubGenerationException 직렬화 실패 시
     */
    @Override
    public String serialize(
            EpubNavigation navigation,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        validate(navigation, options);

        try {
            SerializationContext context =
                    new SerializationContext(options);

            StringBuilder xhtml =
                    new StringBuilder(8192);

            writeXmlDeclaration(
                    xhtml,
                    context
            );

            writeDoctype(
                    xhtml,
                    context
            );

            writeHtmlStart(
                    xhtml,
                    navigation,
                    context
            );

            writeHead(
                    xhtml,
                    navigation,
                    context
            );

            writeBodyStart(
                    xhtml,
                    context
            );

            writeToc(
                    xhtml,
                    navigation,
                    context
            );

            if (navigation.hasLandmarks()) {
                writeLandmarks(
                        xhtml,
                        navigation,
                        context
                );
            }

            if (navigation.hasPageList()) {
                writePageList(
                        xhtml,
                        navigation,
                        context
                );
            }

            writeBodyEnd(
                    xhtml,
                    context
            );

            writeHtmlEnd(
                    xhtml,
                    context
            );

            return xhtml.toString();

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to serialize EPUB Navigation Document."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .epubPath(navigationDocumentHref)
                    .cause(exception)
                    .build();
        }
    }

    /**
     * XML 선언을 출력합니다.
     */
    private void writeXmlDeclaration(
            StringBuilder xhtml,
            SerializationContext context
    ) {
        if (!context.options()
                .isWriteXmlDeclaration()) {
            return;
        }

        xhtml.append("<?xml version=\"")
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

        context.newLine(xhtml);
    }

    /**
     * XHTML5 doctype을 출력합니다.
     */
    private void writeDoctype(
            StringBuilder xhtml,
            SerializationContext context
    ) {
        xhtml.append("<!DOCTYPE html>");

        context.newLine(xhtml);
    }

    /**
     * html 시작 요소를 출력합니다.
     */
    private void writeHtmlStart(
            StringBuilder xhtml,
            EpubNavigation navigation,
            SerializationContext context
    ) {
        context.indent(xhtml, 0);

        xhtml.append("<html");

        writeAttribute(
                xhtml,
                "xmlns",
                XHTML_NAMESPACE
        );

        writeAttribute(
                xhtml,
                "xmlns:epub",
                EPUB_NAMESPACE
        );

        navigation.getLanguage().ifPresent(language -> {
            writeAttribute(
                    xhtml,
                    "lang",
                    language
            );

            writeAttribute(
                    xhtml,
                    "xml:lang",
                    language
            );
        });

        if (navigation.getDirection() != null
                && !navigation.getDirection().isAuto()) {

            writeAttribute(
                    xhtml,
                    "dir",
                    navigation.getDirection().getValue()
            );
        }

        xhtml.append('>');

        context.newLine(xhtml);
    }

    /**
     * html 종료 요소를 출력합니다.
     */
    private void writeHtmlEnd(
            StringBuilder xhtml,
            SerializationContext context
    ) {
        context.indent(xhtml, 0);
        xhtml.append("</html>");
        context.newLine(xhtml);
    }

    /**
     * head 영역을 출력합니다.
     */
    private void writeHead(
            StringBuilder xhtml,
            EpubNavigation navigation,
            SerializationContext context
    ) {
        context.indent(xhtml, 1);
        xhtml.append("<head>");
        context.newLine(xhtml);

        context.indent(xhtml, 2);

        xhtml.append("<meta charset=\"")
                .append(
                        escapeAttribute(
                                context.options()
                                        .getCharset()
                                        .name()
                        )
                )
                .append("\"/>");

        context.newLine(xhtml);

        context.indent(xhtml, 2);

        xhtml.append("<title>")
                .append(
                        escapeText(
                                navigation.getTitle()
                        )
                )
                .append("</title>");

        context.newLine(xhtml);

        context.indent(xhtml, 1);
        xhtml.append("</head>");
        context.newLine(xhtml);
    }

    /**
     * body 시작 요소를 출력합니다.
     */
    private void writeBodyStart(
            StringBuilder xhtml,
            SerializationContext context
    ) {
        context.indent(xhtml, 1);
        xhtml.append("<body>");
        context.newLine(xhtml);
    }

    /**
     * body 종료 요소를 출력합니다.
     */
    private void writeBodyEnd(
            StringBuilder xhtml,
            SerializationContext context
    ) {
        context.indent(xhtml, 1);
        xhtml.append("</body>");
        context.newLine(xhtml);
    }

    /**
     * toc navigation을 출력합니다.
     */
    private void writeToc(
            StringBuilder xhtml,
            EpubNavigation navigation,
            SerializationContext context
    ) throws EpubGenerationException {

        writeNavigationSection(
                xhtml,
                "toc",
                "toc",
                navigation.getTocTitle(),
                navigation.getTocItems(),
                1,
                context
        );
    }

    /**
     * landmarks navigation을 출력합니다.
     */
    private void writeLandmarks(
            StringBuilder xhtml,
            EpubNavigation navigation,
            SerializationContext context
    ) throws EpubGenerationException {

        writeNavigationSection(
                xhtml,
                "landmarks",
                "landmarks",
                navigation.getLandmarksTitle(),
                navigation.getLandmarkItems(),
                2,
                context
        );
    }

    /**
     * page-list navigation을 출력합니다.
     */
    private void writePageList(
            StringBuilder xhtml,
            EpubNavigation navigation,
            SerializationContext context
    ) throws EpubGenerationException {

        writeNavigationSection(
                xhtml,
                "page-list",
                "page-list",
                navigation.getPageListTitle(),
                navigation.getPageListItems(),
                2,
                context
        );
    }

    /**
     * 하나의 nav 영역을 출력합니다.
     */
    private void writeNavigationSection(
            StringBuilder xhtml,
            String epubType,
            String id,
            String title,
            List<EpubNavigationItem> items,
            int headingLevel,
            SerializationContext context
    ) throws EpubGenerationException {

        context.indent(xhtml, 2);

        xhtml.append("<nav");

        writeAttribute(
                xhtml,
                "epub:type",
                epubType
        );

        writeAttribute(
                xhtml,
                "id",
                id
        );

        /*
         * landmark 및 page-list는 일반 독자 화면에서 숨겨도
         * 독서 시스템 접근이 가능하도록 hidden 속성을 사용할 수
         * 있지만, 기본 구현에서는 명시적으로 숨기지 않습니다.
         */

        xhtml.append('>');
        context.newLine(xhtml);

        writeHeading(
                xhtml,
                title,
                headingLevel,
                3,
                context
        );

        context.indent(xhtml, 3);
        xhtml.append("<ol>");
        context.newLine(xhtml);

        for (EpubNavigationItem item : items) {
            writeNavigationItem(
                    xhtml,
                    item,
                    4,
                    context
            );
        }

        context.indent(xhtml, 3);
        xhtml.append("</ol>");
        context.newLine(xhtml);

        context.indent(xhtml, 2);
        xhtml.append("</nav>");
        context.newLine(xhtml);
    }

    /**
     * navigation heading을 출력합니다.
     */
    private void writeHeading(
            StringBuilder xhtml,
            String title,
            int headingLevel,
            int depth,
            SerializationContext context
    ) {
        int normalizedHeadingLevel =
                Math.max(
                        1,
                        Math.min(6, headingLevel)
                );

        context.indent(
                xhtml,
                depth
        );

        xhtml.append("<h")
                .append(normalizedHeadingLevel)
                .append('>');

        xhtml.append(
                escapeText(title)
        );

        xhtml.append("</h")
                .append(normalizedHeadingLevel)
                .append('>');

        context.newLine(xhtml);
    }

    /**
     * 개별 navigation item을 재귀적으로 출력합니다.
     */
    private void writeNavigationItem(
            StringBuilder xhtml,
            EpubNavigationItem item,
            int depth,
            SerializationContext context
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                item,
                "EPUB Navigation Item must not be null."
        );

        if (!item.isIncluded()) {
            return;
        }

        validateNavigationItem(item);

        context.indent(
                xhtml,
                depth
        );

        xhtml.append("<li");

        item.getId().ifPresent(id ->
                writeAttribute(
                        xhtml,
                        "id",
                        id
                )
        );

        xhtml.append('>');

        context.newLine(xhtml);

        context.indent(
                xhtml,
                depth + 1
        );

        xhtml.append("<a");

        writeAttribute(
                xhtml,
                "href",
                resolveNavigationHref(
                        item.getHref()
                )
        );

        item.getEpubType().ifPresent(epubType ->
                writeAttribute(
                        xhtml,
                        "epub:type",
                        epubType
                )
        );

        item.getLanguage().ifPresent(language -> {
            writeAttribute(
                    xhtml,
                    "lang",
                    language
            );

            writeAttribute(
                    xhtml,
                    "xml:lang",
                    language
            );
        });

        item.getDirection().ifPresent(direction -> {
            if (!direction.isAuto()) {
                writeAttribute(
                        xhtml,
                        "dir",
                        direction.getValue()
                );
            }
        });

        xhtml.append('>');

        xhtml.append(
                escapeText(
                        item.getLabel()
                )
        );

        xhtml.append("</a>");

        context.newLine(xhtml);

        if (item.hasChildren()) {
            writeChildItems(
                    xhtml,
                    item.getChildren(),
                    depth + 1,
                    context
            );
        }

        context.indent(
                xhtml,
                depth
        );

        xhtml.append("</li>");

        context.newLine(xhtml);
    }

    /**
     * 하위 navigation item 목록을 출력합니다.
     */
    private void writeChildItems(
            StringBuilder xhtml,
            List<EpubNavigationItem> children,
            int depth,
            SerializationContext context
    ) throws EpubGenerationException {

        boolean hasIncludedChild =
                children.stream()
                        .anyMatch(
                                EpubNavigationItem::isIncluded
                        );

        if (!hasIncludedChild) {
            return;
        }

        context.indent(
                xhtml,
                depth
        );

        xhtml.append("<ol>");

        context.newLine(xhtml);

        for (EpubNavigationItem child : children) {
            writeNavigationItem(
                    xhtml,
                    child,
                    depth + 1,
                    context
            );
        }

        context.indent(
                xhtml,
                depth
        );

        xhtml.append("</ol>");

        context.newLine(xhtml);
    }

    /**
     * OPF 기준 href를 nav.xhtml 기준 상대경로로 변환합니다.
     *
     * <p>예:</p>
     *
     * <pre>
     * Navigation: Text/nav.xhtml
     * Target:     Text/chapter01.xhtml
     *
     * Result:     chapter01.xhtml
     * </pre>
     *
     * <pre>
     * Navigation: Text/nav.xhtml
     * Target:     Images/cover.xhtml
     *
     * Result:     ../Images/cover.xhtml
     * </pre>
     */
    private String resolveNavigationHref(
            String targetHref
    ) throws EpubGenerationException {

        String normalizedTarget =
                normalizeRelativeHref(targetHref);

        String targetPath =
                stripQueryAndFragment(
                        normalizedTarget
                );

        String suffix =
                extractQueryAndFragment(
                        normalizedTarget
                );

        try {
            Path navigationPath =
                    Path.of(
                            navigationDocumentHref
                                    .replace('/', java.io.File.separatorChar)
                    );

            Path navigationDirectory =
                    navigationPath.getParent();

            Path target =
                    Path.of(
                            targetPath
                                    .replace('/', java.io.File.separatorChar)
                    );

            if (navigationDirectory == null) {
                return normalizeRelativeHref(
                        targetPath + suffix
                );
            }

            String relative =
                    navigationDirectory
                            .normalize()
                            .relativize(
                                    target.normalize()
                            )
                            .toString()
                            .replace('\\', '/');

            if (relative.isBlank()) {
                relative =
                        target.getFileName()
                                .toString()
                                .replace('\\', '/');
            }

            return relative + suffix;

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to resolve Navigation Document href: "
                            + targetHref
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .epubPath(targetHref)
                    .detail(
                            "navigationDocumentHref",
                            navigationDocumentHref
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * navigation item 직렬화 가능 여부를 검증합니다.
     */
    private void validateNavigationItem(
            EpubNavigationItem item
    ) throws EpubGenerationException {

        if (item.getLabel() == null
                || item.getLabel().isBlank()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "EPUB navigation label must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        if (item.getHref() == null
                || item.getHref().isBlank()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "EPUB navigation href must not be blank: "
                            + item.getLabel()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }
    }

    /**
     * XML 속성을 기록합니다.
     */
    private static void writeAttribute(
            StringBuilder xhtml,
            String name,
            String value
    ) {
        if (name == null
                || name.isBlank()
                || value == null) {
            return;
        }

        xhtml.append(' ')
                .append(name)
                .append("=\"")
                .append(
                        escapeAttribute(value)
                )
                .append('"');
    }

    /**
     * XHTML 텍스트를 XML 이스케이프합니다.
     */
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

    /**
     * XHTML 속성값을 XML 이스케이프합니다.
     */
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
     * href를 EPUB 내부 상대경로 형태로 정규화합니다.
     */
    private static String normalizeRelativeHref(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    "EPUB href must not be blank."
            );
        }

        String normalized =
                value.trim()
                        .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized =
                    normalized.substring(2);
        }

        while (normalized.contains("//")) {
            normalized =
                    normalized.replace("//", "/");
        }

        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException(
                    "EPUB href must be relative: "
                            + value
            );
        }

        return normalized;
    }

    /**
     * query와 fragment를 제외한 경로를 반환합니다.
     */
    private static String stripQueryAndFragment(
            String href
    ) {
        int queryIndex =
                href.indexOf('?');

        int fragmentIndex =
                href.indexOf('#');

        int index;

        if (queryIndex < 0) {
            index = fragmentIndex;

        } else if (fragmentIndex < 0) {
            index = queryIndex;

        } else {
            index = Math.min(
                    queryIndex,
                    fragmentIndex
            );
        }

        return index < 0
                ? href
                : href.substring(0, index);
    }

    /**
     * query 및 fragment 부분을 그대로 반환합니다.
     */
    private static String extractQueryAndFragment(
            String href
    ) {
        int queryIndex =
                href.indexOf('?');

        int fragmentIndex =
                href.indexOf('#');

        int index;

        if (queryIndex < 0) {
            index = fragmentIndex;

        } else if (fragmentIndex < 0) {
            index = queryIndex;

        } else {
            index = Math.min(
                    queryIndex,
                    fragmentIndex
            );
        }

        return index < 0
                ? ""
                : href.substring(index);
    }

    /**
     * 현재 Writer가 사용하는 Navigation Document href를 반환합니다.
     *
     * @return OPF 기준 nav.xhtml href
     */
    public String getNavigationDocumentHref() {
        return navigationDocumentHref;
    }

    /**
     * 직렬화 옵션 컨텍스트입니다.
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
                StringBuilder xhtml,
                int depth
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            int count =
                    depth
                            * options
                                    .getXmlIndentSize();

            for (int index = 0;
                    index < count;
                    index++) {

                xhtml.append(' ');
            }
        }

        private void newLine(
                StringBuilder xhtml
        ) {
            if (!options.isPrettyPrintXml()) {
                return;
            }

            xhtml.append(
                    options
                            .getLineSeparatorValue()
            );
        }
    }
}