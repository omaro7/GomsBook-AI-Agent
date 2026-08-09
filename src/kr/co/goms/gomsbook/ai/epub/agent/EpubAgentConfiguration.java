/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.agent;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * EPUB Agent 실행 설정을 정의합니다.
 *
 * <p>EPUB Agent가 사용할 LLM 모델, System Prompt,
 * Tool 활성화 여부, 최대 실행 단계, timeout 등의 설정을
 * 한 곳에서 관리합니다.</p>
 *
 * <p>이 클래스는 불변 객체이며 {@link Builder}를 통해 생성합니다.</p>
 */
public final class EpubAgentConfiguration {

    /**
     * 기본 Agent 이름입니다.
     */
    public static final String DEFAULT_AGENT_NAME =
            "GomsBook EPUB Agent";

    /**
     * 기본 모델입니다.
     *
     * <p>현재 프로젝트의 로컬 Ollama Agent 모델 정책에 맞게
     * 필요 시 Builder에서 변경할 수 있습니다.</p>
     */
    public static final String DEFAULT_MODEL =
            "gemma4:31b-cloud";

    /**
     * 기본 temperature입니다.
     *
     * <p>EPUB 생성/검증 Agent는 창작보다는 정확성이 중요하므로
     * 낮은 값을 사용합니다.</p>
     */
    public static final double DEFAULT_TEMPERATURE =
            0.1d;

    /**
     * 기본 최대 Agent 실행 단계입니다.
     */
    public static final int DEFAULT_MAX_STEPS =
            12;

    /**
     * 기본 최대 Tool 호출 횟수입니다.
     */
    public static final int DEFAULT_MAX_TOOL_CALLS =
            16;

    /**
     * 기본 Agent timeout입니다.
     */
    public static final Duration DEFAULT_TIMEOUT =
            Duration.ofMinutes(5);

    /**
     * Agent 이름입니다.
     */
    private final String agentName;

    /**
     * LLM 모델명입니다.
     */
    private final String model;

    /**
     * Agent System Prompt입니다.
     */
    private final String systemPrompt;

    /**
     * LLM temperature입니다.
     */
    private final double temperature;

    /**
     * 최대 Agent step 수입니다.
     */
    private final int maxSteps;

    /**
     * 최대 Tool 호출 횟수입니다.
     */
    private final int maxToolCalls;

    /**
     * 전체 Agent 실행 timeout입니다.
     */
    private final Duration timeout;

    /**
     * EPUB 생성 Tool 활성화 여부입니다.
     */
    private final boolean generateEpubToolEnabled;

    /**
     * EPUB 검증 Tool 활성화 여부입니다.
     */
    private final boolean validateEpubToolEnabled;

    /**
     * EPUB inspection Tool 활성화 여부입니다.
     */
    private final boolean inspectEpubToolEnabled;

    /**
     * Tool 호출 허용 여부입니다.
     */
    private final boolean toolCallingEnabled;

    /**
     * Tool 실패 후 Agent가 계속 진행할 수 있는지 여부입니다.
     */
    private final boolean continueOnToolFailure;

    /**
     * Tool 결과를 최종 응답에 요약하도록 지시할지 여부입니다.
     */
    private final boolean summarizeToolResults;

    /**
     * 검증 오류를 숨기지 않고 응답에 노출하도록 하는 정책입니다.
     */
    private final boolean exposeValidationIssues;

    /**
     * Agent 응답에 기술적 상세 정보를 포함할지 여부입니다.
     */
    private final boolean technicalDetailsEnabled;

    /**
     * 프로젝트별 추가 지침입니다.
     */
    private final String additionalInstructions;

    private EpubAgentConfiguration(
            Builder builder
    ) {

        this.agentName =
                requireText(
                        builder.agentName,
                        "EPUB agent name"
                );

        this.model =
                requireText(
                        builder.model,
                        "EPUB agent model"
                );

        this.temperature =
                validateTemperature(
                        builder.temperature
                );

        this.maxSteps =
                requirePositive(
                        builder.maxSteps,
                        "EPUB agent maxSteps"
                );

        this.maxToolCalls =
                requirePositive(
                        builder.maxToolCalls,
                        "EPUB agent maxToolCalls"
                );

        this.timeout =
                validateTimeout(
                        builder.timeout
                );

        this.generateEpubToolEnabled =
                builder.generateEpubToolEnabled;

        this.validateEpubToolEnabled =
                builder.validateEpubToolEnabled;

        this.inspectEpubToolEnabled =
                builder.inspectEpubToolEnabled;

        this.toolCallingEnabled =
                builder.toolCallingEnabled;

        this.continueOnToolFailure =
                builder.continueOnToolFailure;

        this.summarizeToolResults =
                builder.summarizeToolResults;

        this.exposeValidationIssues =
                builder.exposeValidationIssues;

        this.technicalDetailsEnabled =
                builder.technicalDetailsEnabled;

        this.additionalInstructions =
                normalizeOptionalText(
                        builder.additionalInstructions
                );

        /*
         * System Prompt를 직접 지정하지 않은 경우
         * Tool 활성화 상태와 추가 지침을 반영해서 생성합니다.
         */
        if (builder.systemPrompt == null
                || builder.systemPrompt.isBlank()) {

            this.systemPrompt =
                    EpubSystemPrompt.build(
                            toolCallingEnabled
                                    && generateEpubToolEnabled,
                            toolCallingEnabled
                                    && validateEpubToolEnabled,
                            toolCallingEnabled
                                    && inspectEpubToolEnabled,
                            this.additionalInstructions
                    );

        } else {

            this.systemPrompt =
                    EpubSystemPrompt.requireValid(
                            builder.systemPrompt
                    );
        }

        validateConfiguration();
    }

    /**
     * 기본 Builder를 생성합니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 EPUB Agent 설정을 생성합니다.
     */
    public static EpubAgentConfiguration defaultConfiguration() {
        return builder().build();
    }

    /**
     * Tool 호출을 비활성화한 read-only LLM 설정을 생성합니다.
     */
    public static EpubAgentConfiguration noTools() {
        return builder()
                .toolCallingEnabled(false)
                .generateEpubToolEnabled(false)
                .validateEpubToolEnabled(false)
                .inspectEpubToolEnabled(false)
                .build();
    }

    /**
     * inspection/validation만 허용하고 EPUB 생성은 금지하는
     * read-only 설정을 생성합니다.
     */
    public static EpubAgentConfiguration readOnly() {
        return builder()
                .generateEpubToolEnabled(false)
                .validateEpubToolEnabled(true)
                .inspectEpubToolEnabled(true)
                .build();
    }

    public String getAgentName() {
        return agentName;
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public long getTimeoutMillis() {
        return timeout.toMillis();
    }

    public boolean isGenerateEpubToolEnabled() {
        return generateEpubToolEnabled;
    }

    public boolean isValidateEpubToolEnabled() {
        return validateEpubToolEnabled;
    }

    public boolean isInspectEpubToolEnabled() {
        return inspectEpubToolEnabled;
    }

    public boolean isToolCallingEnabled() {
        return toolCallingEnabled;
    }

    public boolean isContinueOnToolFailure() {
        return continueOnToolFailure;
    }

    public boolean isSummarizeToolResults() {
        return summarizeToolResults;
    }

    public boolean isExposeValidationIssues() {
        return exposeValidationIssues;
    }

    public boolean isTechnicalDetailsEnabled() {
        return technicalDetailsEnabled;
    }

    public Optional<String> getAdditionalInstructions() {
        return Optional.ofNullable(
                additionalInstructions
        );
    }

    /**
     * 하나 이상의 EPUB Tool이 활성화되어 있는지 확인합니다.
     */
    public boolean hasEnabledTools() {
        return toolCallingEnabled
                && (
                        generateEpubToolEnabled
                        || validateEpubToolEnabled
                        || inspectEpubToolEnabled
                );
    }

    /**
     * EPUB을 실제 생성할 수 있는 Agent 설정인지 확인합니다.
     */
    public boolean canGenerateEpub() {
        return toolCallingEnabled
                && generateEpubToolEnabled;
    }

    /**
     * EPUB 검증이 가능한 설정인지 확인합니다.
     */
    public boolean canValidateEpub() {
        return toolCallingEnabled
                && validateEpubToolEnabled;
    }

    /**
     * EPUB inspection이 가능한 설정인지 확인합니다.
     */
    public boolean canInspectEpub() {
        return toolCallingEnabled
                && inspectEpubToolEnabled;
    }

    /**
     * 현재 설정을 기반으로 Builder를 생성합니다.
     */
    public Builder toBuilder() {
        return new Builder()
                .agentName(agentName)
                .model(model)
                .systemPrompt(systemPrompt)
                .temperature(temperature)
                .maxSteps(maxSteps)
                .maxToolCalls(maxToolCalls)
                .timeout(timeout)
                .generateEpubToolEnabled(
                        generateEpubToolEnabled
                )
                .validateEpubToolEnabled(
                        validateEpubToolEnabled
                )
                .inspectEpubToolEnabled(
                        inspectEpubToolEnabled
                )
                .toolCallingEnabled(
                        toolCallingEnabled
                )
                .continueOnToolFailure(
                        continueOnToolFailure
                )
                .summarizeToolResults(
                        summarizeToolResults
                )
                .exposeValidationIssues(
                        exposeValidationIssues
                )
                .technicalDetailsEnabled(
                        technicalDetailsEnabled
                )
                .additionalInstructions(
                        additionalInstructions
                );
    }

    private void validateConfiguration() {

        if (!toolCallingEnabled
                && (
                        generateEpubToolEnabled
                        || validateEpubToolEnabled
                        || inspectEpubToolEnabled
                )) {

            /*
             * Tool 자체 활성화 플래그는 유지할 수 있지만,
             * 실제 호출은 toolCallingEnabled가 우선합니다.
             */
        }

        if (maxToolCalls < maxSteps / 2) {

            /*
             * 잘못된 설정은 아니므로 강제 오류로 만들지 않습니다.
             * 복잡한 EPUB 생성 흐름에서는 Tool 횟수가 부족할 수 있습니다.
             */
        }
    }

    private static String requireText(
            String value,
            String fieldName
    ) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank."
            );
        }

        return value.trim();
    }

    private static int requirePositive(
            int value,
            String fieldName
    ) {

        if (value <= 0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must be greater than zero: "
                            + value
            );
        }

        return value;
    }

    private static double validateTemperature(
            double value
    ) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            throw new IllegalArgumentException(
                    "EPUB agent temperature "
                            + "must be a finite value."
            );
        }

        if (value < 0.0d
                || value > 2.0d) {

            throw new IllegalArgumentException(
                    "EPUB agent temperature must be "
                            + "between 0.0 and 2.0: "
                            + value
            );
        }

        return value;
    }

    private static Duration validateTimeout(
            Duration value
    ) {

        Objects.requireNonNull(
                value,
                "EPUB agent timeout must not be null."
        );

        if (value.isZero()
                || value.isNegative()) {

            throw new IllegalArgumentException(
                    "EPUB agent timeout must be positive."
            );
        }

        return value;
    }

    private static String normalizeOptionalText(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "EpubAgentConfiguration{"
                + "agentName='" + agentName + '\''
                + ", model='" + model + '\''
                + ", temperature=" + temperature
                + ", maxSteps=" + maxSteps
                + ", maxToolCalls=" + maxToolCalls
                + ", timeout=" + timeout
                + ", toolCallingEnabled="
                + toolCallingEnabled
                + ", generateEpubToolEnabled="
                + generateEpubToolEnabled
                + ", validateEpubToolEnabled="
                + validateEpubToolEnabled
                + ", inspectEpubToolEnabled="
                + inspectEpubToolEnabled
                + '}';
    }

    /**
     * {@link EpubAgentConfiguration} Builder입니다.
     */
    public static final class Builder {

        private String agentName =
                DEFAULT_AGENT_NAME;

        private String model =
                DEFAULT_MODEL;

        private String systemPrompt;

        private double temperature =
                DEFAULT_TEMPERATURE;

        private int maxSteps =
                DEFAULT_MAX_STEPS;

        private int maxToolCalls =
                DEFAULT_MAX_TOOL_CALLS;

        private Duration timeout =
                DEFAULT_TIMEOUT;

        private boolean generateEpubToolEnabled =
                true;

        private boolean validateEpubToolEnabled =
                true;

        private boolean inspectEpubToolEnabled =
                true;

        private boolean toolCallingEnabled =
                true;

        private boolean continueOnToolFailure =
                false;

        private boolean summarizeToolResults =
                true;

        private boolean exposeValidationIssues =
                true;

        private boolean technicalDetailsEnabled =
                true;

        private String additionalInstructions;

        private Builder() {
        }

        public Builder agentName(
                String agentName
        ) {
            this.agentName = agentName;
            return this;
        }

        public Builder model(
                String model
        ) {
            this.model = model;
            return this;
        }

        /**
         * System Prompt를 직접 지정합니다.
         *
         * <p>직접 지정하면 EpubSystemPrompt 자동 생성은 사용하지 않습니다.</p>
         */
        public Builder systemPrompt(
                String systemPrompt
        ) {
            this.systemPrompt =
                    systemPrompt;
            return this;
        }

        public Builder temperature(
                double temperature
        ) {
            this.temperature =
                    temperature;
            return this;
        }

        public Builder maxSteps(
                int maxSteps
        ) {
            this.maxSteps =
                    maxSteps;
            return this;
        }

        public Builder maxToolCalls(
                int maxToolCalls
        ) {
            this.maxToolCalls =
                    maxToolCalls;
            return this;
        }

        public Builder timeout(
                Duration timeout
        ) {
            this.timeout =
                    timeout;
            return this;
        }

        public Builder timeoutMillis(
                long timeoutMillis
        ) {

            if (timeoutMillis <= 0L) {
                throw new IllegalArgumentException(
                        "EPUB agent timeoutMillis "
                                + "must be greater than zero."
                );
            }

            this.timeout =
                    Duration.ofMillis(
                            timeoutMillis
                    );

            return this;
        }

        public Builder generateEpubToolEnabled(
                boolean value
        ) {
            this.generateEpubToolEnabled =
                    value;
            return this;
        }

        public Builder validateEpubToolEnabled(
                boolean value
        ) {
            this.validateEpubToolEnabled =
                    value;
            return this;
        }

        public Builder inspectEpubToolEnabled(
                boolean value
        ) {
            this.inspectEpubToolEnabled =
                    value;
            return this;
        }

        public Builder toolCallingEnabled(
                boolean value
        ) {
            this.toolCallingEnabled =
                    value;
            return this;
        }

        public Builder continueOnToolFailure(
                boolean value
        ) {
            this.continueOnToolFailure =
                    value;
            return this;
        }

        public Builder summarizeToolResults(
                boolean value
        ) {
            this.summarizeToolResults =
                    value;
            return this;
        }

        public Builder exposeValidationIssues(
                boolean value
        ) {
            this.exposeValidationIssues =
                    value;
            return this;
        }

        public Builder technicalDetailsEnabled(
                boolean value
        ) {
            this.technicalDetailsEnabled =
                    value;
            return this;
        }

        public Builder additionalInstructions(
                String value
        ) {
            this.additionalInstructions =
                    value;
            return this;
        }

        /**
         * EPUB 생성 Tool만 활성화합니다.
         */
        public Builder generateOnly() {
            this.generateEpubToolEnabled =
                    true;

            this.validateEpubToolEnabled =
                    false;

            this.inspectEpubToolEnabled =
                    false;

            this.toolCallingEnabled =
                    true;

            return this;
        }

        /**
         * 검증/inspection만 활성화합니다.
         */
        public Builder readOnlyTools() {
            this.generateEpubToolEnabled =
                    false;

            this.validateEpubToolEnabled =
                    true;

            this.inspectEpubToolEnabled =
                    true;

            this.toolCallingEnabled =
                    true;

            return this;
        }

        /**
         * 모든 EPUB Tool을 활성화합니다.
         */
        public Builder allTools() {
            this.generateEpubToolEnabled =
                    true;

            this.validateEpubToolEnabled =
                    true;

            this.inspectEpubToolEnabled =
                    true;

            this.toolCallingEnabled =
                    true;

            return this;
        }

        public EpubAgentConfiguration build() {
            return new EpubAgentConfiguration(
                    this
            );
        }
    }
}