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
 * EPUB 패키지 문서의 spine을 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code spine} 요소와 대응하며,
 * EPUB 출판물의 기본 읽기 순서를 관리합니다.</p>
 *
 * <pre>
 * {@code
 * <spine page-progression-direction="ltr">
 *     <itemref idref="cover" linear="no"/>
 *     <itemref idref="chapter01"/>
 *     <itemref idref="chapter02"/>
 * </spine>
 * }
 * </pre>
 *
 * <p>다음 기능을 제공합니다.</p>
 *
 * <ul>
 *     <li>spine 항목 등록 순서 유지</li>
 *     <li>중복 {@code idref} 검사</li>
 *     <li>선형 및 비선형 읽기 순서 관리</li>
 *     <li>페이지 진행 방향 관리</li>
 *     <li>EPUB 2 NCX 참조 관리</li>
 *     <li>manifest 참조 무결성 검증</li>
 *     <li>페이지 펼침 위치 자동 계산</li>
 * </ul>
 */
public final class EpubSpine {

    /**
     * spine 항목 목록입니다.
     *
     * <p>등록 순서가 곧 출판물의 읽기 순서입니다.</p>
     */
    private final List<EpubSpineItem> items;

    /**
     * manifest 리소스 ID별 spine 항목입니다.
     */
    private final Map<String, EpubSpineItem> itemsByIdref;

    /**
     * itemref 자체 ID별 spine 항목입니다.
     */
    private final Map<String, EpubSpineItem> itemsById;

    /**
     * 페이지 진행 방향입니다.
     */
    private EpubPageProgressionDirection pageProgressionDirection;

    /**
     * EPUB 2 spine의 toc 속성이 참조하는 NCX manifest ID입니다.
     *
     * <pre>
     * {@code
     * <spine toc="ncx">
     * }
     * </pre>
     */
    private String tocId;

    /**
     * 빈 spine을 생성합니다.
     */
    public EpubSpine() {
        this(
                EpubPageProgressionDirection.defaultDirection(),
                null
        );
    }

    /**
     * 지정한 페이지 진행 방향으로 빈 spine을 생성합니다.
     *
     * @param pageProgressionDirection 페이지 진행 방향
     */
    public EpubSpine(
            EpubPageProgressionDirection pageProgressionDirection
    ) {
        this(pageProgressionDirection, null);
    }

    /**
     * 페이지 진행 방향과 EPUB 2 NCX 참조를 지정하여 생성합니다.
     *
     * @param pageProgressionDirection 페이지 진행 방향
     * @param tocId                    NCX manifest 리소스 ID
     */
    public EpubSpine(
            EpubPageProgressionDirection pageProgressionDirection,
            String tocId
    ) {
        this.items = new ArrayList<>();
        this.itemsByIdref = new LinkedHashMap<>();
        this.itemsById = new LinkedHashMap<>();
        this.pageProgressionDirection =
                pageProgressionDirection == null
                        ? EpubPageProgressionDirection.defaultDirection()
                        : pageProgressionDirection;
        this.tocId = normalizeOptionalIdentifier(tocId);
    }

    /**
     * 초기 spine 항목으로 spine을 생성합니다.
     *
     * @param items 초기 spine 항목
     */
    public EpubSpine(Collection<EpubSpineItem> items) {
        this();

        addAll(items);
    }

    /**
     * Builder를 생성합니다.
     *
     * @return spine builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 빈 spine을 생성합니다.
     *
     * @return 빈 spine
     */
    public static EpubSpine empty() {
        return new EpubSpine();
    }

    /**
     * spine 항목을 마지막에 추가합니다.
     *
     * @param item 추가할 spine 항목
     * @return 현재 spine
     */
    public EpubSpine add(EpubSpineItem item) {
        EpubSpineItem validatedItem = Objects.requireNonNull(
                item,
                "EPUB spine item must not be null."
        );

        validateDuplicateIdref(validatedItem);
        validateDuplicateItemId(validatedItem);

        items.add(validatedItem);
        itemsByIdref.put(validatedItem.getIdref(), validatedItem);

        validatedItem.getId().ifPresent(itemId ->
                itemsById.put(itemId, validatedItem)
        );

        return this;
    }

    /**
     * 지정한 위치에 spine 항목을 추가합니다.
     *
     * @param index 추가 위치
     * @param item  spine 항목
     * @return 현재 spine
     */
    public EpubSpine add(
            int index,
            EpubSpineItem item
    ) {
        validateInsertionIndex(index);

        EpubSpineItem validatedItem = Objects.requireNonNull(
                item,
                "EPUB spine item must not be null."
        );

        validateDuplicateIdref(validatedItem);
        validateDuplicateItemId(validatedItem);

        items.add(index, validatedItem);
        rebuildIndexes();

        return this;
    }

    /**
     * 여러 spine 항목을 순서대로 추가합니다.
     *
     * @param spineItems 추가할 항목
     * @return 현재 spine
     */
    public EpubSpine addAll(
            Collection<EpubSpineItem> spineItems
    ) {
        if (spineItems == null || spineItems.isEmpty()) {
            return this;
        }

        for (EpubSpineItem item : spineItems) {
            add(item);
        }

        return this;
    }

    /**
     * manifest 리소스 ID를 사용해 기본 선형 항목을 추가합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 현재 spine
     */
    public EpubSpine add(String idref) {
        return add(EpubSpineItem.of(idref));
    }

    /**
     * manifest 리소스 ID를 사용해 비선형 항목을 추가합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 현재 spine
     */
    public EpubSpine addNonLinear(String idref) {
        return add(EpubSpineItem.nonLinear(idref));
    }

    /**
     * 기존 spine 항목을 교체합니다.
     *
     * <p>동일한 {@code idref}를 가진 항목을 등록 순서를 유지한 채
     * 교체합니다.</p>
     *
     * @param item 새 spine 항목
     * @return 이전 spine 항목
     */
    public Optional<EpubSpineItem> put(EpubSpineItem item) {
        EpubSpineItem validatedItem = Objects.requireNonNull(
                item,
                "EPUB spine item must not be null."
        );

        EpubSpineItem previous =
                itemsByIdref.get(validatedItem.getIdref());

        if (previous == null) {
            add(validatedItem);
            return Optional.empty();
        }

        int index = items.indexOf(previous);

        removeByIdref(previous.getIdref());

        try {
            add(index, validatedItem);
        } catch (RuntimeException exception) {
            add(index, previous);
            throw exception;
        }

        return Optional.of(previous);
    }

    /**
     * manifest 리소스 ID로 spine 항목을 제거합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 제거된 spine 항목
     */
    public Optional<EpubSpineItem> removeByIdref(String idref) {
        String normalizedIdref = normalizeLookupValue(idref);

        if (normalizedIdref == null) {
            return Optional.empty();
        }

        EpubSpineItem removed =
                itemsByIdref.get(normalizedIdref);

        if (removed == null) {
            return Optional.empty();
        }

        items.remove(removed);
        rebuildIndexes();

        return Optional.of(removed);
    }

    /**
     * itemref 자체 ID로 spine 항목을 제거합니다.
     *
     * @param itemId itemref ID
     * @return 제거된 spine 항목
     */
    public Optional<EpubSpineItem> removeById(String itemId) {
        String normalizedId = normalizeLookupValue(itemId);

        if (normalizedId == null) {
            return Optional.empty();
        }

        EpubSpineItem item = itemsById.get(normalizedId);

        if (item == null) {
            return Optional.empty();
        }

        return removeByIdref(item.getIdref());
    }

    /**
     * 지정한 위치의 spine 항목을 제거합니다.
     *
     * @param index spine 순번
     * @return 제거된 spine 항목
     */
    public EpubSpineItem remove(int index) {
        validateExistingIndex(index);

        EpubSpineItem removed = items.remove(index);
        rebuildIndexes();

        return removed;
    }

    /**
     * spine 항목의 위치를 이동합니다.
     *
     * @param fromIndex 기존 위치
     * @param toIndex   이동할 위치
     * @return 현재 spine
     */
    public EpubSpine move(
            int fromIndex,
            int toIndex
    ) {
        validateExistingIndex(fromIndex);
        validateExistingIndex(toIndex);

        if (fromIndex == toIndex) {
            return this;
        }

        EpubSpineItem item = items.remove(fromIndex);
        items.add(toIndex, item);
        rebuildIndexes();

        return this;
    }

    /**
     * manifest ID로 spine 항목을 조회합니다.
     *
     * @param idref manifest 리소스 ID
     * @return spine 항목
     */
    public Optional<EpubSpineItem> findByIdref(String idref) {
        String normalizedIdref = normalizeLookupValue(idref);

        if (normalizedIdref == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                itemsByIdref.get(normalizedIdref)
        );
    }

    /**
     * itemref 자체 ID로 spine 항목을 조회합니다.
     *
     * @param itemId itemref ID
     * @return spine 항목
     */
    public Optional<EpubSpineItem> findById(String itemId) {
        String normalizedId = normalizeLookupValue(itemId);

        if (normalizedId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                itemsById.get(normalizedId)
        );
    }

    /**
     * manifest ID에 해당하는 spine 항목을 반환합니다.
     *
     * @param idref manifest 리소스 ID
     * @return spine 항목
     */
    public EpubSpineItem requireByIdref(String idref) {
        return findByIdref(idref)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB spine item not found by idref: " + idref
                ));
    }

    /**
     * 지정한 위치의 spine 항목을 반환합니다.
     *
     * @param index spine 순번
     * @return spine 항목
     */
    public EpubSpineItem get(int index) {
        validateExistingIndex(index);

        return items.get(index);
    }

    /**
     * manifest ID가 spine에 등록되어 있는지 확인합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 등록되어 있으면 {@code true}
     */
    public boolean containsIdref(String idref) {
        return findByIdref(idref).isPresent();
    }

    /**
     * itemref ID가 등록되어 있는지 확인합니다.
     *
     * @param itemId itemref ID
     * @return 등록되어 있으면 {@code true}
     */
    public boolean containsId(String itemId) {
        return findById(itemId).isPresent();
    }

    /**
     * 모든 spine 항목을 등록 순서대로 반환합니다.
     *
     * @return 수정할 수 없는 spine 목록
     */
    public List<EpubSpineItem> getItems() {
        return Collections.unmodifiableList(
                new ArrayList<>(items)
        );
    }

    /**
     * 선형 읽기 순서에 포함된 항목만 반환합니다.
     *
     * @return 선형 spine 항목
     */
    public List<EpubSpineItem> getLinearItems() {
        return filter(EpubSpineItem::isLinear);
    }

    /**
     * 비선형 항목만 반환합니다.
     *
     * @return 비선형 spine 항목
     */
    public List<EpubSpineItem> getNonLinearItems() {
        return filter(EpubSpineItem::isNonLinear);
    }

    /**
     * 중앙 단독 페이지 항목을 반환합니다.
     *
     * @return 중앙 페이지 항목
     */
    public List<EpubSpineItem> getCenteredItems() {
        return filter(EpubSpineItem::isCenteredPage);
    }

    /**
     * 개별 rendition override가 지정된 항목을 반환합니다.
     *
     * @return 렌디션 override 항목
     */
    public List<EpubSpineItem> getRenditionOverrideItems() {
        return filter(EpubSpineItem::hasRenditionOverride);
    }

    /**
     * 조건에 일치하는 spine 항목을 반환합니다.
     *
     * @param predicate 검색 조건
     * @return 조건에 맞는 항목
     */
    public List<EpubSpineItem> filter(
            Predicate<EpubSpineItem> predicate
    ) {
        Objects.requireNonNull(
                predicate,
                "EPUB spine predicate must not be null."
        );

        return items.stream()
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 등록된 manifest 리소스 ID를 반환합니다.
     *
     * @return idref 집합
     */
    public Set<String> getIdrefs() {
        return Collections.unmodifiableSet(
                itemsByIdref.keySet()
        );
    }

    /**
     * spine 항목 수를 반환합니다.
     *
     * @return 항목 수
     */
    public int size() {
        return items.size();
    }

    /**
     * 선형 spine 항목 수를 반환합니다.
     *
     * @return 선형 항목 수
     */
    public int linearSize() {
        return (int) items.stream()
                .filter(EpubSpineItem::isLinear)
                .count();
    }

    /**
     * spine이 비어 있는지 확인합니다.
     *
     * @return 비어 있으면 {@code true}
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 모든 spine 항목을 제거합니다.
     */
    public void clear() {
        items.clear();
        itemsByIdref.clear();
        itemsById.clear();
    }

    public EpubPageProgressionDirection
            getPageProgressionDirection() {
        return pageProgressionDirection;
    }

    public void setPageProgressionDirection(
            EpubPageProgressionDirection pageProgressionDirection
    ) {
        this.pageProgressionDirection =
                pageProgressionDirection == null
                        ? EpubPageProgressionDirection.defaultDirection()
                        : pageProgressionDirection;
    }

    /**
     * OPF spine에 page-progression-direction 속성을
     * 출력해야 하는지 확인합니다.
     *
     * @return 명시적 방향이면 {@code true}
     */
    public boolean shouldWritePageProgressionDirection() {
        return pageProgressionDirection != null
                && pageProgressionDirection.shouldWriteAttribute();
    }

    /**
     * EPUB 2 NCX manifest ID를 반환합니다.
     *
     * @return NCX ID
     */
    public Optional<String> getTocId() {
        return Optional.ofNullable(tocId);
    }

    /**
     * EPUB 2 NCX manifest ID를 설정합니다.
     *
     * @param tocId NCX manifest ID
     */
    public void setTocId(String tocId) {
        this.tocId = normalizeOptionalIdentifier(tocId);
    }

    /**
     * EPUB 2 toc 속성을 출력해야 하는지 확인합니다.
     *
     * @return NCX 참조가 있으면 {@code true}
     */
    public boolean shouldWriteTocAttribute() {
        return tocId != null;
    }

    /**
     * spine 순번을 반환합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 0부터 시작하는 순번
     */
    public Optional<Integer> indexOf(String idref) {
        Optional<EpubSpineItem> item = findByIdref(idref);

        if (item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(items.indexOf(item.get()));
    }

    /**
     * 지정한 항목의 이전 spine 항목을 반환합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 이전 spine 항목
     */
    public Optional<EpubSpineItem> previous(String idref) {
        Optional<Integer> index = indexOf(idref);

        if (index.isEmpty() || index.get() <= 0) {
            return Optional.empty();
        }

        return Optional.of(items.get(index.get() - 1));
    }

    /**
     * 지정한 항목의 다음 spine 항목을 반환합니다.
     *
     * @param idref manifest 리소스 ID
     * @return 다음 spine 항목
     */
    public Optional<EpubSpineItem> next(String idref) {
        Optional<Integer> index = indexOf(idref);

        if (index.isEmpty()
                || index.get() >= items.size() - 1) {
            return Optional.empty();
        }

        return Optional.of(items.get(index.get() + 1));
    }

    /**
     * 지정한 spine 순번에 권장되는 페이지 펼침 위치를 반환합니다.
     *
     * <p>항목에 명시적인 {@link EpubPageSpread}가 있으면 해당 값을
     * 반환하고, 그렇지 않으면 페이지 진행 방향과 spine 순번을 기준으로
     * 계산합니다.</p>
     *
     * @param index spine 순번
     * @return 페이지 펼침 위치
     */
    public EpubPageSpread resolvePageSpread(int index) {
        EpubSpineItem item = get(index);

        if (item.hasPageSpread()) {
            return item.getPageSpread();
        }

        return EpubPageSpread.resolve(
                index,
                pageProgressionDirection
        );
    }

    /**
     * 모든 AUTO 페이지 배치값을 명시적인 LEFT 또는 RIGHT 값으로
     * 변환한 새 spine을 반환합니다.
     *
     * <p>원본 spine은 변경하지 않습니다.</p>
     *
     * @return 페이지 펼침 위치가 반영된 spine
     */
    public EpubSpine withResolvedPageSpreads() {
        EpubSpine resolved = new EpubSpine(
                pageProgressionDirection,
                tocId
        );

        for (int index = 0; index < items.size(); index++) {
            EpubSpineItem item = items.get(index);

            if (item.hasPageSpread()) {
                resolved.add(item);
                continue;
            }

            resolved.add(
                    item.toBuilder()
                            .pageSpread(
                                    EpubPageSpread.resolve(
                                            index,
                                            pageProgressionDirection
                                    )
                            )
                            .build()
            );
        }

        return resolved;
    }

    /**
     * manifest 참조 관계를 검증합니다.
     *
     * @param manifest EPUB manifest
     */
    public void validate(EpubManifest manifest) {
        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "EPUB spine must contain at least one item."
            );
        }

        for (EpubSpineItem item : items) {
            item.validate(manifest);
        }

        if (tocId != null) {
            EpubResource tocResource = manifest.findById(tocId)
                    .orElseThrow(() -> new IllegalStateException(
                            "EPUB spine toc resource not found: "
                                    + tocId
                    ));

            if (tocResource.getResourceType()
                    != EpubResourceType.NCX) {
                throw new IllegalStateException(
                        "EPUB spine toc reference must point to "
                                + "an NCX resource: "
                                + tocId
                                + " ("
                                + tocResource.getResourceType()
                                + ")"
                );
            }
        }
    }

    /**
     * EPUB 버전을 기준으로 spine을 검증합니다.
     *
     * @param version EPUB 버전
     */
    public void validate(EpubVersion version) {
        Objects.requireNonNull(
                version,
                "EPUB version must not be null."
        );

        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "EPUB spine must contain at least one item."
            );
        }

        for (EpubSpineItem item : items) {
            item.validate(version);
        }

        if (version.isEpub2() && tocId == null) {
            throw new IllegalStateException(
                    "EPUB 2 spine requires a toc attribute "
                            + "referencing an NCX resource."
            );
        }

        if (version.isEpub3() && tocId != null) {
            /*
             * EPUB 3에서도 하위 호환용 NCX를 사용할 수 있으므로
             * 오류가 아닌 허용 상태로 처리합니다.
             */
            return;
        }
    }

    /**
     * manifest와 EPUB 버전을 함께 검증합니다.
     *
     * @param manifest EPUB manifest
     * @param version  EPUB 버전
     */
    public void validate(
            EpubManifest manifest,
            EpubVersion version
    ) {
        validate(manifest);
        validate(version);
    }

    /**
     * 선형 읽기 순서에 최소 하나의 항목이 있는지 확인합니다.
     *
     * @return 선형 항목이 있으면 {@code true}
     */
    public boolean hasLinearItems() {
        return items.stream().anyMatch(EpubSpineItem::isLinear);
    }

    /**
     * 모든 항목이 비선형인지 확인합니다.
     *
     * @return 모든 항목이 비선형이면 {@code true}
     */
    public boolean isEntirelyNonLinear() {
        return !items.isEmpty() && !hasLinearItems();
    }

    /**
     * 현재 spine의 독립 복사본을 생성합니다.
     *
     * @return 복사된 spine
     */
    public EpubSpine copy() {
        EpubSpine copied = new EpubSpine(
                pageProgressionDirection,
                tocId
        );

        copied.addAll(items);

        return copied;
    }

    private void validateDuplicateIdref(EpubSpineItem item) {
        if (itemsByIdref.containsKey(item.getIdref())) {
            throw new IllegalArgumentException(
                    "Duplicate EPUB spine idref: "
                            + item.getIdref()
            );
        }
    }

    private void validateDuplicateItemId(EpubSpineItem item) {
        item.getId().ifPresent(itemId -> {
            if (itemsById.containsKey(itemId)) {
                throw new IllegalArgumentException(
                        "Duplicate EPUB spine item id: " + itemId
                );
            }
        });
    }

    private void rebuildIndexes() {
        itemsByIdref.clear();
        itemsById.clear();

        for (EpubSpineItem item : items) {
            itemsByIdref.put(item.getIdref(), item);

            item.getId().ifPresent(itemId ->
                    itemsById.put(itemId, item)
            );
        }
    }

    private void validateExistingIndex(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException(
                    "EPUB spine index out of range: "
                            + index
                            + ", size: "
                            + items.size()
            );
        }
    }

    private void validateInsertionIndex(int index) {
        if (index < 0 || index > items.size()) {
            throw new IndexOutOfBoundsException(
                    "EPUB spine insertion index out of range: "
                            + index
                            + ", size: "
                            + items.size()
            );
        }
    }

    private static String normalizeLookupValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeOptionalIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB identifier: " + value
            );
        }

        return normalized;
    }

    private static boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        char first = value.charAt(0);

        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }

        for (int index = 1; index < value.length(); index++) {
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

    @Override
    public String toString() {
        return "EpubSpine{"
                + "itemCount=" + items.size()
                + ", linearItemCount=" + linearSize()
                + ", pageProgressionDirection="
                + pageProgressionDirection
                + ", tocId='" + tocId + '\''
                + '}';
    }

    /**
     * {@link EpubSpine} 생성 Builder입니다.
     */
    public static final class Builder {

        private final List<EpubSpineItem> items =
                new ArrayList<>();

        private EpubPageProgressionDirection
                pageProgressionDirection =
                EpubPageProgressionDirection.defaultDirection();

        private String tocId;

        private EpubManifest manifest;

        private EpubVersion version;

        private boolean validateOnBuild = true;

        private boolean resolvePageSpreads;

        private Builder() {
        }

        public Builder item(EpubSpineItem item) {
            items.add(
                    Objects.requireNonNull(
                            item,
                            "EPUB spine item must not be null."
                    )
            );
            return this;
        }

        public Builder items(
                Collection<EpubSpineItem> items
        ) {
            if (items == null) {
                return this;
            }

            for (EpubSpineItem item : items) {
                item(item);
            }

            return this;
        }

        /**
         * 기본 선형 spine 항목을 추가합니다.
         *
         * @param idref manifest 리소스 ID
         * @return 현재 builder
         */
        public Builder item(String idref) {
            return item(EpubSpineItem.of(idref));
        }

        /**
         * 비선형 spine 항목을 추가합니다.
         *
         * @param idref manifest 리소스 ID
         * @return 현재 builder
         */
        public Builder nonLinearItem(String idref) {
            return item(EpubSpineItem.nonLinear(idref));
        }

        public Builder pageProgressionDirection(
                EpubPageProgressionDirection direction
        ) {
            this.pageProgressionDirection = direction;
            return this;
        }

        public Builder tocId(String tocId) {
            this.tocId = tocId;
            return this;
        }

        /**
         * build 시 manifest 참조 검증에 사용할 manifest를 설정합니다.
         *
         * @param manifest EPUB manifest
         * @return 현재 builder
         */
        public Builder manifest(EpubManifest manifest) {
            this.manifest = manifest;
            return this;
        }

        /**
         * build 시 버전 호환성 검증에 사용할 EPUB 버전을 설정합니다.
         *
         * @param version EPUB 버전
         * @return 현재 builder
         */
        public Builder version(EpubVersion version) {
            this.version = version;
            return this;
        }

        public Builder validateOnBuild(boolean validateOnBuild) {
            this.validateOnBuild = validateOnBuild;
            return this;
        }

        /**
         * AUTO 페이지 펼침 위치를 build 시 자동 계산할지 설정합니다.
         *
         * @param resolvePageSpreads 자동 계산 여부
         * @return 현재 builder
         */
        public Builder resolvePageSpreads(
                boolean resolvePageSpreads
        ) {
            this.resolvePageSpreads = resolvePageSpreads;
            return this;
        }

        public EpubSpine build() {
            EpubSpine spine = new EpubSpine(
                    pageProgressionDirection,
                    tocId
            );

            spine.addAll(items);

            if (resolvePageSpreads) {
                spine = spine.withResolvedPageSpreads();
            }

            if (validateOnBuild) {
                if (manifest != null && version != null) {
                    spine.validate(manifest, version);
                } else if (manifest != null) {
                    spine.validate(manifest);
                } else if (version != null) {
                    spine.validate(version);
                } else if (spine.isEmpty()) {
                    throw new IllegalStateException(
                            "EPUB spine must contain at least one item."
                    );
                }
            }

            return spine;
        }
    }
}