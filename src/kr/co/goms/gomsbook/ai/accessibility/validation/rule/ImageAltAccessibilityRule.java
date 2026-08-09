/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation.rule;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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
 * XHTML 문서의 이미지 대체 텍스트와 관련 접근성 속성을 검사한다.
 *
 * <p>이 규칙은 다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>alt 속성 누락</li>
 *   <li>정보성 이미지의 빈 alt</li>
 *   <li>지나치게 긴 alt</li>
 *   <li>파일명을 그대로 사용한 alt</li>
 *   <li>불필요한 "이미지", "사진" 등의 접두어</li>
 *   <li>장식 이미지의 alt 및 role 속성</li>
 *   <li>정보성 이미지의 role="presentation" 또는 aria-hidden</li>
 *   <li>이미지 src 파일 존재 여부</li>
 *   <li>이미지 링크의 접근 가능한 이름</li>
 *   <li>figcaption과 alt의 중복</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 문제 목록만 반환한다.</p>
 */
public final class ImageAltAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID = "image-alt";

    public static final String OPTION_CHECK_IMAGE_SOURCE =
            "imageAlt.checkImageSource";

    public static final String OPTION_CHECK_CAPTION_DUPLICATION =
            "imageAlt.checkCaptionDuplication";

    public static final String OPTION_REQUIRE_PRESENTATION_ROLE =
            "imageAlt.requirePresentationRole";

    public static final String OPTION_EMPTY_ALT_AS_ERROR =
            "imageAlt.emptyAltAsError";

    private static final String XHTML_NAMESPACE =
            "http://www.w3.org/1999/xhtml";

    private static final Set<String> PRESENTATION_ROLES =
            Set.of("presentation", "none");

    private static final Set<String> GENERIC_ALT_VALUES =
            Set.of(
                    "image",
                    "img",
                    "photo",
                    "picture",
                    "graphic",
                    "illustration",
                    "이미지",
                    "사진",
                    "그림",
                    "삽화"
            );

    private static final List<String> REDUNDANT_PREFIXES =
            List.of(
                    "image of ",
                    "an image of ",
                    "a picture of ",
                    "picture of ",
                    "photo of ",
                    "photograph of ",
                    "graphic of ",
                    "illustration of ",
                    "이미지:",
                    "이미지 ",
                    "사진:",
                    "사진 ",
                    "그림:",
                    "그림 ",
                    "삽화:",
                    "삽화 "
            );

    private static final Pattern IMAGE_FILE_NAME_PATTERN =
            Pattern.compile(
                    "(?i)^.+\\.(png|jpe?g|gif|webp|bmp|svg)$"
            );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "이미지 대체 텍스트 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.IMAGE;
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
        return true;
    }

    @Override
    public int getOrder() {
        return 20;
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

            NodeList imageNodes =
                    findImageElements(context);

            for (int index = 0;
                    index < imageNodes.getLength();
                    index++) {

                Node node = imageNodes.item(index);

                if (!(node instanceof Element image)) {
                    continue;
                }

                validateImage(
                        context,
                        image,
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
                    "Failed to validate image alternative text.",
                    exception
            );
        }
    }

    private void validateImage(
            AccessibilityRuleContext context,
            Element image,
            List<AccessibilityIssue> issues) {

        String src = normalizeOptionalText(
                image.getAttribute("src")
        );

        boolean hasAltAttribute =
                image.hasAttribute("alt");

        String alt = hasAltAttribute
                ? image.getAttribute("alt").trim()
                : null;

        String role = normalizeOptionalText(
                image.getAttribute("role")
        );

        String ariaHidden = normalizeOptionalText(
                image.getAttribute("aria-hidden")
        );

        boolean presentationRole =
                isPresentationRole(role);

        boolean hiddenFromAssistiveTechnology =
                "true".equalsIgnoreCase(ariaHidden);

        boolean decorative =
                hasAltAttribute
                        && alt.isEmpty()
                        && (presentationRole
                        || hiddenFromAssistiveTechnology);

        validateSource(
                context,
                image,
                src,
                issues
        );

        if (!hasAltAttribute) {
            issues.add(
                    createMissingAltIssue(
                            context,
                            image,
                            src
                    )
            );

            validateImageLinkName(
                    context,
                    image,
                    null,
                    issues
            );

            return;
        }

        if (alt.isEmpty()) {
            validateEmptyAlt(
                    context,
                    image,
                    role,
                    ariaHidden,
                    issues
            );

            validateImageLinkName(
                    context,
                    image,
                    alt,
                    issues
            );

            return;
        }

        validateMeaningfulImageVisibility(
                context,
                image,
                alt,
                role,
                ariaHidden,
                issues
        );

        validateAltLength(
                context,
                image,
                alt,
                issues
        );

        validateGenericAlt(
                context,
                image,
                alt,
                issues
        );

        validateFileNameAlt(
                context,
                image,
                src,
                alt,
                issues
        );

        validateRedundantPrefix(
                context,
                image,
                alt,
                issues
        );

        validateCaptionDuplication(
                context,
                image,
                alt,
                issues
        );

        validateImageLinkName(
                context,
                image,
                alt,
                issues
        );

        /*
         * decorative 변수는 이후 확장 규칙에서 사용할 수 있으나,
         * 현재 비어 있지 않은 alt는 장식 이미지로 간주하지 않는다.
         */
        if (decorative) {
            issues.add(
                    createDecorativeAltIssue(
                            context,
                            image,
                            alt
                    )
            );
        }
    }

    private void validateSource(
            AccessibilityRuleContext context,
            Element image,
            String src,
            List<AccessibilityIssue> issues) {

        boolean checkSource =
                context.getBooleanOption(
                        OPTION_CHECK_IMAGE_SOURCE,
                        true
                );

        if (!checkSource) {
            return;
        }

        if (src == null) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .IMAGE_SOURCE_MISSING
                            )
                            .message(
                                    "이미지 src 속성이 없거나 비어 있습니다."
                            )
                            .description(
                                    "img 요소가 참조할 이미지 파일을 "
                                            + "지정하지 않았습니다."
                            )
                            .recommendation(
                                    "유효한 프로젝트 상대 이미지 경로를 "
                                            + "src 속성에 지정하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            image,
                                            "src",
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

        if (isExternalResource(src)
                || isDataUri(src)) {

            return;
        }

        Path resolvedPath =
                resolveImagePath(
                        context.getDocumentPath(),
                        src
                );

        if (resolvedPath == null
                || !resolvedPath.startsWith(
                        context.getProjectRoot())
                || !Files.isRegularFile(resolvedPath)) {

            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .IMAGE_SOURCE_MISSING
                            )
                            .message(
                                    "참조된 이미지 파일을 찾을 수 없습니다."
                            )
                            .description(
                                    "img 요소의 src가 프로젝트 내부의 "
                                            + "유효한 이미지 파일을 가리키지 않습니다."
                            )
                            .recommendation(
                                    "src 경로와 이미지 파일 존재 여부를 "
                                            + "확인하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            image,
                                            "src",
                                            src
                                    )
                            )
                            .currentValue(src)
                            .ruleId(RULE_ID)
                            .automaticallyFixable(false)
                            .manualReviewRequired(true)
                            .metadata(
                                    "resolvedPath",
                                    resolvedPath == null
                                            ? ""
                                            : resolvedPath.toString()
                            )
                            .build()
            );
        }
    }

    private void validateEmptyAlt(
            AccessibilityRuleContext context,
            Element image,
            String role,
            String ariaHidden,
            List<AccessibilityIssue> issues) {

        boolean presentationRole =
                isPresentationRole(role);

        boolean ariaHiddenTrue =
                "true".equalsIgnoreCase(ariaHidden);

        boolean requirePresentationRole =
                context.getBooleanOption(
                        OPTION_REQUIRE_PRESENTATION_ROLE,
                        false
                );

        if (presentationRole || ariaHiddenTrue) {
            return;
        }

        AccessibilitySeverity severity =
                context.getBooleanOption(
                        OPTION_EMPTY_ALT_AS_ERROR,
                        false
                )
                        ? AccessibilitySeverity.ERROR
                        : AccessibilitySeverity.WARNING;

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_EMPTY
                        )
                        .severity(severity)
                        .message(
                                "이미지의 alt 속성이 비어 있지만 "
                                        + "장식 이미지임을 확인할 수 없습니다."
                        )
                        .description(
                                "alt=\"\"는 장식 이미지에 적합하지만, "
                                        + "현재 요소에는 role=\"presentation\" "
                                        + "또는 aria-hidden=\"true\"가 없습니다."
                        )
                        .recommendation(
                                requirePresentationRole
                                        ? "장식 이미지라면 alt=\"\"와 "
                                                + "role=\"presentation\"을 적용하고, "
                                                + "정보성 이미지라면 의미 있는 "
                                                + "대체 텍스트를 작성하십시오."
                                        : "장식 이미지인지 검토하고, 정보성 "
                                                + "이미지라면 의미 있는 대체 텍스트를 "
                                                + "작성하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        ""
                                )
                        )
                        .currentValue("")
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );

        if (requirePresentationRole) {
            issues.add(
                    AccessibilityIssue.builder(
                                    AccessibilityIssueCode
                                            .DECORATIVE_IMAGE_ROLE_INVALID
                            )
                            .message(
                                    "장식 이미지에 presentation 역할이 없습니다."
                            )
                            .description(
                                    "빈 alt를 사용하는 장식 이미지에 "
                                            + "role=\"presentation\"이 설정되지 않았습니다."
                            )
                            .recommendation(
                                    "장식 이미지로 확정된 경우 "
                                            + "role=\"presentation\"을 적용하십시오."
                            )
                            .location(
                                    createLocation(
                                            context,
                                            image,
                                            "role",
                                            role
                                    )
                            )
                            .currentValue(role)
                            .suggestedValue("presentation")
                            .ruleId(RULE_ID)
                            .automaticallyFixable(true)
                            .manualReviewRequired(false)
                            .build()
            );
        }
    }

    private void validateMeaningfulImageVisibility(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            String role,
            String ariaHidden,
            List<AccessibilityIssue> issues) {

        boolean presentationRole =
                isPresentationRole(role);

        boolean ariaHiddenTrue =
                "true".equalsIgnoreCase(ariaHidden);

        if (!presentationRole && !ariaHiddenTrue) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .MEANINGFUL_IMAGE_HIDDEN
                        )
                        .message(
                                "대체 텍스트가 있는 이미지가 "
                                        + "보조기기에서 숨겨져 있습니다."
                        )
                        .description(
                                "비어 있지 않은 alt가 있으나 "
                                        + "role=\"presentation\", role=\"none\" "
                                        + "또는 aria-hidden=\"true\"가 적용되어 "
                                        + "대체 텍스트가 전달되지 않을 수 있습니다."
                        )
                        .recommendation(
                                "정보성 이미지라면 presentation 역할과 "
                                        + "aria-hidden 속성을 제거하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        presentationRole
                                                ? "role"
                                                : "aria-hidden",
                                        presentationRole
                                                ? role
                                                : ariaHidden
                                )
                        )
                        .currentValue(
                                presentationRole
                                        ? role
                                        : ariaHidden
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(true)
                        .manualReviewRequired(true)
                        .metadata("altText", alt)
                        .build()
        );
    }

    private void validateAltLength(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            List<AccessibilityIssue> issues) {

        int maximumLength =
                context.getMaximumAltTextLength();

        if (alt.length() <= maximumLength) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_TOO_LONG
                        )
                        .message(
                                "이미지 대체 텍스트가 권장 최대 길이를 "
                                        + "초과합니다."
                        )
                        .description(
                                "현재 대체 텍스트 길이는 "
                                        + alt.length()
                                        + "자이며 권장 최대 길이는 "
                                        + maximumLength
                                        + "자입니다."
                        )
                        .recommendation(
                                "이미지의 핵심 의미만 간결하게 설명하고, "
                                        + "복합 정보는 본문이나 별도 상세 설명으로 "
                                        + "제공하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .currentValue(alt)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "currentLength",
                                Integer.toString(alt.length())
                        )
                        .metadata(
                                "maximumLength",
                                Integer.toString(maximumLength)
                        )
                        .build()
        );
    }

    private void validateGenericAlt(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            List<AccessibilityIssue> issues) {

        String normalized =
                alt.trim().toLowerCase(Locale.ROOT);

        if (!GENERIC_ALT_VALUES.contains(normalized)) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_EMPTY
                        )
                        .severity(AccessibilitySeverity.ERROR)
                        .message(
                                "이미지의 의미를 전달하지 못하는 "
                                        + "일반적인 대체 텍스트입니다."
                        )
                        .description(
                                "대체 텍스트가 단순히 \"" + alt
                                        + "\"로 설정되어 이미지의 핵심 정보를 "
                                        + "전달하지 못합니다."
                        )
                        .recommendation(
                                "이미지가 문서에서 전달하는 핵심 의미나 "
                                        + "기능을 구체적으로 작성하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .currentValue(alt)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateFileNameAlt(
            AccessibilityRuleContext context,
            Element image,
            String src,
            String alt,
            List<AccessibilityIssue> issues) {

        String sourceFileName =
                extractFileName(src);

        boolean sameAsSourceFile =
                sourceFileName != null
                        && normalizeFileName(sourceFileName)
                        .equals(normalizeFileName(alt));

        boolean looksLikeFileName =
                IMAGE_FILE_NAME_PATTERN
                        .matcher(alt)
                        .matches();

        if (!sameAsSourceFile && !looksLikeFileName) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_FILENAME_USED
                        )
                        .message(
                                "이미지 파일명이 대체 텍스트로 사용되었습니다."
                        )
                        .description(
                                "파일명은 이미지의 의미나 기능을 "
                                        + "설명하지 못합니다."
                        )
                        .recommendation(
                                "이미지의 핵심 내용 또는 기능을 설명하는 "
                                        + "대체 텍스트로 교체하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .currentValue(alt)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateRedundantPrefix(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            List<AccessibilityIssue> issues) {

        String normalized =
                alt.toLowerCase(Locale.ROOT);

        String matchedPrefix = null;

        for (String prefix : REDUNDANT_PREFIXES) {
            if (normalized.startsWith(
                    prefix.toLowerCase(Locale.ROOT))) {

                matchedPrefix = prefix;
                break;
            }
        }

        if (matchedPrefix == null) {
            return;
        }

        String suggestedValue =
                alt.substring(
                        Math.min(
                                matchedPrefix.length(),
                                alt.length()
                        )
                ).trim();

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_REDUNDANT_PREFIX
                        )
                        .message(
                                "대체 텍스트에 불필요한 이미지 설명 "
                                        + "접두어가 포함되어 있습니다."
                        )
                        .description(
                                "스크린 리더는 해당 요소가 이미지임을 "
                                        + "이미 알리므로 \"" + matchedPrefix
                                        + "\"와 같은 표현은 일반적으로 불필요합니다."
                        )
                        .recommendation(
                                "불필요한 접두어를 제거하고 이미지의 "
                                        + "핵심 내용부터 작성하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .values(
                                alt,
                                suggestedValue
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(
                                !suggestedValue.isBlank()
                        )
                        .manualReviewRequired(
                                suggestedValue.isBlank()
                        )
                        .build()
        );
    }

    private void validateCaptionDuplication(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_CHECK_CAPTION_DUPLICATION,
                        true
                );

        if (!enabled
                || !context.isIncludeInformationalIssues()) {

            return;
        }

        Element figure =
                findAncestorElement(
                        image,
                        "figure"
                );

        if (figure == null) {
            return;
        }

        Element figcaption =
                findDirectOrDescendantElement(
                        figure,
                        "figcaption"
                );

        if (figcaption == null) {
            return;
        }

        String caption =
                normalizeOptionalText(
                        figcaption.getTextContent()
                );

        if (caption == null) {
            return;
        }

        if (!normalizeComparableText(alt)
                .equals(normalizeComparableText(caption))) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_ALT_DUPLICATES_CAPTION
                        )
                        .message(
                                "이미지 대체 텍스트가 캡션과 동일합니다."
                        )
                        .description(
                                "스크린 리더 사용자가 동일한 내용을 "
                                        + "연속으로 들을 수 있습니다."
                        )
                        .recommendation(
                                "alt에는 이미지 자체의 핵심 의미를 제공하고, "
                                        + "figcaption에는 추가 문맥이나 설명을 "
                                        + "제공하도록 내용을 조정하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .currentValue(alt)
                        .relatedValue(caption)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateImageLinkName(
            AccessibilityRuleContext context,
            Element image,
            String alt,
            List<AccessibilityIssue> issues) {

        Element anchor =
                findAncestorElement(
                        image,
                        "a"
                );

        if (anchor == null) {
            return;
        }

        String linkText =
                extractLinkTextExcludingImages(anchor);

        String ariaLabel =
                normalizeOptionalText(
                        anchor.getAttribute("aria-label")
                );

        String ariaLabelledBy =
                normalizeOptionalText(
                        anchor.getAttribute("aria-labelledby")
                );

        boolean imageHasName =
                alt != null && !alt.isBlank();

        boolean linkHasName =
                imageHasName
                        || linkText != null
                        || ariaLabel != null
                        || ariaLabelledBy != null;

        if (linkHasName) {
            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .IMAGE_LINK_NAME_MISSING
                        )
                        .message(
                                "이미지 링크에 접근 가능한 이름이 없습니다."
                        )
                        .description(
                                "링크가 이미지로만 구성되어 있고, 이미지 alt와 "
                                        + "링크의 ARIA 레이블이 모두 비어 있습니다."
                        )
                        .recommendation(
                                "이미지 alt에 링크의 목적을 작성하거나 "
                                        + "링크 요소에 적절한 접근 가능한 이름을 "
                                        + "제공하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        image,
                                        "alt",
                                        alt
                                )
                        )
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "linkHref",
                                normalizeOptionalText(
                                        anchor.getAttribute("href")
                                )
                        )
                        .build()
        );
    }

    private AccessibilityIssue createMissingAltIssue(
            AccessibilityRuleContext context,
            Element image,
            String src) {

        return AccessibilityIssue.builder(
                        AccessibilityIssueCode
                                .IMAGE_ALT_MISSING
                )
                .message(
                        "이미지에 alt 속성이 없습니다."
                )
                .description(
                        "alt 속성이 없으면 스크린 리더 사용자가 "
                                + "이미지의 의미나 기능을 확인할 수 없습니다."
                )
                .recommendation(
                        "정보성 이미지에는 의미 있는 대체 텍스트를 "
                                + "작성하고, 장식 이미지에는 alt=\"\"를 "
                                + "적용하십시오."
                )
                .location(
                        createLocation(
                                context,
                                image,
                                "alt",
                                null
                        )
                )
                .currentValue(null)
                .ruleId(RULE_ID)
                .automaticallyFixable(false)
                .manualReviewRequired(true)
                .metadata("imageSource", src)
                .build();
    }

    private AccessibilityIssue createDecorativeAltIssue(
            AccessibilityRuleContext context,
            Element image,
            String alt) {

        return AccessibilityIssue.builder(
                        AccessibilityIssueCode
                                .DECORATIVE_IMAGE_ALT_NOT_EMPTY
                )
                .message(
                        "장식 이미지에 비어 있지 않은 alt가 설정되어 있습니다."
                )
                .description(
                        "장식 이미지는 보조기기에서 무시될 수 있도록 "
                                + "빈 alt를 사용해야 합니다."
                )
                .recommendation(
                        "alt=\"\"를 적용하고 필요하면 "
                                + "role=\"presentation\"을 사용하십시오."
                )
                .location(
                        createLocation(
                                context,
                                image,
                                "alt",
                                alt
                        )
                )
                .values(alt, "")
                .ruleId(RULE_ID)
                .automaticallyFixable(true)
                .manualReviewRequired(false)
                .build();
    }

    private NodeList findImageElements(
            AccessibilityRuleContext context) {

        NodeList nodes =
                context.getDocument()
                        .getElementsByTagNameNS(
                                XHTML_NAMESPACE,
                                "img"
                        );

        if (nodes.getLength() == 0) {
            nodes = context.getDocument()
                    .getElementsByTagName("img");
        }

        return nodes;
    }

    private AccessibilityLocation createLocation(
            AccessibilityRuleContext context,
            Element image,
            String attributeName,
            String attributeValue) {

        AccessibilityLocation.Builder builder =
                context.locationBuilder(image)
                        .xpath(createXPath(image))
                        .textExcerpt(
                                createElementExcerpt(image)
                        );

        if (attributeName != null) {
            builder.attribute(
                    attributeName,
                    attributeValue
            );
        }

        String src =
                normalizeOptionalText(
                        image.getAttribute("src")
                );

        if (src != null) {
            builder.metadata("imageSource", src);
        }

        return builder.build();
    }

    private Path resolveImagePath(
            Path documentPath,
            String src) {

        if (documentPath == null
                || src == null
                || src.isBlank()) {

            return null;
        }

        String cleanSource =
                removeFragmentAndQuery(src);

        if (cleanSource.isBlank()) {
            return null;
        }

        try {
            Path sourcePath =
                    Path.of(cleanSource);

            if (sourcePath.isAbsolute()) {
                return sourcePath
                        .toAbsolutePath()
                        .normalize();
            }

            Path parent =
                    documentPath.getParent();

            if (parent == null) {
                return null;
            }

            return parent.resolve(sourcePath)
                    .toAbsolutePath()
                    .normalize();

        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private String removeFragmentAndQuery(
            String value) {

        String result = value.trim();

        int fragmentIndex =
                result.indexOf('#');

        if (fragmentIndex >= 0) {
            result = result.substring(
                    0,
                    fragmentIndex
            );
        }

        int queryIndex =
                result.indexOf('?');

        if (queryIndex >= 0) {
            result = result.substring(
                    0,
                    queryIndex
            );
        }

        return result;
    }

    private boolean isExternalResource(
            String src) {

        String normalized =
                src.toLowerCase(Locale.ROOT);

        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//");
    }

    private boolean isDataUri(String src) {
        return src.toLowerCase(Locale.ROOT)
                .startsWith("data:");
    }

    private boolean isPresentationRole(
            String role) {

        if (role == null) {
            return false;
        }

        return PRESENTATION_ROLES.contains(
                role.toLowerCase(Locale.ROOT)
        );
    }

    private Element findAncestorElement(
            Element element,
            String expectedName) {

        Node parent = element.getParentNode();

        while (parent != null) {
            if (parent instanceof Element parentElement
                    && expectedName.equalsIgnoreCase(
                            resolveElementName(parentElement))) {

                return parentElement;
            }

            parent = parent.getParentNode();
        }

        return null;
    }

    private Element findDirectOrDescendantElement(
            Element parent,
            String expectedName) {

        NodeList descendants =
                parent.getElementsByTagNameNS(
                        XHTML_NAMESPACE,
                        expectedName
                );

        if (descendants.getLength() == 0) {
            descendants =
                    parent.getElementsByTagName(
                            expectedName
                    );
        }

        for (int index = 0;
                index < descendants.getLength();
                index++) {

            Node node =
                    descendants.item(index);

            if (node instanceof Element element) {
                return element;
            }
        }

        return null;
    }

    private String extractLinkTextExcludingImages(
            Element anchor) {

        StringBuilder text =
                new StringBuilder();

        collectTextExcludingImages(
                anchor,
                text
        );

        return normalizeOptionalText(
                text.toString()
        );
    }

    private void collectTextExcludingImages(
            Node node,
            StringBuilder target) {

        if (node == null) {
            return;
        }

        if (node instanceof Element element
                && "img".equalsIgnoreCase(
                        resolveElementName(element))) {

            return;
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

            collectTextExcludingImages(
                    children.item(index),
                    target
            );
        }
    }

    private String createXPath(
            Element element) {

        List<String> parts =
                new ArrayList<>();

        Node current = element;

        while (current instanceof Element currentElement) {
            String name =
                    resolveElementName(currentElement);

            String id =
                    normalizeOptionalText(
                            currentElement.getAttribute("id")
                    );

            if (id != null) {
                parts.add(
                        0,
                        name + "[@id='" + id + "']"
                );
                break;
            }

            int position =
                    calculateSiblingPosition(
                            currentElement
                    );

            parts.add(
                    0,
                    name + "[" + position + "]"
            );

            current =
                    currentElement.getParentNode();
        }

        if (parts.isEmpty()) {
            return null;
        }

        return "/" + String.join("/", parts);
    }

    private int calculateSiblingPosition(
            Element element) {

        int position = 1;

        Node sibling =
                element.getPreviousSibling();

        while (sibling != null) {
            if (sibling instanceof Element siblingElement
                    && resolveElementName(siblingElement)
                    .equalsIgnoreCase(
                            resolveElementName(element))) {

                position++;
            }

            sibling =
                    sibling.getPreviousSibling();
        }

        return position;
    }

    private String createElementExcerpt(
            Element image) {

        StringBuilder result =
                new StringBuilder();

        result.append("<img");

        appendAttributeExcerpt(
                result,
                image,
                "id"
        );

        appendAttributeExcerpt(
                result,
                image,
                "src"
        );

        if (image.hasAttribute("alt")) {
            result.append(" alt=\"");
            result.append(
                    truncate(
                            image.getAttribute("alt"),
                            120
                    )
            );
            result.append('"');
        }

        appendAttributeExcerpt(
                result,
                image,
                "role"
        );

        appendAttributeExcerpt(
                result,
                image,
                "aria-hidden"
        );

        result.append(" />");

        return result.toString();
    }

    private void appendAttributeExcerpt(
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

    private String extractFileName(
            String src) {

        if (src == null || src.isBlank()) {
            return null;
        }

        String clean =
                removeFragmentAndQuery(src)
                        .replace('\\', '/');

        int slashIndex =
                clean.lastIndexOf('/');

        if (slashIndex >= 0
                && slashIndex + 1 < clean.length()) {

            return clean.substring(
                    slashIndex + 1
            );
        }

        return clean;
    }

    private String normalizeFileName(
            String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
    }

    private String normalizeComparableText(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll(
                        "[\\p{Punct}]",
                        ""
                );
    }

    private String resolveElementName(
            Element element) {

        String localName =
                element.getLocalName();

        if (localName != null
                && !localName.isBlank()) {

            return localName;
        }

        return element.getTagName();
    }

    private String truncate(
            String value,
            int maximumLength) {

        if (value == null
                || value.length() <= maximumLength) {

            return value;
        }

        return value.substring(
                0,
                maximumLength
        );
    }

    private String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}