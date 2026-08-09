/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
/**
 * EPUB 패키지의 manifest와 spine을 기반으로
 * {@link EpubNavigation} 모델을 생성하는 기본 구현체입니다.
 *
 * <p>기본 구현은 다음 정책을 사용합니다.</p>
 *
 * <ul>
 *     <li>spine의 linear reading order를 기준으로 TOC 생성</li>
 *     <li>Navigation Document 자체는 TOC에서 제외</li>
 *     <li>cover, toc, bodymatter landmarks 자동 생성</li>
 *     <li>page-list는 자동 생성하지 않음</li>
 *     <li>리소스 title → 파일명 → resource id 순으로 label 결정</li>
 * </ul>
 *
 * <p>문서의 실제 {@code h1}, {@code h2}, {@code title} 등을
 * 분석하여 목차명을 생성하려면
 * {@link NavigationLabelResolver}를 교체하여 확장할 수 있습니다.</p>
 *
 * <p>이 클래스는 상태 변경이 없으므로 여러 EPUB 생성 요청에서
 * 재사용할 수 있습니다.</p>
 */
public final class DefaultEpubNavigationBuilder
        implements EpubNavigationBuilder {

    private final NavigationLabelResolver labelResolver;

    private final NavigationTypeResolver typeResolver;

    private final NavigationIdGenerator idGenerator;

    private final boolean includeLandmarks;

    private final boolean includePageList;

    /**
     * 기본 전략을 사용하는 Navigation Builder를 생성합니다.
     */
    public DefaultEpubNavigationBuilder() {
        this(
                NavigationLabelResolver.defaultResolver(),
                NavigationTypeResolver.defaultResolver(),
                NavigationIdGenerator.sequential(),
                true,
                false
        );
    }

    /**
     * 사용자 정의 label resolver를 사용하는 Builder를 생성합니다.
     *
     * @param labelResolver Navigation 표시명 결정 전략
     */
    public DefaultEpubNavigationBuilder(
            NavigationLabelResolver labelResolver
    ) {
        this(
                labelResolver,
                NavigationTypeResolver.defaultResolver(),
                NavigationIdGenerator.sequential(),
                true,
                false
        );
    }

    /**
     * 전체 Navigation 생성 전략을 지정합니다.
     *
     * @param labelResolver     label 결정 전략
     * @param typeResolver      epub:type 결정 전략
     * @param idGenerator       Navigation ID 생성 전략
     * @param includeLandmarks  landmarks 생성 여부
     * @param includePageList   page-list 생성 여부
     */
    public DefaultEpubNavigationBuilder(
            NavigationLabelResolver labelResolver,
            NavigationTypeResolver typeResolver,
            NavigationIdGenerator idGenerator,
            boolean includeLandmarks,
            boolean includePageList
    ) {
        this.labelResolver = Objects.requireNonNull(
                labelResolver,
                "Navigation label resolver must not be null."
        );

        this.typeResolver = Objects.requireNonNull(
                typeResolver,
                "Navigation type resolver must not be null."
        );

        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "Navigation id generator must not be null."
        );

        this.includeLandmarks = includeLandmarks;
        this.includePageList = includePageList;
    }

    /**
     * EPUB 패키지를 기반으로 Navigation 모델을 생성합니다.
     *
     * @param epubPackage EPUB 패키지
     * @return Navigation 모델
     * @throws EpubGenerationException Navigation 생성 실패 시
     */
    @Override
    public EpubNavigation build(
            EpubPackage epubPackage
    ) throws EpubGenerationException {

        if (epubPackage == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB package must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        if (!epubPackage.getVersion().isEpub3()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .UNSUPPORTED_REQUEST,
                    "EPUB Navigation Document can only be built "
                            + "for EPUB 3 packages."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .detail(
                            "epubVersion",
                            epubPackage.getVersion().toString()
                    )
                    .build();
        }

        String title = epubPackage.getTitle()
                .orElseThrow(() ->
                        EpubGenerationException.builder(
                                EpubGenerationException.ErrorCode
                                        .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                                "EPUB package title is required "
                                        + "to build Navigation Document."
                        )
                                .stage(
                                        EpubGenerationException.Stage
                                                .NAVIGATION_GENERATION
                                )
                                .build()
                );

        return build(
                title,
                epubPackage.getLanguage(),
                epubPackage.getManifest(),
                epubPackage.getSpine()
        );
    }

    /**
     * manifest와 spine을 기반으로 Navigation 모델을 생성합니다.
     *
     * @param title    출판물 제목
     * @param language 출판물 언어
     * @param manifest manifest
     * @param spine    spine
     * @return Navigation 모델
     * @throws EpubGenerationException Navigation 생성 실패 시
     */
    @Override
    public EpubNavigation build(
            String title,
            String language,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        validate(
                title,
                manifest,
                spine
        );

        try {
            List<EpubNavigationItem> tocItems =
                    Support.buildFlatToc(
                            manifest,
                            spine,
                            labelResolver,
                            typeResolver,
                            idGenerator
                    );

            if (tocItems.isEmpty()) {
                throw EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                        "No linear spine resources are available "
                                + "to build EPUB navigation."
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .NAVIGATION_GENERATION
                        )
                        .build();
            }

            EpubNavigation.Builder builder =
                    EpubNavigation.builder()
                            .title(title)
                            .tocTitle("차례")
                            .language(language)
                            .tocItems(tocItems)
                            .includeLandmarks(
                                    includeLandmarks
                            )
                            .includePageList(
                                    includePageList
                            );

            if (includeLandmarks) {
                List<EpubNavigationItem> landmarks =
                        Support.buildLandmarks(
                                manifest,
                                spine
                        );

                builder.landmarkItems(
                        landmarks
                );
            }

            /*
             * 기본 구현에서는 page-list를 자동 생성하지 않습니다.
             *
             * page-list는 실제 XHTML의 epub:type="pagebreak" 또는
             * 인쇄본 페이지 매핑 정보가 필요하기 때문입니다.
             */
            EpubNavigation navigation =
                    builder.build();

            /*
             * 생성 직후 manifest 참조까지 검증합니다.
             */
            navigation.validate(manifest);

            return navigation;

        } catch (EpubGenerationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "Failed to build EPUB Navigation model."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * 현재 Builder가 landmarks를 자동 생성하는지 반환합니다.
     */
    public boolean isIncludeLandmarks() {
        return includeLandmarks;
    }

    /**
     * 현재 Builder가 page-list 생성을 요청하는지 반환합니다.
     */
    public boolean isIncludePageList() {
        return includePageList;
    }

    public NavigationLabelResolver getLabelResolver() {
        return labelResolver;
    }

    public NavigationTypeResolver getTypeResolver() {
        return typeResolver;
    }

    public NavigationIdGenerator getIdGenerator() {
        return idGenerator;
    }

    /**
     * Builder 생성용 정적 팩토리입니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link DefaultEpubNavigationBuilder} 구성 Builder입니다.
     */
    public static final class Builder {

        private NavigationLabelResolver labelResolver =
                NavigationLabelResolver.defaultResolver();

        private NavigationTypeResolver typeResolver =
                NavigationTypeResolver.defaultResolver();

        private NavigationIdGenerator idGenerator =
                NavigationIdGenerator.sequential();

        private boolean includeLandmarks = true;

        private boolean includePageList;

        private Builder() {
        }

        public Builder labelResolver(
                NavigationLabelResolver labelResolver
        ) {
            this.labelResolver = Objects.requireNonNull(
                    labelResolver,
                    "Navigation label resolver must not be null."
            );

            return this;
        }

        public Builder typeResolver(
                NavigationTypeResolver typeResolver
        ) {
            this.typeResolver = Objects.requireNonNull(
                    typeResolver,
                    "Navigation type resolver must not be null."
            );

            return this;
        }

        public Builder idGenerator(
                NavigationIdGenerator idGenerator
        ) {
            this.idGenerator = Objects.requireNonNull(
                    idGenerator,
                    "Navigation id generator must not be null."
            );

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

        public DefaultEpubNavigationBuilder build() {
            return new DefaultEpubNavigationBuilder(
                    labelResolver,
                    typeResolver,
                    idGenerator,
                    includeLandmarks,
                    includePageList
            );
        }
    }
}