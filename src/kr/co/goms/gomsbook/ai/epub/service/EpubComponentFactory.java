/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.CompositeEpubValidator;
import kr.co.goms.gomsbook.ai.epub.validation.DefaultEpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.DefaultEpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.DefaultEpubValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidator;

/**
 * EPUB 계층의 기본 컴포넌트를 생성하고 조립하는 Factory입니다.
 *
 * <p>EPUB 생성에 필요한 Writer, Builder, Workspace Manager,
 * Archive Writer 및 Validator를 한 곳에서 생성하여
 * 구성의 일관성을 유지합니다.</p>
 *
 * <p>전체 구성은 다음과 같습니다.</p>
 *
 * <pre>
 * EpubComponentFactory
 * │
 * ├─ Core Components
 * │  ├─ EpubWorkspaceManager
 * │  ├─ EpubMimetypeWriter
 * │  ├─ EpubContainerDocumentWriter
 * │  ├─ EpubPackageDocumentWriter
 * │  ├─ EpubResourceWriter
 * │  ├─ EpubNavigationBuilder
 * │  ├─ EpubNavigationDocumentWriter
 * │  ├─ EpubNcxBuilder
 * │  ├─ EpubNcxDocumentWriter
 * │  └─ EpubArchiveWriter
 * │
 * ├─ Validators
 * │  ├─ DefaultEpubValidator
 * │  ├─ DefaultEpubAccessibilityValidator
 * │  ├─ DefaultEpubCheckValidator
 * │  └─ CompositeEpubValidator
 * │
 * └─ DefaultEpubGenerator
 * </pre>
 *
 * <p>이 클래스는 상태를 가지지 않으며 정적 팩토리 메서드만
 * 제공합니다.</p>
 */
public final class EpubComponentFactory {

    private EpubComponentFactory() {
    }

    /*
     * ============================================================
     * Core Components
     * ============================================================
     */

    /**
     * 기본 Workspace Manager를 생성합니다.
     */
    public static EpubWorkspaceManager
            createWorkspaceManager() {

        return new DefaultEpubWorkspaceManager();
    }

    /**
     * 기본 mimetype Writer를 생성합니다.
     */
    public static EpubMimetypeWriter
            createMimetypeWriter() {

        return new DefaultEpubMimetypeWriter();
    }

    /**
     * 기본 container.xml Writer를 생성합니다.
     */
    public static EpubContainerDocumentWriter
            createContainerDocumentWriter() {

        return new DefaultEpubContainerDocumentWriter();
    }

    /**
     * 기본 content.opf Writer를 생성합니다.
     */
    public static EpubPackageDocumentWriter
            createPackageDocumentWriter() {

        return new DefaultEpubPackageDocumentWriter();
    }

    /**
     * 기본 EPUB Resource Writer를 생성합니다.
     */
    public static EpubResourceWriter
            createResourceWriter() {

        return new DefaultEpubResourceWriter();
    }

    /**
     * 기본 EPUB 3 Navigation Builder를 생성합니다.
     */
    public static EpubNavigationBuilder
            createNavigationBuilder() {

        return new DefaultEpubNavigationBuilder();
    }

    /**
     * 기본 Navigation Document Writer를 생성합니다.
     *
     * <p>기본 OPF 기준 경로는 {@code Text/nav.xhtml}입니다.</p>
     */
    public static EpubNavigationDocumentWriter
            createNavigationDocumentWriter() {

        return new DefaultEpubNavigationDocumentWriter();
    }

    /**
     * Navigation Document의 OPF 기준 href를 지정하여
     * Writer를 생성합니다.
     */
    public static EpubNavigationDocumentWriter
            createNavigationDocumentWriter(
                    String navigationDocumentHref
            ) {

        return new DefaultEpubNavigationDocumentWriter(
                Objects.requireNonNull(
                        navigationDocumentHref,
                        "Navigation document href must not be null."
                )
        );
    }

    /**
     * 기본 NCX Builder를 생성합니다.
     */
    public static EpubNcxBuilder
            createNcxBuilder() {

        return new DefaultEpubNcxBuilder();
    }

    /**
     * 기본 NCX Document Writer를 생성합니다.
     */
    public static EpubNcxDocumentWriter
            createNcxDocumentWriter() {

        return new DefaultEpubNcxDocumentWriter();
    }

    /**
     * 기본 Archive Writer를 생성합니다.
     */
    public static EpubArchiveWriter
            createArchiveWriter() {

        return new DefaultEpubArchiveWriter();
    }

    /**
     * 핵심 EPUB 컴포넌트를 한 번에 생성합니다.
     */
    public static Components
            createCoreComponents() {

        EpubWorkspaceManager workspaceManager =
                createWorkspaceManager();

        EpubMimetypeWriter mimetypeWriter =
                createMimetypeWriter();

        EpubContainerDocumentWriter
                containerDocumentWriter =
                createContainerDocumentWriter();

        EpubPackageDocumentWriter
                packageDocumentWriter =
                createPackageDocumentWriter();

        EpubResourceWriter resourceWriter =
                createResourceWriter();

        EpubNavigationBuilder navigationBuilder =
                createNavigationBuilder();

        EpubNavigationDocumentWriter
                navigationDocumentWriter =
                createNavigationDocumentWriter();

        EpubNcxBuilder ncxBuilder =
                createNcxBuilder();

        EpubNcxDocumentWriter ncxDocumentWriter =
                createNcxDocumentWriter();

        EpubArchiveWriter archiveWriter =
                createArchiveWriter();

        return new Components(
                workspaceManager,
                mimetypeWriter,
                containerDocumentWriter,
                packageDocumentWriter,
                resourceWriter,
                navigationBuilder,
                navigationDocumentWriter,
                ncxBuilder,
                ncxDocumentWriter,
                archiveWriter
        );
    }

    /*
     * ============================================================
     * Validators
     * ============================================================
     */

    /**
     * EPUB 내부 구조 Validator를 생성합니다.
     */
    public static EpubValidator
            createInternalValidator() {

        return new DefaultEpubValidator();
    }

    /**
     * 기존 접근성 계층을 EPUB 접근성 Validator로 연결합니다.
     */
    public static EpubAccessibilityValidator
            createAccessibilityValidator(
                    AccessibilityValidator
                            accessibilityValidator
            ) {

        return new DefaultEpubAccessibilityValidator(
                Objects.requireNonNull(
                        accessibilityValidator,
                        "Accessibility validator must not be null."
                )
        );
    }

    /**
     * 기본 EPUBCheck Validator를 생성합니다.
     */
    public static EpubCheckValidator
            createEpubCheckValidator() {

        return new DefaultEpubCheckValidator();
    }

    /**
     * 내부 EPUB Validator만 사용하는 검증기 집합입니다.
     */
    public static Validators
            createInternalValidators() {

        EpubValidator internal =
                createInternalValidator();

        return new Validators(
                internal,
                null,
                null,
                CompositeEpubValidator.builder()
                        .internalValidator(internal)
                        .build()
        );
    }

    /**
     * 접근성 계층 없이
     * 내부 검증 + EPUBCheck를 구성합니다.
     */
    public static Validators
            createStandardValidators() {

        EpubValidator internal =
                createInternalValidator();

        EpubCheckValidator epubCheck =
                createEpubCheckValidator();

        CompositeEpubValidator composite =
                CompositeEpubValidator.builder()
                        .internalValidator(
                                internal
                        )
                        .epubCheckValidator(
                                epubCheck
                        )
                        .continueOnValidatorFailure(
                                true
                        )
                        .skipUnsupportedValidators(
                                true
                        )
                        .build();

        return new Validators(
                internal,
                null,
                epubCheck,
                composite
        );
    }

    /**
     * 내부 검증 + 접근성 검증 + EPUBCheck 전체 Validator 세트를
     * 생성합니다.
     */
    public static Validators
            createValidators(
                    AccessibilityValidator
                            accessibilityValidator
            ) {

        Objects.requireNonNull(
                accessibilityValidator,
                "Accessibility validator must not be null."
        );

        EpubValidator internal =
                createInternalValidator();

        EpubAccessibilityValidator accessibility =
                createAccessibilityValidator(
                        accessibilityValidator
                );

        EpubCheckValidator epubCheck =
                createEpubCheckValidator();

        CompositeEpubValidator composite =
                CompositeEpubValidator.builder()
                        .internalValidator(
                                internal
                        )
                        .accessibilityValidator(
                                accessibility
                        )
                        .epubCheckValidator(
                                epubCheck
                        )
                        .continueOnValidatorFailure(
                                true
                        )
                        .skipUnsupportedValidators(
                                true
                        )
                        .build();

        return new Validators(
                internal,
                accessibility,
                epubCheck,
                composite
        );
    }

    /**
     * 외부에서 직접 생성한 Validator를 사용하여
     * Validator 세트를 구성합니다.
     */
    public static Validators
            createValidators(
                    EpubValidator internalValidator,
                    EpubAccessibilityValidator
                            accessibilityValidator,
                    EpubCheckValidator epubCheckValidator
            ) {

        CompositeEpubValidator.Builder composite =
                CompositeEpubValidator.builder()
                        .continueOnValidatorFailure(
                                true
                        )
                        .skipUnsupportedValidators(
                                true
                        );

        if (internalValidator != null) {
            composite.internalValidator(
                    internalValidator
            );
        }

        if (accessibilityValidator != null) {
            composite.accessibilityValidator(
                    accessibilityValidator
            );
        }

        if (epubCheckValidator != null) {
            composite.epubCheckValidator(
                    epubCheckValidator
            );
        }

        return new Validators(
                internalValidator,
                accessibilityValidator,
                epubCheckValidator,
                composite.build()
        );
    }

    /*
     * ============================================================
     * Generator
     * ============================================================
     */

    /**
     * 검증기를 연결하지 않은 기본 EPUB Generator를 생성합니다.
     *
     * <p>EPUB 생성 자체만 필요한 테스트 또는 최소 환경에서
     * 사용할 수 있습니다.</p>
     */
    public static EpubGenerator
            createGenerator() {

        Components components =
                createCoreComponents();

        return new DefaultEpubGenerator(
                components.workspaceManager(),
                components.mimetypeWriter(),
                components.containerDocumentWriter(),
                components.packageDocumentWriter(),
                components.resourceWriter(),
                components.navigationBuilder(),
                components.navigationDocumentWriter(),
                components.ncxBuilder(),
                components.ncxDocumentWriter(),
                components.archiveWriter()
        );
    }

    /**
     * 내부 Validator와 EPUBCheck를 포함하는
     * 표준 EPUB Generator를 생성합니다.
     *
     * <p>접근성 Validator는 포함하지 않습니다.</p>
     */
    public static EpubGenerator
            createStandardGenerator() {

        Components components =
                createCoreComponents();

        Validators validators =
                createStandardValidators();

        return createGenerator(
                components,
                validators
        );
    }

    /**
     * 기존 Accessibility 계층까지 포함한
     * 전체 EPUB Generator를 생성합니다.
     *
     * <p>실제 GomsBook Editor 실행 환경에서는 이 메서드를
     * 기본 진입점으로 사용하는 것을 권장합니다.</p>
     */
    public static EpubGenerator
            createGenerator(
                    AccessibilityValidator
                            accessibilityValidator
            ) {

        Components components =
                createCoreComponents();

        Validators validators =
                createValidators(
                        accessibilityValidator
                );

        return createGenerator(
                components,
                validators
        );
    }

    /**
     * Core Components와 Validators를 직접 전달하여
     * EPUB Generator를 생성합니다.
     */
    public static EpubGenerator
            createGenerator(
                    Components components,
                    Validators validators
            ) {

        Objects.requireNonNull(
                components,
                "EPUB components must not be null."
        );

        Objects.requireNonNull(
                validators,
                "EPUB validators must not be null."
        );

        return new DefaultEpubGenerator(
                components.workspaceManager(),
                components.mimetypeWriter(),
                components.containerDocumentWriter(),
                components.packageDocumentWriter(),
                components.resourceWriter(),
                components.navigationBuilder(),
                components.navigationDocumentWriter(),
                components.ncxBuilder(),
                components.ncxDocumentWriter(),
                components.archiveWriter(),

                validators.internalValidator(),
                validators.accessibilityValidator(),
                validators.epubCheckValidator()
        );
    }

    /**
     * 모든 생성 컴포넌트와 Validator를 직접 지정하여
     * Generator를 생성합니다.
     */
    public static EpubGenerator
            createGenerator(
                    EpubWorkspaceManager workspaceManager,
                    EpubMimetypeWriter mimetypeWriter,
                    EpubContainerDocumentWriter
                            containerDocumentWriter,
                    EpubPackageDocumentWriter
                            packageDocumentWriter,
                    EpubResourceWriter resourceWriter,
                    EpubNavigationBuilder
                            navigationBuilder,
                    EpubNavigationDocumentWriter
                            navigationDocumentWriter,
                    EpubNcxBuilder ncxBuilder,
                    EpubNcxDocumentWriter
                            ncxDocumentWriter,
                    EpubArchiveWriter archiveWriter,
                    EpubValidator epubValidator,
                    EpubAccessibilityValidator
                            accessibilityValidator,
                    EpubCheckValidator
                            epubCheckValidator
            ) {

        return new DefaultEpubGenerator(
                workspaceManager,
                mimetypeWriter,
                containerDocumentWriter,
                packageDocumentWriter,
                resourceWriter,
                navigationBuilder,
                navigationDocumentWriter,
                ncxBuilder,
                ncxDocumentWriter,
                archiveWriter,
                epubValidator,
                accessibilityValidator,
                epubCheckValidator
        );
    }

    /*
     * ============================================================
     * Full Component Set
     * ============================================================
     */

    /**
     * 접근성 계층을 포함한 EPUB 전체 구성 객체를 생성합니다.
     */
    public static ComponentSet
            createComponentSet(
                    AccessibilityValidator
                            accessibilityValidator
            ) {

        Components components =
                createCoreComponents();

        Validators validators =
                createValidators(
                        accessibilityValidator
                );

        EpubGenerator generator =
                createGenerator(
                        components,
                        validators
                );

        return new ComponentSet(
                components,
                validators,
                generator
        );
    }

    /**
     * 접근성 계층 없이 표준 EPUB 전체 구성 객체를 생성합니다.
     */
    public static ComponentSet
            createStandardComponentSet() {

        Components components =
                createCoreComponents();

        Validators validators =
                createStandardValidators();

        EpubGenerator generator =
                createGenerator(
                        components,
                        validators
                );

        return new ComponentSet(
                components,
                validators,
                generator
        );
    }

    /*
     * ============================================================
     * Component Records
     * ============================================================
     */

    /**
     * EPUB 생성 핵심 컴포넌트 집합입니다.
     */
    public record Components(
            EpubWorkspaceManager workspaceManager,
            EpubMimetypeWriter mimetypeWriter,
            EpubContainerDocumentWriter
                    containerDocumentWriter,
            EpubPackageDocumentWriter
                    packageDocumentWriter,
            EpubResourceWriter resourceWriter,
            EpubNavigationBuilder navigationBuilder,
            EpubNavigationDocumentWriter
                    navigationDocumentWriter,
            EpubNcxBuilder ncxBuilder,
            EpubNcxDocumentWriter
                    ncxDocumentWriter,
            EpubArchiveWriter archiveWriter
    ) {

        public Components {

            Objects.requireNonNull(
                    workspaceManager,
                    "EPUB workspace manager must not be null."
            );

            Objects.requireNonNull(
                    mimetypeWriter,
                    "EPUB mimetype writer must not be null."
            );

            Objects.requireNonNull(
                    containerDocumentWriter,
                    "EPUB container document writer "
                            + "must not be null."
            );

            Objects.requireNonNull(
                    packageDocumentWriter,
                    "EPUB package document writer "
                            + "must not be null."
            );

            Objects.requireNonNull(
                    resourceWriter,
                    "EPUB resource writer must not be null."
            );

            Objects.requireNonNull(
                    navigationBuilder,
                    "EPUB navigation builder must not be null."
            );

            Objects.requireNonNull(
                    navigationDocumentWriter,
                    "EPUB navigation document writer "
                            + "must not be null."
            );

            Objects.requireNonNull(
                    ncxBuilder,
                    "EPUB NCX builder must not be null."
            );

            Objects.requireNonNull(
                    ncxDocumentWriter,
                    "EPUB NCX document writer must not be null."
            );

            Objects.requireNonNull(
                    archiveWriter,
                    "EPUB archive writer must not be null."
            );
        }
    }

    /**
     * EPUB Validator 집합입니다.
     *
     * <p>접근성 또는 EPUBCheck가 없는 환경을 지원하기 위해
     * 각 개별 Validator는 null일 수 있습니다.
     * Composite Validator는 항상 존재합니다.</p>
     */
    public record Validators(
            EpubValidator internalValidator,
            EpubAccessibilityValidator
                    accessibilityValidator,
            EpubCheckValidator epubCheckValidator,
            CompositeEpubValidator compositeValidator
    ) {

        public Validators {

            Objects.requireNonNull(
                    compositeValidator,
                    "Composite EPUB validator must not be null."
            );
        }

        /**
         * 내부 Validator가 존재하는지 확인합니다.
         */
        public boolean hasInternalValidator() {
            return internalValidator != null;
        }

        /**
         * 접근성 Validator가 존재하는지 확인합니다.
         */
        public boolean hasAccessibilityValidator() {
            return accessibilityValidator != null;
        }

        /**
         * EPUBCheck Validator가 존재하는지 확인합니다.
         */
        public boolean hasEpubCheckValidator() {
            return epubCheckValidator != null;
        }

        /**
         * 모든 Validator가 구성되어 있는지 확인합니다.
         */
        public boolean isComplete() {
            return internalValidator != null
                    && accessibilityValidator != null
                    && epubCheckValidator != null;
        }
    }

    /**
     * EPUB 전체 런타임 구성에 필요한 컴포넌트 집합입니다.
     */
    public record ComponentSet(
            Components components,
            Validators validators,
            EpubGenerator generator
    ) {

        public ComponentSet {

            Objects.requireNonNull(
                    components,
                    "EPUB core components must not be null."
            );

            Objects.requireNonNull(
                    validators,
                    "EPUB validators must not be null."
            );

            Objects.requireNonNull(
                    generator,
                    "EPUB generator must not be null."
            );
        }

        public EpubValidator internalValidator() {
            return validators.internalValidator();
        }

        public EpubAccessibilityValidator
                accessibilityValidator() {

            return validators
                    .accessibilityValidator();
        }

        public EpubCheckValidator
                epubCheckValidator() {

            return validators
                    .epubCheckValidator();
        }

        public CompositeEpubValidator
                compositeValidator() {

            return validators
                    .compositeValidator();
        }
    }
}