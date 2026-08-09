/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EPUB 3 Navigation Document의 전체 탐색 구조를 표현합니다.
 *
 * <p>일반적으로 {@code nav.xhtml} 내부의 다음 탐색 영역을 관리합니다.</p>
 *
 * <ul>
 *     <li>toc - 출판물 목차</li>
 *     <li>landmarks - 주요 구조적 위치</li>
 *     <li>page-list - 인쇄본 기준 페이지 목록</li>
 * </ul>
 *
 * <pre>
 * {@code
 * <nav epub:type="toc">
 *     <h1>차례</h1>
 *     <ol>
 *         ...
 *     </ol>
 * </nav>
 *
 * <nav epub:type="landmarks">
 *     <h2>Landmarks</h2>
 *     <ol>
 *         ...
 *     </ol>
 * </nav>
 *
 * <nav epub:type="page-list">
 *     <h2>Pages</h2>
 *     <ol>
 *         ...
 *     </ol>
 * </nav>
 * }
 * </pre>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubNavigation {

    public static final String DEFAULT_TOC_TITLE = "차례";

    public static final String DEFAULT_LANDMARKS_TITLE = "Landmarks";

    public static final String DEFAULT_PAGE_LIST_TITLE = "Pages";

    private final String title;

    private final String language;

    private final EpubNavigationItem.TextDirection direction;

    private final List<EpubNavigationItem> tocItems;

    private final List<EpubNavigationItem> landmarkItems;

    private final List<EpubNavigationItem> pageListItems;

    private final Map<String, EpubNavigationItem> itemsById;

    private final String tocTitle;

    private final String landmarksTitle;

    private final String pageListTitle;

    private final boolean includeLandmarks;

    private final boolean includePageList;

    private final String description;

    private EpubNavigation(Builder builder) {
        this.title = requireText(builder.title, "navigation title");
        this.language = normalizeLanguage(builder.language);
        this.direction = builder.direction == null
                ? EpubNavigationItem.TextDirection.AUTO
                : builder.direction;

        this.tocItems = immutableItems(builder.tocItems);
        this.landmarkItems = immutableItems(builder.landmarkItems);
        this.pageListItems = immutableItems(builder.pageListItems);

        this.tocTitle = normalizeTextOrDefault(
                builder.tocTitle,
                DEFAULT_TOC_TITLE
        );

        this.landmarksTitle = normalizeTextOrDefault(
                builder.landmarksTitle,
                DEFAULT_LANDMARKS_TITLE
        );

        this.pageListTitle = normalizeTextOrDefault(
                builder.pageListTitle,
                DEFAULT_PAGE_LIST_TITLE
        );

        this.includeLandmarks = builder.includeLandmarks;
        this.includePageList = builder.includePageList;
        this.description = normalizeOptionalText(builder.description);

        this.itemsById = buildIdIndex();

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EpubNavigation of(
            String title,
            Collection<EpubNavigationItem> tocItems
    ) {
        return builder()
                .title(title)
                .tocItems(tocItems)
                .build();
    }

    public String getTitle() {
        return title;
    }

    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    public EpubNavigationItem.TextDirection getDirection() {
        return direction;
    }

    public List<EpubNavigationItem> getTocItems() {
        return tocItems;
    }

    public List<EpubNavigationItem> getLandmarkItems() {
        return landmarkItems;
    }

    public List<EpubNavigationItem> getPageListItems() {
        return pageListItems;
    }

    public String getTocTitle() {
        return tocTitle;
    }

    public String getLandmarksTitle() {
        return landmarksTitle;
    }

    public String getPageListTitle() {
        return pageListTitle;
    }

    public boolean isIncludeLandmarks() {
        return includeLandmarks;
    }

    public boolean isIncludePageList() {
        return includePageList;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public boolean hasToc() {
        return !tocItems.isEmpty();
    }

    public boolean hasLandmarks() {
        return includeLandmarks && !landmarkItems.isEmpty();
    }

    public boolean hasPageList() {
        return includePageList && !pageListItems.isEmpty();
    }

    public int getTocItemCount() {
        return flatten(tocItems).size();
    }

    public int getLandmarkItemCount() {
        return flatten(landmarkItems).size();
    }

    public int getPageListItemCount() {
        return flatten(pageListItems).size();
    }

    public int getTotalItemCount() {
        return getTocItemCount()
                + getLandmarkItemCount()
                + getPageListItemCount();
    }

    /**
     * TOC 트리의 최대 깊이를 반환합니다.
     */
    public int getTocDepth() {
        return getDepth(tocItems);
    }

    /**
     * 전체 탐색 항목 중 ID로 검색합니다.
     */
    public Optional<EpubNavigationItem> findById(String id) {
        String normalized = normalizeOptionalText(id);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(itemsById.get(normalized));
    }

    /**
     * 전체 탐색 항목에서 href로 검색합니다.
     */
    public Optional<EpubNavigationItem> findByHref(String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }

        for (EpubNavigationItem item : getAllItems()) {
            Optional<EpubNavigationItem> found =
                    item.findByHref(href);

            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    /**
     * 전체 탐색 항목을 pre-order 순서로 반환합니다.
     */
    public List<EpubNavigationItem> flattenAll() {
        List<EpubNavigationItem> result = new ArrayList<>();

        result.addAll(flatten(tocItems));
        result.addAll(flatten(landmarkItems));
        result.addAll(flatten(pageListItems));

        return Collections.unmodifiableList(result);
    }

    public List<EpubNavigationItem> filter(
            Predicate<EpubNavigationItem> predicate
    ) {
        Objects.requireNonNull(
                predicate,
                "EPUB navigation predicate must not be null."
        );

        return flattenAll()
                .stream()
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Navigation Document가 참조하는 모든 문서 href를 반환합니다.
     */
    public Set<String> getReferencedDocumentHrefs() {
        return flattenAll()
                .stream()
                .filter(EpubNavigationItem::isIncluded)
                .map(EpubNavigationItem::getDocumentHref)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * manifest와 Navigation Document 참조를 검증합니다.
     */
    public void validate(EpubManifest manifest) {
        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        validate();

        for (EpubNavigationItem item : flattenAll()) {
            if (!item.isIncluded()) {
                continue;
            }

            String documentHref = item.getDocumentHref();

            boolean exists = manifest.getResources()
                    .stream()
                    .anyMatch(resource ->
                            normalizeHref(resource.getHref())
                                    .equals(normalizeHref(documentHref))
                    );

            if (!exists) {
                throw new IllegalStateException(
                        "EPUB navigation item references "
                                + "a resource not present in manifest: "
                                + item.getLabel()
                                + " -> "
                                + item.getHref()
                );
            }
        }
    }

    /**
     * EPUB 3 기준 Navigation Document 구조를 검증합니다.
     */
    public void validate(EpubVersion version) {
        Objects.requireNonNull(
                version,
                "EPUB version must not be null."
        );

        if (!version.isEpub3()) {
            throw new IllegalStateException(
                    "EPUB Navigation Document is only available "
                            + "for EPUB 3: "
                            + version
            );
        }

        validate();
    }

    /**
     * 기본 구조 검증입니다.
     */
    public void validate() {
        if (tocItems.isEmpty()) {
            throw new IllegalStateException(
                    "EPUB Navigation Document requires "
                            + "at least one TOC item."
            );
        }

        validateUniqueIds();
        validateLandmarks();
        validatePageList();
    }

    private void validateUniqueIds() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (EpubNavigationItem item : flattenAll()) {
            item.getId().ifPresent(id ->
                    counts.merge(id, 1, Integer::sum)
            );
        }

        List<String> duplicates = counts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate EPUB navigation item ids: "
                            + String.join(", ", duplicates)
            );
        }
    }

    /**
     * landmarks는 동일 semantic type의 중복을 제한합니다.
     */
    private void validateLandmarks() {
        if (!includeLandmarks) {
            return;
        }

        Map<String, Integer> types = new LinkedHashMap<>();

        for (EpubNavigationItem item : landmarkItems) {
            item.getEpubType().ifPresent(type ->
                    types.merge(type, 1, Integer::sum)
            );
        }

        /*
         * bodymatter 등은 일반적으로 하나만 존재하는 것이
         * 바람직하므로 직접 형제 수준에서 중복을 방지합니다.
         */
        for (Map.Entry<String, Integer> entry : types.entrySet()) {
            if (entry.getValue() > 1
                    && isSingleLandmarkType(entry.getKey())) {

                throw new IllegalStateException(
                        "Duplicate EPUB landmark type: "
                                + entry.getKey()
                );
            }
        }
    }

    private void validatePageList() {
        if (!includePageList) {
            return;
        }

        for (EpubNavigationItem item : pageListItems) {
            if (item.hasChildren()) {
                throw new IllegalStateException(
                        "EPUB page-list items should not "
                                + "contain child navigation items: "
                                + item.getLabel()
                );
            }
        }
    }

    private static boolean isSingleLandmarkType(String type) {
        return switch (type) {
            case "cover",
                 "toc",
                 "bodymatter",
                 "titlepage",
                 "copyright-page" -> true;

            default -> false;
        };
    }

    private Map<String, EpubNavigationItem> buildIdIndex() {
        Map<String, EpubNavigationItem> result =
                new LinkedHashMap<>();

        for (EpubNavigationItem item : flattenAllInternal()) {
            item.getId().ifPresent(id -> {
                EpubNavigationItem existing = result.put(id, item);

                if (existing != null) {
                    throw new IllegalArgumentException(
                            "Duplicate EPUB navigation item id: "
                                    + id
                    );
                }
            });
        }

        return Collections.unmodifiableMap(result);
    }

    private List<EpubNavigationItem> getAllItems() {
        List<EpubNavigationItem> result = new ArrayList<>();

        result.addAll(tocItems);
        result.addAll(landmarkItems);
        result.addAll(pageListItems);

        return result;
    }

    private List<EpubNavigationItem> flattenAllInternal() {
        List<EpubNavigationItem> result = new ArrayList<>();

        result.addAll(flatten(tocItems));
        result.addAll(flatten(landmarkItems));
        result.addAll(flatten(pageListItems));

        return result;
    }

    private static List<EpubNavigationItem> flatten(
            List<EpubNavigationItem> items
    ) {
        List<EpubNavigationItem> result = new ArrayList<>();

        for (EpubNavigationItem item : items) {
            result.addAll(item.flatten());
        }

        return result;
    }

    private static int getDepth(
            List<EpubNavigationItem> items
    ) {
        int maxDepth = 0;

        for (EpubNavigationItem item : items) {
            maxDepth = Math.max(
                    maxDepth,
                    item.getDepth()
            );
        }

        return maxDepth;
    }

    public Builder toBuilder() {
        return new Builder()
                .title(title)
                .language(language)
                .direction(direction)
                .tocItems(tocItems)
                .landmarkItems(landmarkItems)
                .pageListItems(pageListItems)
                .tocTitle(tocTitle)
                .landmarksTitle(landmarksTitle)
                .pageListTitle(pageListTitle)
                .includeLandmarks(includeLandmarks)
                .includePageList(includePageList)
                .description(description);
    }

    private static List<EpubNavigationItem> immutableItems(
            Collection<EpubNavigationItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubNavigationItem> result =
                new ArrayList<>();

        for (EpubNavigationItem item : items) {
            result.add(
                    Objects.requireNonNull(
                            item,
                            "EPUB navigation item must not be null."
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB " + fieldName + " must not be blank."
            );
        }

        return value.trim();
    }

    private static String normalizeTextOrDefault(
            String value,
            String defaultValue
    ) {
        String normalized = normalizeOptionalText(value);

        return normalized == null
                ? defaultValue
                : normalized;
    }

    private static String normalizeLanguage(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        return normalized.replace('_', '-');
    }

    private static String normalizeHref(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubNavigation{"
                + "title='" + title + '\''
                + ", language='" + language + '\''
                + ", tocItemCount=" + getTocItemCount()
                + ", tocDepth=" + getTocDepth()
                + ", landmarkItemCount="
                + getLandmarkItemCount()
                + ", pageListItemCount="
                + getPageListItemCount()
                + '}';
    }

    /**
     * {@link EpubNavigation} 생성 Builder입니다.
     */
    public static final class Builder {

        private String title = DEFAULT_TOC_TITLE;

        private String language;

        private EpubNavigationItem.TextDirection direction =
                EpubNavigationItem.TextDirection.AUTO;

        private final List<EpubNavigationItem> tocItems =
                new ArrayList<>();

        private final List<EpubNavigationItem> landmarkItems =
                new ArrayList<>();

        private final List<EpubNavigationItem> pageListItems =
                new ArrayList<>();

        private String tocTitle =
                DEFAULT_TOC_TITLE;

        private String landmarksTitle =
                DEFAULT_LANDMARKS_TITLE;

        private String pageListTitle =
                DEFAULT_PAGE_LIST_TITLE;

        private boolean includeLandmarks = true;

        private boolean includePageList;

        private String description;

        private Builder() {
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder direction(
                EpubNavigationItem.TextDirection direction
        ) {
            this.direction = direction;
            return this;
        }

        public Builder tocItem(EpubNavigationItem item) {
            tocItems.add(
                    Objects.requireNonNull(
                            item,
                            "EPUB TOC item must not be null."
                    )
            );

            return this;
        }

        public Builder tocItems(
                Collection<EpubNavigationItem> items
        ) {
            if (items != null) {
                for (EpubNavigationItem item : items) {
                    tocItem(item);
                }
            }

            return this;
        }

        public Builder landmarkItem(
                EpubNavigationItem item
        ) {
            landmarkItems.add(
                    Objects.requireNonNull(
                            item,
                            "EPUB landmark item must not be null."
                    )
            );

            return this;
        }

        public Builder landmarkItems(
                Collection<EpubNavigationItem> items
        ) {
            if (items != null) {
                for (EpubNavigationItem item : items) {
                    landmarkItem(item);
                }
            }

            return this;
        }

        public Builder pageListItem(
                EpubNavigationItem item
        ) {
            pageListItems.add(
                    Objects.requireNonNull(
                            item,
                            "EPUB page-list item must not be null."
                    )
            );

            return this;
        }

        public Builder pageListItems(
                Collection<EpubNavigationItem> items
        ) {
            if (items != null) {
                for (EpubNavigationItem item : items) {
                    pageListItem(item);
                }
            }

            return this;
        }

        public Builder tocTitle(String tocTitle) {
            this.tocTitle = tocTitle;
            return this;
        }

        public Builder landmarksTitle(
                String landmarksTitle
        ) {
            this.landmarksTitle = landmarksTitle;
            return this;
        }

        public Builder pageListTitle(
                String pageListTitle
        ) {
            this.pageListTitle = pageListTitle;
            return this;
        }

        public Builder includeLandmarks(
                boolean includeLandmarks
        ) {
            this.includeLandmarks = includeLandmarks;
            return this;
        }

        public Builder includePageList(
                boolean includePageList
        ) {
            this.includePageList = includePageList;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public EpubNavigation build() {
            return new EpubNavigation(this);
        }
    }
}