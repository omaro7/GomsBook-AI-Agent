/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.validation.xhtml;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * XHTML 문서의 기본 문법과 EPUB3 필수 구조를 검증하는 기본 구현체입니다.
 *
 * <p>다음 항목을 검증합니다.</p>
 *
 * <ul>
 *     <li>XML Well-formed 여부</li>
 *     <li>html 루트 요소 존재 여부</li>
 *     <li>XHTML namespace</li>
 *     <li>lang 또는 xml:lang</li>
 *     <li>head 요소</li>
 *     <li>title 요소</li>
 *     <li>body 요소</li>
 *     <li>중복 id</li>
 *     <li>img alt 속성</li>
 * </ul>
 */
public final class DefaultXhtmlValidator
        implements XhtmlValidator {

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    private static final String XML_NAMESPACE =
            XMLConstants.XML_NS_URI;

    private static final String EPUB_NAMESPACE =
            "http://www.idpf.org/2007/ops";

    /**
     * XHTML 문자열을 검증합니다.
     *
     * @param xhtml 검증할 XHTML
     * @return 검증 결과
     */
    @Override
    public XhtmlValidationResult validate(String xhtml) {

        List<String> issues =
                new ArrayList<>();

        if (xhtml == null || xhtml.isBlank()) {
            return XhtmlValidationResult.invalid(
                    "XHTML content must not be blank."
            );
        }

        Document document;

        try {
            document = parse(xhtml);

        } catch (Exception exception) {

            String message =
                    exception.getMessage();

            if (message == null || message.isBlank()) {
                message = exception
                        .getClass()
                        .getSimpleName();
            }

            issues.add(
                    "XHTML XML parsing failed: "
                            + message
            );

            return XhtmlValidationResult.invalid(
                    issues
            );
        }

        Element root =
                document.getDocumentElement();

        validateRootElement(root, issues);

        if (root != null) {
            validateLanguage(root, issues);
            validateHead(root, issues);
            validateBody(root, issues);
            validateDuplicateIds(root, issues);
            validateImages(root, issues);
        }

        if (issues.isEmpty()) {
            return XhtmlValidationResult.validResult();
        }

        return XhtmlValidationResult.invalid(
                issues
        );
    }

    /**
     * XHTML을 XML DOM으로 파싱합니다.
     */
    private Document parse(String xhtml)
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        /*
         * XHTML namespace 검증을 위해 반드시 활성화합니다.
         */
        factory.setNamespaceAware(true);

        /*
         * 외부 엔티티 및 외부 DTD 접근을 차단합니다.
         */
        configureSecureParsing(factory);

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        InputSource source =
                new InputSource(
                        new StringReader(xhtml)
                );

        Document document =
                builder.parse(source);

        document
                .getDocumentElement()
                .normalize();

        return document;
    }

    /**
     * XML Parser의 외부 엔티티 접근을 차단합니다.
     */
    private void configureSecureParsing(
            DocumentBuilderFactory factory) {

        try {
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    false
            );
        } catch (Exception ignored) {
            // Parser 구현에 따라 지원하지 않을 수 있습니다.
        }

        try {
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );
        } catch (Exception ignored) {
        }

        try {
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );
        } catch (Exception ignored) {
        }

        try {
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false
            );
        } catch (Exception ignored) {
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );
        } catch (Exception ignored) {
        }

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );
        } catch (Exception ignored) {
        }

        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
    }

    /**
     * html 루트 요소와 namespace를 검증합니다.
     */
    private void validateRootElement(
            Element root,
            List<String> issues) {

        if (root == null) {
            issues.add(
                    "XHTML document does not contain a root element."
            );

            return;
        }

        String localName =
                resolveLocalName(root);

        if (!"html".equalsIgnoreCase(localName)) {
            issues.add(
                    "The root element must be <html>."
            );
        }

        String namespace =
                root.getNamespaceURI();

        if (!XHTML_NAMESPACE.equals(namespace)) {
            issues.add(
                    "The <html> element must use the XHTML namespace: "
                            + XHTML_NAMESPACE
            );
        }

        /*
         * EPUB namespace는 epub:type 등을 사용하는 문서에서 필요합니다.
         */
        if (usesEpubPrefix(root)
                && !EPUB_NAMESPACE.equals(
                        root.lookupNamespaceURI("epub")
                )) {

            issues.add(
                    "The epub prefix must be bound to: "
                            + EPUB_NAMESPACE
            );
        }
    }

    /**
     * html 요소의 언어 속성을 검증합니다.
     */
    private void validateLanguage(
            Element root,
            List<String> issues) {

        String lang =
                normalize(
                        root.getAttribute("lang")
                );

        String xmlLang =
                normalize(
                        root.getAttributeNS(
                                XML_NAMESPACE,
                                "lang"
                        )
                );

        if (lang == null && xmlLang == null) {
            issues.add(
                    "The <html> element should contain "
                            + "lang or xml:lang."
            );

            return;
        }

        if (lang != null
                && xmlLang != null
                && !lang.equalsIgnoreCase(xmlLang)) {

            issues.add(
                    "lang and xml:lang must have the same value."
            );
        }
    }

    /**
     * head와 title 요소를 검증합니다.
     */
    private void validateHead(
            Element root,
            List<String> issues) {

        Element head =
                findDirectChild(
                        root,
                        "head"
                );

        if (head == null) {
            issues.add(
                    "XHTML document must contain a <head> element."
            );

            return;
        }

        Element title =
                findDirectChild(
                        head,
                        "title"
                );

        if (title == null) {
            issues.add(
                    "The <head> element must contain a <title> element."
            );

            return;
        }

        String titleText =
                normalize(
                        title.getTextContent()
                );

        if (titleText == null) {
            issues.add(
                    "The <title> element must not be empty."
            );
        }
    }

    /**
     * body 요소를 검증합니다.
     */
    private void validateBody(
            Element root,
            List<String> issues) {

        Element body =
                findDirectChild(
                        root,
                        "body"
                );

        if (body == null) {
            issues.add(
                    "XHTML document must contain a <body> element."
            );
        }
    }

    /**
     * 문서 전체의 id 중복을 검증합니다.
     */
    private void validateDuplicateIds(
            Element root,
            List<String> issues) {

        Set<String> ids =
                new HashSet<>();

        Set<String> duplicateIds =
                new HashSet<>();

        collectIds(
                root,
                ids,
                duplicateIds
        );

        for (String duplicateId
                : duplicateIds) {

            issues.add(
                    "Duplicate XHTML id found: "
                            + duplicateId
            );
        }
    }

    private void collectIds(
            Element element,
            Set<String> ids,
            Set<String> duplicateIds) {

        String id =
                normalize(
                        element.getAttribute("id")
                );

        if (id != null
                && !ids.add(id)) {

            duplicateIds.add(id);
        }

        NodeList children =
                element.getChildNodes();

        for (int i = 0;
                i < children.getLength();
                i++) {

            Node child =
                    children.item(i);

            if (child instanceof Element) {
                collectIds(
                        (Element) child,
                        ids,
                        duplicateIds
                );
            }
        }
    }

    /**
     * img 요소의 alt 속성을 검증합니다.
     *
     * <p>장식 이미지의 경우 빈 alt는 허용합니다.
     * 다만 alt 속성 자체가 없는 경우는 문제로 처리합니다.</p>
     */
    private void validateImages(
            Element root,
            List<String> issues) {

        NodeList images =
                root.getElementsByTagNameNS(
                        XHTML_NAMESPACE,
                        "img"
                );

        /*
         * Namespace 없이 파싱된 비정상 XHTML에서도
         * 최소한 검증할 수 있도록 fallback합니다.
         */
        if (images.getLength() == 0) {
            images =
                    root.getElementsByTagName(
                            "img"
                    );
        }

        for (int i = 0;
                i < images.getLength();
                i++) {

            Node node =
                    images.item(i);

            if (!(node instanceof Element)) {
                continue;
            }

            Element image =
                    (Element) node;

            if (!image.hasAttribute("alt")) {
                String src =
                        normalize(
                                image.getAttribute("src")
                        );

                issues.add(
                        "Image element must contain an alt attribute"
                                + (src == null
                                        ? "."
                                        : ": " + src)
                );
            }
        }
    }

    /**
     * 직접 자식 요소를 찾습니다.
     */
    private Element findDirectChild(
            Element parent,
            String name) {

        NodeList children =
                parent.getChildNodes();

        for (int i = 0;
                i < children.getLength();
                i++) {

            Node child =
                    children.item(i);

            if (!(child instanceof Element)) {
                continue;
            }

            Element element =
                    (Element) child;

            String localName =
                    resolveLocalName(element);

            if (name.equalsIgnoreCase(localName)) {
                return element;
            }
        }

        return null;
    }

    /**
     * epub prefix 사용 여부를 검사합니다.
     */
    private boolean usesEpubPrefix(
            Element root) {

        return usesEpubPrefixRecursive(root);
    }

    private boolean usesEpubPrefixRecursive(
            Element element) {

        NamedNodeMap attributes =
                element.getAttributes();

        for (int i = 0;
                i < attributes.getLength();
                i++) {

            Node attribute =
                    attributes.item(i);

            if ("epub".equals(
                    attribute.getPrefix())) {

                return true;
            }
        }

        NodeList children =
                element.getChildNodes();

        for (int i = 0;
                i < children.getLength();
                i++) {

            Node child =
                    children.item(i);

            if (child instanceof Element
                    && usesEpubPrefixRecursive(
                            (Element) child
                    )) {

                return true;
            }
        }

        return false;
    }

    private String resolveLocalName(
            Element element) {

        String localName =
                element.getLocalName();

        if (localName != null
                && !localName.isBlank()) {

            return localName;
        }

        String tagName =
                element.getTagName();

        int separator =
                tagName.indexOf(':');

        if (separator >= 0
                && separator < tagName.length() - 1) {

            return tagName.substring(
                    separator + 1
            );
        }

        return tagName;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}