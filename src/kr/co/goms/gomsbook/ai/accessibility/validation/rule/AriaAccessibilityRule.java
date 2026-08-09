/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * XHTML 문서의 ARIA 속성과 역할을 검사한다.
 *
 * <p>다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>유효하지 않은 role 값</li>
 *   <li>비어 있는 aria-label</li>
 *   <li>aria-labelledby 및 aria-describedby 참조 오류</li>
 *   <li>aria-hidden 요소 내부의 포커스 가능 요소</li>
 *   <li>유효하지 않은 aria-hidden 값</li>
 *   <li>role="heading"의 aria-level 누락 및 오류</li>
 *   <li>role="presentation" 요소의 접근 가능한 이름 충돌</li>
 *   <li>불필요한 ARIA 역할</li>
 *   <li>중복된 요소 id</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 문제 목록만 반환한다.</p>
 */
public final class AriaAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID =
            "aria-accessibility";

    public static final String OPTION_CHECK_REDUNDANT_ROLES =
            "aria.checkRedundantRoles";

    public static final String OPTION_CHECK_DUPLICATE_IDS =
            "aria.checkDuplicateIds";

    public static final String OPTION_CHECK_HIDDEN_FOCUSABLE =
            "aria.checkHiddenFocusable";

    public static final String OPTION_ALLOW_EMPTY_ARIA_LABEL =
            "aria.allowEmptyAriaLabel";

    public static final String OPTION_STRICT_ROLE_VALIDATION =
            "aria.strictRoleValidation";

    private static final Set<String> VALID_ROLES = Set.of(
            "alert",
            "alertdialog",
            "application",
            "article",
            "banner",
            "button",
            "cell",
            "checkbox",
            "columnheader",
            "combobox",
            "complementary",
            "contentinfo",
            "definition",
            "dialog",
            "directory",
            "document",
            "feed",
            "figure",
            "form",
            "grid",
            "gridcell",
            "group",
            "heading",
            "img",
            "link",
            "list",
            "listbox",
            "listitem",
            "log",
            "main",
            "marquee",
            "math",
            "menu",
            "menubar",
            "menuitem",
            "menuitemcheckbox",
            "menuitemradio",
            "meter",
            "navigation",
            "none",
            "note",
            "option",
            "presentation",
            "progressbar",
            "radio",
            "radiogroup",
            "region",
            "row",
            "rowgroup",
            "rowheader",
            "scrollbar",
            "search",
            "searchbox",
            "separator",
            "slider",
            "spinbutton",
            "status",
            "switch",
            "tab",
            "table",
            "tablist",
            "tabpanel",
            "term",
            "textbox",
            "timer",
            "toolbar",
            "tooltip",
            "tree",
            "treegrid",
            "treeitem"
    );

    private static final Set<String> BOOLEAN_ARIA_VALUES =
            Set.of("true", "false");

    private static final Set<String> FOCUSABLE_ELEMENTS = Set.of(
            "a",
            "button",
            "input",
            "select",
            "textarea",
            "summary",
            "iframe",
            "object",
            "embed"
    );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "ARIA 접근성 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.ARIA;
    }

    @Override
    public AccessibilitySeverity getDefaultSeverity() {
        return AccessibilitySeverity.ERROR;
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
         * 일부 중복 role이나 빈 ARIA 속성은 자동 수정할 수 있지만,
         * 요소의 의미와 의도를 확인해야 하는 경우가 많으므로
         * 규칙 전체는 자동 수정 불가로 둔다.
         */
        return false;
    }

    @Override
    public int getOrder() {
        return 60;
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

            NodeList elements =
                    context.getDocument()
                            .getElementsByTagName("*");

            for (int index = 0;
                    index < elements.getLength();
                    index++) {

                Node node = elements.item(index);

                if (!(node instanceof Element element)) {
                    continue;
                }

                validateElement(
                        context,
                        element,
                        issues
                );
            }

            validateDuplicateIds(
                    context,
                    elements,
                    issues
            );

            return List.copyOf(issues);

        } catch (AccessibilityRuleException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new AccessibilityRuleException(
                    RULE_ID,
                    context.getDocumentPath(),
                    "Failed to validate ARIA accessibility.",
                    exception
            );
        }
    }

    private void validateElement(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        validateRole(
                context,
                element,
                issues
        );

        validateAriaLabel(
                context,
                element,
                issues
        );

        validateReferenceAttribute(
                context,
                element,
                "aria-labelledby",
                issues
        );

        validateReferenceAttribute(
                context,
                element,
                "aria-describedby",
                issues
        );

        validateAriaHidden(
                context,
                element,
                issues
        );

        validateHeadingRole(
                context,
                element,
                issues
        );

        validatePresentationRole(
                context,
                element,
                issues
        );

        validateRedundantRole(
                context,
                element,
                issues
        );
    }

    private void validateRole(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        String roleValue =
                normalizeOptionalText(
                        element.getAttribute("role")
                );

        if (roleValue == null) {
            return;
        }

        String[] roleTokens =
                roleValue.split("\\s+");

        List<String> invalidRoles =
                new ArrayList<>();

        for (String role : roleTokens) {
            String normalizedRole =
                    role.toLowerCase(Locale.ROOT);

            if (!VALID_ROLES.contains(normalizedRole)) {
                invalidRoles.add(role);
            }
        }

        if (invalidRoles.isEmpty()) {
            return;
        }

        AccessibilitySeverity severity =
                context.getBooleanOption(
                        OPTION_STRICT_ROLE_VALIDATION,
                        true
                )
                        ? AccessibilitySeverity.ERROR
                        : AccessibilitySeverity.WARNING;

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_ROLE_INVALID
                        )
                        .severity(severity)
                        .message(
                                "유효하지 않은 ARIA role이 사용되었습니다."
                        )
                        .description(
                                "다음 role 값을 인식할 수 없습니다: "
                                        + String.join(
                                                ", ",
                                                invalidRoles
                                        )
                        )
                        .recommendation(
                                "유효한 WAI-ARIA role을 사용하거나 "
                                        + "불필요한 role 속성을 제거하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "role",
                                        roleValue
                                )
                        )
                        .currentValue(roleValue)
                        .relatedValues(invalidRoles)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateAriaLabel(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        if (!element.hasAttribute("aria-label")) {
            return;
        }

        String ariaLabel =
                element.getAttribute("aria-label");

        if (!ariaLabel.trim().isEmpty()) {
            return;
        }

        boolean allowEmpty =
                context.getBooleanOption(
                        OPTION_ALLOW_EMPTY_ARIA_LABEL,
                        false
                );

        if (allowEmpty) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_LABEL_EMPTY
                        )
                        .message(
                                "aria-label 속성이 비어 있습니다."
                        )
                        .description(
                                "빈 aria-label은 요소의 기존 접근 가능한 "
                                        + "이름을 제거하거나 보조기기에 "
                                        + "빈 이름으로 전달될 수 있습니다."
                        )
                        .recommendation(
                                "의미 있는 접근 가능한 이름을 작성하거나 "
                                        + "불필요한 aria-label 속성을 제거하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "aria-label",
                                        ""
                                )
                        )
                        .currentValue("")
                        .ruleId(RULE_ID)
                        .automaticallyFixable(true)
                        .manualReviewRequired(false)
                        .build()
        );
    }

    private void validateReferenceAttribute(
            AccessibilityRuleContext context,
            Element element,
            String attributeName,
            List<AccessibilityIssue> issues) {

        String referenceValue =
                normalizeOptionalText(
                        element.getAttribute(
                                attributeName
                        )
                );

        if (referenceValue == null) {
            return;
        }

        List<String> missingIds =
                new ArrayList<>();

        List<String> emptyReferenceIds =
                new ArrayList<>();

        for (String id
                : referenceValue.split("\\s+")) {

            String normalizedId =
                    normalizeOptionalText(id);

            if (normalizedId == null) {
                continue;
            }

            Element referencedElement =
                    findElementById(
                            context,
                            normalizedId
                    );

            if (referencedElement == null) {
                missingIds.add(normalizedId);
                continue;
            }

            String referencedText =
                    resolveAccessibleText(
                            referencedElement
                    );

            if (referencedText == null) {
                emptyReferenceIds.add(
                        normalizedId
                );
            }
        }

        if (!missingIds.isEmpty()) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .ARIA_REFERENCE_INVALID
                            )
                            .message(
                                    attributeName
                                            + " 참조 대상이 존재하지 않습니다."
                            )
                            .description(
                                    "다음 id를 가진 요소를 찾을 수 없습니다: "
                                            + String.join(
                                                    ", ",
                                                    missingIds
                                            )
                            )
                            .recommendation(
                                    attributeName
                                            + " 값을 실제로 존재하는 요소 id로 "
                                            + "수정하거나 해당 속성을 제거하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            element,
                                            attributeName,
                                            referenceValue
                                    )
                            )
                            .currentValue(referenceValue)
                            .relatedValues(missingIds)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }

        if (!emptyReferenceIds.isEmpty()) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .ARIA_REFERENCE_INVALID
                            )
                            .severity(AccessibilitySeverity.WARNING)
                            .message(
                                    attributeName
                                            + " 참조 대상에 유효한 텍스트가 없습니다."
                            )
                            .description(
                                    "참조 요소는 존재하지만 접근 가능한 "
                                            + "이름이나 설명으로 사용할 "
                                            + "텍스트가 비어 있습니다: "
                                            + String.join(
                                                    ", ",
                                                    emptyReferenceIds
                                            )
                            )
                            .recommendation(
                                    "참조 대상 요소에 의미 있는 텍스트를 "
                                            + "작성하거나 다른 요소를 참조하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            element,
                                            attributeName,
                                            referenceValue
                                    )
                            )
                            .currentValue(referenceValue)
                            .relatedValues(
                                    emptyReferenceIds
                            )
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private void validateAriaHidden(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        if (!element.hasAttribute("aria-hidden")) {
            return;
        }

        String ariaHidden =
                normalizeOptionalText(
                        element.getAttribute(
                                "aria-hidden"
                        )
                );

        if (ariaHidden == null
                || !BOOLEAN_ARIA_VALUES.contains(
                        ariaHidden.toLowerCase(
                                Locale.ROOT))) {

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .ARIA_ATTRIBUTE_INVALID
                            )
                            .message(
                                    "aria-hidden 값이 올바르지 않습니다."
                            )
                            .description(
                                    "aria-hidden은 true 또는 false 값만 "
                                            + "사용할 수 있습니다."
                            )
                            .recommendation(
                                    "aria-hidden 값을 true 또는 false로 "
                                            + "수정하거나 불필요하면 속성을 "
                                            + "제거하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            element,
                                            "aria-hidden",
                                            ariaHidden
                                    )
                            )
                            .currentValue(ariaHidden)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );

            return;
        }

        if (!"true".equalsIgnoreCase(ariaHidden)) {
            return;
        }

        boolean checkFocusable =
                context.getBooleanOption(
                        OPTION_CHECK_HIDDEN_FOCUSABLE,
                        true
                );

        if (!checkFocusable) {
            return;
        }

        List<Element> focusableElements =
                findFocusableElements(element);

        if (isFocusable(element)
                && !focusableElements.contains(element)) {

            focusableElements.add(
                    0,
                    element
            );
        }

        if (focusableElements.isEmpty()) {
            return;
        }

        List<String> focusableDescriptions =
                new ArrayList<>();

        for (Element focusable :
                focusableElements) {

            focusableDescriptions.add(
                    createElementSummary(
                            focusable
                    )
            );
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_HIDDEN_FOCUSABLE
                        )
                        .message(
                                "aria-hidden 요소 안에 포커스 가능한 "
                                        + "요소가 있습니다."
                        )
                        .description(
                                "키보드 포커스는 이동할 수 있지만 "
                                        + "보조기기에서는 숨겨져 있어 "
                                        + "사용자가 요소의 목적을 알 수 없습니다."
                        )
                        .recommendation(
                                "포커스 가능한 요소에서 aria-hidden을 "
                                        + "제거하거나, 숨겨야 한다면 포커스도 "
                                        + "받지 않도록 구조를 수정하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "aria-hidden",
                                        "true"
                                )
                        )
                        .currentValue("true")
                        .relatedValues(
                                focusableDescriptions
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "focusableElementCount",
                                Integer.toString(
                                        focusableElements.size()
                                )
                        )
                        .build()
        );
    }

    private void validateHeadingRole(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        String role =
                normalizeOptionalText(
                        element.getAttribute("role")
                );

        if (!containsToken(role, "heading")) {
            return;
        }

        String ariaLevel =
                normalizeOptionalText(
                        element.getAttribute(
                                "aria-level"
                        )
                );

        if (ariaLevel == null) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .ARIA_ATTRIBUTE_INVALID
                            )
                            .message(
                                    "role=\"heading\" 요소에 "
                                            + "aria-level이 없습니다."
                            )
                            .description(
                                    "ARIA 제목 역할은 제목 수준을 "
                                            + "나타내는 aria-level이 필요합니다."
                            )
                            .recommendation(
                                    "문서 구조에 맞는 aria-level 값을 "
                                            + "설정하거나 기본 h1~h6 요소를 "
                                            + "사용하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            element,
                                            "aria-level",
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

        int level;

        try {
            level = Integer.parseInt(
                    ariaLevel
            );
        } catch (NumberFormatException exception) {
            level = -1;
        }

        if (level >= 1 && level <= 6) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_ATTRIBUTE_INVALID
                        )
                        .message(
                                "aria-level 값이 올바르지 않습니다."
                        )
                        .description(
                                "role=\"heading\"의 aria-level은 "
                                        + "일반적으로 1부터 6 사이의 정수여야 합니다."
                        )
                        .recommendation(
                                "문서 제목 구조에 맞는 1~6 사이의 "
                                        + "aria-level을 설정하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "aria-level",
                                        ariaLevel
                                )
                        )
                        .currentValue(ariaLevel)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validatePresentationRole(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        String role =
                normalizeOptionalText(
                        element.getAttribute("role")
                );

        if (!isPresentationRole(role)) {
            return;
        }

        String ariaLabel =
                normalizeOptionalText(
                        element.getAttribute(
                                "aria-label"
                        )
                );

        String ariaLabelledBy =
                normalizeOptionalText(
                        element.getAttribute(
                                "aria-labelledby"
                        )
                );

        String alt = null;

        if (element.hasAttribute("alt")) {
            alt = element.getAttribute("alt");
        }

        boolean hasAccessibleName =
                ariaLabel != null
                        || ariaLabelledBy != null
                        || alt != null
                        && !alt.isBlank();

        if (!hasAccessibleName) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_ATTRIBUTE_NOT_ALLOWED
                        )
                        .message(
                                "presentation 역할 요소에 접근 가능한 "
                                        + "이름이 설정되어 있습니다."
                        )
                        .description(
                                "role=\"presentation\" 또는 role=\"none\"은 "
                                        + "요소의 의미를 제거하지만 alt 또는 "
                                        + "ARIA 레이블이 함께 설정되어 "
                                        + "의도가 충돌합니다."
                        )
                        .recommendation(
                                "장식 요소라면 alt와 ARIA 레이블을 비우고, "
                                        + "정보성 요소라면 presentation 역할을 "
                                        + "제거하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "role",
                                        role
                                )
                        )
                        .currentValue(role)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "ariaLabel",
                                ariaLabel
                        )
                        .metadata(
                                "ariaLabelledBy",
                                ariaLabelledBy
                        )
                        .metadata(
                                "altText",
                                alt
                        )
                        .build()
        );
    }

    private void validateRedundantRole(
            AccessibilityRuleContext context,
            Element element,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_CHECK_REDUNDANT_ROLES,
                        true
                );

        if (!enabled
                || !context.isIncludeInformationalIssues()) {

            return;
        }

        String role =
                normalizeOptionalText(
                        element.getAttribute("role")
                );

        if (role == null) {
            return;
        }

        String elementName =
                resolveElementName(element)
                        .toLowerCase(Locale.ROOT);

        String implicitRole =
                resolveImplicitRole(
                        element,
                        elementName
                );

        if (implicitRole == null
                || !implicitRole.equalsIgnoreCase(
                        role)) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .REDUNDANT_ARIA_ROLE
                        )
                        .message(
                                "기본 HTML 의미와 동일한 ARIA role이 "
                                        + "중복 설정되어 있습니다."
                        )
                        .description(
                                "<"
                                        + elementName
                                        + "> 요소는 기본적으로 role=\""
                                        + implicitRole
                                        + "\" 의미를 갖습니다."
                        )
                        .recommendation(
                                "기본 HTML 요소의 의미를 그대로 사용하는 경우 "
                                        + "중복된 role 속성을 제거하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        element,
                                        "role",
                                        role
                                )
                        )
                        .currentValue(role)
                        .suggestedValue("")
                        .ruleId(RULE_ID)
                        .automaticallyFixable(true)
                        .manualReviewRequired(false)
                        .build()
        );
    }

    private void validateDuplicateIds(
            AccessibilityRuleContext context,
            NodeList elements,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_CHECK_DUPLICATE_IDS,
                        true
                );

        if (!enabled) {
            return;
        }

        Set<String> seenIds =
                new HashSet<>();

        Set<String> reportedIds =
                new HashSet<>();

        for (int index = 0;
                index < elements.getLength();
                index++) {

            Node node = elements.item(index);

            if (!(node instanceof Element element)) {
                continue;
            }

            String id =
                    normalizeOptionalText(
                            element.getAttribute("id")
                    );

            if (id == null) {
                continue;
            }

            if (seenIds.add(id)
                    || !reportedIds.add(id)) {

                continue;
            }

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .DUPLICATE_ELEMENT_ID
                            )
                            .message(
                                    "문서에 중복된 요소 id가 있습니다."
                            )
                            .description(
                                    "id=\""
                                            + id
                                            + "\"가 여러 요소에 사용되어 "
                                            + "ARIA 참조와 내부 링크 대상이 "
                                            + "모호해질 수 있습니다."
                            )
                            .recommendation(
                                    "각 요소에 문서 내에서 고유한 id를 "
                                            + "지정하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            element,
                                            "id",
                                            id
                                    )
                            )
                            .currentValue(id)
                            .relatedValue(id)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private List<Element> findFocusableElements(
            Element root) {

        List<Element> result =
                new ArrayList<>();

        NodeList descendants =
                root.getElementsByTagName("*");

        for (int index = 0;
                index < descendants.getLength();
                index++) {

            Node node =
                    descendants.item(index);

            if (node instanceof Element element
                    && isFocusable(element)) {

                result.add(element);
            }
        }

        return result;
    }

    private boolean isFocusable(
            Element element) {

        if (element == null) {
            return false;
        }

        if (element.hasAttribute("disabled")) {
            return false;
        }

        String tabindex =
                normalizeOptionalText(
                        element.getAttribute(
                                "tabindex"
                        )
                );

        if (tabindex != null) {
            try {
                return Integer.parseInt(tabindex)
                        >= 0;
            } catch (NumberFormatException exception) {
                return true;
            }
        }

        String elementName =
                resolveElementName(element)
                        .toLowerCase(Locale.ROOT);

        if (!FOCUSABLE_ELEMENTS.contains(
                elementName)) {

            return false;
        }

        if ("a".equals(elementName)) {
            return normalizeOptionalText(
                    element.getAttribute("href")
            ) != null;
        }

        if ("input".equals(elementName)
                && "hidden".equalsIgnoreCase(
                        element.getAttribute("type"))) {

            return false;
        }

        return true;
    }

    private Element findElementById(
            AccessibilityRuleContext context,
            String id) {

        if (id == null || id.isBlank()) {
            return null;
        }

        Element byDomId =
                context.getDocument()
                        .getElementById(id);

        if (byDomId != null) {
            return byDomId;
        }

        NodeList elements =
                context.getDocument()
                        .getElementsByTagName("*");

        for (int index = 0;
                index < elements.getLength();
                index++) {

            Node node = elements.item(index);

            if (node instanceof Element element
                    && id.equals(
                            element.getAttribute("id"))) {

                return element;
            }
        }

        return null;
    }

    private String resolveAccessibleText(
            Element element) {

        if (element == null) {
            return null;
        }

        String ariaLabel =
                normalizeOptionalText(
                        element.getAttribute(
                                "aria-label"
                        )
                );

        if (ariaLabel != null) {
            return ariaLabel;
        }

        String alt =
                normalizeOptionalText(
                        element.getAttribute("alt")
                );

        if (alt != null) {
            return alt;
        }

        return normalizeOptionalText(
                element.getTextContent()
        );
    }

    private String resolveImplicitRole(
            Element element,
            String elementName) {

        return switch (elementName) {
            case "a" ->
                    normalizeOptionalText(
                            element.getAttribute("href")
                    ) == null
                            ? null
                            : "link";

            case "button" -> "button";
            case "nav" -> "navigation";
            case "main" -> "main";
            case "article" -> "article";
            case "aside" -> "complementary";
            case "form" -> "form";
            case "table" -> "table";
            case "ul", "ol" -> "list";
            case "li" -> "listitem";
            case "img" -> "img";
            case "textarea" -> "textbox";

            case "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6" -> "heading";

            default -> null;
        };
    }

    private boolean isPresentationRole(
            String role) {

        if (role == null) {
            return false;
        }

        return containsToken(
                role,
                "presentation"
        ) || containsToken(
                role,
                "none"
        );
    }

    private boolean containsToken(
            String value,
            String expectedToken) {

        if (value == null
                || expectedToken == null) {

            return false;
        }

        for (String token
                : value.trim().split("\\s+")) {

            if (expectedToken.equalsIgnoreCase(
                    token)) {

                return true;
            }
        }

        return false;
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
                                createElementExcerpt(
                                        element
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
                "role"
        );

        appendAttribute(
                result,
                element,
                "aria-label"
        );

        appendAttribute(
                result,
                element,
                "aria-labelledby"
        );

        appendAttribute(
                result,
                element,
                "aria-describedby"
        );

        appendAttribute(
                result,
                element,
                "aria-hidden"
        );

        appendAttribute(
                result,
                element,
                "aria-level"
        );

        appendAttribute(
                result,
                element,
                "tabindex"
        );

        result.append('>');

        String text =
                normalizeOptionalText(
                        element.getTextContent()
                );

        if (text != null) {
            result.append(
                    truncate(
                            text,
                            120
                    )
            );
        }

        result.append("</");
        result.append(name);
        result.append('>');

        return result.toString();
    }

    private String createElementSummary(
            Element element) {

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

        String href =
                normalizeOptionalText(
                        element.getAttribute("href")
                );

        if (href != null) {
            result.append(" href=\"");
            result.append(
                    truncate(href, 80)
            );
            result.append('"');
        }

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
}