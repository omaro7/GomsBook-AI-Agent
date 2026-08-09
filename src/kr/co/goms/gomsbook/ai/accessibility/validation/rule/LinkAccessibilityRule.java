/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation.rule;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * XHTML 문서의 링크 접근성을 검사한다.
 *
 * <p>다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>href 속성 누락 또는 빈 링크 대상</li>
 *   <li>링크의 접근 가능한 이름 누락</li>
 *   <li>의미가 불명확한 링크 텍스트</li>
 *   <li>내부 파일 링크 대상 누락</li>
 *   <li>문서 내부 fragment 대상 누락</li>
 *   <li>다른 XHTML 문서의 fragment 대상 누락</li>
 *   <li>이미지로만 구성된 링크의 접근 가능한 이름 누락</li>
 *   <li>aria-labelledby 참조 오류</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 접근성 문제 목록만 반환한다.</p>
 */
public final class LinkAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID = "link-accessibility";

    public static final String OPTION_CHECK_TARGET_EXISTENCE =
            "link.checkTargetExistence";

    public static final String OPTION_CHECK_FRAGMENT_EXISTENCE =
            "link.checkFragmentExistence";

    public static final String OPTION_CHECK_EXTERNAL_LINKS =
            "link.checkExternalLinks";

    public static final String OPTION_AMBIGUOUS_TEXT_AS_ERROR =
            "link.ambiguousTextAsError";

    public static final String OPTION_ALLOW_EMPTY_FRAGMENT =
            "link.allowEmptyFragment";

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    private static final Set<String> AMBIGUOUS_LINK_TEXTS =
            Set.of(
                    "click here",
                    "click",
                    "here",
                    "read more",
                    "more",
                    "details",
                    "link",
                    "go",
                    "continue",
                    "download",
                    "this",
                    "이곳",
                    "여기",
                    "여기를 클릭",
                    "클릭",
                    "클릭하세요",
                    "더보기",
                    "자세히",
                    "자세히 보기",
                    "계속",
                    "링크",
                    "바로가기"
            );

    private static final Set<String> NON_FILE_SCHEMES =
            Set.of(
                    "http",
                    "https",
                    "mailto",
                    "tel",
                    "sms",
                    "ftp",
                    "data",
                    "javascript"
            );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "링크 접근성 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.LINK;
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
         * 링크 텍스트와 목적은 문맥 판단이 필요하므로
         * 기본적으로 자동 수정하지 않는다.
         */
        return false;
    }

    @Override
    public int getOrder() {
        return 40;
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

            NodeList links = findLinkElements(context);

            for (int index = 0;
                    index < links.getLength();
                    index++) {

                Node node = links.item(index);

                if (!(node instanceof Element link)) {
                    continue;
                }

                validateLink(
                        context,
                        link,
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
                    "Failed to validate link accessibility.",
                    exception
            );
        }
    }

    private void validateLink(
            AccessibilityRuleContext context,
            Element link,
            List<AccessibilityIssue> issues) {

        boolean hasHrefAttribute =
                link.hasAttribute("href");

        String href = normalizeOptionalText(
                link.getAttribute("href")
        );

        if (!hasHrefAttribute || href == null) {
            issues.add(
                    createMissingTargetIssue(
                            context,
                            link,
                            href
                    )
            );
        }

        AccessibleName accessibleName =
                resolveAccessibleName(
                        context,
                        link
                );

        validateAccessibleName(
                context,
                link,
                href,
                accessibleName,
                issues
        );

        validateAriaLabelledBy(
                context,
                link,
                issues
        );

        if (href == null) {
            return;
        }

        validateLinkTarget(
                context,
                link,
                href,
                issues
        );
    }

    private void validateAccessibleName(
            AccessibilityRuleContext context,
            Element link,
            String href,
            AccessibleName accessibleName,
            List<AccessibilityIssue> issues) {

        if (accessibleName.value() == null) {
            AccessibilityIssueCode issueCode =
                    containsMeaningfulImage(link)
                            ? AccessibilityIssueCode
                                    .IMAGE_LINK_NAME_MISSING
                            : AccessibilityIssueCode
                                    .LINK_TEXT_EMPTY;

            issues.add(
                    AccessibilityIssue.builder(issueCode)
                            .message(
                                    issueCode
                                            == AccessibilityIssueCode
                                                    .IMAGE_LINK_NAME_MISSING
                                            ? "이미지 링크에 접근 가능한 이름이 없습니다."
                                            : "링크에 접근 가능한 이름이 없습니다."
                            )
                            .description(
                                    "링크의 텍스트, aria-label, "
                                            + "aria-labelledby 또는 이미지 alt를 "
                                            + "통해 링크 목적을 확인할 수 없습니다."
                            )
                            .recommendation(
                                    "링크 목적을 명확히 설명하는 텍스트나 "
                                            + "접근 가능한 이름을 제공하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            link,
                                            accessibleName.attributeName(),
                                            null
                                    )
                            )
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .metadata("href", href)
                            .build()
            );

            return;
        }

        if (!isAmbiguousLinkText(
                accessibleName.value())) {

            return;
        }

        AccessibilitySeverity severity =
                context.getBooleanOption(
                        OPTION_AMBIGUOUS_TEXT_AS_ERROR,
                        false
                )
                        ? AccessibilitySeverity.ERROR
                        : AccessibilitySeverity.WARNING;

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .LINK_TEXT_AMBIGUOUS
                        )
                        .severity(severity)
                        .message(
                                "링크 목적을 명확히 알기 어려운 "
                                        + "링크 텍스트입니다."
                        )
                        .description(
                                "\""
                                        + accessibleName.value()
                                        + "\"만으로는 링크가 이동하는 대상이나 "
                                        + "수행하는 동작을 충분히 이해하기 어렵습니다."
                        )
                        .recommendation(
                                "주변 문맥 없이도 링크 목적을 이해할 수 있는 "
                                        + "구체적인 링크 텍스트를 사용하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        link,
                                        accessibleName.attributeName(),
                                        accessibleName.value()
                                )
                        )
                        .currentValue(
                                accessibleName.value()
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "accessibleNameSource",
                                accessibleName.source()
                        )
                        .metadata("href", href)
                        .build()
        );
    }

    private void validateAriaLabelledBy(
            AccessibilityRuleContext context,
            Element link,
            List<AccessibilityIssue> issues) {

        String ariaLabelledBy =
                normalizeOptionalText(
                        link.getAttribute(
                                "aria-labelledby"
                        )
                );

        if (ariaLabelledBy == null) {
            return;
        }

        List<String> missingIds =
                new ArrayList<>();

        for (String id
                : ariaLabelledBy.split("\\s+")) {

            if (id.isBlank()) {
                continue;
            }

            Element referenced =
                    context.getDocument()
                            .getElementById(id);

            if (referenced == null) {
                referenced = findElementById(
                        context,
                        id
                );
            }

            if (referenced == null) {
                missingIds.add(id);
            }
        }

        if (missingIds.isEmpty()) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .ARIA_REFERENCE_INVALID
                        )
                        .message(
                                "링크의 aria-labelledby 참조 대상이 "
                                        + "존재하지 않습니다."
                        )
                        .description(
                                "다음 id를 가진 요소를 찾을 수 없습니다: "
                                        + String.join(
                                                ", ",
                                                missingIds
                                        )
                        )
                        .recommendation(
                                "aria-labelledby 값을 존재하는 요소 id로 "
                                        + "수정하거나 적절한 aria-label 또는 "
                                        + "링크 텍스트를 제공하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        link,
                                        "aria-labelledby",
                                        ariaLabelledBy
                                )
                        )
                        .currentValue(ariaLabelledBy)
                        .relatedValues(missingIds)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateLinkTarget(
            AccessibilityRuleContext context,
            Element link,
            String href,
            List<AccessibilityIssue> issues) {

        if (isExternalLink(href)) {
            boolean checkExternalLinks =
                    context.getBooleanOption(
                            OPTION_CHECK_EXTERNAL_LINKS,
                            false
                    );

            /*
             * 외부 네트워크 요청은 이 규칙의 책임이 아니다.
             * 옵션은 향후 외부 URL 검사 서비스 연결을 위한 확장 지점이다.
             */
            if (!checkExternalLinks) {
                return;
            }

            return;
        }

        if (href.startsWith("#")) {
            validateCurrentDocumentFragment(
                    context,
                    link,
                    href,
                    issues
            );
            return;
        }

        boolean checkTargetExistence =
                context.getBooleanOption(
                        OPTION_CHECK_TARGET_EXISTENCE,
                        true
                );

        if (!checkTargetExistence) {
            return;
        }

        LinkReference reference =
                parseLinkReference(href);

        if (reference == null
                || reference.path() == null
                || reference.path().isBlank()) {

            return;
        }

        Path targetPath =
                resolveTargetPath(
                        context,
                        reference.path()
                );

        if (targetPath == null
                || !targetPath.startsWith(
                        context.getProjectRoot())
                || !Files.isRegularFile(targetPath)) {

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .LINK_TARGET_NOT_FOUND
                            )
                            .message(
                                    "링크 대상 파일을 찾을 수 없습니다."
                            )
                            .description(
                                    "href가 프로젝트 내부의 유효한 파일을 "
                                            + "가리키지 않습니다."
                            )
                            .recommendation(
                                    "링크 경로와 대상 파일 존재 여부를 "
                                            + "확인하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            link,
                                            "href",
                                            href
                                    )
                            )
                            .currentValue(href)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .metadata(
                                    "resolvedTarget",
                                    targetPath == null
                                            ? ""
                                            : targetPath.toString()
                            )
                            .build()
            );

            return;
        }

        if (reference.fragment() != null
                && !reference.fragment().isBlank()) {

            validateExternalDocumentFragment(
                    context,
                    link,
                    href,
                    targetPath,
                    reference.fragment(),
                    issues
            );
        }
    }

    private void validateCurrentDocumentFragment(
            AccessibilityRuleContext context,
            Element link,
            String href,
            List<AccessibilityIssue> issues) {

        boolean checkFragment =
                context.getBooleanOption(
                        OPTION_CHECK_FRAGMENT_EXISTENCE,
                        true
                );

        if (!checkFragment) {
            return;
        }

        String fragment =
                href.substring(1).trim();

        if (fragment.isEmpty()) {
            boolean allowEmptyFragment =
                    context.getBooleanOption(
                            OPTION_ALLOW_EMPTY_FRAGMENT,
                            false
                    );

            if (allowEmptyFragment) {
                return;
            }

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .LINK_TARGET_MISSING
                            )
                            .message(
                                    "링크 fragment 대상이 비어 있습니다."
                            )
                            .description(
                                    "href=\"#\"는 명확한 문서 내 이동 "
                                            + "대상을 지정하지 않습니다."
                            )
                            .recommendation(
                                    "이동할 요소의 id를 fragment로 지정하거나 "
                                            + "불필요한 링크를 제거하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            link,
                                            "href",
                                            href
                                    )
                            )
                            .currentValue(href)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );

            return;
        }

        if (findElementById(
                context,
                fragment) != null) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .LINK_TARGET_NOT_FOUND
                        )
                        .message(
                                "문서 내부 링크 대상을 찾을 수 없습니다."
                        )
                        .description(
                                "현재 문서에 id=\""
                                        + fragment
                                        + "\"인 요소가 없습니다."
                        )
                        .recommendation(
                                "href의 fragment와 대상 요소 id가 "
                                        + "일치하는지 확인하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        link,
                                        "href",
                                        href
                                )
                        )
                        .currentValue(href)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata("fragment", fragment)
                        .build()
        );
    }

    private void validateExternalDocumentFragment(
            AccessibilityRuleContext context,
            Element link,
            String href,
            Path targetPath,
            String fragment,
            List<AccessibilityIssue> issues) {

        boolean checkFragment =
                context.getBooleanOption(
                        OPTION_CHECK_FRAGMENT_EXISTENCE,
                        true
                );

        if (!checkFragment) {
            return;
        }

        /*
         * 현재 Validator의 DOM은 현재 문서만 보유한다.
         * 다른 XHTML의 fragment 검증은 경량 텍스트 탐색으로 처리한다.
         *
         * 정밀 검증이 필요하면 향후 XhtmlDocumentLoader 또는
         * ProjectDocumentResolver를 주입하는 방식으로 교체할 수 있다.
         */
        if (targetContainsId(
                targetPath,
                fragment)) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .LINK_TARGET_NOT_FOUND
                        )
                        .message(
                                "링크 대상 문서에서 fragment 요소를 "
                                        + "찾을 수 없습니다."
                        )
                        .description(
                                targetPath.getFileName()
                                        + " 문서에 id=\""
                                        + fragment
                                        + "\"인 요소가 없습니다."
                        )
                        .recommendation(
                                "대상 문서의 요소 id와 href fragment를 "
                                        + "일치시키십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        link,
                                        "href",
                                        href
                                )
                        )
                        .currentValue(href)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "targetPath",
                                targetPath.toString()
                        )
                        .metadata(
                                "fragment",
                                fragment
                        )
                        .build()
        );
    }

    private boolean targetContainsId(
            Path targetPath,
            String fragment) {

        if (targetPath == null
                || fragment == null
                || fragment.isBlank()) {

            return false;
        }

        try {
            String content =
                    Files.readString(targetPath);

            String escapedDouble =
                    "id=\"" + fragment + "\"";

            String escapedSingle =
                    "id='" + fragment + "'";

            return content.contains(escapedDouble)
                    || content.contains(escapedSingle);

        } catch (Exception exception) {
            return false;
        }
    }

    private AccessibilityIssue createMissingTargetIssue(
            AccessibilityRuleContext context,
            Element link,
            String href) {

        return AccessibilityIssue.builder(
                        AccessibilityIssueCode
                                .LINK_TARGET_MISSING
                )
                .message(
                        "링크의 href 속성이 없거나 비어 있습니다."
                )
                .description(
                        "링크 요소가 이동 대상이나 수행 동작을 "
                                + "지정하지 않았습니다."
                )
                .recommendation(
                        "유효한 문서, fragment 또는 외부 주소를 "
                                + "href 속성에 지정하십시오."
                )
                .location(
                        createLocation(
                                context,
                                link,
                                "href",
                                href
                        )
                )
                .currentValue(href)
                .ruleId(RULE_ID)
                .automaticallyFixable(false)
                .manualReviewRequired(true)
                .build();
    }

    private AccessibleName resolveAccessibleName(
            AccessibilityRuleContext context,
            Element link) {

        String ariaLabel =
                normalizeOptionalText(
                        link.getAttribute("aria-label")
                );

        if (ariaLabel != null) {
            return new AccessibleName(
                    ariaLabel,
                    "aria-label",
                    "aria-label"
            );
        }

        String ariaLabelledBy =
                normalizeOptionalText(
                        link.getAttribute(
                                "aria-labelledby"
                        )
                );

        if (ariaLabelledBy != null) {
            String labelledText =
                    resolveLabelledByText(
                            context,
                            ariaLabelledBy
                    );

            if (labelledText != null) {
                return new AccessibleName(
                        labelledText,
                        "aria-labelledby",
                        "aria-labelledby"
                );
            }
        }

        String visibleText =
                extractVisibleLinkText(link);

        if (visibleText != null) {
            return new AccessibleName(
                    visibleText,
                    null,
                    "text"
            );
        }

        String imageAlt =
                extractImageAlt(link);

        if (imageAlt != null) {
            return new AccessibleName(
                    imageAlt,
                    "alt",
                    "image-alt"
            );
        }

        String title =
                normalizeOptionalText(
                        link.getAttribute("title")
                );

        if (title != null) {
            return new AccessibleName(
                    title,
                    "title",
                    "title"
            );
        }

        return new AccessibleName(
                null,
                null,
                "none"
        );
    }

    private String resolveLabelledByText(
            AccessibilityRuleContext context,
            String ariaLabelledBy) {

        StringBuilder result =
                new StringBuilder();

        for (String id
                : ariaLabelledBy.split("\\s+")) {

            Element referenced =
                    findElementById(
                            context,
                            id
                    );

            if (referenced == null) {
                continue;
            }

            String text =
                    normalizeOptionalText(
                            referenced.getTextContent()
                    );

            if (text == null) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(text);
        }

        return normalizeOptionalText(
                result.toString()
        );
    }

    private Element findElementById(
            AccessibilityRuleContext context,
            String id) {

        if (id == null || id.isBlank()) {
            return null;
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

    private String extractVisibleLinkText(
            Element link) {

        StringBuilder text =
                new StringBuilder();

        collectVisibleText(
                link,
                text
        );

        return normalizeOptionalText(
                text.toString()
        );
    }

    private void collectVisibleText(
            Node node,
            StringBuilder target) {

        if (node == null) {
            return;
        }

        if (node instanceof Element element) {
            String elementName =
                    resolveElementName(element);

            if ("img".equalsIgnoreCase(
                    elementName)
                    || "svg".equalsIgnoreCase(
                            elementName)) {

                return;
            }

            if ("true".equalsIgnoreCase(
                    element.getAttribute(
                            "aria-hidden"))) {

                return;
            }
        }

        if (node.getNodeType()
                == Node.TEXT_NODE) {

            target.append(
                    node.getNodeValue()
            );
            target.append(' ');
            return;
        }

        NodeList children =
                node.getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            collectVisibleText(
                    children.item(index),
                    target
            );
        }
    }

    private String extractImageAlt(
            Element link) {

        NodeList images =
                link.getElementsByTagNameNS(
                        XHTML_NAMESPACE,
                        "img"
                );

        if (images.getLength() == 0) {
            images =
                    link.getElementsByTagName("img");
        }

        StringBuilder result =
                new StringBuilder();

        for (int index = 0;
                index < images.getLength();
                index++) {

            Node node = images.item(index);

            if (!(node instanceof Element image)
                    || !image.hasAttribute("alt")) {

                continue;
            }

            String alt =
                    normalizeOptionalText(
                            image.getAttribute("alt")
                    );

            if (alt == null) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(alt);
        }

        return normalizeOptionalText(
                result.toString()
        );
    }

    private boolean containsMeaningfulImage(
            Element link) {

        NodeList images =
                link.getElementsByTagNameNS(
                        XHTML_NAMESPACE,
                        "img"
                );

        if (images.getLength() == 0) {
            images =
                    link.getElementsByTagName("img");
        }

        return images.getLength() > 0;
    }

    private boolean isAmbiguousLinkText(
            String value) {

        if (value == null) {
            return false;
        }

        String normalized =
                normalizeComparableText(value);

        return AMBIGUOUS_LINK_TEXTS.contains(
                normalized
        );
    }

    private void validateExternalUriSyntax(
            AccessibilityRuleContext context,
            Element link,
            String href,
            List<AccessibilityIssue> issues) {

        try {
            new URI(href);

        } catch (URISyntaxException exception) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .LINK_TARGET_NOT_FOUND
                            )
                            .message(
                                    "외부 링크 주소 형식이 올바르지 않습니다."
                            )
                            .description(
                                    "href 값을 URI로 해석할 수 없습니다."
                            )
                            .recommendation(
                                    "유효한 URL 또는 URI 형식으로 "
                                            + "링크 주소를 수정하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            link,
                                            "href",
                                            href
                                    )
                            )
                            .currentValue(href)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .build()
            );
        }
    }

    private boolean isExternalLink(
            String href) {

        if (href == null || href.isBlank()) {
            return false;
        }

        String normalized =
                href.trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.startsWith("//")) {
            return true;
        }

        int colonIndex =
                normalized.indexOf(':');

        if (colonIndex <= 0) {
            return false;
        }

        String scheme =
                normalized.substring(
                        0,
                        colonIndex
                );

        return NON_FILE_SCHEMES.contains(scheme);
    }

    private LinkReference parseLinkReference(
            String href) {

        if (href == null || href.isBlank()) {
            return null;
        }

        String value = href.trim();

        int queryIndex =
                value.indexOf('?');

        String queryRemoved =
                queryIndex >= 0
                        ? value.substring(
                                0,
                                queryIndex
                        )
                        : value;

        int fragmentIndex =
                queryRemoved.indexOf('#');

        if (fragmentIndex < 0) {
            return new LinkReference(
                    queryRemoved,
                    null
            );
        }

        return new LinkReference(
                queryRemoved.substring(
                        0,
                        fragmentIndex
                ),
                queryRemoved.substring(
                        fragmentIndex + 1
                )
        );
    }

    private Path resolveTargetPath(
            AccessibilityRuleContext context,
            String targetReference) {

        if (targetReference == null
                || targetReference.isBlank()) {

            return null;
        }

        try {
            Path reference =
                    Path.of(targetReference);

            if (reference.isAbsolute()) {
                return reference
                        .toAbsolutePath()
                        .normalize();
            }

            Path parent =
                    context.getDocumentPath()
                            .getParent();

            if (parent == null) {
                return null;
            }

            return parent.resolve(reference)
                    .toAbsolutePath()
                    .normalize();

        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private NodeList findLinkElements(
            AccessibilityRuleContext context) {

        NodeList links =
                context.getDocument()
                        .getElementsByTagNameNS(
                                XHTML_NAMESPACE,
                                "a"
                        );

        if (links.getLength() == 0) {
            links =
                    context.getDocument()
                            .getElementsByTagName("a");
        }

        return links;
    }

    private AccessibilityLocation createLocation(
            AccessibilityRuleContext context,
            Element link,
            String attributeName,
            String attributeValue) {

        AccessibilityLocation.Builder builder =
                context.locationBuilder(link)
                        .xpath(createXPath(link))
                        .textExcerpt(
                                createLinkExcerpt(link)
                        );

        if (attributeName != null) {
            builder.attribute(
                    attributeName,
                    attributeValue
            );
        }

        return builder.build();
    }

    private String createLinkExcerpt(
            Element link) {

        StringBuilder result =
                new StringBuilder();

        result.append("<a");

        appendAttribute(
                result,
                link,
                "id"
        );

        appendAttribute(
                result,
                link,
                "href"
        );

        appendAttribute(
                result,
                link,
                "aria-label"
        );

        appendAttribute(
                result,
                link,
                "aria-labelledby"
        );

        result.append('>');

        String text =
                extractVisibleLinkText(link);

        if (text != null) {
            result.append(
                    truncate(text, 120)
            );
        }

        result.append("</a>");

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

    private String normalizeComparableText(
            String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll(
                        "[\\p{Punct}]",
                        ""
                );
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

    private record AccessibleName(
            String value,
            String attributeName,
            String source) {
    }

    private record LinkReference(
            String path,
            String fragment) {
    }
}