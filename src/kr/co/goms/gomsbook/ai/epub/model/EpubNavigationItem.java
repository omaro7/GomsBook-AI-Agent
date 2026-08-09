/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB Navigation Document의 개별 탐색 항목을 표현합니다.
 *
 * <p>일반적으로 {@code nav.xhtml}의 {@code ol/li/a} 구조와 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <ol>
 *     <li>
 *         <a href="Text/chapter01.xhtml">1장</a>
 *         <ol>
 *             <li>
 *                 <a href="Text/chapter01.xhtml#section01">
 *                     첫 번째 절
 *                 </a>
 *             </li>
 *         </ol>
 *     </li>
 * </ol>
 * }
 * </pre>
 *
 * <p>이 클래스는 계층형 탐색 구조를 지원하며, 하나의 항목은
 * 다음 정보를 가질 수 있습니다.</p>
 *
 * <ul>
 *     <li>표시 제목</li>
 *     <li>대상 href</li>
 *     <li>선택적 ID</li>
 *     <li>EPUB type</li>
 *     <li>언어</li>
 *     <li>텍스트 방향</li>
 *     <li>하위 탐색 항목</li>
 * </ul>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubNavigationItem {

    /**
     * 탐색 항목의 선택적 ID입니다.
     */
    private final String id;

    /**
     * 사용자에게 표시할 탐색 제목입니다.
     */
    private final String label;

    /**
     * 탐색 대상 EPUB 상대 경로입니다.
     *
     * <p>예: {@code Text/chapter01.xhtml},
     * {@code Text/chapter01.xhtml#section01}</p>
     */
    private final String href;

    /**
     * EPUB semantic type입니다.
     *
     * <p>예: {@code chapter}, {@code part}, {@code appendix}</p>
     */
    private final String epubType;

    /**
     * BCP 47 언어 태그입니다.
     */
    private final String language;

    /**
     * 탐색 라벨의 텍스트 방향입니다.
     */
    private final TextDirection direction;

    /**
     * 하위 탐색 항목입니다.
     */
    private final List<EpubNavigationItem> children;

    /**
     * 항목을 Navigation Document에 포함할지 여부입니다.
     */
    private final boolean included;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubNavigationItem(Builder builder) {
        this.id = normalizeOptionalIdentifier(builder.id);
        this.label = requireLabel(builder.label);
        this.href = normalizeHref(builder.href);
        this.epubType = normalizeOptionalToken(builder.epubType);
        this.language = normalizeLanguage(builder.language);
        this.direction = builder.direction;
        this.children = immutableChildren(builder.children);
        this.included = builder.included;
        this.description = normalizeOptionalText(builder.description);

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(
            String label,
            String href
    ) {
        return new Builder()
                .label(label)
                .href(href);
    }

    /**
     * 기본 탐색 항목을 생성합니다.
     *
     * @param label 표시 제목
     * @param href  대상 경로
     * @return 탐색 항목
     */
    public static EpubNavigationItem of(
            String label,
            String href
    ) {
        return builder(label, href).build();
    }

    /**
     * chapter 타입 탐색 항목을 생성합니다.
     *
     * @param label 표시 제목
     * @param href  대상 경로
     * @return chapter 탐색 항목
     */
    public static EpubNavigationItem chapter(
            String label,
            String href
    ) {
        return builder(label, href)
                .epubType("chapter")
                .build();
    }

    /**
     * part 타입 탐색 항목을 생성합니다.
     *
     * @param label 표시 제목
     * @param href  대상 경로
     * @return part 탐색 항목
     */
    public static EpubNavigationItem part(
            String label,
            String href
    ) {
        return builder(label, href)
                .epubType("part")
                .build();
    }

    public Optional<String> getId() {
        return Optional.ofNullable(id);
    }

    public String getLabel() {
        return label;
    }

    public String getHref() {
        return href;
    }

    public Optional<String> getEpubType() {
        return Optional.ofNullable(epubType);
    }

    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    public Optional<TextDirection> getDirection() {
        return Optional.ofNullable(direction);
    }

    public List<EpubNavigationItem> getChildren() {
        return children;
    }

    public boolean isIncluded() {
        return included;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * 하위 항목이 존재하는지 확인합니다.
     *
     * @return 하위 항목이 있으면 {@code true}
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * 리프 탐색 항목인지 확인합니다.
     *
     * @return 하위 항목이 없으면 {@code true}
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * fragment를 포함한 href인지 확인합니다.
     *
     * @return fragment가 있으면 {@code true}
     */
    public boolean hasFragment() {
        return href.indexOf('#') >= 0;
    }

    /**
     * href의 fragment를 제외한 문서 경로를 반환합니다.
     *
     * @return 문서 경로
     */
    public String getDocumentHref() {
        int fragmentIndex = href.indexOf('#');

        return fragmentIndex < 0
                ? href
                : href.substring(0, fragmentIndex);
    }

    /**
     * href의 fragment ID를 반환합니다.
     *
     * @return fragment ID
     */
    public Optional<String> getFragment() {
        int fragmentIndex = href.indexOf('#');

        if (fragmentIndex < 0
                || fragmentIndex == href.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(
                href.substring(fragmentIndex + 1)
        );
    }

    /**
     * 지정한 EPUB type인지 확인합니다.
     *
     * @param expectedType 기대 type
     * @return 일치하면 {@code true}
     */
    public boolean hasEpubType(String expectedType) {
        if (epubType == null
                || expectedType == null
                || expectedType.isBlank()) {
            return false;
        }

        return epubType.equalsIgnoreCase(
                expectedType.trim()
        );
    }

    public boolean isChapter() {
        return hasEpubType("chapter");
    }

    public boolean isPart() {
        return hasEpubType("part");
    }

    public boolean isAppendix() {
        return hasEpubType("appendix");
    }

    public boolean isCover() {
        return hasEpubType("cover");
    }

    /**
     * 전체 하위 항목 수를 재귀적으로 계산합니다.
     *
     * @return 현재 항목을 제외한 하위 항목 수
     */
    public int getDescendantCount() {
        int count = 0;

        for (EpubNavigationItem child : children) {
            count++;
            count += child.getDescendantCount();
        }

        return count;
    }

    /**
     * 현재 항목을 포함한 전체 노드 수를 반환합니다.
     *
     * @return 현재 항목 + 모든 하위 항목 수
     */
    public int getTreeSize() {
        return 1 + getDescendantCount();
    }

    /**
     * 현재 항목 아래의 최대 깊이를 반환합니다.
     *
     * <p>리프 항목의 깊이는 1입니다.</p>
     *
     * @return 탐색 트리 깊이
     */
    public int getDepth() {
        if (children.isEmpty()) {
            return 1;
        }

        int maxChildDepth = 0;

        for (EpubNavigationItem child : children) {
            maxChildDepth = Math.max(
                    maxChildDepth,
                    child.getDepth()
            );
        }

        return 1 + maxChildDepth;
    }

    /**
     * 지정한 href를 가진 항목을 현재 트리에서 검색합니다.
     *
     * @param targetHref 검색할 href
     * @return 검색된 항목
     */
    public Optional<EpubNavigationItem> findByHref(
            String targetHref
    ) {
        String normalized = normalizeLookupHref(targetHref);

        if (normalized == null) {
            return Optional.empty();
        }

        if (href.equals(normalized)) {
            return Optional.of(this);
        }

        for (EpubNavigationItem child : children) {
            Optional<EpubNavigationItem> result =
                    child.findByHref(normalized);

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * 지정한 ID를 가진 항목을 현재 트리에서 검색합니다.
     *
     * @param targetId 검색할 ID
     * @return 검색된 항목
     */
    public Optional<EpubNavigationItem> findById(
            String targetId
    ) {
        String normalized = normalizeOptionalText(targetId);

        if (normalized == null) {
            return Optional.empty();
        }

        if (id != null && id.equals(normalized)) {
            return Optional.of(this);
        }

        for (EpubNavigationItem child : children) {
            Optional<EpubNavigationItem> result =
                    child.findById(normalized);

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * 현재 트리를 pre-order 순서로 평탄화합니다.
     *
     * @return 현재 항목을 포함한 전체 탐색 항목
     */
    public List<EpubNavigationItem> flatten() {
        List<EpubNavigationItem> result =
                new ArrayList<>();

        flattenInto(this, result);

        return Collections.unmodifiableList(result);
    }

    private static void flattenInto(
            EpubNavigationItem item,
            List<EpubNavigationItem> result
    ) {
        result.add(item);

        for (EpubNavigationItem child : item.children) {
            flattenInto(child, result);
        }
    }

    /**
     * 현재 항목을 기반으로 Builder를 생성합니다.
     *
     * @return 복사된 Builder
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .label(label)
                .href(href)
                .epubType(epubType)
                .language(language)
                .direction(direction)
                .children(children)
                .included(included)
                .description(description);
    }

    private void validate() {
        if (hasFragment()) {
            String fragment = getFragment()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "EPUB navigation href contains "
                                            + "an empty fragment: "
                                            + href
                            )
                    );

            if (fragment.isBlank()) {
                throw new IllegalArgumentException(
                        "EPUB navigation fragment must not be blank: "
                                + href
                );
            }
        }

        if (id != null) {
            for (EpubNavigationItem child : children) {
                if (child.findById(id).isPresent()) {
                    throw new IllegalArgumentException(
                            "Duplicate EPUB navigation item id "
                                    + "inside subtree: "
                                    + id
                    );
                }
            }
        }

        validateDuplicateChildIds();
        validateDuplicateChildHrefs();
    }

    private void validateDuplicateChildIds() {
        List<String> ids = new ArrayList<>();

        for (EpubNavigationItem child : children) {
            for (EpubNavigationItem item : child.flatten()) {
                item.getId().ifPresent(itemId -> {
                    if (ids.contains(itemId)) {
                        throw new IllegalArgumentException(
                                "Duplicate EPUB navigation item id: "
                                        + itemId
                        );
                    }

                    ids.add(itemId);
                });
            }
        }
    }

    private void validateDuplicateChildHrefs() {
        /*
         * 동일 href가 목차에서 반드시 오류는 아니므로
         * 여기서는 직접적인 동일 형제 항목만 방지합니다.
         */
        List<String> hrefs = new ArrayList<>();

        for (EpubNavigationItem child : children) {
            if (hrefs.contains(child.getHref())) {
                throw new IllegalArgumentException(
                        "Duplicate EPUB navigation child href: "
                                + child.getHref()
                );
            }

            hrefs.add(child.getHref());
        }
    }

    private static List<EpubNavigationItem> immutableChildren(
            Collection<EpubNavigationItem> children
    ) {
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubNavigationItem> result =
                new ArrayList<>();

        for (EpubNavigationItem child : children) {
            result.add(
                    Objects.requireNonNull(
                            child,
                            "EPUB navigation child must not be null."
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    private static String requireLabel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB navigation label must not be blank."
            );
        }

        return value.trim();
    }

    private static String normalizeHref(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB navigation href must not be blank."
            );
        }

        String normalized = value.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        while (normalized.contains("//")
                && !normalized.startsWith("http://")
                && !normalized.startsWith("https://")) {
            normalized = normalized.replace("//", "/");
        }

        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException(
                    "EPUB navigation href must be relative: "
                            + value
            );
        }

        if (containsParentTraversal(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB navigation href must not contain "
                            + "parent traversal: "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeLookupHref(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return normalizeHref(value);
    }

    private static boolean containsParentTraversal(
            String value
    ) {
        String path = value;

        int fragmentIndex = path.indexOf('#');

        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }

        int queryIndex = path.indexOf('?');

        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }

        return false;
    }

    private static String normalizeOptionalIdentifier(
            String value
    ) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB navigation item id: "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeOptionalToken(
            String value
    ) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB navigation token must not contain "
                            + "whitespace: "
                            + value
            );
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeLanguage(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.replace('_', '-');

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB navigation language tag: "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        char first = value.charAt(0);

        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }

        for (int index = 1;
                index < value.length();
                index++) {

            char character = value.charAt(index);

            if (!(Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == '.')) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0;
                index < value.length();
                index++) {

            if (Character.isWhitespace(
                    value.charAt(index)
            )) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubNavigationItem other)) {
            return false;
        }

        if (id != null && other.id != null) {
            return id.equals(other.id);
        }

        return label.equals(other.label)
                && href.equals(other.href);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }

        return Objects.hash(label, href);
    }

    @Override
    public String toString() {
        return "EpubNavigationItem{"
                + "id='" + id + '\''
                + ", label='" + label + '\''
                + ", href='" + href + '\''
                + ", epubType='" + epubType + '\''
                + ", childCount=" + children.size()
                + ", included=" + included
                + '}';
    }

    /**
     * Navigation Document의 텍스트 방향입니다.
     */
    public enum TextDirection {

        LEFT_TO_RIGHT("ltr"),

        RIGHT_TO_LEFT("rtl"),

        AUTO("auto");

        private final String value;

        TextDirection(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public boolean isAuto() {
            return this == AUTO;
        }

        public static Optional<TextDirection> from(
                String value
        ) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }

            String normalized = value.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace('_', '-');

            return switch (normalized) {
                case "ltr", "left-to-right" ->
                        Optional.of(LEFT_TO_RIGHT);

                case "rtl", "right-to-left" ->
                        Optional.of(RIGHT_TO_LEFT);

                case "auto" ->
                        Optional.of(AUTO);

                default ->
                        Optional.empty();
            };
        }

        public static TextDirection require(
                String value
        ) {
            return from(value)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Unsupported EPUB navigation "
                                            + "text direction: "
                                            + value
                            )
                    );
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * {@link EpubNavigationItem} 생성 Builder입니다.
     */
    public static final class Builder {

        private String id;

        private String label;

        private String href;

        private String epubType;

        private String language;

        private TextDirection direction;

        private final List<EpubNavigationItem> children =
                new ArrayList<>();

        private boolean included = true;

        private String description;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder href(String href) {
            this.href = href;
            return this;
        }

        public Builder epubType(String epubType) {
            this.epubType = epubType;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder direction(
                TextDirection direction
        ) {
            this.direction = direction;
            return this;
        }

        public Builder direction(String direction) {
            this.direction = direction == null
                    ? null
                    : TextDirection.require(direction);

            return this;
        }

        public Builder child(
                EpubNavigationItem child
        ) {
            children.add(
                    Objects.requireNonNull(
                            child,
                            "EPUB navigation child "
                                    + "must not be null."
                    )
            );

            return this;
        }

        public Builder children(
                Collection<EpubNavigationItem> children
        ) {
            if (children == null) {
                return this;
            }

            for (EpubNavigationItem child : children) {
                child(child);
            }

            return this;
        }

        public Builder clearChildren() {
            children.clear();
            return this;
        }

        public Builder included(boolean included) {
            this.included = included;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public EpubNavigationItem build() {
            return new EpubNavigationItem(this);
        }
    }
}