/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 등록된 접근성 규칙을 실행하는 기본
 * {@link AccessibilityValidator} 구현체.
 *
 * <p>검사 대상 XML/XHTML 문서를 안전하게 파싱한 뒤
 * {@link AccessibilityRuleContext}를 생성하고, 활성화된 규칙을
 * 실행하여 {@link AccessibilityValidationResult}로 통합한다.</p>
 *
 * <p>이 클래스는 접근성 문제를 검사하기만 하며 원본 문서를
 * 수정하거나 저장하지 않는다.</p>
 */
public final class DefaultAccessibilityValidator
        implements AccessibilityValidator {

    private static final Set<AccessibilityDocumentType>
            SUPPORTED_DOCUMENT_TYPES = Set.of(
                    AccessibilityDocumentType.XHTML,
                    AccessibilityDocumentType.HTML,
                    AccessibilityDocumentType.OPF,
                    AccessibilityDocumentType.NAVIGATION,
                    AccessibilityDocumentType.SVG,
                    AccessibilityDocumentType.XML
            );

    private static final Comparator<AccessibilityRule> RULE_ORDER =
            Comparator
                    .comparingInt(AccessibilityRule::getOrder)
                    .thenComparing(AccessibilityRule::getId);

    private final List<AccessibilityRule> rules;
    private final boolean namespaceAware;

    /**
     * 규칙 목록으로 Validator를 생성한다.
     *
     * @param rules 실행할 접근성 규칙
     */
    public DefaultAccessibilityValidator(
            List<? extends AccessibilityRule> rules) {

        this(rules, true);
    }

    /**
     * 규칙 목록과 namespace 처리 옵션으로 Validator를 생성한다.
     *
     * @param rules 실행할 접근성 규칙
     * @param namespaceAware XML namespace 인식 여부
     */
    public DefaultAccessibilityValidator(
            List<? extends AccessibilityRule> rules,
            boolean namespaceAware) {

        this.rules = normalizeRules(rules);
        this.namespaceAware = namespaceAware;
    }

    @Override
    public AccessibilityValidationResult validate(
            AccessibilityValidationRequest request)
            throws AccessibilityValidationException {

        Instant startedAt = Instant.now();

        validateRequest(request);
        validateDocumentFile(request);

        Document document;

        try {
            document = parseDocument(
                    request.getDocumentPath()
            );
        } catch (ParserConfigurationException
                | SAXException
                | IOException exception) {

            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .DOCUMENT_PARSE_FAILED,
                    request.getDocumentPath(),
                    null,
                    "Failed to parse accessibility validation document.",
                    exception
            );
        }

        String documentLanguage =
                resolveDocumentLanguage(document);

        String documentTitle =
                resolveDocumentTitle(document);

        AccessibilityDocumentType documentType =
                resolveDocumentType(
                        request,
                        document
                );

        AccessibilityRuleContext context =
                createRuleContext(
                        request,
                        document,
                        documentType,
                        documentLanguage,
                        documentTitle
                );

        List<AccessibilityIssue> issues =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        boolean validationCompleted = true;

        for (AccessibilityRule rule : rules) {

            if (!request.isRuleEnabled(rule)) {
                continue;
            }

            if (!rule.supports(context)) {
                continue;
            }

            try {
                List<AccessibilityIssue> ruleIssues =
                        rule.validateIfSupported(context);

                addIssues(
                        issues,
                        ruleIssues,
                        request
                );

            } catch (AccessibilityRuleException exception) {

                if (!request.isContinueOnRuleError()) {
                    throw new AccessibilityValidationException(
                            AccessibilityValidationErrorCode
                                    .RULE_EXECUTION_FAILED,
                            request.getDocumentPath(),
                            rule.getId(),
                            createRuleFailureMessage(rule),
                            exception
                    );
                }

                validationCompleted = false;

                warnings.add(
                        createRuleWarning(
                                rule,
                                exception
                        )
                );

                issues.add(
                        createRuleFailureIssue(
                                context,
                                rule,
                                exception
                        )
                );

            } catch (RuntimeException exception) {

                if (!request.isContinueOnRuleError()) {
                    throw new AccessibilityValidationException(
                            AccessibilityValidationErrorCode
                                    .RULE_EXECUTION_FAILED,
                            request.getDocumentPath(),
                            rule.getId(),
                            createRuleFailureMessage(rule),
                            exception
                    );
                }

                validationCompleted = false;

                warnings.add(
                        createRuleWarning(
                                rule,
                                exception
                        )
                );

                issues.add(
                        createRuleFailureIssue(
                                context,
                                rule,
                                exception
                        )
                );
            }
        }

        Instant completedAt = Instant.now();

        return AccessibilityValidationResult
                .builder(
                        request.getProjectRoot(),
                        request.getDocumentPath()
                )
                .validationCompleted(
                        validationCompleted
                )
                .issues(issues)
                .warnings(warnings)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .validatorName(getName())
                .metadata(request.getMetadata())
                .metadata(
                        "documentType",
                        documentType.name()
                )
                .metadata(
                        "executedRuleCount",
                        Integer.toString(
                                countExecutableRules(
                                        request,
                                        context
                                )
                        )
                )
                .metadata(
                        "registeredRuleCount",
                        Integer.toString(
                                rules.size()
                        )
                )
                .build();
    }

    @Override
    public boolean supports(
            AccessibilityValidationRequest request) {

        if (request == null
                || request.getProjectRoot() == null
                || request.getDocumentPath() == null) {

            return false;
        }

        AccessibilityDocumentType type =
                request.getDocumentType();

        if (type == null
                || type == AccessibilityDocumentType.UNKNOWN) {

            type = AccessibilityDocumentType.fromPath(
                    request.getDocumentPath()
            );
        }

        return SUPPORTED_DOCUMENT_TYPES.contains(type);
    }

    @Override
    public List<AccessibilityRule> getRules() {
        return rules;
    }

    @Override
    public String getName() {
        return "DefaultAccessibilityValidator";
    }

    /**
     * 등록된 규칙 중 지정한 ID의 규칙을 반환한다.
     *
     * @param ruleId 규칙 ID
     * @return 규칙
     */
    public Optional<AccessibilityRule> findRule(
            String ruleId) {

        if (ruleId == null || ruleId.isBlank()) {
            return Optional.empty();
        }

        String normalized = ruleId.trim();

        return rules.stream()
                .filter(
                        rule -> normalized.equals(
                                rule.getId()
                        )
                )
                .findFirst();
    }

    private void validateRequest(
            AccessibilityValidationRequest request) {

        if (request == null) {
            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .INVALID_REQUEST,
                    "Accessibility validation request must not be null."
            );
        }

        if (!request.getDocumentPath()
                .startsWith(request.getProjectRoot())) {

            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .INVALID_REQUEST,
                    request.getDocumentPath(),
                    null,
                    "Validation document must be inside projectRoot."
            );
        }

        if (!supports(request)) {
            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .UNSUPPORTED_DOCUMENT_TYPE,
                    request.getDocumentPath(),
                    null,
                    "Unsupported accessibility document type: "
                            + request.getDocumentType()
            );
        }

        validateRequestedRules(request);
    }

    private void validateRequestedRules(
            AccessibilityValidationRequest request) {

        for (String ruleId
                : request.getEnabledRuleIds()) {

            if (!hasRule(ruleId)) {
                throw new AccessibilityValidationException(
                        AccessibilityValidationErrorCode
                                .INVALID_REQUEST,
                        request.getDocumentPath(),
                        ruleId,
                        "Enabled accessibility rule is not registered: "
                                + ruleId
                );
            }
        }

        for (String ruleId
                : request.getDisabledRuleIds()) {

            if (!hasRule(ruleId)) {
                throw new AccessibilityValidationException(
                        AccessibilityValidationErrorCode
                                .INVALID_REQUEST,
                        request.getDocumentPath(),
                        ruleId,
                        "Disabled accessibility rule is not registered: "
                                + ruleId
                );
            }
        }
    }

    private void validateDocumentFile(
            AccessibilityValidationRequest request) {

        Path documentPath =
                request.getDocumentPath();

        if (!Files.exists(documentPath)) {
            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .DOCUMENT_NOT_FOUND,
                    documentPath,
                    null,
                    "Accessibility validation document does not exist."
            );
        }

        if (!Files.isRegularFile(documentPath)) {
            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .INVALID_REQUEST,
                    documentPath,
                    null,
                    "Accessibility validation path is not a regular file."
            );
        }

        if (!Files.isReadable(documentPath)) {
            throw new AccessibilityValidationException(
                    AccessibilityValidationErrorCode
                            .DOCUMENT_NOT_READABLE,
                    documentPath,
                    null,
                    "Accessibility validation document is not readable."
            );
        }
    }

    private Document parseDocument(
            Path documentPath)
            throws ParserConfigurationException,
            SAXException,
            IOException {

        DocumentBuilderFactory factory =
                createDocumentBuilderFactory();

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        Document document =
                builder.parse(documentPath.toFile());

        document.normalizeDocument();

        return document;
    }

    private DocumentBuilderFactory
            createDocumentBuilderFactory()
            throws ParserConfigurationException {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(namespaceAware);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        /*
         * EPUB XHTML은 DOCTYPE을 포함할 수 있으므로 선언 자체는
         * 허용하되 외부 DTD와 외부 엔티티 로딩은 차단한다.
         */
        setFeature(
                factory,
                "http://apache.org/xml/features/"
                        + "disallow-doctype-decl",
                false
        );

        setFeature(
                factory,
                "http://xml.org/sax/features/"
                        + "external-general-entities",
                false
        );

        setFeature(
                factory,
                "http://xml.org/sax/features/"
                        + "external-parameter-entities",
                false
        );

        setFeature(
                factory,
                "http://apache.org/xml/features/"
                        + "nonvalidating/load-external-dtd",
                false
        );

        setAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        setAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );

        return factory;
    }

    private void setFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value)
            throws ParserConfigurationException {

        factory.setFeature(feature, value);
    }

    private void setAttributeIfSupported(
            DocumentBuilderFactory factory,
            String name,
            String value) {

        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
            /*
             * 일부 XML parser 구현은 해당 JAXP 속성을 지원하지
             * 않을 수 있다.
             */
        }
    }

    private AccessibilityRuleContext createRuleContext(
            AccessibilityValidationRequest request,
            Document document,
            AccessibilityDocumentType documentType,
            String documentLanguage,
            String documentTitle) {

        return AccessibilityRuleContext.builder(
                        request.getProjectRoot(),
                        request.getDocumentPath(),
                        document
                )
                .projectRelativePath(
                        request.getProjectRelativePath()
                )
                .documentType(documentType)
                .documentLanguage(documentLanguage)
                .documentTitle(documentTitle)
                .strictMode(request.isStrictMode())
                .includeInformationalIssues(
                        request.isIncludeInformationalIssues()
                )
                .maximumAltTextLength(
                        request.getMaximumAltTextLength()
                )
                .options(request.getOptions())
                .metadata(request.getMetadata())
                .build();
    }

    private AccessibilityDocumentType resolveDocumentType(
            AccessibilityValidationRequest request,
            Document document) {

        AccessibilityDocumentType requestedType =
                request.getDocumentType();

        if (requestedType
                == AccessibilityDocumentType.NAVIGATION) {

            return requestedType;
        }

        AccessibilityDocumentType pathType =
                AccessibilityDocumentType.fromPath(
                        request.getDocumentPath()
                );

        if ((pathType == AccessibilityDocumentType.XHTML
                || pathType == AccessibilityDocumentType.HTML)
                && containsTocNavigation(document)) {

            return AccessibilityDocumentType.NAVIGATION;
        }

        if (requestedType != null
                && requestedType
                        != AccessibilityDocumentType.UNKNOWN) {

            return requestedType;
        }

        return pathType;
    }

    private boolean containsTocNavigation(
            Document document) {

        if (document == null) {
            return false;
        }

        NodeList navigationElements =
                document.getElementsByTagNameNS(
                        "http://www.w3.org/1999/xhtml",
                        "nav"
                );

        if (navigationElements.getLength() == 0) {
            navigationElements =
                    document.getElementsByTagName("nav");
        }

        for (int index = 0;
                index < navigationElements.getLength();
                index++) {

            if (!(navigationElements.item(index)
                    instanceof Element element)) {

                continue;
            }

            String epubType =
                    element.getAttributeNS(
                            "http://www.idpf.org/2007/ops",
                            "type"
                    );

            if (epubType == null
                    || epubType.isBlank()) {

                epubType =
                        element.getAttribute("epub:type");
            }

            if (containsToken(epubType, "toc")) {
                return true;
            }
        }

        return false;
    }

    private String resolveDocumentLanguage(
            Document document) {

        Element root =
                document == null
                        ? null
                        : document.getDocumentElement();

        if (root == null) {
            return null;
        }

        String language =
                normalizeOptionalText(
                        root.getAttribute("lang")
                );

        if (language != null) {
            return language;
        }

        return normalizeOptionalText(
                root.getAttributeNS(
                        "http://www.w3.org/XML/1998/namespace",
                        "lang"
                )
        );
    }

    private String resolveDocumentTitle(
            Document document) {

        if (document == null) {
            return null;
        }

        NodeList titleElements =
                document.getElementsByTagNameNS(
                        "http://www.w3.org/1999/xhtml",
                        "title"
                );

        if (titleElements.getLength() == 0) {
            titleElements =
                    document.getElementsByTagName("title");
        }

        if (titleElements.getLength() == 0) {
            return null;
        }

        return normalizeOptionalText(
                titleElements.item(0)
                        .getTextContent()
        );
    }

    private void addIssues(
            List<AccessibilityIssue> target,
            List<AccessibilityIssue> source,
            AccessibilityValidationRequest request) {

        if (source == null || source.isEmpty()) {
            return;
        }

        for (AccessibilityIssue issue : source) {

            if (issue == null) {
                continue;
            }

            if (!request.isIncludeInformationalIssues()
                    && issue.getSeverity()
                            == AccessibilitySeverity.INFO) {

                continue;
            }

            target.add(issue);
        }
    }

    private AccessibilityIssue createRuleFailureIssue(
            AccessibilityRuleContext context,
            AccessibilityRule rule,
            RuntimeException exception) {

        return AccessibilityIssue.builder(
                        AccessibilityIssueCode
                                .VALIDATION_FAILED
                )
                .severity(AccessibilitySeverity.ERROR)
                .message(
                        "접근성 검사 규칙 실행에 실패했습니다."
                )
                .description(
                        createRuleWarning(
                                rule,
                                exception
                        )
                )
                .recommendation(
                        "규칙 구현과 대상 XHTML 문서 상태를 "
                                + "확인하십시오."
                )
                .location(
                        context.locationBuilder()
                                .build()
                )
                .automaticallyFixable(false)
                .manualReviewRequired(true)
                .ruleId(rule.getId())
                .metadata(
                        "ruleName",
                        rule.getDisplayName()
                )
                .metadata(
                        "exceptionType",
                        exception.getClass()
                                .getName()
                )
                .build();
    }

    private String createRuleFailureMessage(
            AccessibilityRule rule) {

        return "Accessibility rule execution failed: "
                + rule.getId();
    }

    private String createRuleWarning(
            AccessibilityRule rule,
            Throwable exception) {

        String message =
                exception == null
                        ? null
                        : normalizeOptionalText(
                                exception.getMessage()
                        );

        return "규칙 "
                + rule.getId()
                + " 실행 실패"
                + (message == null
                        ? ""
                        : ": " + message);
    }

    private int countExecutableRules(
            AccessibilityValidationRequest request,
            AccessibilityRuleContext context) {

        int count = 0;

        for (AccessibilityRule rule : rules) {
            if (request.isRuleEnabled(rule)
                    && rule.supports(context)) {

                count++;
            }
        }

        return count;
    }

    private static List<AccessibilityRule> normalizeRules(
            List<? extends AccessibilityRule> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<AccessibilityRule> result =
                new ArrayList<>();

        Set<String> ruleIds =
                new LinkedHashSet<>();

        for (AccessibilityRule rule : source) {

            if (rule == null) {
                continue;
            }

            String ruleId =
                    normalizeOptionalText(
                            rule.getId()
                    );

            if (ruleId == null) {
                throw new IllegalArgumentException(
                        "Accessibility rule ID must not be blank"
                );
            }

            if (!ruleIds.add(ruleId)) {
                throw new IllegalArgumentException(
                        "Duplicate accessibility rule ID: "
                                + ruleId
                );
            }

            result.add(rule);
        }

        result.sort(RULE_ORDER);

        return Collections.unmodifiableList(result);
    }

    private static boolean containsToken(
            String value,
            String expectedToken) {

        if (value == null
                || value.isBlank()
                || expectedToken == null
                || expectedToken.isBlank()) {

            return false;
        }

        String expected =
                expectedToken
                        .trim()
                        .toLowerCase(Locale.ROOT);

        for (String token : value.trim().split("\\s+")) {
            if (expected.equals(
                    token.toLowerCase(Locale.ROOT))) {

                return true;
            }
        }

        return false;
    }

    private static String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}