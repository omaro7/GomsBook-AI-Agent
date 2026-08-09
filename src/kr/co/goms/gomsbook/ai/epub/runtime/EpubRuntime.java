/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.runtime;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationRequest;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubComponentFactory;
import kr.co.goms.gomsbook.ai.epub.service.EpubGenerationException;
import kr.co.goms.gomsbook.ai.epub.service.EpubGenerator;
import kr.co.goms.gomsbook.ai.epub.validation.CompositeEpubValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubAccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidator;

/**
 * EPUB 계층의 애플리케이션 런타임 진입점입니다.
 *
 * <p>EPUB 생성에 필요한 Generator, Validator 및 하위 컴포넌트를
 * 애플리케이션 생명주기 동안 한 번 구성하고 재사용합니다.</p>
 *
 * <p>외부 계층에서는 개별 Writer나 Builder를 직접 조립할 필요 없이
 * 이 Runtime을 통해 EPUB 생성을 수행할 수 있습니다.</p>
 *
 * <pre>
 * AccessibilityRuntime
 *         │
 *         ▼
 * AccessibilityValidator
 *         │
 *         ▼
 * EpubRuntime
 *         │
 *         ├─ EpubGenerator
 *         ├─ EpubValidator
 *         ├─ EpubAccessibilityValidator
 *         ├─ EpubCheckValidator
 *         └─ CompositeEpubValidator
 * </pre>
 *
 * <p>이 클래스는 내부 컴포넌트 자체를 변경하지 않으므로
 * 생성 후 여러 EPUB 요청에서 재사용할 수 있습니다.</p>
 */
public final class EpubRuntime {

    /**
     * 전체 EPUB 컴포넌트 구성입니다.
     */
    private final EpubComponentFactory.ComponentSet componentSet;

    /**
     * EPUB Generator입니다.
     */
    private final EpubGenerator generator;

    /**
     * 기본 EPUB 구조 Validator입니다.
     */
    private final EpubValidator internalValidator;

    /**
     * EPUB 접근성 Validator입니다.
     *
     * <p>접근성 없이 구성된 Runtime에서는 null일 수 있습니다.</p>
     */
    private final EpubAccessibilityValidator accessibilityValidator;

    /**
     * EPUBCheck Validator입니다.
     *
     * <p>최소 Runtime에서는 null일 수 있습니다.</p>
     */
    private final EpubCheckValidator epubCheckValidator;

    /**
     * 여러 Validator를 통합 실행하는 Composite Validator입니다.
     */
    private final CompositeEpubValidator compositeValidator;

    private EpubRuntime(
            EpubComponentFactory.ComponentSet componentSet
    ) {
        this.componentSet =
                Objects.requireNonNull(
                        componentSet,
                        "EPUB component set must not be null."
                );

        this.generator =
                Objects.requireNonNull(
                        componentSet.generator(),
                        "EPUB generator must not be null."
                );

        this.internalValidator =
                componentSet.internalValidator();

        this.accessibilityValidator =
                componentSet.accessibilityValidator();

        this.epubCheckValidator =
                componentSet.epubCheckValidator();

        this.compositeValidator =
                Objects.requireNonNull(
                        componentSet.compositeValidator(),
                        "Composite EPUB validator must not be null."
                );
    }

    /**
     * 접근성 계층까지 연결된 전체 EPUB Runtime을 생성합니다.
     *
     * <p>실제 GomsBook Editor 환경에서 기본적으로 사용할
     * 권장 팩토리 메서드입니다.</p>
     *
     * @param accessibilityValidator 기존 접근성 Validator
     * @return EPUB Runtime
     */
    public static EpubRuntime create(
            AccessibilityValidator accessibilityValidator
    ) {
        Objects.requireNonNull(
                accessibilityValidator,
                "Accessibility validator must not be null."
        );

        EpubComponentFactory.ComponentSet componentSet =
                EpubComponentFactory.createComponentSet(
                        accessibilityValidator
                );

        return new EpubRuntime(
                componentSet
        );
    }

    /**
     * 내부 검증 + EPUBCheck를 포함하지만
     * 접근성 Validator는 포함하지 않는 Runtime을 생성합니다.
     *
     * <p>접근성 계층을 사용할 수 없는 개발/테스트 환경에서
     * 사용할 수 있습니다.</p>
     *
     * @return 표준 EPUB Runtime
     */
    public static EpubRuntime createStandard() {

        return new EpubRuntime(
                EpubComponentFactory
                        .createStandardComponentSet()
        );
    }

    /**
     * 외부에서 조립한 ComponentSet으로 Runtime을 생성합니다.
     *
     * <p>테스트 또는 사용자 정의 컴포넌트를 연결할 때 사용합니다.</p>
     *
     * @param componentSet EPUB 컴포넌트 구성
     * @return EPUB Runtime
     */
    public static EpubRuntime of(
            EpubComponentFactory.ComponentSet componentSet
    ) {
        return new EpubRuntime(
                componentSet
        );
    }

    /**
     * EPUB 파일을 생성합니다.
     *
     * <p>실제 생성 파이프라인은 {@link EpubGenerator}가 담당합니다.</p>
     *
     * @param request EPUB 생성 요청
     * @return EPUB 생성 결과
     * @throws EpubGenerationException 생성 요청 자체를 처리할 수 없는 경우
     */
    public EpubGenerationResult generate(
            EpubGenerationRequest request
    ) throws EpubGenerationException {

        Objects.requireNonNull(
                request,
                "EPUB generation request must not be null."
        );

        return generator.generate(
                request
        );
    }

    /**
     * 현재 Runtime이 지정한 생성 요청을 지원하는지 확인합니다.
     *
     * @param request EPUB 생성 요청
     * @return 지원 여부
     */
    public boolean supports(
            EpubGenerationRequest request
    ) {
        return request != null
                && generator.supports(request);
    }

    /**
     * 접근성 검증이 연결되어 있는지 확인합니다.
     */
    public boolean hasAccessibilityValidation() {
        return accessibilityValidator != null;
    }

    /**
     * EPUBCheck가 연결되어 있는지 확인합니다.
     */
    public boolean hasEpubCheck() {
        return epubCheckValidator != null;
    }

    /**
     * 내부 구조 Validator가 연결되어 있는지 확인합니다.
     */
    public boolean hasInternalValidation() {
        return internalValidator != null;
    }

    /**
     * EPUBCheck Java API가 실제 실행 가능한지 확인합니다.
     */
    public boolean isEpubCheckAvailable() {

        return epubCheckValidator != null
                && epubCheckValidator.isAvailable();
    }

    /**
     * 전체 Validator 구성이 완료되었는지 확인합니다.
     *
     * <p>내부 검증 + 접근성 검증 + EPUBCheck가 모두 존재하면
     * {@code true}입니다.</p>
     */
    public boolean isFullValidationAvailable() {
        return internalValidator != null
                && accessibilityValidator != null
                && epubCheckValidator != null;
    }

    /**
     * EPUB Generator를 반환합니다.
     */
    public EpubGenerator getGenerator() {
        return generator;
    }

    /**
     * 내부 EPUB Validator를 반환합니다.
     *
     * @return Validator 또는 null
     */
    public EpubValidator getInternalValidator() {
        return internalValidator;
    }

    /**
     * EPUB 접근성 Validator를 반환합니다.
     *
     * @return 접근성 Validator 또는 null
     */
    public EpubAccessibilityValidator
            getAccessibilityValidator() {

        return accessibilityValidator;
    }

    /**
     * EPUBCheck Validator를 반환합니다.
     *
     * @return EPUBCheck Validator 또는 null
     */
    public EpubCheckValidator
            getEpubCheckValidator() {

        return epubCheckValidator;
    }

    /**
     * Composite Validator를 반환합니다.
     */
    public CompositeEpubValidator
            getCompositeValidator() {

        return compositeValidator;
    }

    /**
     * 전체 ComponentSet을 반환합니다.
     *
     * <p>특수한 테스트나 하위 컴포넌트 직접 접근이 필요한 경우에만
     * 사용하는 것을 권장합니다.</p>
     */
    public EpubComponentFactory.ComponentSet
            getComponentSet() {

        return componentSet;
    }

    /**
     * Core Components를 반환합니다.
     */
    public EpubComponentFactory.Components
            getComponents() {

        return componentSet.components();
    }

    /**
     * Validator 구성 객체를 반환합니다.
     */
    public EpubComponentFactory.Validators
            getValidators() {

        return componentSet.validators();
    }

    /**
     * 현재 Runtime 상태를 간단히 반환합니다.
     */
    public RuntimeStatus getStatus() {

        return new RuntimeStatus(
                generator != null,
                internalValidator != null,
                accessibilityValidator != null,
                epubCheckValidator != null,
                isEpubCheckAvailable(),
                compositeValidator
                        .getValidatorCount()
        );
    }

    @Override
    public String toString() {
        return "EpubRuntime{"
                + "generator="
                + generator.getClass().getSimpleName()
                + ", internalValidation="
                + (internalValidator != null)
                + ", accessibilityValidation="
                + (accessibilityValidator != null)
                + ", epubCheck="
                + (epubCheckValidator != null)
                + ", epubCheckAvailable="
                + isEpubCheckAvailable()
                + '}';
    }

    /**
     * EPUB Runtime의 현재 구성 상태입니다.
     */
    public record RuntimeStatus(
            boolean generatorAvailable,
            boolean internalValidationAvailable,
            boolean accessibilityValidationAvailable,
            boolean epubCheckConfigured,
            boolean epubCheckAvailable,
            int validatorCount
    ) {

        /**
         * EPUB 생성 기능을 사용할 수 있는지 확인합니다.
         */
        public boolean isReady() {
            return generatorAvailable;
        }

        /**
         * 모든 검증 계층이 연결되어 있는지 확인합니다.
         */
        public boolean isFullValidationConfigured() {
            return internalValidationAvailable
                    && accessibilityValidationAvailable
                    && epubCheckConfigured;
        }

        /**
         * 모든 검증 계층이 실제 실행 가능한지 확인합니다.
         */
        public boolean isFullyOperational() {
            return generatorAvailable
                    && internalValidationAvailable
                    && accessibilityValidationAvailable
                    && epubCheckConfigured
                    && epubCheckAvailable;
        }

        @Override
        public String toString() {
            return "RuntimeStatus{"
                    + "ready=" + isReady()
                    + ", internalValidation="
                    + internalValidationAvailable
                    + ", accessibilityValidation="
                    + accessibilityValidationAvailable
                    + ", epubCheckConfigured="
                    + epubCheckConfigured
                    + ", epubCheckAvailable="
                    + epubCheckAvailable
                    + ", validatorCount="
                    + validatorCount
                    + '}';
        }
    }
}