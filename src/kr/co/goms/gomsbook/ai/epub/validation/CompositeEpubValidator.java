/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;

/**
 * 여러 {@link EpubValidator}를 순차적으로 실행하고
 * 검증 결과를 하나의 {@link EpubValidationResult}로 통합하는
 * Composite Validator입니다.
 *
 * <p>예를 들어 다음 Validator를 하나의 검증 파이프라인으로
 * 구성할 수 있습니다.</p>
 *
 * <pre>
 * DefaultEpubValidator
 *         +
 * DefaultEpubAccessibilityValidator
 *         +
 * DefaultEpubCheckValidator
 *         ↓
 * CompositeEpubValidator
 *         ↓
 * EpubValidationResult
 * </pre>
 *
 * <p>각 Validator는 독립적으로 실행되며, 하나의 Validator에서
 * 예외가 발생하더라도 정책에 따라 다음 Validator를 계속 실행할 수
 * 있습니다.</p>
 */
public final class CompositeEpubValidator
        implements EpubValidator {

    private static final String VALIDATOR_NAME =
            "GomsBook Composite EPUB Validator";

    private static final String VALIDATOR_VERSION =
            "1.0";

    /**
     * 실행할 Validator 목록입니다.
     */
    private final List<EpubValidator> validators;

    /**
     * Validator 하나가 실패했을 때 다음 Validator 실행을
     * 계속할지 여부입니다.
     */
    private final boolean continueOnValidatorFailure;

    /**
     * 지원하지 않는 Validator를 건너뛸지 여부입니다.
     */
    private final boolean skipUnsupportedValidators;

    /**
     * 빈 Composite Validator를 생성합니다.
     *
     * <p>일반적으로 {@link Builder} 사용을 권장합니다.</p>
     */
    public CompositeEpubValidator() {
        this(
                Collections.emptyList(),
                true,
                true
        );
    }

    /**
     * Validator 목록으로 Composite를 생성합니다.
     *
     * @param validators Validator 목록
     */
    public CompositeEpubValidator(
            Collection<? extends EpubValidator> validators
    ) {
        this(
                validators,
                true,
                true
        );
    }

    /**
     * 전체 정책을 지정하여 Composite Validator를 생성합니다.
     *
     * @param validators Validator 목록
     * @param continueOnValidatorFailure 개별 Validator 실패 후 계속 여부
     * @param skipUnsupportedValidators 지원하지 않는 Validator 건너뛰기 여부
     */
    public CompositeEpubValidator(
            Collection<? extends EpubValidator> validators,
            boolean continueOnValidatorFailure,
            boolean skipUnsupportedValidators
    ) {
        this.validators =
                immutableValidators(validators);

        this.continueOnValidatorFailure =
                continueOnValidatorFailure;

        this.skipUnsupportedValidators =
                skipUnsupportedValidators;
    }

    /**
     * EPUB Package 모델을 모든 지원 Validator로 검증합니다.
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) {

        Instant startedAt =
                Instant.now();

        EpubValidationResult.Builder combined =
                EpubValidationResult.builder()
                        .validatorName(getName())
                        .validatorVersion(getVersion())
                        .startedAt(startedAt);

        if (pathConfiguration != null
                && pathConfiguration.getOutputFile() != null) {

            combined.target(
                    pathConfiguration
                            .getOutputFile()
                            .toString()
            );
        }

        List<EpubValidationIssue> issues =
                new ArrayList<>();

        boolean performed = false;

        boolean partial = false;

        Throwable failure = null;

        for (EpubValidator validator : validators) {

            if (!validator.supportsPackageValidation()) {
                if (!skipUnsupportedValidators) {
                    partial = true;

                    issues.add(
                            unsupportedIssue(
                                    validator,
                                    "package-model"
                            )
                    );
                }

                continue;
            }

            if (!validator.supports(
                    epubPackage,
                    options
            )) {

                if (!skipUnsupportedValidators) {
                    partial = true;

                    issues.add(
                            unsupportedIssue(
                                    validator,
                                    "package-model"
                            )
                    );
                }

                continue;
            }

            performed = true;

            try {
                EpubValidationResult result =
                        validator.validate(
                        		projectRoot,
                                epubPackage,
                                pathConfiguration,
                                options
                        );

                mergeResult(
                        result,
                        issues
                );

                if (result != null
                        && result.isPartial()) {
                    partial = true;
                }

                if (result != null
                        && result.getCause().isPresent()) {

                    if (failure == null) {
                        failure =
                                result.getCause().get();
                    }

                    if (!continueOnValidatorFailure) {
                        break;
                    }
                }

            } catch (RuntimeException exception) {

                partial = true;

                issues.add(
                        validatorFailureIssue(
                                validator,
                                exception
                        )
                );

                if (failure == null) {
                    failure = exception;
                }

                if (!continueOnValidatorFailure) {
                    break;
                }
            }
        }

        if (!performed) {
            return EpubValidationResult.builder()
                    .performed(false)
                    .status(
                            EpubValidationResult.Status
                                    .NOT_PERFORMED
                    )
                    .validatorName(getName())
                    .validatorVersion(getVersion())
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .message(
                            "No EPUB package validators were executed."
                    )
                    .build();
        }

        combined.issues(issues)
                .partial(partial)
                .completedAt(Instant.now())
                .message(
                        createSummaryMessage(
                                issues,
                                partial
                        )
                );

        /*
         * 개별 Validator 실행 실패를 Composite 자체 실행 실패로
         * 간주할지는 정책에 따라 다를 수 있습니다.
         *
         * 여기서는 continueOnValidatorFailure=true인 경우에는
         * failure를 cause로 설정하지 않고 PARTIAL + issue로만
         * 표현합니다.
         */
        if (failure != null
                && !continueOnValidatorFailure) {

            combined.cause(failure);
        }

        return combined.build();
    }

    /**
     * 최종 EPUB 파일을 모든 지원 Validator로 검증합니다.
     */
    @Override
    public EpubValidationResult validate(
    		Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    ) {

        Instant startedAt =
                Instant.now();

        EpubValidationResult.Builder combined =
                EpubValidationResult.builder()
                        .validatorName(getName())
                        .validatorVersion(getVersion())
                        .startedAt(startedAt);

        if (epubFile != null) {
            combined.target(
                    epubFile
                            .toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        List<EpubValidationIssue> issues =
                new ArrayList<>();

        boolean performed = false;

        boolean partial = false;

        Throwable failure = null;

        for (EpubValidator validator : validators) {

            if (!validator.supportsArchiveValidation()) {

                if (!skipUnsupportedValidators) {
                    partial = true;

                    issues.add(
                            unsupportedIssue(
                                    validator,
                                    "archive"
                            )
                    );
                }

                continue;
            }

            if (!validator.supports(
                    epubFile,
                    options
            )) {

                if (!skipUnsupportedValidators) {
                    partial = true;

                    issues.add(
                            unsupportedIssue(
                                    validator,
                                    "archive"
                            )
                    );
                }

                continue;
            }

            performed = true;

            try {
                EpubValidationResult result =
                        validator.validate(
                        		projectRoot,
                                epubFile,
                                options
                        );

                mergeResult(
                        result,
                        issues
                );

                if (result != null
                        && result.isPartial()) {
                    partial = true;
                }

                if (result != null
                        && result.getCause().isPresent()) {

                    if (failure == null) {
                        failure =
                                result.getCause().get();
                    }

                    if (!continueOnValidatorFailure) {
                        break;
                    }
                }

            } catch (RuntimeException exception) {

                partial = true;

                issues.add(
                        validatorFailureIssue(
                                validator,
                                exception
                        )
                );

                if (failure == null) {
                    failure = exception;
                }

                if (!continueOnValidatorFailure) {
                    break;
                }
            }
        }

        if (!performed) {

            return EpubValidationResult.builder()
                    .performed(false)
                    .status(
                            EpubValidationResult.Status
                                    .NOT_PERFORMED
                    )
                    .validatorName(getName())
                    .validatorVersion(getVersion())
                    .target(
                            epubFile == null
                                    ? null
                                    : epubFile
                                            .toAbsolutePath()
                                            .normalize()
                                            .toString()
                    )
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .message(
                            "No EPUB archive validators were executed."
                    )
                    .build();
        }

        combined.issues(issues)
                .partial(partial)
                .completedAt(Instant.now())
                .message(
                        createSummaryMessage(
                                issues,
                                partial
                        )
                );

        if (failure != null
                && !continueOnValidatorFailure) {

            combined.cause(failure);
        }

        return combined.build();
    }

    /**
     * Composite가 지정한 Package를 지원하는지 확인합니다.
     */
    @Override
    public boolean supports(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) {

        if (epubPackage == null
                || options == null) {

            return false;
        }

        for (EpubValidator validator : validators) {

            if (!validator.supportsPackageValidation()) {
                continue;
            }

            if (validator.supports(
                    epubPackage,
                    options
            )) {
                return true;
            }
        }

        return false;
    }

    /**
     * Composite가 지정한 EPUB 파일을 지원하는지 확인합니다.
     */
    @Override
    public boolean supports(
            Path epubFile,
            EpubGenerationOptions options
    ) {

        if (epubFile == null
                || options == null) {

            return false;
        }

        for (EpubValidator validator : validators) {

            if (!validator.supportsArchiveValidation()) {
                continue;
            }

            if (validator.supports(
                    epubFile,
                    options
            )) {
                return true;
            }
        }

        return false;
    }

    /**
     * 하나 이상의 Package Validator가 있으면 true입니다.
     */
    @Override
    public boolean supportsPackageValidation() {

        return validators.stream()
                .anyMatch(
                        EpubValidator::
                                supportsPackageValidation
                );
    }

    /**
     * 하나 이상의 Archive Validator가 있으면 true입니다.
     */
    @Override
    public boolean supportsArchiveValidation() {

        return validators.stream()
                .anyMatch(
                        EpubValidator::
                                supportsArchiveValidation
                );
    }

    @Override
    public Type getType() {
        return Type.CUSTOM;
    }

    @Override
    public String getName() {
        return VALIDATOR_NAME;
    }

    @Override
    public String getVersion() {
        return VALIDATOR_VERSION;
    }

    /**
     * 등록된 Validator 목록을 반환합니다.
     */
    public List<EpubValidator> getValidators() {
        return validators;
    }

    public int getValidatorCount() {
        return validators.size();
    }

    public boolean isEmpty() {
        return validators.isEmpty();
    }

    public boolean isContinueOnValidatorFailure() {
        return continueOnValidatorFailure;
    }

    public boolean isSkipUnsupportedValidators() {
        return skipUnsupportedValidators;
    }

    /**
     * 지정한 타입의 Validator가 등록되어 있는지 확인합니다.
     */
    public boolean containsType(
            Type type
    ) {

        if (type == null) {
            return false;
        }

        return validators.stream()
                .anyMatch(
                        validator ->
                                validator.getType()
                                        == type
                );
    }

    /**
     * 지정한 타입의 Validator를 반환합니다.
     */
    public List<EpubValidator> getValidators(
            Type type
    ) {

        if (type == null) {
            return List.of();
        }

        return validators.stream()
                .filter(
                        validator ->
                                validator.getType()
                                        == type
                )
                .toList();
    }

    /**
     * 하위 Validator 결과의 이슈를 Composite 결과에 병합합니다.
     */
    private void mergeResult(
            EpubValidationResult result,
            List<EpubValidationIssue> issues
    ) {

        if (result == null) {
            return;
        }

        issues.addAll(
                result.getIssues()
        );

        /*
         * 하위 Validator가 예외만 반환하고 별도 issue를 만들지
         * 않았다면 진단 정보가 사라지지 않도록 issue를 보완합니다.
         */
        if (result.getCause().isPresent()
                && result.getIssues().isEmpty()) {

            Throwable cause =
                    result.getCause().get();

            issues.add(
                    EpubValidationIssue.builder(
                            "EPUB-VALIDATOR-EXECUTION-001",
                            EpubValidationIssue.Severity.ERROR,
                            "An EPUB validator failed during execution."
                    )
                            .category(
                                    EpubValidationIssue.Category
                                            .GENERAL
                            )
                            .validator(
                                    result.getValidatorName()
                                            .orElse("Unknown Validator")
                            )
                            .originalMessage(
                                    safeMessage(cause)
                            )
                            .detail(
                                    "exceptionType",
                                    cause.getClass()
                                            .getName()
                            )
                            .build()
            );
        }
    }

    /**
     * Validator 자체 실행 실패 이슈를 생성합니다.
     */
    private EpubValidationIssue validatorFailureIssue(
            EpubValidator validator,
            Throwable cause
    ) {

        return EpubValidationIssue.builder(
                "EPUB-VALIDATOR-EXECUTION-002",
                EpubValidationIssue.Severity.ERROR,
                "EPUB validator execution failed: "
                        + validator.getName()
        )
                .category(
                        categoryForValidator(
                                validator
                        )
                )
                .validator(
                        validator.getName()
                )
                .originalMessage(
                        safeMessage(cause)
                )
                .detail(
                        "validatorType",
                        validator.getType()
                                .name()
                )
                .detail(
                        "exceptionType",
                        cause == null
                                ? "unknown"
                                : cause.getClass()
                                    .getName()
                )
                .build();
    }

    /**
     * 지원하지 않는 Validator 이슈입니다.
     */
    private EpubValidationIssue unsupportedIssue(
            EpubValidator validator,
            String targetType
    ) {

        return EpubValidationIssue.builder(
                "EPUB-VALIDATOR-UNSUPPORTED-001",
                EpubValidationIssue.Severity.INFO,
                "Validator does not support "
                        + targetType
                        + " validation: "
                        + validator.getName()
        )
                .category(
                        categoryForValidator(
                                validator
                        )
                )
                .validator(
                        validator.getName()
                )
                .detail(
                        "validatorType",
                        validator.getType()
                                .name()
                )
                .detail(
                        "targetType",
                        targetType
                )
                .build();
    }

    /**
     * Validator 타입을 검증 Category로 변환합니다.
     */
    private EpubValidationIssue.Category
            categoryForValidator(
                    EpubValidator validator
            ) {

        if (validator == null) {
            return EpubValidationIssue.Category.GENERAL;
        }

        return switch (validator.getType()) {

            case ACCESSIBILITY ->
                    EpubValidationIssue.Category
                            .ACCESSIBILITY;

            case EPUB_CHECK ->
                    EpubValidationIssue.Category
                            .EPUB_CHECK;

            case XHTML ->
                    EpubValidationIssue.Category
                            .XHTML;

            case INTERNAL,
                 CUSTOM ->
                    EpubValidationIssue.Category
                            .GENERAL;
        };
    }

    /**
     * Composite 결과 메시지를 생성합니다.
     */
    private String createSummaryMessage(
            List<EpubValidationIssue> issues,
            boolean partial
    ) {

        long fatal =
                issues.stream()
                        .filter(
                                EpubValidationIssue::isFatal
                        )
                        .count();

        long errors =
                issues.stream()
                        .filter(issue ->
                                issue.getSeverity()
                                        == EpubValidationIssue
                                            .Severity.ERROR
                        )
                        .count();

        long warnings =
                issues.stream()
                        .filter(
                                EpubValidationIssue::isWarning
                        )
                        .count();

        long info =
                issues.stream()
                        .filter(
                                EpubValidationIssue::isInfo
                        )
                        .count();

        return "Composite EPUB validation completed. "
                + "validators="
                + validators.size()
                + ", fatal="
                + fatal
                + ", errors="
                + errors
                + ", warnings="
                + warnings
                + ", info="
                + info
                + ", partial="
                + partial;
    }

    private static String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown validation error.";
        }

        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {

            return throwable
                    .getClass()
                    .getName();
        }

        return message.trim();
    }

    private static List<EpubValidator>
            immutableValidators(
                    Collection<? extends EpubValidator>
                            validators
            ) {

        if (validators == null
                || validators.isEmpty()) {

            return Collections.emptyList();
        }

        List<EpubValidator> result =
                new ArrayList<>();

        for (EpubValidator validator : validators) {

            EpubValidator value =
                    Objects.requireNonNull(
                            validator,
                            "EPUB validator must not be null."
                    );

            /*
             * 같은 인스턴스가 중복 등록되는 것을 방지합니다.
             */
            if (!result.contains(value)) {
                result.add(value);
            }
        }

        return Collections.unmodifiableList(
                result
        );
    }

    /**
     * Composite Validator Builder입니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<EpubValidator> validators =
                new ArrayList<>();

        private boolean continueOnValidatorFailure =
                true;

        private boolean skipUnsupportedValidators =
                true;

        private Builder() {
        }

        public Builder validator(
                EpubValidator validator
        ) {

            validators.add(
                    Objects.requireNonNull(
                            validator,
                            "EPUB validator must not be null."
                    )
            );

            return this;
        }

        public Builder validators(
                Collection<? extends EpubValidator>
                        validators
        ) {

            if (validators == null) {
                return this;
            }

            for (EpubValidator validator :
                    validators) {

                validator(validator);
            }

            return this;
        }

        /**
         * 내부 EPUB Validator를 등록합니다.
         */
        public Builder internalValidator(
                EpubValidator validator
        ) {
            return validator(validator);
        }

        /**
         * 접근성 Validator를 등록합니다.
         */
        public Builder accessibilityValidator(
                EpubAccessibilityValidator validator
        ) {
            return validator(validator);
        }

        /**
         * EPUBCheck Validator를 등록합니다.
         */
        public Builder epubCheckValidator(
                EpubCheckValidator validator
        ) {
            return validator(validator);
        }

        public Builder continueOnValidatorFailure(
                boolean value
        ) {

            this.continueOnValidatorFailure =
                    value;

            return this;
        }

        public Builder skipUnsupportedValidators(
                boolean value
        ) {

            this.skipUnsupportedValidators =
                    value;

            return this;
        }

        public CompositeEpubValidator build() {

            return new CompositeEpubValidator(
                    validators,
                    continueOnValidatorFailure,
                    skipUnsupportedValidators
            );
        }
    }
}