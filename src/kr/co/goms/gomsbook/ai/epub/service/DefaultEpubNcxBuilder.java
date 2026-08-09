/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcxNavPoint;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;

/**
 * EPUB 패키지의 manifest와 spine을 기반으로
 * {@link EpubNcx} 모델을 생성하는 기본 구현체입니다.
 *
 * <p>기본 정책은 다음과 같습니다.</p>
 *
 * <ul>
 *     <li>spine의 linear reading order를 NCX navMap으로 변환</li>
 *     <li>Navigation Document 및 NCX 자체는 목차에서 제외</li>
 *     <li>manifest resource title을 우선적으로 label에 사용</li>
 *     <li>playOrder는 최종적으로 자동 정규화</li>
 *     <li>NCX depth는 navPoint 구조에서 자동 계산</li>
 * </ul>
 *
 * <p>기본 구현은 flat navMap을 생성합니다. 실제 XHTML heading 또는
 * 프로젝트의 부/장 구조를 분석하여 계층형 navPoint를 생성하려면
 * {@link NcxLabelResolver}, {@link NcxSrcResolver},
 * {@link NcxIdGenerator}를 교체하거나 별도 Builder 구현체로
 * 확장할 수 있습니다.</p>
 */
public final class DefaultEpubNcxBuilder
        implements EpubNcxBuilder {

    private final NcxLabelResolver labelResolver;

    private final NcxSrcResolver srcResolver;

    private final NcxIdGenerator idGenerator;

    private final boolean resolvePlayOrders;

    private final boolean autoDepth;

    private final int totalPageCount;

    private final int maxPageNumber;

    /**
     * 기본 전략으로 NCX Builder를 생성합니다.
     */
    public DefaultEpubNcxBuilder() {
        this(
                NcxLabelResolver.defaultResolver(),
                NcxSrcResolver.defaultResolver(),
                NcxIdGenerator.sequential(),
                true,
                true,
                0,
                0
        );
    }

    /**
     * 사용자 정의 label resolver를 사용하는 Builder를 생성합니다.
     *
     * @param labelResolver NCX label 결정 전략
     */
    public DefaultEpubNcxBuilder(
            NcxLabelResolver labelResolver
    ) {
        this(
                labelResolver,
                NcxSrcResolver.defaultResolver(),
                NcxIdGenerator.sequential(),
                true,
                true,
                0,
                0
        );
    }

    /**
     * 전체 NCX 생성 정책을 지정합니다.
     *
     * @param labelResolver     label 결정 전략
     * @param srcResolver       content src 결정 전략
     * @param idGenerator       navPoint ID 생성 전략
     * @param resolvePlayOrders playOrder 자동 정규화 여부
     * @param autoDepth         depth 자동 계산 여부
     * @param totalPageCount    전체 페이지 수
     * @param maxPageNumber     최대 페이지 번호
     */
    public DefaultEpubNcxBuilder(
            NcxLabelResolver labelResolver,
            NcxSrcResolver srcResolver,
            NcxIdGenerator idGenerator,
            boolean resolvePlayOrders,
            boolean autoDepth,
            int totalPageCount,
            int maxPageNumber
    ) {
        this.labelResolver = Objects.requireNonNull(
                labelResolver,
                "NCX label resolver must not be null."
        );

        this.srcResolver = Objects.requireNonNull(
                srcResolver,
                "NCX src resolver must not be null."
        );

        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "NCX id generator must not be null."
        );

        if (totalPageCount < 0) {
            throw new IllegalArgumentException(
                    "NCX totalPageCount must not be negative: "
                            + totalPageCount
            );
        }

        if (maxPageNumber < 0) {
            throw new IllegalArgumentException(
                    "NCX maxPageNumber must not be negative: "
                            + maxPageNumber
            );
        }

        this.resolvePlayOrders = resolvePlayOrders;
        this.autoDepth = autoDepth;
        this.totalPageCount = totalPageCount;
        this.maxPageNumber = maxPageNumber;
    }

    /**
     * EPUB 패키지를 기반으로 NCX 모델을 생성합니다.
     *
     * @param epubPackage EPUB 패키지
     * @return NCX 모델
     * @throws EpubGenerationException 생성 실패 시
     */
    @Override
    public EpubNcx build(
            EpubPackage epubPackage
    ) throws EpubGenerationException {

        if (epubPackage == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }

        String uid;

        try {
            uid = epubPackage.getUniqueIdentifierValue();
        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "EPUB unique identifier is required "
                            + "to build NCX."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }

        String title = epubPackage.getTitle()
                .orElseThrow(() ->
                        EpubGenerationException.builder(
                                EpubGenerationException.ErrorCode
                                        .NCX_GENERATION_FAILED,
                                "EPUB package title is required "
                                        + "to build NCX."
                        )
                                .stage(
                                        EpubGenerationException.Stage
                                                .NCX_GENERATION
                                )
                                .build()
                );

        String author = epubPackage.getCreator()
                .orElse(null);

        return build(
                uid,
                title,
                author,
                epubPackage.getLanguage(),
                epubPackage.getManifest(),
                epubPackage.getSpine()
        );
    }

    /**
     * 개별 입력값을 기반으로 NCX 모델을 생성합니다.
     *
     * @param uid      고유 식별자
     * @param title    제목
     * @param author   저자
     * @param language 언어
     * @param manifest manifest
     * @param spine    spine
     * @return NCX 모델
     * @throws EpubGenerationException 생성 실패 시
     */
    @Override
    public EpubNcx build(
            String uid,
            String title,
            String author,
            String language,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        validate(
                uid,
                title,
                manifest,
                spine
        );

        try {
            List<EpubNcxNavPoint> navPoints =
                    Support.buildFlatNavPoints(
                            manifest,
                            spine,
                            labelResolver,
                            srcResolver,
                            idGenerator
                    );

            if (navPoints.isEmpty()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .NCX_GENERATION_FAILED,
                        "No linear spine resources are available "
                                + "to build NCX."
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .NCX_GENERATION
                        )
                        .build();
            }

            EpubNcx.Builder builder =
                    EpubNcx.builder()
                            .uid(uid)
                            .title(title)
                            .author(author)
                            .language(language)
                            .navPoints(navPoints)
                            .totalPageCount(totalPageCount)
                            .maxPageNumber(maxPageNumber);

            if (autoDepth) {
                builder.autoDepth();
            } else {
                builder.depth(
                        calculateDepth(navPoints)
                );
            }

            EpubNcx ncx = builder.build();

            /*
             * 생성 후 manifest 참조 무결성을 검사합니다.
             */
            ncx.validate(manifest);

            if (resolvePlayOrders) {
                ncx = ncx.withResolvedPlayOrders();
            }

            return ncx;

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "Failed to build EPUB NCX model."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * navPoint 트리의 최대 깊이를 계산합니다.
     */
    private int calculateDepth(
            List<EpubNcxNavPoint> navPoints
    ) {
        int depth = 0;

        for (EpubNcxNavPoint navPoint : navPoints) {
            if (!navPoint.isIncluded()) {
                continue;
            }

            depth = Math.max(
                    depth,
                    navPoint.getDepth()
            );
        }

        return depth;
    }

    public NcxLabelResolver getLabelResolver() {
        return labelResolver;
    }

    public NcxSrcResolver getSrcResolver() {
        return srcResolver;
    }

    public NcxIdGenerator getIdGenerator() {
        return idGenerator;
    }

    public boolean isResolvePlayOrders() {
        return resolvePlayOrders;
    }

    public boolean isAutoDepth() {
        return autoDepth;
    }

    public int getTotalPageCount() {
        return totalPageCount;
    }

    public int getMaxPageNumber() {
        return maxPageNumber;
    }

    /**
     * 설정용 Builder를 반환합니다.
     *
     * @return 구성 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link DefaultEpubNcxBuilder} 구성 Builder입니다.
     */
    public static final class Builder {

        private NcxLabelResolver labelResolver =
                NcxLabelResolver.defaultResolver();

        private NcxSrcResolver srcResolver =
                NcxSrcResolver.defaultResolver();

        private NcxIdGenerator idGenerator =
                NcxIdGenerator.sequential();

        private boolean resolvePlayOrders = true;

        private boolean autoDepth = true;

        private int totalPageCount;

        private int maxPageNumber;

        private Builder() {
        }

        public Builder labelResolver(
                NcxLabelResolver labelResolver
        ) {
            this.labelResolver = Objects.requireNonNull(
                    labelResolver,
                    "NCX label resolver must not be null."
            );

            return this;
        }

        public Builder srcResolver(
                NcxSrcResolver srcResolver
        ) {
            this.srcResolver = Objects.requireNonNull(
                    srcResolver,
                    "NCX src resolver must not be null."
            );

            return this;
        }

        public Builder idGenerator(
                NcxIdGenerator idGenerator
        ) {
            this.idGenerator = Objects.requireNonNull(
                    idGenerator,
                    "NCX id generator must not be null."
            );

            return this;
        }

        public Builder resolvePlayOrders(
                boolean resolvePlayOrders
        ) {
            this.resolvePlayOrders =
                    resolvePlayOrders;

            return this;
        }

        public Builder autoDepth(
                boolean autoDepth
        ) {
            this.autoDepth = autoDepth;
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

        /**
         * 인쇄 페이지 정보를 한 번에 설정합니다.
         */
        public Builder pageInformation(
                int totalPageCount,
                int maxPageNumber
        ) {
            this.totalPageCount =
                    totalPageCount;

            this.maxPageNumber =
                    maxPageNumber;

            return this;
        }

        /**
         * manifest resource ID를 기반으로 navPoint ID를
         * 생성하도록 설정합니다.
         */
        public Builder resourceBasedIds() {
            this.idGenerator =
                    NcxIdGenerator.resourceBased();

            return this;
        }

        public DefaultEpubNcxBuilder build() {
            return new DefaultEpubNcxBuilder(
                    labelResolver,
                    srcResolver,
                    idGenerator,
                    resolvePlayOrders,
                    autoDepth,
                    totalPageCount,
                    maxPageNumber
            );
        }
    }
}