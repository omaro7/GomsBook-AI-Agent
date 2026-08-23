/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.epub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * 현재 EPUB 프로젝트의 nav.xhtml을 읽고
 * EPUB Navigation Document의 TOC를 반환하는 Tool입니다.
 *
 * <pre>
 * CurrentProjectProvider
 *      ↓
 * EpubProjectContext
 *      ↓
 * OEBPS/Text/nav.xhtml
 *      ↓
 * nav epub:type="toc"
 *      ↓
 * ol / li / a
 *      ↓
 * TOC hierarchy
 * </pre>
 */
public final class ReadEpubNavigationTool
        implements AgentTool {

    public static final String NAME =
            "read_epub_navigation";

    private static final String EPUB_NAMESPACE =
            "http://www.idpf.org/2007/ops";

    private final CurrentProjectProvider projectProvider;


    public ReadEpubNavigationTool(
            CurrentProjectProvider projectProvider) {

        this.projectProvider =
                Objects.requireNonNull(
                        projectProvider,
                        "projectProvider must not be null"
                );
    }


    @Override
    public String getName() {

        return NAME;
    }


    @Override
    public String getDescription() {

        return "Reads nav.xhtml from the current EPUB project "
                + "and returns the table of contents hierarchy, "
                + "including title, href and depth.";
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                Collections.emptyMap()
        );

        schema.put(
                "additionalProperties",
                false
        );

        return Collections.unmodifiableMap(
                schema
        );
    }


    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        try {

            EpubProjectContext project =
                    projectProvider
                            .getCurrentProject();


            Path navFile =
                    resolveNavFile(
                            project
                    );


            validateNavFile(
                    navFile
            );


            Document document =
                    parseNavigationDocument(
                            navFile
                    );


            Element tocNav =
                    findTocNavigation(
                            document
                    );


            if (tocNav == null) {

                return ToolResult
                        .failure(
                                NAME,
                                "nav.xhtml does not contain "
                                        + "a TOC navigation element."
                        )
                        .build();
            }


            Element rootOl =
                    findDirectChild(
                            tocNav,
                            "ol"
                    );


            if (rootOl == null) {

                return ToolResult
                        .failure(
                                NAME,
                                "TOC navigation does not contain "
                                        + "an ordered list."
                        )
                        .build();
            }


            List<Map<String, Object>> entries =
                    parseOl(
                            rootOl,
                            1
                    );


            int totalEntries =
                    countEntries(
                            entries
                    );


            return ToolResult
                    .success(NAME)
                    .message(
                            "EPUB navigation table of contents "
                                    + "was read successfully."
                    )
                    .data(
                            "projectName",
                            project.getProjectName()
                    )
                    .data(
                            "navFile",
                            navFile
                                    .toAbsolutePath()
                                    .normalize()
                                    .toString()
                    )
                    .data(
                            "entryCount",
                            totalEntries
                    )
                    .data(
                            "toc",
                            entries
                    )
                    .build();


        } catch (Exception exception) {

            return ToolResult
                    .failure(
                            NAME,
                            "Failed to read EPUB navigation: "
                                    + safeMessage(
                                            exception
                                    ),
                            exception
                    )
                    .build();
        }
    }


    /**
     * 현재 GomsBook 프로젝트에서 nav.xhtml 위치를 결정합니다.
     *
     * 현재 프로젝트 구조:
     *
     * OEBPS/
     * └─ Text/
     *    └─ nav.xhtml
     */
    private Path resolveNavFile(
            EpubProjectContext project) {

        Path textDirectory =
                Objects.requireNonNull(
                        project.getTextDirectory(),
                        "TEXT directory must not be null"
                );


        return textDirectory
                .resolve(
                        "nav.xhtml"
                )
                .toAbsolutePath()
                .normalize();
    }


    private void validateNavFile(
            Path navFile) {

        if (!Files.exists(
                navFile
        )) {

            throw new IllegalStateException(
                    "nav.xhtml does not exist: "
                            + navFile
            );
        }


        if (!Files.isRegularFile(
                navFile
        )) {

            throw new IllegalStateException(
                    "nav.xhtml is not a regular file: "
                            + navFile
            );
        }
    }


    /**
     * XHTML Navigation Document를 XML DOM으로 읽습니다.
     *
     * EPUB XHTML의 <!DOCTYPE html>은 허용하되
     * 외부 Entity / 외부 DTD 로딩은 비활성화합니다.
     */
    private Document parseNavigationDocument(
            Path navFile)
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory
                        .newInstance();


        factory.setNamespaceAware(
                true
        );


        /*
         * XXE 방지.
         *
         * 이전 XHTML Parser에서
         * disallow-doctype-decl=true를 사용하면
         *
         * <!DOCTYPE html>
         *
         * 때문에 EPUB XHTML 파싱이 실패했으므로
         * DOCTYPE 자체는 금지하지 않습니다.
         */
        setFeatureIfSupported(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        setFeatureIfSupported(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        setFeatureIfSupported(
                factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );


        try {

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

        } catch (IllegalArgumentException ignored) {
            // Parser가 지원하지 않는 경우 무시
        }


        try {

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

        } catch (IllegalArgumentException ignored) {
            // Parser가 지원하지 않는 경우 무시
        }


        DocumentBuilder builder =
                factory.newDocumentBuilder();


        Document document =
                builder.parse(
                        navFile.toFile()
                );


        document
                .getDocumentElement()
                .normalize();


        return document;
    }


    private void setFeatureIfSupported(
            DocumentBuilderFactory factory,
            String feature,
            boolean value) {

        try {

            factory.setFeature(
                    feature,
                    value
            );

        } catch (Exception ignored) {

            /*
             * XML Parser 구현에 따라 일부 Feature가
             * 지원되지 않을 수 있습니다.
             */
        }
    }


    /**
     * epub:type="toc"인 nav 요소를 찾습니다.
     */
    private Element findTocNavigation(
            Document document) {

        NodeList navNodes =
                document.getElementsByTagNameNS(
                        "*",
                        "nav"
                );


        for (int index = 0;
                index < navNodes.getLength();
                index++) {

            Node node =
                    navNodes.item(
                            index
                    );


            if (!(node instanceof Element nav)) {

                continue;
            }


            String epubType =
                    nav.getAttributeNS(
                            EPUB_NAMESPACE,
                            "type"
                    );


            /*
             * Namespace-aware parser에서도 문서 구성에 따라
             * prefix attribute lookup이 필요할 수 있으므로 fallback.
             */
            if (epubType == null
                    || epubType.isBlank()) {

                epubType =
                        nav.getAttribute(
                                "epub:type"
                        );
            }


            if ("toc".equalsIgnoreCase(
                    epubType == null
                            ? ""
                            : epubType.trim()
            )) {

                return nav;
            }
        }


        return null;
    }


    /**
     * ol의 직접 자식 li만 처리합니다.
     */
    private List<Map<String, Object>> parseOl(
            Element ol,
            int depth) {

        List<Map<String, Object>> entries =
                new ArrayList<>();


        NodeList children =
                ol.getChildNodes();


        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node =
                    children.item(
                            index
                    );


            if (!(node instanceof Element element)) {

                continue;
            }


            if (!isElement(
                    element,
                    "li"
            )) {

                continue;
            }


            Map<String, Object> entry =
                    parseLi(
                            element,
                            depth
                    );


            if (!entry.isEmpty()) {

                entries.add(
                        entry
                );
            }
        }


        return List.copyOf(
                entries
        );
    }


    private Map<String, Object> parseLi(
            Element li,
            int depth) {

        Element anchor =
                findDirectChild(
                        li,
                        "a"
                );


        Element span =
                findDirectChild(
                        li,
                        "span"
                );


        Element labelElement =
                anchor != null
                        ? anchor
                        : span;


        if (labelElement == null) {

            return Collections.emptyMap();
        }


        String title =
                normalizeText(
                        labelElement.getTextContent()
                );


        String href =
                anchor == null
                        ? ""
                        : normalizeText(
                                anchor.getAttribute(
                                        "href"
                                )
                        );


        Element nestedOl =
                findDirectChild(
                        li,
                        "ol"
                );


        List<Map<String, Object>> children =
                nestedOl == null
                        ? List.of()
                        : parseOl(
                                nestedOl,
                                depth + 1
                        );


        Map<String, Object> entry =
                new LinkedHashMap<>();


        entry.put(
                "title",
                title
        );


        entry.put(
                "href",
                href
        );


        entry.put(
                "depth",
                depth
        );


        entry.put(
                "children",
                children
        );


        return Collections.unmodifiableMap(
                entry
        );
    }


    private Element findDirectChild(
            Element parent,
            String localName) {

        NodeList children =
                parent.getChildNodes();


        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node =
                    children.item(
                            index
                    );


            if (!(node instanceof Element element)) {

                continue;
            }


            if (isElement(
                    element,
                    localName
            )) {

                return element;
            }
        }


        return null;
    }


    private boolean isElement(
            Element element,
            String name) {

        String localName =
                element.getLocalName();


        if (localName != null) {

            return name.equalsIgnoreCase(
                    localName
            );
        }


        return name.equalsIgnoreCase(
                element.getTagName()
        );
    }


    private int countEntries(
            List<Map<String, Object>> entries) {

        int count = 0;


        for (Map<String, Object> entry
                : entries) {

            count++;


            Object childrenValue =
                    entry.get(
                            "children"
                    );


            if (childrenValue instanceof List<?> children) {

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> childEntries =
                        (List<Map<String, Object>>) children;


                count +=
                        countEntries(
                                childEntries
                        );
            }
        }


        return count;
    }


    private String normalizeText(
            String value) {

        if (value == null) {

            return "";
        }


        return value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {

            return "Unknown error";
        }


        return throwable.getMessage();
    }
}