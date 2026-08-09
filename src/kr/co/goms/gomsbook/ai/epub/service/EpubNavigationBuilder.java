/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;

/**
 * EPUB manifest와 spine 정보를 기반으로
 * {@link EpubNavigation} 모델을 생성하는 계약입니다.
 *
 * <p>기본적으로 spine의 reading order를 기준으로 TOC 항목을 생성하고,
 * 필요하면 cover, toc, bodymatter 등의 landmark 항목도 구성합니다.</p>
 *
 * <p>이 Builder는 XHTML 본문의 실제 heading 텍스트까지 파싱하지 않습니다.
 * 기본 label은 리소스 제목, 파일명 또는 ID를 사용하며,
 * 실제 문서 제목을 정확히 사용하려면 별도의
 * {@link NavigationLabelResolver} 구현을 주입하는 방식이 적절합니다.</p>
 */
public interface EpubNavigationBuilder {

    /**
     * EPUB 패키지를 기반으로 Navigation 모델을 생성합니다.
     *
     * @param epubPackage EPUB 패키지
     * @return Navigation 모델
     * @throws EpubGenerationException Navigation 생성 실패 시
     */
    EpubNavigation build(
            EpubPackage epubPackage
    ) throws EpubGenerationException;

    /**
     * manifest와 spine을 기반으로 Navigation 모델을 생성합니다.
     *
     * @param title    출판물 제목
     * @param language 출판물 언어
     * @param manifest EPUB manifest
     * @param spine    EPUB spine
     * @return Navigation 모델
     * @throws EpubGenerationException Navigation 생성 실패 시
     */
    EpubNavigation build(
            String title,
            String language,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException;

    /**
     * 기본 Navigation 생성 입력값을 검증합니다.
     */
    default void validate(
            String title,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        if (title == null || title.isBlank()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB navigation title must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        if (manifest == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB manifest must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        if (spine == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB spine must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }

        if (spine.isEmpty()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                    "EPUB spine must contain at least one item "
                            + "to build navigation."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NAVIGATION_GENERATION
                    )
                    .build();
        }
    }

    /**
     * spine item이 참조하는 manifest resource를 조회합니다.
     */
    default EpubResource resolveResource(
            EpubManifest manifest,
            EpubSpineItem spineItem
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        Objects.requireNonNull(
                spineItem,
                "EPUB spine item must not be null."
        );

        return manifest.findById(
                spineItem.getIdref()
        ).orElseThrow(() ->
                EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                        "EPUB spine references a manifest item "
                                + "that does not exist: "
                                + spineItem.getIdref()
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .NAVIGATION_GENERATION
                        )
                        .resourceId(
                                spineItem.getIdref()
                        )
                        .build()
        );
    }

    /**
     * spine의 reading order 기준 리소스 목록을 반환합니다.
     */
    default List<EpubResource> resolveReadingOrderResources(
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        List<EpubResource> result = new ArrayList<>();

        for (EpubSpineItem spineItem : spine.getItems()) {
            if (!spineItem.isLinear()) {
                continue;
            }

            EpubResource resource =
                    resolveResource(
                            manifest,
                            spineItem
                    );

            if (!resource.isIncluded()) {
                continue;
            }

            result.add(resource);
        }

        return List.copyOf(result);
    }

    /**
     * 구현체 이름을 반환합니다.
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Navigation label을 결정하는 전략입니다.
     */
    @FunctionalInterface
    interface NavigationLabelResolver {

        /**
         * 리소스의 목차 표시명을 반환합니다.
         *
         * @param resource   manifest 리소스
         * @param spineItem  spine item
         * @param sequence   reading order 순번
         * @return 표시명
         */
        String resolve(
                EpubResource resource,
                EpubSpineItem spineItem,
                int sequence
        );

        /**
         * 기본 label resolver입니다.
         *
         * <p>다음 우선순위로 label을 결정합니다.</p>
         *
         * <ol>
         *     <li>resource title</li>
         *     <li>파일명</li>
         *     <li>resource id</li>
         * </ol>
         */
        static NavigationLabelResolver defaultResolver() {
            return (resource, spineItem, sequence) -> {

                String href =
                        resource.getHref();

                if (href != null && !href.isBlank()) {

                    String normalized =
                            href.replace('\\', '/');

                    int slash =
                            normalized.lastIndexOf('/');

                    String fileName =
                            slash >= 0
                                    ? normalized.substring(
                                            slash + 1
                                    )
                                    : normalized;

                    int dot =
                            fileName.lastIndexOf('.');

                    if (dot > 0) {
                        fileName =
                                fileName.substring(
                                        0,
                                        dot
                                );
                    }

                    if (!fileName.isBlank()) {
                        return fileName;
                    }
                }

                String resourceId =
                        resource.getId();

                if (resourceId != null
                        && !resourceId.isBlank()) {

                    return resourceId;
                }

                return "Chapter " + sequence;
            };
        }
    }

    /**
     * EPUB semantic type을 결정하는 전략입니다.
     */
    @FunctionalInterface
    interface NavigationTypeResolver {

        /**
         * Navigation item의 epub:type을 반환합니다.
         *
         * @param resource  manifest resource
         * @param spineItem spine item
         * @param sequence  reading order 순번
         * @return epub:type 또는 null
         */
        String resolve(
                EpubResource resource,
                EpubSpineItem spineItem,
                int sequence
        );

        /**
         * 기본 type resolver입니다.
         */
        static NavigationTypeResolver defaultResolver() {
            return (resource, spineItem, sequence) -> {

                if (resource == null) {
                    return "chapter";
                }

                /*
                 * EPUB 3 Navigation Document
                 */
                if (resource.isNavigationDocument()) {
                    return "toc";
                }

                /*
                 * 첫 번째 spine 문서는 일반적으로
                 * 표지 또는 시작 문서일 수 있지만,
                 * 명확한 정보가 없으므로 임의로
                 * cover로 판단하지 않습니다.
                 */
                return "chapter";
            };
        }
    }

    /**
     * Navigation ID 생성 전략입니다.
     */
    @FunctionalInterface
    interface NavigationIdGenerator {

        String generate(
                EpubResource resource,
                int sequence
        );

        static NavigationIdGenerator sequential() {
            return (resource, sequence) ->
                    String.format(
                            "nav-item-%03d",
                            sequence
                    );
        }
    }

    /**
     * 기본 구현체에서 공유할 수 있는 내부 생성 유틸리티입니다.
     */
    final class Support {

        private Support() {
        }

        public static List<EpubNavigationItem> buildFlatToc(
                EpubManifest manifest,
                EpubSpine spine,
                NavigationLabelResolver labelResolver,
                NavigationTypeResolver typeResolver,
                NavigationIdGenerator idGenerator
        ) throws EpubGenerationException {

            Objects.requireNonNull(
                    manifest,
                    "EPUB manifest must not be null."
            );

            Objects.requireNonNull(
                    spine,
                    "EPUB spine must not be null."
            );

            Objects.requireNonNull(
                    labelResolver,
                    "Navigation label resolver must not be null."
            );

            Objects.requireNonNull(
                    typeResolver,
                    "Navigation type resolver must not be null."
            );

            Objects.requireNonNull(
                    idGenerator,
                    "Navigation id generator must not be null."
            );

            List<EpubNavigationItem> result =
                    new ArrayList<>();

            AtomicInteger sequence =
                    new AtomicInteger(1);

            for (EpubSpineItem spineItem : spine.getItems()) {
                if (!spineItem.isLinear()) {
                    continue;
                }

                EpubResource resource =
                        manifest.findById(
                                spineItem.getIdref()
                        ).orElseThrow(() ->
                                EpubGenerationException.builder(
                                        EpubGenerationException.ErrorCode
                                                .NAVIGATION_DOCUMENT_GENERATION_FAILED,
                                        "Spine item references "
                                                + "an unknown manifest item: "
                                                + spineItem.getIdref()
                                )
                                        .stage(
                                                EpubGenerationException.Stage
                                                        .NAVIGATION_GENERATION
                                        )
                                        .resourceId(
                                                spineItem.getIdref()
                                        )
                                        .build()
                        );

                if (!resource.isIncluded()) {
                    continue;
                }

                /*
                 * nav.xhtml 자체는 TOC reading order에 넣지 않습니다.
                 */
                if (resource.isNavigationDocument()) {
                    continue;
                }

                int current =
                        sequence.getAndIncrement();

                String label =
                        labelResolver.resolve(
                                resource,
                                spineItem,
                                current
                        );

                if (label == null || label.isBlank()) {
                    label =
                            "Chapter " + current;
                }

                String epubType =
                        typeResolver.resolve(
                                resource,
                                spineItem,
                                current
                        );

                EpubNavigationItem.Builder builder =
                        EpubNavigationItem.builder()
                                .id(
                                        idGenerator.generate(
                                                resource,
                                                current
                                        )
                                )
                                .label(label)
                                .href(resource.getHref());

                if (epubType != null
                        && !epubType.isBlank()) {
                    builder.epubType(epubType);
                }

                result.add(builder.build());
            }

            return List.copyOf(result);
        }

        /**
         * 기본 landmarks를 생성합니다.
         */
        public static List<EpubNavigationItem> buildLandmarks(
                EpubManifest manifest,
                EpubSpine spine
        ) throws EpubGenerationException {

            List<EpubNavigationItem> result =
                    new ArrayList<>();

            /*
             * Navigation Document
             */
            manifest.getNavigationDocument()
                    .ifPresent(resource ->
                            result.add(
                                    EpubNavigationItem.builder()
                                            .id("landmark-toc")
                                            .label("차례")
                                            .href(resource.getHref())
                                            .epubType("toc")
                                            .build()
                            )
                    );

            /*
             * 첫 번째 linear spine 문서를 bodymatter로 사용합니다.
             */
            for (EpubSpineItem spineItem : spine.getItems()) {
                if (!spineItem.isLinear()) {
                    continue;
                }

                EpubResource resource =
                        manifest.findById(
                                spineItem.getIdref()
                        ).orElse(null);

                if (resource == null
                        || !resource.isIncluded()
                        || resource.isNavigationDocument()) {
                    continue;
                }

                result.add(
                        EpubNavigationItem.builder()
                                .id("landmark-bodymatter")
                                .label("본문")
                                .href(resource.getHref())
                                .epubType("bodymatter")
                                .build()
                );

                break;
            }

            return List.copyOf(result);
        }
    }
}