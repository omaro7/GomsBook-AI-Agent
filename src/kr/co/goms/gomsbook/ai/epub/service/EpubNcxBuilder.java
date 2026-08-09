/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcx;
import kr.co.goms.gomsbook.ai.epub.model.EpubNcxNavPoint;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;

/**
 * EPUB manifest와 spine을 기반으로
 * {@link EpubNcx} 모델을 생성하는 계약입니다.
 *
 * <p>EPUB 2에서는 NCX가 기본 탐색 문서이며,
 * EPUB 3에서는 하위 호환 목적으로 선택적으로 사용할 수 있습니다.</p>
 *
 * <p>기본 생성 정책은 spine의 linear reading order를 기준으로
 * {@link EpubNcxNavPoint}를 생성하는 것입니다.</p>
 */
public interface EpubNcxBuilder {

    /**
     * EPUB 패키지를 기반으로 NCX 모델을 생성합니다.
     *
     * @param epubPackage EPUB 패키지
     * @return NCX 모델
     * @throws EpubGenerationException NCX 생성 실패 시
     */
    EpubNcx build(
            EpubPackage epubPackage
    ) throws EpubGenerationException;

    /**
     * 개별 EPUB 정보를 기반으로 NCX 모델을 생성합니다.
     *
     * @param uid 출판물 고유 식별자
     * @param title 출판물 제목
     * @param author 저자
     * @param language 언어
     * @param manifest manifest
     * @param spine spine
     * @return NCX 모델
     * @throws EpubGenerationException 생성 실패 시
     */
    EpubNcx build(
            String uid,
            String title,
            String author,
            String language,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException;

    /**
     * NCX 생성 입력값을 검증합니다.
     */
    default void validate(
            String uid,
            String title,
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        if (uid == null
                || uid.isBlank()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "NCX uid must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }

        if (title == null
                || title.isBlank()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "NCX title must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
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
                                    .NCX_GENERATION
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
                                    .NCX_GENERATION
                    )
                    .build();
        }

        if (spine.getItems() == null
                || spine.getItems().isEmpty()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "EPUB spine must contain at least one item "
                            + "to build NCX."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }
    }

    /**
     * Spine item이 참조하는 Manifest resource를 조회합니다.
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

        String idref =
                spineItem.getIdref();

        if (idref == null
                || idref.isBlank()) {

            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .NCX_GENERATION_FAILED,
                    "EPUB spine item idref must not be blank."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .NCX_GENERATION
                    )
                    .build();
        }

        return manifest.findById(
                idref
        ).orElseThrow(() ->
                EpubGenerationException.builder(
                        EpubGenerationException.ErrorCode
                                .NCX_GENERATION_FAILED,
                        "EPUB spine references a manifest resource "
                                + "that does not exist: "
                                + idref
                )
                        .stage(
                                EpubGenerationException.Stage
                                        .NCX_GENERATION
                        )
                        .resourceId(idref)
                        .build()
        );
    }

    /**
     * Spine의 linear reading order 리소스를 반환합니다.
     */
    default List<EpubResource> resolveReadingOrderResources(
            EpubManifest manifest,
            EpubSpine spine
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                manifest,
                "EPUB manifest must not be null."
        );

        Objects.requireNonNull(
                spine,
                "EPUB spine must not be null."
        );

        List<EpubResource> result =
                new ArrayList<>();

        for (EpubSpineItem spineItem :
                spine.getItems()) {

            if (spineItem == null) {
                continue;
            }

            if (!spineItem.isLinear()) {
                continue;
            }

            EpubResource resource =
                    resolveResource(
                            manifest,
                            spineItem
                    );

            if (resource == null) {
                continue;
            }

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
     * NCX navPoint 표시명 결정 전략입니다.
     */
    @FunctionalInterface
    interface NcxLabelResolver {

        /**
         * NCX navPoint label을 생성합니다.
         */
        String resolve(
                EpubResource resource,
                EpubSpineItem spineItem,
                int sequence
        );

        /**
         * 기본 label resolver입니다.
         *
         * <p>현재 EpubResource에는 별도 title 필드가 없으므로
         * 다음 순서로 label을 결정합니다.</p>
         *
         * <ol>
         *     <li>href의 파일명</li>
         *     <li>resource id</li>
         *     <li>Chapter N</li>
         * </ol>
         */
        static NcxLabelResolver defaultResolver() {

            return (resource, spineItem, sequence) -> {

                if (resource == null) {
                    return "Chapter " + sequence;
                }

                String href =
                        resource.getHref();

                if (href != null
                        && !href.isBlank()) {

                    String normalized =
                            href.replace('\\', '/');

                    int slashIndex =
                            normalized.lastIndexOf('/');

                    String fileName =
                            slashIndex >= 0
                                    ? normalized.substring(
                                            slashIndex + 1
                                    )
                                    : normalized;

                    int queryIndex =
                            fileName.indexOf('?');

                    if (queryIndex >= 0) {
                        fileName =
                                fileName.substring(
                                        0,
                                        queryIndex
                                );
                    }

                    int fragmentIndex =
                            fileName.indexOf('#');

                    if (fragmentIndex >= 0) {
                        fileName =
                                fileName.substring(
                                        0,
                                        fragmentIndex
                                );
                    }

                    int extensionIndex =
                            fileName.lastIndexOf('.');

                    if (extensionIndex > 0) {
                        fileName =
                                fileName.substring(
                                        0,
                                        extensionIndex
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
     * NCX navPoint ID 생성 전략입니다.
     */
    @FunctionalInterface
    interface NcxIdGenerator {

        String generate(
                EpubResource resource,
                int sequence
        );

        /**
         * navPoint-001 형식의 ID를 생성합니다.
         */
        static NcxIdGenerator sequential() {

            return (resource, sequence) ->
                    String.format(
                            "navPoint-%03d",
                            sequence
                    );
        }

        /**
         * Manifest resource ID 기반으로 navPoint ID를 생성합니다.
         */
        static NcxIdGenerator resourceBased() {

            return (resource, sequence) -> {

                if (resource == null) {
                    return String.format(
                            "navPoint-%03d",
                            sequence
                    );
                }

                String resourceId =
                        resource.getId();

                if (resourceId == null
                        || resourceId.isBlank()) {

                    return String.format(
                            "navPoint-%03d",
                            sequence
                    );
                }

                return "navPoint-"
                        + sanitizeId(
                                resourceId
                        );
            };
        }

        private static String sanitizeId(
                String value
        ) {

            if (value == null
                    || value.isBlank()) {

                return "item";
            }

            String normalized =
                    value.trim()
                            .replaceAll(
                                    "[^A-Za-z0-9_.-]",
                                    "-"
                            );

            if (normalized.isBlank()) {
                return "item";
            }

            return normalized;
        }
    }

    /**
     * navPoint content src 결정 전략입니다.
     */
    @FunctionalInterface
    interface NcxSrcResolver {

        String resolve(
                EpubResource resource,
                EpubSpineItem spineItem,
                int sequence
        );

        /**
         * 기본적으로 manifest의 href를 그대로 사용합니다.
         */
        static NcxSrcResolver defaultResolver() {

            return (resource, spineItem, sequence) -> {

                if (resource == null) {
                    return null;
                }

                return resource.getHref();
            };
        }
    }

    /**
     * 기본 구현체에서 공유하는 NCX 생성 기능입니다.
     */
    final class Support {

        private Support() {
        }

        /**
         * Spine reading order를 기반으로 flat navPoint 목록을 생성합니다.
         *
         * <p>현재 단계에서는 Part/Chapter 계층을 별도로 추론하지 않고
         * Spine의 실제 reading order를 그대로 유지합니다.</p>
         */
        public static List<EpubNcxNavPoint> buildFlatNavPoints(
                EpubManifest manifest,
                EpubSpine spine,
                NcxLabelResolver labelResolver,
                NcxSrcResolver srcResolver,
                NcxIdGenerator idGenerator
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
                    "NCX label resolver must not be null."
            );

            Objects.requireNonNull(
                    srcResolver,
                    "NCX src resolver must not be null."
            );

            Objects.requireNonNull(
                    idGenerator,
                    "NCX id generator must not be null."
            );

            List<EpubNcxNavPoint> result =
                    new ArrayList<>();

            int sequence = 1;

            for (EpubSpineItem spineItem :
                    spine.getItems()) {

                if (spineItem == null) {
                    continue;
                }

                /*
                 * linear="no" 항목은 기본 reading order에서 제외합니다.
                 */
                if (!spineItem.isLinear()) {
                    continue;
                }

                String idref =
                        spineItem.getIdref();

                if (idref == null
                        || idref.isBlank()) {

                    throw EpubGenerationException.builder(
                            EpubGenerationException.ErrorCode
                                    .NCX_GENERATION_FAILED,
                            "Spine item idref must not be blank."
                    )
                            .stage(
                                    EpubGenerationException.Stage
                                            .NCX_GENERATION
                            )
                            .build();
                }

                EpubResource resource =
                        manifest.findById(
                                idref
                        ).orElseThrow(() ->
                                EpubGenerationException.builder(
                                        EpubGenerationException.ErrorCode
                                                .NCX_GENERATION_FAILED,
                                        "Spine item references an unknown "
                                                + "manifest resource: "
                                                + idref
                                )
                                        .stage(
                                                EpubGenerationException.Stage
                                                        .NCX_GENERATION
                                        )
                                        .resourceId(idref)
                                        .build()
                        );

                if (!resource.isIncluded()) {
                    continue;
                }

                /*
                 * NCX 자체가 실수로 spine에 들어가 있더라도
                 * NCX navMap에서는 제외합니다.
                 */
                if (isNcxResource(resource)) {
                    continue;
                }

                /*
                 * EPUB 3 Navigation Document 자체도
                 * NCX navMap에서는 제외합니다.
                 */
                if (isNavigationResource(resource)) {
                    continue;
                }

                int currentSequence =
                        sequence++;

                String label =
                        labelResolver.resolve(
                                resource,
                                spineItem,
                                currentSequence
                        );

                if (label == null
                        || label.isBlank()) {

                    label =
                            "Chapter "
                                    + currentSequence;
                }

                String src =
                        srcResolver.resolve(
                                resource,
                                spineItem,
                                currentSequence
                        );

                if (src == null
                        || src.isBlank()) {

                    throw EpubGenerationException.builder(
                            EpubGenerationException.ErrorCode
                                    .NCX_GENERATION_FAILED,
                            "NCX src resolver returned an empty value."
                    )
                            .stage(
                                    EpubGenerationException.Stage
                                            .NCX_GENERATION
                            )
                            .resourceId(
                                    resource.getId()
                            )
                            .build();
                }

                String id =
                        idGenerator.generate(
                                resource,
                                currentSequence
                        );

                if (id == null
                        || id.isBlank()) {

                    throw EpubGenerationException.builder(
                            EpubGenerationException.ErrorCode
                                    .NCX_GENERATION_FAILED,
                            "NCX id generator returned an empty value."
                    )
                            .stage(
                                    EpubGenerationException.Stage
                                            .NCX_GENERATION
                            )
                            .resourceId(
                                    resource.getId()
                            )
                            .build();
                }

                EpubNcxNavPoint navPoint =
                        EpubNcxNavPoint.builder()
                                .id(id)
                                .label(
                                        label.trim()
                                )
                                .src(
                                        normalizeHref(src)
                                )
                                .autoPlayOrder()
                                .build();

                result.add(
                        navPoint
                );
            }

            return List.copyOf(result);
        }

        /**
         * EpubResource에 isNcx() 메서드가 없더라도
         * NCX 여부를 판단할 수 있도록 이 Builder 내부에서 처리합니다.
         */
        private static boolean isNcxResource(
                EpubResource resource
        ) {

            if (resource == null) {
                return false;
            }

            String mediaType =
                    resource.getMediaType();

            if ("application/x-dtbncx+xml"
                    .equalsIgnoreCase(
                            mediaType
                    )) {

                return true;
            }

            String href =
                    resource.getHref();

            return href != null
                    && href.toLowerCase(
                            java.util.Locale.ROOT
                    )
                            .endsWith(".ncx");
        }

        /**
         * EPUB 3 Navigation Document 여부를 확인합니다.
         *
         * <p>EpubResource의 properties API 구조가 프로젝트마다
         * 다를 수 있으므로 우선 현재 제공되는
         * isNavigationDocument()를 사용합니다.</p>
         */
        private static boolean isNavigationResource(
                EpubResource resource
        ) {

            if (resource == null) {
                return false;
            }

            try {
                return resource
                        .isNavigationDocument();

            } catch (RuntimeException exception) {
                return false;
            }
        }

        private static String normalizeHref(
                String value
        ) {

            if (value == null) {
                return null;
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
    }
}