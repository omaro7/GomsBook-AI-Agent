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
 * XHTML 문서의 표 접근성을 검사한다.
 *
 * <p>다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>표 제목 caption 누락</li>
 *   <li>빈 caption</li>
 *   <li>표 머리글 th 누락</li>
 *   <li>th의 scope 속성 누락 또는 오류</li>
 *   <li>headers 속성의 잘못된 참조</li>
 *   <li>중복된 th id</li>
 *   <li>복합 표 구조 사용자 검토 필요</li>
 *   <li>레이아웃 목적으로 사용된 표</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 접근성 문제 목록만 반환한다.</p>
 */
public final class TableAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID = "table-accessibility";

    public static final String OPTION_REQUIRE_CAPTION =
            "table.requireCaption";

    public static final String OPTION_REQUIRE_SCOPE =
            "table.requireScope";

    public static final String OPTION_CHECK_HEADERS_REFERENCE =
            "table.checkHeadersReference";

    public static final String OPTION_DETECT_LAYOUT_TABLE =
            "table.detectLayoutTable";

    public static final String OPTION_COMPLEX_TABLE_AS_ERROR =
            "table.complexTableAsError";

    public static final String OPTION_REQUIRE_HEADERS_FOR_COMPLEX_TABLE =
            "table.requireHeadersForComplexTable";

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "표 접근성 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.TABLE;
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
         * 단순 th의 scope 보완은 자동화 가능하지만,
         * 표의 의미 구조를 판단해야 하므로 규칙 전체는
         * 자동 수정 불가로 둔다.
         */
        return false;
    }

    @Override
    public int getOrder() {
        return 50;
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

            NodeList tableNodes =
                    findElements(
                            context.getDocumentElement(),
                            "table"
                    );

            for (int index = 0;
                    index < tableNodes.getLength();
                    index++) {

                Node node = tableNodes.item(index);

                if (!(node instanceof Element table)) {
                    continue;
                }

                validateTable(
                        context,
                        table,
                        issues
                );
            }

            return List.copyOf(issues);

        } catch (AccessibilityRuleException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new AccessibilityRuleException(
                    RULE_ID,
                    context.getDocumentPath(),
                    "Failed to validate table accessibility.",
                    exception
            );
        }
    }

    private void validateTable(
            AccessibilityRuleContext context,
            Element table,
            List<AccessibilityIssue> issues) {

        TableStructure structure =
                analyzeStructure(table);

        validateCaption(
                context,
                table,
                structure,
                issues
        );

        validateHeaders(
                context,
                table,
                structure,
                issues
        );

        validateScope(
                context,
                structure,
                issues
        );

        validateHeadersReferences(
                context,
                table,
                structure,
                issues
        );

        validateDuplicateHeaderIds(
                context,
                structure,
                issues
        );

        validateComplexStructure(
                context,
                table,
                structure,
                issues
        );

        validateLayoutUsage(
                context,
                table,
                structure,
                issues
        );
    }

    private void validateCaption(
            AccessibilityRuleContext context,
            Element table,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        boolean requireCaption =
                context.getBooleanOption(
                        OPTION_REQUIRE_CAPTION,
                        true
                );

        if (!requireCaption) {
            return;
        }

        Element caption = structure.caption();

        if (caption == null) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .TABLE_CAPTION_MISSING
                            )
                            .message(
                                    "표에 caption 요소가 없습니다."
                            )
                            .description(
                                    "caption은 표의 목적과 내용을 "
                                            + "스크린 리더 사용자에게 설명합니다."
                            )
                            .recommendation(
                                    "표의 핵심 목적을 설명하는 caption 요소를 "
                                            + "table의 첫 번째 자식으로 추가하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            table,
                                            null,
                                            null
                                    )
                            )
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );

            return;
        }

        String captionText =
                normalizeOptionalText(
                        caption.getTextContent()
                );

        if (captionText != null) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .TABLE_CAPTION_MISSING
                        )
                        .severity(AccessibilitySeverity.WARNING)
                        .message(
                                "표의 caption 요소가 비어 있습니다."
                        )
                        .description(
                                "빈 caption은 표의 목적을 전달하지 못합니다."
                        )
                        .recommendation(
                                "표의 내용을 구체적으로 설명하는 "
                                        + "caption 텍스트를 작성하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        caption,
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

    private void validateHeaders(
            AccessibilityRuleContext context,
            Element table,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        if (!structure.headerCells().isEmpty()) {
            return;
        }

        if (structure.dataCells().isEmpty()) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .TABLE_HEADER_MISSING
                        )
                        .message(
                                "표에 머리글 셀이 없습니다."
                        )
                        .description(
                                "데이터 셀과 행·열 제목의 관계를 제공하려면 "
                                        + "th 요소가 필요합니다."
                        )
                        .recommendation(
                                "행 또는 열 제목에 해당하는 셀을 th로 지정하고 "
                                        + "적절한 scope를 설정하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        table,
                                        null,
                                        null
                                )
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "dataCellCount",
                                Integer.toString(
                                        structure.dataCells().size()
                                )
                        )
                        .build()
        );
    }

    private void validateScope(
            AccessibilityRuleContext context,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        boolean requireScope =
                context.getBooleanOption(
                        OPTION_REQUIRE_SCOPE,
                        true
                );

        if (!requireScope) {
            return;
        }

        for (Element header : structure.headerCells()) {
            String scope =
                    normalizeOptionalText(
                            header.getAttribute("scope")
                    );

            if (scope == null) {
                issues.add(
                        AccessibilityIssue.builder(
                                        AccessibilityIssueCode
                                                .TABLE_HEADER_SCOPE_MISSING
                                )
                                .message(
                                        "표 머리글에 scope 속성이 없습니다."
                                )
                                .description(
                                        "단순한 표에서는 scope를 사용하면 "
                                                + "머리글과 데이터 셀의 관계를 "
                                                + "명확히 전달할 수 있습니다."
                                )
                                .recommendation(
                                        "열 머리글에는 scope=\"col\", "
                                                + "행 머리글에는 scope=\"row\"를 "
                                                + "설정하십시오."
                                )
                                .location(
                                        createLocation(
                                                context,
                                                header,
                                                "scope",
                                                null
                                        )
                                )
                                .ruleId(RULE_ID)
                                .automaticallyFixable(false)
                                .manualReviewRequired(true)
                                .build()
                );

                continue;
            }

            if (isValidScope(scope)) {
                continue;
            }

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .TABLE_HEADER_SCOPE_MISSING
                            )
                            .severity(AccessibilitySeverity.ERROR)
                            .message(
                                    "표 머리글의 scope 값이 올바르지 않습니다."
                            )
                            .description(
                                    "scope=\""
                                            + scope
                                            + "\"은 유효한 값이 아닙니다."
                            )
                            .recommendation(
                                    "row, col, rowgroup 또는 colgroup 중 "
                                            + "표 구조에 맞는 값을 사용하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            header,
                                            "scope",
                                            scope
                                    )
                            )
                            .currentValue(scope)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private void validateHeadersReferences(
            AccessibilityRuleContext context,
            Element table,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_CHECK_HEADERS_REFERENCE,
                        true
                );

        if (!enabled) {
            return;
        }

        for (Element dataCell : structure.dataCells()) {
            String headers =
                    normalizeOptionalText(
                            dataCell.getAttribute("headers")
                    );

            if (headers == null) {
                continue;
            }

            List<String> missingIds =
                    new ArrayList<>();

            for (String headerId
                    : headers.split("\\s+")) {

                if (headerId.isBlank()) {
                    continue;
                }

                Element referenced =
                        findElementById(
                                table,
                                headerId
                        );

                if (referenced == null
                        || !"th".equalsIgnoreCase(
                                resolveElementName(
                                        referenced))) {

                    missingIds.add(headerId);
                }
            }

            if (missingIds.isEmpty()) {
                continue;
            }

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .TABLE_HEADER_REFERENCE_INVALID
                            )
                            .message(
                                    "표 데이터 셀의 headers 참조가 올바르지 않습니다."
                            )
                            .description(
                                    "다음 머리글 id를 찾을 수 없거나 "
                                            + "해당 요소가 th가 아닙니다: "
                                            + String.join(
                                                    ", ",
                                                    missingIds
                                            )
                            )
                            .recommendation(
                                    "headers 속성이 실제 th 요소의 id를 "
                                            + "참조하도록 수정하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            dataCell,
                                            "headers",
                                            headers
                                    )
                            )
                            .currentValue(headers)
                            .relatedValues(missingIds)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private void validateDuplicateHeaderIds(
            AccessibilityRuleContext context,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        List<String> seenIds =
                new ArrayList<>();

        List<String> duplicateIds =
                new ArrayList<>();

        for (Element header : structure.headerCells()) {
            String id =
                    normalizeOptionalText(
                            header.getAttribute("id")
                    );

            if (id == null) {
                continue;
            }

            if (seenIds.contains(id)
                    && !duplicateIds.contains(id)) {

                duplicateIds.add(id);
            } else {
                seenIds.add(id);
            }
        }

        for (String duplicateId : duplicateIds) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .DUPLICATE_ELEMENT_ID
                            )
                            .message(
                                    "표 머리글 id가 중복되었습니다."
                            )
                            .description(
                                    "id=\""
                                            + duplicateId
                                            + "\"인 th 요소가 여러 개 있어 "
                                            + "headers 참조가 모호해질 수 있습니다."
                            )
                            .recommendation(
                                    "각 th 요소에 고유한 id를 지정하십시오."
                            )
                            .location(
                                    context.locationBuilder()
                                            .xpath(
                                                    createXPath(
                                                            structure.table()
                                                    )
                                            )
                                            .metadata(
                                                    "duplicateId",
                                                    duplicateId
                                            )
                                            .build()
                            )
                            .relatedValue(duplicateId)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private void validateComplexStructure(
            AccessibilityRuleContext context,
            Element table,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        if (!structure.complex()) {
            return;
        }

        boolean requireHeaders =
                context.getBooleanOption(
                        OPTION_REQUIRE_HEADERS_FOR_COMPLEX_TABLE,
                        true
                );

        boolean allDataCellsHaveHeaders =
                structure.dataCells()
                        .stream()
                        .allMatch(
                                cell -> normalizeOptionalText(
                                        cell.getAttribute(
                                                "headers"
                                        )
                                ) != null
                        );

        AccessibilitySeverity severity =
                context.getBooleanOption(
                        OPTION_COMPLEX_TABLE_AS_ERROR,
                        false
                )
                        ? AccessibilitySeverity.ERROR
                        : AccessibilitySeverity.WARNING;

        if (requireHeaders
                && allDataCellsHaveHeaders) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .TABLE_STRUCTURE_COMPLEX
                        )
                        .severity(severity)
                        .message(
                                "복합 표 구조에 대한 사용자 검토가 필요합니다."
                        )
                        .description(
                                "rowspan, colspan, 여러 머리글 행 또는 "
                                        + "행·열 그룹이 포함된 복합 표입니다."
                        )
                        .recommendation(
                                "각 데이터 셀의 headers 속성과 "
                                        + "각 머리글의 고유 id를 사용하여 "
                                        + "셀 관계를 명확히 정의하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        table,
                                        null,
                                        null
                                )
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "rowSpanUsed",
                                Boolean.toString(
                                        structure.rowSpanUsed()
                                )
                        )
                        .metadata(
                                "columnSpanUsed",
                                Boolean.toString(
                                        structure.columnSpanUsed()
                                )
                        )
                        .metadata(
                                "headerRowCount",
                                Integer.toString(
                                        structure.headerRowCount()
                                )
                        )
                        .build()
        );
    }

    private void validateLayoutUsage(
            AccessibilityRuleContext context,
            Element table,
            TableStructure structure,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_DETECT_LAYOUT_TABLE,
                        true
                );

        if (!enabled) {
            return;
        }

        boolean explicitPresentation =
                isPresentationRole(
                        normalizeOptionalText(
                                table.getAttribute("role")
                        )
                );

        boolean likelyLayoutTable =
                explicitPresentation
                        || (structure.headerCells().isEmpty()
                        && structure.caption() == null
                        && structure.rowCount() <= 2
                        && structure.columnCount() <= 3
                        && containsMostlyNonTabularContent(table));

        if (!likelyLayoutTable) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .TABLE_LAYOUT_USAGE
                        )
                        .message(
                                "표가 레이아웃 목적으로 사용된 것으로 보입니다."
                        )
                        .description(
                                "레이아웃용 table은 보조기기에 불필요한 "
                                        + "표 구조로 전달될 수 있습니다."
                        )
                        .recommendation(
                                "가능하면 CSS 레이아웃으로 변경하십시오. "
                                        + "반드시 표를 사용해야 한다면 "
                                        + "role=\"presentation\" 적용 여부를 "
                                        + "신중히 검토하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        table,
                                        "role",
                                        normalizeOptionalText(
                                                table.getAttribute(
                                                        "role"
                                                )
                                        )
                                )
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private TableStructure analyzeStructure(
            Element table) {

        Element caption =
                findFirstDirectChild(
                        table,
                        "caption"
                );

        List<Element> rows =
                findDescendantElements(
                        table,
                        "tr"
                );

        List<Element> headerCells =
                findDescendantElements(
                        table,
                        "th"
                );

        List<Element> dataCells =
                findDescendantElements(
                        table,
                        "td"
                );

        boolean rowSpanUsed = false;
        boolean columnSpanUsed = false;

        int maximumColumnCount = 0;
        int headerRowCount = 0;

        for (Element row : rows) {
            List<Element> cells =
                    findDirectChildren(
                            row,
                            "th",
                            "td"
                    );

            int effectiveColumnCount = 0;
            boolean headerRow = false;

            for (Element cell : cells) {
                String name =
                        resolveElementName(cell);

                if ("th".equalsIgnoreCase(name)) {
                    headerRow = true;
                }

                int rowSpan =
                        parsePositiveInteger(
                                cell.getAttribute(
                                        "rowspan"
                                ),
                                1
                        );

                int columnSpan =
                        parsePositiveInteger(
                                cell.getAttribute(
                                        "colspan"
                                ),
                                1
                        );

                if (rowSpan > 1) {
                    rowSpanUsed = true;
                }

                if (columnSpan > 1) {
                    columnSpanUsed = true;
                }

                effectiveColumnCount += columnSpan;
            }

            if (headerRow) {
                headerRowCount++;
            }

            maximumColumnCount =
                    Math.max(
                            maximumColumnCount,
                            effectiveColumnCount
                    );
        }

        boolean containsRowGroup =
                !findDescendantElements(
                        table,
                        "thead"
                ).isEmpty()
                        || !findDescendantElements(
                                table,
                                "tbody"
                        ).isEmpty()
                        || !findDescendantElements(
                                table,
                                "tfoot"
                        ).isEmpty();

        boolean containsColumnGroup =
                !findDescendantElements(
                        table,
                        "colgroup"
                ).isEmpty();

        boolean complex =
                rowSpanUsed
                        || columnSpanUsed
                        || headerRowCount > 1
                        || containsColumnGroup
                        || containsRowGroup
                        && hasBothRowAndColumnHeaders(
                                headerCells
                        );

        return new TableStructure(
                table,
                caption,
                List.copyOf(rows),
                List.copyOf(headerCells),
                List.copyOf(dataCells),
                rows.size(),
                maximumColumnCount,
                headerRowCount,
                rowSpanUsed,
                columnSpanUsed,
                complex
        );
    }

    private boolean hasBothRowAndColumnHeaders(
            List<Element> headerCells) {

        boolean row = false;
        boolean column = false;

        for (Element header : headerCells) {
            String scope =
                    normalizeOptionalText(
                            header.getAttribute(
                                    "scope"
                            )
                    );

            if ("row".equalsIgnoreCase(scope)
                    || "rowgroup".equalsIgnoreCase(scope)) {

                row = true;
            }

            if ("col".equalsIgnoreCase(scope)
                    || "colgroup".equalsIgnoreCase(scope)) {

                column = true;
            }
        }

        return row && column;
    }

    private boolean containsMostlyNonTabularContent(
            Element table) {

        NodeList descendants =
                table.getElementsByTagName("*");

        int images = 0;
        int links = 0;
        int paragraphs = 0;

        for (int index = 0;
                index < descendants.getLength();
                index++) {

            Node node = descendants.item(index);

            if (!(node instanceof Element element)) {
                continue;
            }

            String name =
                    resolveElementName(element)
                            .toLowerCase(Locale.ROOT);

            if ("img".equals(name)) {
                images++;
            } else if ("a".equals(name)) {
                links++;
            } else if ("p".equals(name)
                    || "div".equals(name)) {
                paragraphs++;
            }
        }

        return images + links + paragraphs > 0;
    }

    private boolean isValidScope(
            String scope) {

        if (scope == null) {
            return false;
        }

        return switch (
                scope.toLowerCase(Locale.ROOT)) {

            case "row",
                    "col",
                    "rowgroup",
                    "colgroup" -> true;

            default -> false;
        };
    }

    private boolean isPresentationRole(
            String role) {

        if (role == null) {
            return false;
        }

        return "presentation".equalsIgnoreCase(role)
                || "none".equalsIgnoreCase(role);
    }

    private Element findElementById(
            Element root,
            String id) {

        if (root == null
                || id == null
                || id.isBlank()) {

            return null;
        }

        if (id.equals(
                root.getAttribute("id"))) {

            return root;
        }

        NodeList descendants =
                root.getElementsByTagName("*");

        for (int index = 0;
                index < descendants.getLength();
                index++) {

            Node node = descendants.item(index);

            if (node instanceof Element element
                    && id.equals(
                            element.getAttribute("id"))) {

                return element;
            }
        }

        return null;
    }

    private Element findFirstDirectChild(
            Element parent,
            String expectedName) {

        NodeList children =
                parent.getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node = children.item(index);

            if (node instanceof Element element
                    && expectedName.equalsIgnoreCase(
                            resolveElementName(element))) {

                return element;
            }
        }

        return null;
    }

    private List<Element> findDirectChildren(
            Element parent,
            String... expectedNames) {

        List<Element> result =
                new ArrayList<>();

        NodeList children =
                parent.getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node = children.item(index);

            if (!(node instanceof Element element)) {
                continue;
            }

            String name =
                    resolveElementName(element);

            for (String expectedName : expectedNames) {
                if (expectedName.equalsIgnoreCase(name)) {
                    result.add(element);
                    break;
                }
            }
        }

        return result;
    }

    private List<Element> findDescendantElements(
            Element parent,
            String expectedName) {

        List<Element> result =
                new ArrayList<>();

        NodeList nodes =
                findElements(
                        parent,
                        expectedName
                );

        for (int index = 0;
                index < nodes.getLength();
                index++) {

            Node node = nodes.item(index);

            if (node instanceof Element element) {
                result.add(element);
            }
        }

        return result;
    }

    private NodeList findElements(
            Element root,
            String elementName) {

        NodeList nodes =
                root.getElementsByTagNameNS(
                        XHTML_NAMESPACE,
                        elementName
                );

        if (nodes.getLength() == 0) {
            nodes =
                    root.getElementsByTagName(
                            elementName
                    );
        }

        return nodes;
    }

    private AccessibilityLocation createLocation(
            AccessibilityRuleContext context,
            Element element,
            String attributeName,
            String attributeValue) {

        AccessibilityLocation.Builder builder =
                context.locationBuilder(element)
                        .xpath(
                                createXPath(element)
                        )
                        .textExcerpt(
                                createElementExcerpt(element)
                        );

        if (attributeName != null) {
            builder.attribute(
                    attributeName,
                    attributeValue
            );
        }

        return builder.build();
    }

    private String createElementExcerpt(
            Element element) {

        String name =
                resolveElementName(element);

        StringBuilder result =
                new StringBuilder();

        result.append('<');
        result.append(name);

        appendAttribute(
                result,
                element,
                "id"
        );

        appendAttribute(
                result,
                element,
                "scope"
        );

        appendAttribute(
                result,
                element,
                "headers"
        );

        appendAttribute(
                result,
                element,
                "rowspan"
        );

        appendAttribute(
                result,
                element,
                "colspan"
        );

        appendAttribute(
                result,
                element,
                "role"
        );

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
        result.append(name);
        result.append('>');

        return result.toString();
    }

    private void appendAttribute(
            StringBuilder target,
            Element element,
            String attributeName) {

        if (!element.hasAttribute(attributeName)) {
            return;
        }

        target.append(' ');
        target.append(attributeName);
        target.append("=\"");
        target.append(
                truncate(
                        element.getAttribute(
                                attributeName
                        ),
                        120
                )
        );
        target.append('"');
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

    private int parsePositiveInteger(
            String value,
            int defaultValue) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return defaultValue;
        }

        try {
            int parsed =
                    Integer.parseInt(normalized);

            return parsed > 0
                    ? parsed
                    : defaultValue;

        } catch (NumberFormatException exception) {
            return defaultValue;
        }
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
     * 표 구조 분석 결과.
     */
    private record TableStructure(
            Element table,
            Element caption,
            List<Element> rows,
            List<Element> headerCells,
            List<Element> dataCells,
            int rowCount,
            int columnCount,
            int headerRowCount,
            boolean rowSpanUsed,
            boolean columnSpanUsed,
            boolean complex) {
    }
}