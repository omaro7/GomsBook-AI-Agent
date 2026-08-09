/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB NCX 문서의 개별 navPoint를 표현합니다.
 *
 * <p>EPUB 2의 {@code toc.ncx} 또는 EPUB 3 하위 호환용 NCX에서
 * 목차 항목 하나를 나타냅니다.</p>
 *
 * <pre>
 * {@code
 * <navPoint id="navPoint-1" playOrder="1">
 *     <navLabel>
 *         <text>1장. 서울시립미술관</text>
 *     </navLabel>
 *     <content src="Text/chapter01.xhtml"/>
 * </navPoint>
 * }
 * </pre>
 *
 * <p>하위 navPoint를 포함할 수 있으므로 계층형 목차 구조를
 * 표현할 수 있습니다.</p>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubNcxNavPoint {

    /**
     * NCX navPoint의 필수 ID입니다.
     */
    private final String id;

    /**
     * 독자에게 표시되는 목차 제목입니다.
     */
    private final String label;

    /**
     * NCX content 요소의 src 값입니다.
     *
     * <p>NCX 파일을 기준으로 한 상대경로 또는 구현 정책에 따라
     * OPF 기준 href를 저장할 수 있습니다.</p>
     *
     * <p>예: {@code Text/chapter01.xhtml},
     * {@code Text/chapter01.xhtml#section01}</p>
     */
    private final String src;

    /**
     * NCX playOrder 값입니다.
     *
     * <p>양수이면 명시적으로 지정된 값이고, 0이면 Writer가
     * 순서에 따라 자동 할당해야 함을 의미합니다.</p>
     */
    private final int playOrder;

    /**
     * 하위 navPoint 목록입니다.
     */
    private final List<EpubNcxNavPoint> children;

    /**
     * NCX에 포함할지 여부입니다.
     */
    private final boolean included;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubNcxNavPoint(Builder builder) {
        this.id = requireIdentifier(builder.id);
        this.label = requireText(builder.label, "NCX navPoint label");
        this.src = normalizeSrc(builder.src);
        this.playOrder = requirePlayOrder(builder.playOrder);
        this.children = immutableChildren(builder.children);
        this.included = builder.included;
        this.description = normalizeOptionalText(builder.description);

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(
            String id,
            String label,
            String src
    ) {
        return new Builder()
                .id(id)
                .label(label)
                .src(src);
    }

    public static EpubNcxNavPoint of(
            String id,
            String label,
            String src
    ) {
        return builder(id, label, src).build();
    }

    public static EpubNcxNavPoint of(
            String id,
            String label,
            String src,
            int playOrder
    ) {
        return builder(id, label, src)
                .playOrder(playOrder)
                .build();
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getSrc() {
        return src;
    }

    public int getPlayOrder() {
        return playOrder;
    }

    public List<EpubNcxNavPoint> getChildren() {
        return children;
    }

    public boolean isIncluded() {
        return included;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * 명시적 playOrder가 설정되었는지 확인합니다.
     *
     * @return 1 이상이면 {@code true}
     */
    public boolean hasPlayOrder() {
        return playOrder > 0;
    }

    /**
     * 하위 navPoint가 존재하는지 확인합니다.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * fragment가 포함된 src인지 확인합니다.
     */
    public boolean hasFragment() {
        return src.indexOf('#') >= 0;
    }

    /**
     * fragment를 제외한 문서 src를 반환합니다.
     */
    public String getDocumentSrc() {
        int index = src.indexOf('#');

        return index < 0
                ? src
                : src.substring(0, index);
    }

    /**
     * fragment ID를 반환합니다.
     */
    public Optional<String> getFragment() {
        int index = src.indexOf('#');

        if (index < 0 || index == src.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(
                src.substring(index + 1)
        );
    }

    /**
     * 현재 navPoint 아래의 전체 하위 항목 수를 반환합니다.
     */
    public int getDescendantCount() {
        int count = 0;

        for (EpubNcxNavPoint child : children) {
            count++;
            count += child.getDescendantCount();
        }

        return count;
    }

    /**
     * 현재 항목을 포함한 전체 노드 수를 반환합니다.
     */
    public int getTreeSize() {
        return 1 + getDescendantCount();
    }

    /**
     * 현재 navPoint를 기준으로 한 최대 깊이를 반환합니다.
     *
     * <p>리프 navPoint의 깊이는 1입니다.</p>
     */
    public int getDepth() {
        if (children.isEmpty()) {
            return 1;
        }

        int maxDepth = 0;

        for (EpubNcxNavPoint child : children) {
            maxDepth = Math.max(
                    maxDepth,
                    child.getDepth()
            );
        }

        return 1 + maxDepth;
    }

    /**
     * ID로 현재 트리를 검색합니다.
     */
    public Optional<EpubNcxNavPoint> findById(
            String targetId
    ) {
        String normalized = normalizeOptionalText(targetId);

        if (normalized == null) {
            return Optional.empty();
        }

        if (id.equals(normalized)) {
            return Optional.of(this);
        }

        for (EpubNcxNavPoint child : children) {
            Optional<EpubNcxNavPoint> result =
                    child.findById(normalized);

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * src로 현재 트리를 검색합니다.
     */
    public Optional<EpubNcxNavPoint> findBySrc(
            String targetSrc
    ) {
        String normalized = normalizeLookupSrc(targetSrc);

        if (normalized == null) {
            return Optional.empty();
        }

        if (src.equals(normalized)) {
            return Optional.of(this);
        }

        for (EpubNcxNavPoint child : children) {
            Optional<EpubNcxNavPoint> result =
                    child.findBySrc(normalized);

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * 현재 트리를 pre-order 순서로 평탄화합니다.
     */
    public List<EpubNcxNavPoint> flatten() {
        List<EpubNcxNavPoint> result = new ArrayList<>();

        flattenInto(this, result);

        return Collections.unmodifiableList(result);
    }

    private static void flattenInto(
            EpubNcxNavPoint navPoint,
            List<EpubNcxNavPoint> result
    ) {
        result.add(navPoint);

        for (EpubNcxNavPoint child : navPoint.children) {
            flattenInto(child, result);
        }
    }

    /**
     * 자동 playOrder를 적용한 새 navPoint 트리를 반환합니다.
     *
     * @param startPlayOrder 시작 playOrder
     * @return playOrder가 적용된 새 트리
     */
    public EpubNcxNavPoint withResolvedPlayOrder(
            int startPlayOrder
    ) {
        if (startPlayOrder <= 0) {
            throw new IllegalArgumentException(
                    "NCX start playOrder must be greater than zero: "
                            + startPlayOrder
            );
        }

        PlayOrderCounter counter =
                new PlayOrderCounter(startPlayOrder);

        return resolvePlayOrder(this, counter);
    }

    /**
     * 현재 항목을 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .label(label)
                .src(src)
                .playOrder(playOrder)
                .children(children)
                .included(included)
                .description(description);
    }

    private static EpubNcxNavPoint resolvePlayOrder(
            EpubNcxNavPoint source,
            PlayOrderCounter counter
    ) {
        int resolvedPlayOrder = source.hasPlayOrder()
                ? source.getPlayOrder()
                : counter.next();

        /*
         * 명시된 playOrder가 있어도 다음 자동 번호가 충돌하지 않도록
         * counter를 전진시킵니다.
         */
        counter.advancePast(resolvedPlayOrder);

        Builder builder = source.toBuilder()
                .playOrder(resolvedPlayOrder)
                .clearChildren();

        for (EpubNcxNavPoint child : source.children) {
            builder.child(
                    resolvePlayOrder(child, counter)
            );
        }

        return builder.build();
    }

    private void validate() {
        validateDuplicateIds();
        validatePlayOrders();
    }

    private void validateDuplicateIds() {
        List<String> ids = new ArrayList<>();

        for (EpubNcxNavPoint child : children) {
            for (EpubNcxNavPoint item : child.flatten()) {
                if (id.equals(item.getId())) {
                    throw new IllegalArgumentException(
                            "Duplicate NCX navPoint id inside subtree: "
                                    + id
                    );
                }

                if (ids.contains(item.getId())) {
                    throw new IllegalArgumentException(
                            "Duplicate NCX navPoint id: "
                                    + item.getId()
                    );
                }

                ids.add(item.getId());
            }
        }
    }

    private void validatePlayOrders() {
        List<Integer> playOrders = new ArrayList<>();

        if (hasPlayOrder()) {
            playOrders.add(playOrder);
        }

        for (EpubNcxNavPoint child : children) {
            for (EpubNcxNavPoint item : child.flatten()) {
                if (!item.hasPlayOrder()) {
                    continue;
                }

                if (playOrders.contains(item.getPlayOrder())) {
                    throw new IllegalArgumentException(
                            "Duplicate NCX playOrder inside subtree: "
                                    + item.getPlayOrder()
                    );
                }

                playOrders.add(item.getPlayOrder());
            }
        }
    }

    private static List<EpubNcxNavPoint> immutableChildren(
            Collection<EpubNcxNavPoint> children
    ) {
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubNcxNavPoint> result = new ArrayList<>();

        for (EpubNcxNavPoint child : children) {
            result.add(
                    Objects.requireNonNull(
                            child,
                            "NCX child navPoint must not be null."
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    private static String requireIdentifier(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "NCX navPoint id must not be blank."
            );
        }

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid NCX navPoint id: " + value
            );
        }

        return normalized;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }

        return value.trim();
    }

    private static int requirePlayOrder(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "NCX playOrder must be zero or greater: "
                            + value
            );
        }

        return value;
    }

    private static String normalizeSrc(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "NCX navPoint src must not be blank."
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
                    "NCX navPoint src must be relative: "
                            + value
            );
        }

        if (containsParentTraversal(normalized)) {
            throw new IllegalArgumentException(
                    "NCX navPoint src must not contain "
                            + "parent traversal: "
                            + value
            );
        }

        if (normalized.endsWith("#")) {
            throw new IllegalArgumentException(
                    "NCX navPoint src contains an empty fragment: "
                            + value
            );
        }

        return normalized;
    }

    private static String normalizeLookupSrc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return normalizeSrc(value);
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

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof EpubNcxNavPoint other)) {
            return false;
        }

        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EpubNcxNavPoint{"
                + "id='" + id + '\''
                + ", label='" + label + '\''
                + ", src='" + src + '\''
                + ", playOrder=" + playOrder
                + ", childCount=" + children.size()
                + ", included=" + included
                + '}';
    }

    /**
     * 자동 playOrder 할당용 내부 카운터입니다.
     */
    private static final class PlayOrderCounter {

        private int value;

        private PlayOrderCounter(int value) {
            this.value = value;
        }

        private int next() {
            return value++;
        }

        private void advancePast(int usedValue) {
            if (value <= usedValue) {
                value = usedValue + 1;
            }
        }
    }

    /**
     * {@link EpubNcxNavPoint} 생성 Builder입니다.
     */
    public static final class Builder {

        private String id;

        private String label;

        private String src;

        /**
         * 0은 Writer 자동 할당을 의미합니다.
         */
        private int playOrder;

        private final List<EpubNcxNavPoint> children =
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

        public Builder src(String src) {
            this.src = src;
            return this;
        }

        public Builder playOrder(int playOrder) {
            this.playOrder = playOrder;
            return this;
        }

        /**
         * Writer가 playOrder를 자동 할당하도록 설정합니다.
         */
        public Builder autoPlayOrder() {
            this.playOrder = 0;
            return this;
        }

        public Builder child(EpubNcxNavPoint child) {
            children.add(
                    Objects.requireNonNull(
                            child,
                            "NCX child navPoint must not be null."
                    )
            );

            return this;
        }

        public Builder children(
                Collection<EpubNcxNavPoint> children
        ) {
            if (children == null) {
                return this;
            }

            for (EpubNcxNavPoint child : children) {
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

        public EpubNcxNavPoint build() {
            return new EpubNcxNavPoint(this);
        }
    }
}