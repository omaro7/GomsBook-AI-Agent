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

/**
 * EPUB NCX(Navigation Center eXtended) 문서 전체를 표현합니다.
 *
 * <p>EPUB 2에서는 {@code toc.ncx}가 기본 탐색 문서이며,
 * EPUB 3에서는 하위 호환 목적으로 선택적으로 포함할 수 있습니다.</p>
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"
 *      version="2005-1"
 *      xml:lang="ko">
 *
 *     <head>
 *         <meta name="dtb:uid"
 *               content="urn:isbn:9780000000000"/>
 *         <meta name="dtb:depth"
 *               content="2"/>
 *         <meta name="dtb:totalPageCount"
 *               content="0"/>
 *         <meta name="dtb:maxPageNumber"
 *               content="0"/>
 *     </head>
 *
 *     <docTitle>
 *         <text>점심시간, 서울을 걷다</text>
 *     </docTitle>
 *
 *     <docAuthor>
 *         <text>한정훈</text>
 *     </docAuthor>
 *
 *     <navMap>
 *         ...
 *     </navMap>
 * </ncx>
 * }
 * </pre>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubNcx {

    public static final String NCX_NAMESPACE =
            "http://www.daisy.org/z3986/2005/ncx/";

    public static final String DEFAULT_VERSION =
            "2005-1";

    /**
     * NCX 문서 식별자입니다.
     *
     * <p>일반적으로 OPF의 고유 식별자 값과 동일하게 사용합니다.</p>
     */
    private final String uid;

    /**
     * 문서 제목입니다.
     */
    private final String title;

    /**
     * 문서 저자입니다.
     */
    private final String author;

    /**
     * NCX 언어입니다.
     */
    private final String language;

    /**
     * NCX 버전입니다.
     */
    private final String version;

    /**
     * 최상위 navPoint 목록입니다.
     */
    private final List<EpubNcxNavPoint> navPoints;

    /**
     * NCX 목차 최대 깊이입니다.
     *
     * <p>0이면 navPoint 구조에서 자동 계산합니다.</p>
     */
    private final int depth;

    /**
     * 인쇄 페이지 전체 개수입니다.
     */
    private final int totalPageCount;

    /**
     * 가장 큰 페이지 번호입니다.
     */
    private final int maxPageNumber;

    /**
     * 사용자 정의 head meta 값입니다.
     */
    private final Map<String, String> headMetadata;

    /**
     * 애플리케이션 내부 설명입니다.
     */
    private final String description;

    private EpubNcx(Builder builder) {
        this.uid = requireText(builder.uid, "NCX uid");
        this.title = requireText(builder.title, "NCX title");
        this.author = normalizeOptionalText(builder.author);
        this.language = normalizeLanguage(builder.language);
        this.version = normalizeVersion(builder.version);
        this.navPoints = immutableNavPoints(builder.navPoints);
        this.depth = resolveDepth(builder.depth, this.navPoints);
        this.totalPageCount = requireNonNegative(
                builder.totalPageCount,
                "NCX totalPageCount"
        );
        this.maxPageNumber = requireNonNegative(
                builder.maxPageNumber,
                "NCX maxPageNumber"
        );
        this.headMetadata = immutableMetadata(
                builder.headMetadata
        );
        this.description = normalizeOptionalText(
                builder.description
        );

        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EpubNcx of(
            String uid,
            String title,
            Collection<EpubNcxNavPoint> navPoints
    ) {
        return builder()
                .uid(uid)
                .title(title)
                .navPoints(navPoints)
                .build();
    }

    public String getUid() {
        return uid;
    }

    public String getTitle() {
        return title;
    }

    public Optional<String> getAuthor() {
        return Optional.ofNullable(author);
    }

    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    public String getVersion() {
        return version;
    }

    public List<EpubNcxNavPoint> getNavPoints() {
        return navPoints;
    }

    public int getDepth() {
        return depth;
    }

    public int getTotalPageCount() {
        return totalPageCount;
    }

    public int getMaxPageNumber() {
        return maxPageNumber;
    }

    public Map<String, String> getHeadMetadata() {
        return headMetadata;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * navPoint가 존재하는지 확인합니다.
     */
    public boolean hasNavPoints() {
        return !navPoints.isEmpty();
    }

    /**
     * 문서 저자가 설정되어 있는지 확인합니다.
     */
    public boolean hasAuthor() {
        return author != null;
    }

    /**
     * 페이지 정보가 존재하는지 확인합니다.
     */
    public boolean hasPageInformation() {
        return totalPageCount > 0
                || maxPageNumber > 0;
    }

    /**
     * 모든 navPoint를 pre-order 순서로 반환합니다.
     */
    public List<EpubNcxNavPoint> flattenNavPoints() {
        List<EpubNcxNavPoint> result =
                new ArrayList<>();

        for (EpubNcxNavPoint navPoint : navPoints) {
            result.addAll(navPoint.flatten());
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * 전체 navPoint 수를 반환합니다.
     */
    public int getNavPointCount() {
        return flattenNavPoints().size();
    }

    /**
     * 포함 대상 navPoint 수를 반환합니다.
     */
    public int getIncludedNavPointCount() {
        return (int) flattenNavPoints()
                .stream()
                .filter(EpubNcxNavPoint::isIncluded)
                .count();
    }

    /**
     * navPoint ID로 검색합니다.
     */
    public Optional<EpubNcxNavPoint> findById(
            String id
    ) {
        String normalized =
                normalizeOptionalText(id);

        if (normalized == null) {
            return Optional.empty();
        }

        for (EpubNcxNavPoint navPoint : navPoints) {
            Optional<EpubNcxNavPoint> found =
                    navPoint.findById(normalized);

            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    /**
     * navPoint src로 검색합니다.
     */
    public Optional<EpubNcxNavPoint> findBySrc(
            String src
    ) {
        if (src == null || src.isBlank()) {
            return Optional.empty();
        }

        for (EpubNcxNavPoint navPoint : navPoints) {
            Optional<EpubNcxNavPoint> found =
                    navPoint.findBySrc(src);

            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    /**
     * 모든 navPoint에 playOrder를 자동 부여한 새 NCX를 반환합니다.
     *
     * <p>기존에 명시된 playOrder는 유지하며,
     * 자동 항목은 충돌하지 않도록 순차 할당합니다.</p>
     *
     * @return playOrder가 정규화된 NCX
     */
    public EpubNcx withResolvedPlayOrders() {
        PlayOrderCounter counter =
                new PlayOrderCounter(1);

        Builder builder = toBuilder()
                .clearNavPoints();

        for (EpubNcxNavPoint navPoint : navPoints) {
            builder.navPoint(
                    resolvePlayOrder(
                            navPoint,
                            counter
                    )
            );
        }

        return builder.build();
    }

    /**
     * manifest에 NCX가 참조하는 문서가 존재하는지 검증합니다.
     */
    public void validate(EpubManifest manifest) {
        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        validate();

        for (EpubNcxNavPoint navPoint : flattenNavPoints()) {
            if (!navPoint.isIncluded()) {
                continue;
            }

            String target =
                    normalizeHref(
                            navPoint.getDocumentSrc()
                    );

            boolean exists =
                    manifest.getResources()
                            .stream()
                            .anyMatch(resource ->
                                    normalizeHref(
                                            resource.getHref()
                                    ).equals(target)
                            );

            if (!exists) {
                throw new IllegalStateException(
                        "NCX navPoint references a resource "
                                + "not present in manifest: "
                                + navPoint.getId()
                                + " -> "
                                + navPoint.getSrc()
                );
            }
        }
    }

    /**
     * EPUB 버전에 따라 NCX 사용 가능 여부를 검증합니다.
     */
    public void validate(EpubVersion epubVersion) {
        Objects.requireNonNull(
                epubVersion,
                "EPUB version must not be null."
        );

        validate();

        /*
         * EPUB 2에서는 필수이고,
         * EPUB 3에서는 하위 호환용으로 사용할 수 있으므로
         * 두 버전 모두 허용합니다.
         */
        if (!epubVersion.isEpub2()
                && !epubVersion.isEpub3()) {
            throw new IllegalStateException(
                    "Unsupported EPUB version for NCX: "
                            + epubVersion
            );
        }
    }

    /**
     * 기본 무결성을 검증합니다.
     */
    public void validate() {
        if (navPoints.isEmpty()) {
            throw new IllegalStateException(
                    "NCX requires at least one navPoint."
            );
        }

        validateUniqueIds();
        validatePlayOrders();

        if (depth <= 0) {
            throw new IllegalStateException(
                    "NCX depth must be greater than zero."
            );
        }

        if (totalPageCount > 0
                && maxPageNumber > 0
                && maxPageNumber > totalPageCount) {
            /*
             * 페이지 라벨이 실제 페이지 수와 반드시 같지는 않으므로
             * 일반적으로 오류는 아니지만 현재 모델에서는
             * 명백한 잘못된 설정을 방지합니다.
             */
        }
    }

    private void validateUniqueIds() {
        Map<String, Integer> counts =
                new LinkedHashMap<>();

        for (EpubNcxNavPoint navPoint : flattenNavPoints()) {
            counts.merge(
                    navPoint.getId(),
                    1,
                    Integer::sum
            );
        }

        List<String> duplicates =
                counts.entrySet()
                        .stream()
                        .filter(entry ->
                                entry.getValue() > 1
                        )
                        .map(Map.Entry::getKey)
                        .toList();

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate NCX navPoint ids: "
                            + String.join(
                                    ", ",
                                    duplicates
                            )
            );
        }
    }

    private void validatePlayOrders() {
        Map<Integer, String> playOrders =
                new LinkedHashMap<>();

        for (EpubNcxNavPoint navPoint : flattenNavPoints()) {
            if (!navPoint.hasPlayOrder()) {
                continue;
            }

            String existing =
                    playOrders.put(
                            navPoint.getPlayOrder(),
                            navPoint.getId()
                    );

            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate NCX playOrder: "
                                + navPoint.getPlayOrder()
                                + " ("
                                + existing
                                + ", "
                                + navPoint.getId()
                                + ")"
                );
            }
        }
    }

    /**
     * NCX head에 출력할 기본 meta를 반환합니다.
     */
    public Map<String, String> getResolvedHeadMetadata() {
        Map<String, String> result =
                new LinkedHashMap<>();

        result.put(
                "dtb:uid",
                uid
        );

        result.put(
                "dtb:depth",
                String.valueOf(depth)
        );

        result.put(
                "dtb:totalPageCount",
                String.valueOf(totalPageCount)
        );

        result.put(
                "dtb:maxPageNumber",
                String.valueOf(maxPageNumber)
        );

        for (Map.Entry<String, String> entry
                : headMetadata.entrySet()) {

            result.putIfAbsent(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 현재 NCX를 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .uid(uid)
                .title(title)
                .author(author)
                .language(language)
                .version(version)
                .navPoints(navPoints)
                .depth(depth)
                .totalPageCount(totalPageCount)
                .maxPageNumber(maxPageNumber)
                .headMetadata(headMetadata)
                .description(description);
    }

    private static EpubNcxNavPoint resolvePlayOrder(
            EpubNcxNavPoint source,
            PlayOrderCounter counter
    ) {
        int resolvedOrder;

        if (source.hasPlayOrder()) {
            resolvedOrder =
                    source.getPlayOrder();

            counter.advancePast(
                    resolvedOrder
            );
        } else {
            resolvedOrder =
                    counter.next();
        }

        EpubNcxNavPoint.Builder builder =
                source.toBuilder()
                        .playOrder(
                                resolvedOrder
                        )
                        .clearChildren();

        for (EpubNcxNavPoint child :
                source.getChildren()) {

            builder.child(
                    resolvePlayOrder(
                            child,
                            counter
                    )
            );
        }

        return builder.build();
    }

    private static int resolveDepth(
            int configuredDepth,
            List<EpubNcxNavPoint> navPoints
    ) {
        if (configuredDepth < 0) {
            throw new IllegalArgumentException(
                    "NCX depth must be zero or greater: "
                            + configuredDepth
            );
        }

        if (configuredDepth > 0) {
            return configuredDepth;
        }

        int calculated = 0;

        for (EpubNcxNavPoint navPoint : navPoints) {
            if (!navPoint.isIncluded()) {
                continue;
            }

            calculated = Math.max(
                    calculated,
                    navPoint.getDepth()
            );
        }

        return calculated;
    }

    private static List<EpubNcxNavPoint> immutableNavPoints(
            Collection<EpubNcxNavPoint> navPoints
    ) {
        if (navPoints == null || navPoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<EpubNcxNavPoint> result =
                new ArrayList<>();

        for (EpubNcxNavPoint navPoint : navPoints) {
            result.add(
                    Objects.requireNonNull(
                            navPoint,
                            "NCX navPoint must not be null."
                    )
            );
        }

        return Collections.unmodifiableList(result);
    }

    private static Map<String, String> immutableMetadata(
            Map<String, String> metadata
    ) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry :
                metadata.entrySet()) {

            String name =
                    normalizeOptionalText(
                            entry.getKey()
                    );

            String value =
                    normalizeOptionalText(
                            entry.getValue()
                    );

            if (name == null || value == null) {
                continue;
            }

            result.put(name, value);
        }

        return Collections.unmodifiableMap(result);
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

    private static String normalizeLanguage(
            String value
    ) {
        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        return normalized.replace('_', '-');
    }

    private static String normalizeVersion(
            String value
    ) {
        String normalized =
                normalizeOptionalText(value);

        return normalized == null
                ? DEFAULT_VERSION
                : normalized;
    }

    private static String normalizeHref(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized =
                value.trim()
                        .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized =
                    normalized.substring(2);
        }

        return normalized;
    }

    private static int requireNonNegative(
            int value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be negative: "
                            + value
            );
        }

        return value;
    }

    private static String normalizeOptionalText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubNcx{"
                + "uid='" + uid + '\''
                + ", title='" + title + '\''
                + ", author='" + author + '\''
                + ", language='" + language + '\''
                + ", version='" + version + '\''
                + ", depth=" + depth
                + ", navPointCount="
                + getNavPointCount()
                + ", totalPageCount="
                + totalPageCount
                + ", maxPageNumber="
                + maxPageNumber
                + '}';
    }

    /**
     * playOrder 자동 계산용 내부 카운터입니다.
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
     * {@link EpubNcx} 생성 Builder입니다.
     */
    public static final class Builder {

        private String uid;

        private String title;

        private String author;

        private String language;

        private String version =
                DEFAULT_VERSION;

        private final List<EpubNcxNavPoint> navPoints =
                new ArrayList<>();

        /**
         * 0은 자동 계산을 의미합니다.
         */
        private int depth;

        private int totalPageCount;

        private int maxPageNumber;

        private final Map<String, String> headMetadata =
                new LinkedHashMap<>();

        private String description;

        private Builder() {
        }

        public Builder uid(String uid) {
            this.uid = uid;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder navPoint(
                EpubNcxNavPoint navPoint
        ) {
            navPoints.add(
                    Objects.requireNonNull(
                            navPoint,
                            "NCX navPoint must not be null."
                    )
            );

            return this;
        }

        public Builder navPoints(
                Collection<EpubNcxNavPoint> navPoints
        ) {
            if (navPoints == null) {
                return this;
            }

            for (EpubNcxNavPoint navPoint : navPoints) {
                navPoint(navPoint);
            }

            return this;
        }

        public Builder clearNavPoints() {
            navPoints.clear();
            return this;
        }

        /**
         * NCX depth를 명시적으로 설정합니다.
         *
         * <p>0이면 navPoint 구조에서 자동 계산합니다.</p>
         */
        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder autoDepth() {
            this.depth = 0;
            return this;
        }

        public Builder totalPageCount(
                int totalPageCount
        ) {
            this.totalPageCount =
                    totalPageCount;
            return this;
        }

        public Builder maxPageNumber(
                int maxPageNumber
        ) {
            this.maxPageNumber =
                    maxPageNumber;
            return this;
        }

        public Builder headMetadata(
                String name,
                String value
        ) {
            headMetadata.put(
                    name,
                    value
            );

            return this;
        }

        public Builder headMetadata(
                Map<String, String> metadata
        ) {
            if (metadata != null) {
                headMetadata.putAll(metadata);
            }

            return this;
        }

        public Builder description(
                String description
        ) {
            this.description = description;
            return this;
        }

        public EpubNcx build() {
            return new EpubNcx(this);
        }
    }
}