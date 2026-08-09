/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssue;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode.AccessibilityCategory;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityLocation;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleContext;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleException;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilitySeverity;

/**
 * XHTML 문서의 제목 요소 구조를 검사한다.
 *
 * <p>다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>문서 내 제목 요소 누락</li>
 *   <li>빈 제목 요소</li>
 *   <li>제목 단계 건너뛰기</li>
 *   <li>복수의 h1 사용</li>
 *   <li>첫 번째 제목이 h1이 아닌 경우</li>
 *   <li>ARIA heading 역할의 aria-level 누락 또는 오류</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 문제 목록만 반환한다.</p>
 */
public final class HeadingAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID = "heading-structure";

    public static final String OPTION_REQUIRE_HEADING =
            "heading.requireHeading";

    public static final String OPTION_REQUIRE_FIRST_LEVEL_ONE =
            "heading.requireFirstLevelOne";

    public static final String OPTION_ALLOW_MULTIPLE_H1 =
            "heading.allowMultipleH1";

    public static final String OPTION_CHECK_ARIA_HEADINGS =
            "heading.checkAriaHeadings";

    public static final String OPTION_EMPTY_HEADING_AS_ERROR =
            "heading.emptyHeadingAsError";

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "제목 구조 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.HEADING;
    }

    @Override
    public AccessibilitySeverity getDefaultSeverity() {
        return AccessibilitySeverity.WARNING;
    }

    @Override
    public boolean supports(
            AccessibilityRuleContext context) {

        return context != null
                && context.getDocument() != null
                && context.isXhtmlDocument();
    }

    @Override
    public boolean supportsAutomaticFix() {
        /*
         * 제목 단계 변경은 문서 의미를 바꿀 수 있으므로
         * 기본적으로 자동 수정하지 않는다.
         */
        return false;
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public List<AccessibilityIssue> validate(
            AccessibilityRuleContext context)
            throws AccessibilityRuleException {

        if (context == null) {
            throw new AccessibilityRuleException(
                    RULE_ID,
                    "AccessibilityRuleContext must not be null."
            );
        }

        try {
            List<AccessibilityIssue> issues =
                    new ArrayList<>();

            List<HeadingEntry> headings =
                    collectHeadings(context);

            validateHeadingPresence(
                    context,
                    headings,
                    issues
            );

            if (headings.isEmpty()) {
                return List.copyOf(issues);
            }

            validateEmptyHeadings(
                    context,
                    headings,
                    issues
            );

            validateFirstHeadingLevel(
                    context,
                    headings,
                    issues
            );

            validateMultiplePrimaryHeadings(
                    context,
                    headings,
                    issues
            );

            validateHeadingLevelSequence(
                    context,
                    headings,
                    issues
            );

            return List.copyOf(issues);

        } catch (AccessibilityRuleException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new AccessibilityRuleException(
                    RULE_ID,
                    context.getDocumentPath(),
                    "Failed to validate heading structure.",
                    exception
            );
        }
    }

    private List<HeadingEntry> collectHeadings(
            AccessibilityRuleContext context) {

        List<HeadingEntry> headings =
                new ArrayList<>();

        Element root =
                context.getDocumentElement();

        if (root == null) {
            return headings;
        }

        boolean checkAriaHeadings =
                context.getBooleanOption(
                        OPTION_CHECK_ARIA_HEADINGS,
                        true
                );

        collectHeadingsRecursively(
                root,
                headings,
                checkAriaHeadings
        );

        return headings;
    }

    private void collectHeadingsRecursively(
            Node node,
            List<HeadingEntry> headings,
            boolean checkAriaHeadings) {

        if (node instanceof Element element) {
            Integer level =
                    resolveNativeHeadingLevel(element);

            boolean ariaHeading = false;

            if (level == null && checkAriaHeadings) {
                level = resolveAriaHeadingLevel(element);
                ariaHeading = level != null;
            }

            if (level != null) {
                headings.add(
                        new HeadingEntry(
                                element,
                                level,
                                ariaHeading
                        )
                );
            }
        }

        NodeList children =
                node.getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            collectHeadingsRecursively(
                    children.item(index),
                    headings,
                    checkAriaHeadings
            );
        }
    }

    private void validateHeadingPresence(
            AccessibilityRuleContext context,
            List<HeadingEntry> headings,
            List<AccessibilityIssue> issues) {

        boolean requireHeading =
                context.getBooleanOption(
                        OPTION_REQUIRE_HEADING,
                        true
                );

        if (!requireHeading || !headings.isEmpty()) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .HEADING_MISSING
                        )
                        .message(
                                "문서에 제목 요소가 없습니다."
                        )
                        .description(
                                "제목 요소는 스크린 리더 사용자가 "
                                        + "문서 구조를 이해하고 빠르게 "
                                        + "이동할 수 있도록 합니다."
                        )
                        .recommendation(
                                "문서의 주요 제목과 하위 구역에 "
                                        + "h1부터 h6까지의 제목 요소를 "
                                        + "논리적인 순서로 사용하십시오."
                        )
                        .location(
                                context.locationBuilder()
                                        .xpath("/")
                                        .build()
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateEmptyHeadings(
            AccessibilityRuleContext context,
            List<HeadingEntry> headings,
            List<AccessibilityIssue> issues) {

        boolean asError =
                context.getBooleanOption(
                        OPTION_EMPTY_HEADING_AS_ERROR,
                        true
                );

        AccessibilitySeverity severity =
                asError
                        ? AccessibilitySeverity.ERROR
                        : AccessibilitySeverity.WARNING;

        for (HeadingEntry heading : headings) {
            String text =
                    normalizeOptionalText(
                            heading.element()
                                    .getTextContent()
                    );

            String ariaLabel =
                    normalizeOptionalText(
                            heading.element()
                                    .getAttribute("aria-label")
                    );

            String ariaLabelledBy =
                    normalizeOptionalText(
                            heading.element()
                                    .getAttribute("aria-labelledby")
                    );

            if (text != null
                    || ariaLabel != null
                    || ariaLabelledBy != null) {

                continue;
            }

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .HEADING_EMPTY
                            )
                            .severity(severity)
                            .message(
                                    "제목 요소가 비어 있습니다."
                            )
                            .description(
                                    "내용이 없는 제목 요소는 문서 구조를 "
                                            + "왜곡하고 스크린 리더 탐색을 "
                                            + "방해할 수 있습니다."
                            )
                            .recommendation(
                                    "제목 내용을 작성하거나 불필요한 "
                                            + "제목 요소를 제거하십시오."
                            )
                            .location(
                                    createHeadingLocation(
                                            context,
                                            heading,
                                            null,
                                            null
                                    )
                            )
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private void validateFirstHeadingLevel(
            AccessibilityRuleContext context,
            List<HeadingEntry> headings,
            List<AccessibilityIssue> issues) {

        boolean requireFirstLevelOne =
                context.getBooleanOption(
                        OPTION_REQUIRE_FIRST_LEVEL_ONE,
                        true
                );

        if (!requireFirstLevelOne || headings.isEmpty()) {
            return;
        }

        HeadingEntry first =
                headings.get(0);

        if (first.level() == 1) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .HEADING_ORDER_INVALID
                        )
                        .message(
                                "문서의 첫 번째 제목이 h1 수준이 아닙니다."
                        )
                        .description(
                                "첫 번째 제목의 현재 수준은 h"
                                        + first.level()
                                        + "입니다. 일반적인 문서 구조에서는 "
                                        + "문서의 대표 제목을 h1으로 제공합니다."
                        )
                        .recommendation(
                                "문서의 대표 제목을 h1으로 지정하고 "
                                        + "이후 제목 단계를 다시 검토하십시오."
                        )
                        .location(
                                createHeadingLocation(
                                        context,
                                        first,
                                        "headingLevel",
                                        Integer.toString(
                                                first.level()
                                        )
                                )
                        )
                        .currentValue(
                                Integer.toString(
                                        first.level()
                                )
                        )
                        .suggestedValue("1")
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateMultiplePrimaryHeadings(
            AccessibilityRuleContext context,
            List<HeadingEntry> headings,
            List<AccessibilityIssue> issues) {

        boolean allowMultipleH1 =
                context.getBooleanOption(
                        OPTION_ALLOW_MULTIPLE_H1,
                        false
                );

        if (allowMultipleH1) {
            return;
        }

        List<HeadingEntry> primaryHeadings =
                headings.stream()
                        .filter(
                                heading -> heading.level() == 1
                        )
                        .toList();

        if (primaryHeadings.size() <= 1) {
            return;
        }

        for (int index = 1;
                index < primaryHeadings.size();
                index++) {

            HeadingEntry heading =
                    primaryHeadings.get(index);

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .MULTIPLE_PRIMARY_HEADINGS
                            )
                            .message(
                                    "문서에 최상위 제목이 여러 개 있습니다."
                            )
                            .description(
                                    "현재 문서에 h1 수준 제목이 "
                                            + primaryHeadings.size()
                                            + "개 있습니다. 단일 장 또는 "
                                            + "단일 문서에서는 대표 제목을 "
                                            + "하나만 두는 것이 일반적입니다."
                            )
                            .recommendation(
                                    "문서의 대표 제목만 h1으로 유지하고 "
                                            + "나머지는 문서 구조에 맞는 "
                                            + "하위 제목 수준으로 조정하십시오."
                            )
                            .location(
                                    createHeadingLocation(
                                            context,
                                            heading,
                                            "headingLevel",
                                            "1"
                                    )
                            )
                            .currentValue("1")
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .metadata(
                                    "primaryHeadingCount",
                                    Integer.toString(
                                            primaryHeadings.size()
                                    )
                            )
                            .build()
            );
        }
    }

    private void validateHeadingLevelSequence(
            AccessibilityRuleContext context,
            List<HeadingEntry> headings,
            List<AccessibilityIssue> issues) {

        HeadingEntry previous = null;

        for (HeadingEntry current : headings) {
            if (previous == null) {
                previous = current;
                continue;
            }

            int difference =
                    current.level()
                            - previous.level();

            /*
             * 상위 단계로 돌아가는 것은 허용한다.
             *
             * h3 → h2
             * h4 → h1
             *
             * 하위 단계로 이동하면서 한 단계 이상 건너뛰는 경우만
             * 문제로 판단한다.
             */
            if (difference <= 1) {
                previous = current;
                continue;
            }

            int suggestedLevel =
                    Math.min(
                            previous.level() + 1,
                            6
                    );

            AccessibilitySeverity severity =
                    context.isStrictMode()
                            ? AccessibilitySeverity.ERROR
                            : AccessibilitySeverity.WARNING;

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .HEADING_LEVEL_SKIPPED
                            )
                            .severity(severity)
                            .message(
                                    "제목 단계가 건너뛰어졌습니다."
                            )
                            .description(
                                    "h"
                                            + previous.level()
                                            + " 다음에 h"
                                            + current.level()
                                            + "이 나타납니다. 제목 수준은 "
                                            + "문서의 논리적 계층을 반영해야 합니다."
                            )
                            .recommendation(
                                    "현재 제목을 h"
                                            + suggestedLevel
                                            + " 수준으로 조정하거나 "
                                            + "누락된 중간 제목 구조를 "
                                            + "추가하십시오."
                            )
                            .location(
                                    createHeadingLocation(
                                            context,
                                            current,
                                            "headingLevel",
                                            Integer.toString(
                                                    current.level()
                                            )
                                    )
                            )
                            .values(
                                    Integer.toString(
                                            current.level()
                                    ),
                                    Integer.toString(
                                            suggestedLevel
                                    )
                            )
                            .relatedValue(
                                    "previousLevel="
                                            + previous.level()
                            )
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );

            previous = current;
        }
    }

    /**
     * h1~h6 요소의 제목 단계를 반환한다.
     */
    private Integer resolveNativeHeadingLevel(
            Element element) {

        String name =
                resolveElementName(element)
                        .toLowerCase(Locale.ROOT);

        if (name.length() != 2
                || name.charAt(0) != 'h') {

            return null;
        }

        char levelCharacter =
                name.charAt(1);

        if (levelCharacter < '1'
                || levelCharacter > '6') {

            return null;
        }

        return levelCharacter - '0';
    }

    /**
     * role="heading" 요소의 aria-level을 반환한다.
     *
     * <p>role이 heading이지만 aria-level이 유효하지 않은 경우에는
     * 별도 문제를 생성하기 위해 가상 수준 0을 반환하지 않고
     * null로 처리한다.</p>
     */
    private Integer resolveAriaHeadingLevel(
            Element element) {

        String role =
                normalizeOptionalText(
                        element.getAttribute("role")
                );

        if (role == null
                || !containsToken(role, "heading")) {

            return null;
        }

        String ariaLevel =
                normalizeOptionalText(
                        element.getAttribute("aria-level")
                );

        if (ariaLevel == null) {
            return null;
        }

        try {
            int level =
                    Integer.parseInt(ariaLevel);

            if (level < 1 || level > 6) {
                return null;
            }

            return level;

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private AccessibilityLocation createHeadingLocation(
            AccessibilityRuleContext context,
            HeadingEntry heading,
            String attributeName,
            String attributeValue) {

        AccessibilityLocation.Builder builder =
                context.locationBuilder(
                                heading.element()
                        )
                        .xpath(
                                createXPath(
                                        heading.element()
                                )
                        )
                        .textExcerpt(
                                createHeadingExcerpt(
                                        heading
                                )
                        )
                        .metadata(
                                "headingLevel",
                                Integer.toString(
                                        heading.level()
                                )
                        )
                        .metadata(
                                "ariaHeading",
                                Boolean.toString(
                                        heading.ariaHeading()
                                )
                        );

        if (attributeName != null) {
            builder.attribute(
                    attributeName,
                    attributeValue
            );
        }

        return builder.build();
    }

    private String createHeadingExcerpt(
            HeadingEntry heading) {

        Element element =
                heading.element();

        StringBuilder result =
                new StringBuilder();

        result.append('<');
        result.append(
                resolveElementName(element)
        );

        String id =
                normalizeOptionalText(
                        element.getAttribute("id")
                );

        if (id != null) {
            result.append(" id=\"");
            result.append(id);
            result.append('"');
        }

        if (heading.ariaHeading()) {
            result.append(" role=\"heading\"");
            result.append(" aria-level=\"");
            result.append(heading.level());
            result.append('"');
        }

        result.append('>');

        String text =
                normalizeOptionalText(
                        element.getTextContent()
                );

        if (text != null) {
            result.append(
                    truncate(text, 120)
            );
        }

        result.append("</");
        result.append(
                resolveElementName(element)
        );
        result.append('>');

        return result.toString();
    }

    private String createXPath(
            Element element) {

        List<String> parts =
                new ArrayList<>();

        Node current = element;

        while (current instanceof Element currentElement) {
            String name =
                    resolveElementName(
                            currentElement
                    );

            String id =
                    normalizeOptionalText(
                            currentElement
                                    .getAttribute("id")
                    );

            if (id != null) {
                parts.add(
                        0,
                        name
                                + "[@id='"
                                + escapeXPathValue(id)
                                + "']"
                );
                break;
            }

            int position =
                    calculateSiblingPosition(
                            currentElement
                    );

            parts.add(
                    0,
                    name
                            + "["
                            + position
                            + "]"
            );

            current =
                    currentElement.getParentNode();
        }

        return parts.isEmpty()
                ? null
                : "/"
                        + String.join(
                                "/",
                                parts
                        );
    }

    private int calculateSiblingPosition(
            Element element) {

        int position = 1;

        Node sibling =
                element.getPreviousSibling();

        while (sibling != null) {
            if (sibling instanceof Element siblingElement
                    && resolveElementName(
                            siblingElement
                    ).equalsIgnoreCase(
                            resolveElementName(
                                    element
                            ))) {

                position++;
            }

            sibling =
                    sibling.getPreviousSibling();
        }

        return position;
    }

    private boolean containsToken(
            String value,
            String expectedToken) {

        if (value == null
                || expectedToken == null) {

            return false;
        }

        for (String token :
                value.trim().split("\\s+")) {

            if (expectedToken.equalsIgnoreCase(
                    token)) {

                return true;
            }
        }

        return false;
    }

    private String resolveElementName(
            Element element) {

        String localName =
                element.getLocalName();

        if (localName != null
                && !localName.isBlank()) {

            return localName;
        }

        String tagName =
                element.getTagName();

        int colonIndex =
                tagName.indexOf(':');

        if (colonIndex >= 0
                && colonIndex + 1
                        < tagName.length()) {

            return tagName.substring(
                    colonIndex + 1
            );
        }

        return tagName;
    }

    private String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private String truncate(
            String value,
            int maximumLength) {

        if (value == null
                || value.length()
                        <= maximumLength) {

            return value;
        }

        return value.substring(
                0,
                maximumLength
        );
    }

    private String escapeXPathValue(
            String value) {

        if (value == null) {
            return "";
        }

        return value.replace(
                "'",
                "&apos;"
        );
    }

    /**
     * 제목 요소와 분석된 제목 수준.
     *
     * @param element 제목 요소
     * @param level 제목 수준
     * @param ariaHeading ARIA heading 여부
     */
    private record HeadingEntry(
            Element element,
            int level,
            boolean ariaHeading) {
    }
}