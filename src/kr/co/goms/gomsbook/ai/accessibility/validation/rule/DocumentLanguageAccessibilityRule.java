/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssue;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssueCode.AccessibilityCategory;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityLocation;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleContext;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRuleException;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilitySeverity;

/**
 * XHTML 문서 루트 요소의 {@code lang} 및 {@code xml:lang}
 * 접근성 속성을 검사한다.
 *
 * <p>다음 항목을 검사한다.</p>
 *
 * <ul>
 *   <li>{@code lang} 및 {@code xml:lang} 누락</li>
 *   <li>언어 코드 형식 오류</li>
 *   <li>{@code lang}과 {@code xml:lang} 값 불일치</li>
 *   <li>문서 컨텍스트 언어와 루트 언어의 불일치</li>
 * </ul>
 *
 * <p>이 규칙은 문서를 수정하지 않고 접근성 문제 목록만 반환한다.</p>
 */
public final class DocumentLanguageAccessibilityRule
        implements AccessibilityRule {

    public static final String RULE_ID = "document-language";

    public static final String OPTION_REQUIRE_XML_LANG =
            "documentLanguage.requireXmlLang";

    public static final String OPTION_VALIDATE_CONTEXT_LANGUAGE =
            "documentLanguage.validateContextLanguage";

    public static final String OPTION_ALLOW_UND =
            "documentLanguage.allowUnd";

    private static final String XML_NAMESPACE =
            "http://www.w3.org/XML/1998/namespace";

    /**
     * 일반적인 BCP 47 언어 태그 형식을 검사한다.
     *
     * <p>완전한 BCP 47 파서는 아니지만 EPUB 문서에서 주로 사용하는
     * 다음 형식을 허용한다.</p>
     *
     * <ul>
     *   <li>{@code ko}</li>
     *   <li>{@code en}</li>
     *   <li>{@code en-US}</li>
     *   <li>{@code zh-Hans}</li>
     *   <li>{@code zh-Hant-TW}</li>
     * </ul>
     */
    private static final Pattern LANGUAGE_TAG_PATTERN =
            Pattern.compile(
                    "^[A-Za-z]{2,8}"
                            + "(?:-[A-Za-z]{4})?"
                            + "(?:-(?:[A-Za-z]{2}|[0-9]{3}))?"
                            + "(?:-[A-Za-z0-9]{5,8}"
                            + "|-[0-9][A-Za-z0-9]{3})*$"
            );

    private static final Set<String> COMMON_LANGUAGE_CODES =
            Set.of(
                    "ko",
                    "en",
                    "ja",
                    "zh",
                    "fr",
                    "de",
                    "es",
                    "it",
                    "pt",
                    "ru",
                    "ar",
                    "vi",
                    "th",
                    "id"
            );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getDisplayName() {
        return "문서 언어 속성 검사";
    }

    @Override
    public AccessibilityCategory getCategory() {
        return AccessibilityCategory.DOCUMENT;
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
        return 10;
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
            Element root =
                    context.getDocumentElement();

            if (root == null) {
                throw new AccessibilityRuleException(
                        RULE_ID,
                        context.getDocumentPath(),
                        "Document root element is missing."
                );
            }

            List<AccessibilityIssue> issues =
                    new ArrayList<>();

            String lang =
                    normalizeLanguageTag(
                            root.getAttribute("lang")
                    );

            String xmlLang =
                    normalizeLanguageTag(
                            root.getAttributeNS(
                                    XML_NAMESPACE,
                                    "lang"
                            )
                    );

            /*
             * namespace-aware 파싱이 아닌 문서나 잘못된 XHTML을
             * 대비하여 접두사 형태도 확인한다.
             */
            if (xmlLang == null) {
                xmlLang = normalizeLanguageTag(
                        root.getAttribute("xml:lang")
                );
            }

            validateMissingLanguage(
                    context,
                    root,
                    lang,
                    xmlLang,
                    issues
            );

            validateLanguageFormat(
                    context,
                    root,
                    "lang",
                    lang,
                    issues
            );

            validateLanguageFormat(
                    context,
                    root,
                    "xml:lang",
                    xmlLang,
                    issues
            );

            validateLanguageMismatch(
                    context,
                    root,
                    lang,
                    xmlLang,
                    issues
            );

            validateRequiredXmlLanguage(
                    context,
                    root,
                    lang,
                    xmlLang,
                    issues
            );

            validateContextLanguage(
                    context,
                    root,
                    lang,
                    xmlLang,
                    issues
            );

            return List.copyOf(issues);

        } catch (AccessibilityRuleException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new AccessibilityRuleException(
                    RULE_ID,
                    context.getDocumentPath(),
                    "Failed to validate document language.",
                    exception
            );
        }
    }

    private void validateMissingLanguage(
            AccessibilityRuleContext context,
            Element root,
            String lang,
            String xmlLang,
            List<AccessibilityIssue> issues) {

        if (lang != null || xmlLang != null) {
            return;
        }

        String suggestedLanguage =
                resolveSuggestedLanguage(context);

        AccessibilityIssue.Builder builder =
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .DOCUMENT_LANGUAGE_MISSING
                        )
                        .message(
                                "문서 루트 요소에 언어 속성이 없습니다."
                        )
                        .description(
                                "스크린 리더와 읽기 시스템이 문서의 "
                                        + "기본 발음 언어를 판단할 수 있도록 "
                                        + "html 요소에 lang 또는 xml:lang이 "
                                        + "필요합니다."
                        )
                        .recommendation(
                                "html 요소에 문서의 기본 언어를 나타내는 "
                                        + "lang과 xml:lang 속성을 "
                                        + "설정하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        root,
                                        "lang",
                                        null
                                )
                        )
                        .currentValue(null)
                        .ruleId(RULE_ID)
                        .manualReviewRequired(
                                suggestedLanguage == null
                        );

        if (suggestedLanguage != null) {
            builder.suggestedValue(
                    suggestedLanguage
            );
            builder.automaticallyFixable(true);
        } else {
            builder.automaticallyFixable(false);
        }

        issues.add(builder.build());
    }

    private void validateLanguageFormat(
            AccessibilityRuleContext context,
            Element root,
            String attributeName,
            String language,
            List<AccessibilityIssue> issues) {

        if (language == null) {
            return;
        }

        if (isValidLanguageTag(
                context,
                language)) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .DOCUMENT_LANGUAGE_INVALID
                        )
                        .message(
                                "문서 언어 코드 형식이 올바르지 않습니다."
                        )
                        .description(
                                attributeName
                                        + " 속성값 \""
                                        + language
                                        + "\"이 유효한 언어 태그 형식과 "
                                        + "일치하지 않습니다."
                        )
                        .recommendation(
                                "ko, en, en-US, zh-Hans와 같은 "
                                        + "BCP 47 형식의 언어 태그를 "
                                        + "사용하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        root,
                                        attributeName,
                                        language
                                )
                        )
                        .currentValue(language)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .build()
        );
    }

    private void validateLanguageMismatch(
            AccessibilityRuleContext context,
            Element root,
            String lang,
            String xmlLang,
            List<AccessibilityIssue> issues) {

        if (lang == null
                || xmlLang == null
                || languageTagsEqual(lang, xmlLang)) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .DOCUMENT_LANGUAGE_MISMATCH
                        )
                        .message(
                                "lang과 xml:lang 속성값이 서로 다릅니다."
                        )
                        .description(
                                "동일한 문서에 서로 다른 기본 언어가 "
                                        + "선언되어 읽기 시스템마다 다른 "
                                        + "발음 규칙이 적용될 수 있습니다."
                        )
                        .recommendation(
                                "lang과 xml:lang에 동일한 언어 태그를 "
                                        + "사용하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        root,
                                        "lang",
                                        lang
                                )
                        )
                        .currentValue(
                                "lang="
                                        + lang
                                        + ", xml:lang="
                                        + xmlLang
                        )
                        .suggestedValue(lang)
                        .relatedValue(xmlLang)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(true)
                        .manualReviewRequired(false)
                        .build()
        );
    }

    private void validateRequiredXmlLanguage(
            AccessibilityRuleContext context,
            Element root,
            String lang,
            String xmlLang,
            List<AccessibilityIssue> issues) {

        boolean requireXmlLang =
                context.getBooleanOption(
                        OPTION_REQUIRE_XML_LANG,
                        true
                );

        if (!requireXmlLang
                || lang == null
                || xmlLang != null) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .DOCUMENT_LANGUAGE_MISMATCH
                        )
                        .severity(AccessibilitySeverity.INFO)
                        .message(
                                "xml:lang 속성이 설정되지 않았습니다."
                        )
                        .description(
                                "EPUB XHTML 호환성을 위해 lang과 함께 "
                                        + "xml:lang을 동일한 값으로 설정할 수 "
                                        + "있습니다."
                        )
                        .recommendation(
                                "xml:lang=\""
                                        + lang
                                        + "\"을 추가하십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        root,
                                        "xml:lang",
                                        null
                                )
                        )
                        .currentValue(null)
                        .suggestedValue(lang)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(true)
                        .manualReviewRequired(false)
                        .build()
        );
    }

    private void validateContextLanguage(
            AccessibilityRuleContext context,
            Element root,
            String lang,
            String xmlLang,
            List<AccessibilityIssue> issues) {

        boolean enabled =
                context.getBooleanOption(
                        OPTION_VALIDATE_CONTEXT_LANGUAGE,
                        true
                );

        if (!enabled) {
            return;
        }

        String contextLanguage =
                normalizeLanguageTag(
                        context.getDocumentLanguage()
                );

        String rootLanguage =
                lang != null ? lang : xmlLang;

        if (contextLanguage == null
                || rootLanguage == null
                || languageTagsEqual(
                        contextLanguage,
                        rootLanguage)) {

            return;
        }

        issues.add(
                AccessibilityIssue.builder(
                                AccessibilityIssueCode
                                        .DOCUMENT_LANGUAGE_MISMATCH
                        )
                        .severity(AccessibilitySeverity.WARNING)
                        .message(
                                "문서 컨텍스트 언어와 루트 언어가 다릅니다."
                        )
                        .description(
                                "검사 컨텍스트의 문서 언어는 \""
                                        + contextLanguage
                                        + "\"이지만 html 요소의 언어는 \""
                                        + rootLanguage
                                        + "\"입니다."
                        )
                        .recommendation(
                                "실제 문서 내용에 맞는 언어를 확인한 뒤 "
                                        + "문서 메타데이터와 html 언어 속성을 "
                                        + "일치시키십시오."
                        )
                        .location(
                                createLocation(
                                        context,
                                        root,
                                        lang != null
                                                ? "lang"
                                                : "xml:lang",
                                        rootLanguage
                                )
                        )
                        .currentValue(rootLanguage)
                        .suggestedValue(contextLanguage)
                        .ruleId(RULE_ID)
                        .automaticallyFixable(false)
                        .manualReviewRequired(true)
                        .metadata(
                                "contextLanguage",
                                contextLanguage
                        )
                        .build()
        );
    }

    private boolean isValidLanguageTag(
            AccessibilityRuleContext context,
            String language) {

        if (language == null) {
            return false;
        }

        String normalized =
                language.toLowerCase(Locale.ROOT);

        if ("und".equals(normalized)) {
            return context.getBooleanOption(
                    OPTION_ALLOW_UND,
                    false
            );
        }

        if (!LANGUAGE_TAG_PATTERN
                .matcher(language)
                .matches()) {

            return false;
        }

        String primaryLanguage =
                normalized.split("-", 2)[0];

        /*
         * 2~3자리 언어 코드는 일반적으로 허용한다.
         * 긴 사용자 정의 또는 희귀 태그는 정규식 검증 결과를 따른다.
         */
        return COMMON_LANGUAGE_CODES
                .contains(primaryLanguage)
                || primaryLanguage.length() >= 2;
    }

    private String resolveSuggestedLanguage(
            AccessibilityRuleContext context) {

        String language =
                normalizeLanguageTag(
                        context.getDocumentLanguage()
                );

        if (language != null
                && isValidLanguageTag(
                        context,
                        language)) {

            return canonicalizeLanguageTag(language);
        }

        language = normalizeLanguageTag(
                context.getMetadata(
                        "language"
                )
        );

        if (language != null
                && isValidLanguageTag(
                        context,
                        language)) {

            return canonicalizeLanguageTag(language);
        }

        language = normalizeLanguageTag(
                context.getMetadata(
                        "dc:language"
                )
        );

        if (language != null
                && isValidLanguageTag(
                        context,
                        language)) {

            return canonicalizeLanguageTag(language);
        }

        return null;
    }

    private AccessibilityLocation createLocation(
            AccessibilityRuleContext context,
            Element root,
            String attributeName,
            String attributeValue) {

        AccessibilityLocation.Builder builder =
                context.locationBuilder(root)
                        .xpath(
                                "/"
                                        + resolveElementName(root)
                                        + "[1]"
                        )
                        .textExcerpt(
                                createElementExcerpt(root)
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
            Element root) {

        StringBuilder result =
                new StringBuilder();

        result.append('<');
        result.append(
                resolveElementName(root)
        );

        appendAttribute(
                result,
                root,
                "lang"
        );

        String xmlLang =
                root.getAttributeNS(
                        XML_NAMESPACE,
                        "lang"
                );

        if (xmlLang == null
                || xmlLang.isBlank()) {

            xmlLang =
                    root.getAttribute("xml:lang");
        }

        if (xmlLang != null
                && !xmlLang.isBlank()) {

            result.append(" xml:lang=\"");
            result.append(xmlLang.trim());
            result.append('"');
        }

        result.append('>');

        return result.toString();
    }

    private void appendAttribute(
            StringBuilder target,
            Element element,
            String attributeName) {

        if (!element.hasAttribute(
                attributeName)) {

            return;
        }

        target.append(' ');
        target.append(attributeName);
        target.append("=\"");
        target.append(
                element.getAttribute(
                        attributeName
                )
        );
        target.append('"');
    }

    private boolean languageTagsEqual(
            String first,
            String second) {

        if (first == null || second == null) {
            return false;
        }

        return canonicalizeLanguageTag(first)
                .equalsIgnoreCase(
                        canonicalizeLanguageTag(second)
                );
    }

    private String canonicalizeLanguageTag(
            String language) {

        String normalized =
                normalizeLanguageTag(language);

        if (normalized == null) {
            return "";
        }

        String[] parts =
                normalized.split("-");

        StringBuilder result =
                new StringBuilder();

        for (int index = 0;
                index < parts.length;
                index++) {

            String part = parts[index];

            if (index > 0) {
                result.append('-');
            }

            if (index == 0) {
                result.append(
                        part.toLowerCase(
                                Locale.ROOT
                        )
                );

            } else if (part.length() == 4) {
                result.append(
                        part.substring(0, 1)
                                .toUpperCase(Locale.ROOT)
                );
                result.append(
                        part.substring(1)
                                .toLowerCase(Locale.ROOT)
                );

            } else if (part.length() == 2
                    || part.matches("[0-9]{3}")) {

                result.append(
                        part.toUpperCase(Locale.ROOT)
                );

            } else {
                result.append(
                        part.toLowerCase(Locale.ROOT)
                );
            }
        }

        return result.toString();
    }

    private String normalizeLanguageTag(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .replace('_', '-');

        return normalized.isEmpty()
                ? null
                : normalized;
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
}