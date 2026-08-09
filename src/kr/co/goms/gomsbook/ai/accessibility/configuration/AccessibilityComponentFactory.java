/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.configuration;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.agent.AccessibilityAgent;
import kr.co.goms.gomsbook.ai.accessibility.agent.AccessibilityAgentConfiguration;
import kr.co.goms.gomsbook.ai.accessibility.agent.DefaultAccessibilityAgent;
import kr.co.goms.gomsbook.ai.accessibility.agent.DefaultAccessibilityAgent.AgentExecutionBridge;
import kr.co.goms.gomsbook.ai.accessibility.analysis.ImageAnalyzer;
import kr.co.goms.gomsbook.ai.accessibility.analysis.VisionImageAnalyzer;
import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicator;
import kr.co.goms.gomsbook.ai.accessibility.application.DefaultAltTextApplicator;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.DefaultAccessibilityValidator;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.AriaAccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.DocumentLanguageAccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.HeadingAccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.ImageAltAccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.LinkAccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.rule.TableAccessibilityRule;
import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tools.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tools.image.AnalyzeImageTool;
import kr.co.goms.gomsbook.ai.tools.image.ApplyAltTextTool;

/**
 * 접근성 계층의 구성요소를 생성하고 조립하는 Factory.
 *
 * <p>다음 구성요소를 생성한다.</p>
 *
 * <ul>
 *   <li>접근성 검사 규칙</li>
 *   <li>{@link AccessibilityValidator}</li>
 *   <li>{@link ImageAnalyzer}</li>
 *   <li>{@link AltTextApplicator}</li>
 *   <li>접근성 Agent Tool</li>
 *   <li>{@link AccessibilityAgentConfiguration}</li>
 *   <li>{@link AccessibilityAgent}</li>
 * </ul>
 *
 * <p>Factory가 생성한 구성요소는
 * {@link AccessibilityComponents}에 묶어서 반환된다.</p>
 */
public final class AccessibilityComponentFactory {

    /**
     * 접근성 Agent 기본 추론 모델.
     */
    public static final String DEFAULT_AGENT_MODEL =
            "gemma4:31b-cloud";

    /**
     * 이미지 분석 기본 Vision 모델.
     *
     * <p>실제 Ollama 환경에 설치한 Vision 모델명에 맞게
     * Builder에서 변경할 수 있다.</p>
     */
    public static final String DEFAULT_VISION_MODEL =
            "gemma3:12b";

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;
    private final AgentExecutionBridge executionBridge;

    private final String agentModel;
    private final String visionModel;

    private final List<AccessibilityRule> additionalRules;

    private final ImageAnalyzer customImageAnalyzer;
    private final AltTextApplicator customAltTextApplicator;
    private final AccessibilityValidator customValidator;

    private final boolean namespaceAware;
    private final boolean preserveDoctype;

    private final boolean automaticModificationAllowed;
    private final boolean overwriteExistingAltAllowed;
    private final boolean backupRequired;
    private final boolean validationBeforeModificationRequired;
    private final boolean validationAfterModificationRequired;
    private final boolean preferDryRun;
    private final boolean includeInformationalIssues;

    private final double minimumAnalysisConfidence;

    private AccessibilityComponentFactory(
            Builder builder) {

        this.llmClient = builder.llmClient;
        this.jsonMapper = builder.jsonMapper;

        this.executionBridge = Objects.requireNonNull(
                builder.executionBridge,
                "executionBridge must not be null"
        );

        this.agentModel = normalizeRequiredText(
                builder.agentModel,
                "agentModel"
        );

        this.visionModel = normalizeRequiredText(
                builder.visionModel,
                "visionModel"
        );

        this.additionalRules =
                immutableRules(
                        builder.additionalRules
                );

        this.customImageAnalyzer =
                builder.customImageAnalyzer;

        this.customAltTextApplicator =
                builder.customAltTextApplicator;

        this.customValidator =
                builder.customValidator;

        this.namespaceAware =
                builder.namespaceAware;

        this.preserveDoctype =
                builder.preserveDoctype;

        this.automaticModificationAllowed =
                builder.automaticModificationAllowed;

        this.overwriteExistingAltAllowed =
                builder.overwriteExistingAltAllowed;

        this.backupRequired =
                builder.backupRequired;

        this.validationBeforeModificationRequired =
                builder.validationBeforeModificationRequired;

        this.validationAfterModificationRequired =
                builder.validationAfterModificationRequired;

        this.preferDryRun =
                builder.preferDryRun;

        this.includeInformationalIssues =
                builder.includeInformationalIssues;

        this.minimumAnalysisConfidence =
                validateConfidence(
                        builder.minimumAnalysisConfidence
                );

        validateDependencies();
    }

    /**
     * 접근성 계층의 모든 구성요소를 생성한다.
     *
     * @return 접근성 구성요소 묶음
     */
    public AccessibilityComponents create() {

        List<AccessibilityRule> rules =
                createAccessibilityRules();

        AccessibilityValidator validator =
                createAccessibilityValidator(rules);

        ImageAnalyzer imageAnalyzer = createImageAnalyzer();

        AltTextApplicator altTextApplicator = createAltTextApplicator();

        ValidateAccessibilityTool validateTool =
                new ValidateAccessibilityTool(
                        validator
                );

        AnalyzeImageTool analyzeImageTool =
                new AnalyzeImageTool(
                        imageAnalyzer
                );

        ApplyAltTextTool applyAltTextTool =
                new ApplyAltTextTool(
                        altTextApplicator
                );

        AccessibilityAgentConfiguration agentConfiguration =
                createAgentConfiguration(
                        validateTool,
                        analyzeImageTool,
                        applyAltTextTool
                );

        AccessibilityAgent accessibilityAgent =
                new DefaultAccessibilityAgent(
                        agentConfiguration,
                        executionBridge
                );

        return new AccessibilityComponents(
                rules,
                validator,
                imageAnalyzer,
                altTextApplicator,
                validateTool,
                analyzeImageTool,
                applyAltTextTool,
                agentConfiguration,
                accessibilityAgent
        );
    }

    /**
     * 기본 접근성 검사 규칙을 생성한다.
     *
     * @return 실행 순서가 적용된 규칙 목록
     */
    public List<AccessibilityRule>
            createAccessibilityRules() {

        List<AccessibilityRule> rules =
                new ArrayList<>();

        rules.add(
                new DocumentLanguageAccessibilityRule()
        );

        rules.add(
                new ImageAltAccessibilityRule()
        );

        rules.add(
                new HeadingAccessibilityRule()
        );

        rules.add(
                new LinkAccessibilityRule()
        );

        rules.add(
                new TableAccessibilityRule()
        );

        rules.add(
                new AriaAccessibilityRule()
        );

        rules.addAll(additionalRules);

        return Collections.unmodifiableList(rules);
    }

    /**
     * 접근성 Validator를 생성한다.
     *
     * @param rules 등록할 규칙
     * @return 접근성 Validator
     */
    public AccessibilityValidator
            createAccessibilityValidator(
                    List<? extends AccessibilityRule> rules) {

        if (customValidator != null) {
            return customValidator;
        }

        return new DefaultAccessibilityValidator(
                rules,
                namespaceAware
        );
    }

    /**
     * 이미지 분석 서비스를 생성한다.
     *
     * @return 이미지 분석 서비스
     */
    public ImageAnalyzer createImageAnalyzer() {

        if (customImageAnalyzer != null) {
            return customImageAnalyzer;
        }

        return new VisionImageAnalyzer(
                llmClient,
                jsonMapper,
                visionModel
        );
    }

    /**
     * 대체 텍스트 적용 서비스를 생성한다.
     *
     * @return 대체 텍스트 적용 서비스
     */
    public AltTextApplicator createAltTextApplicator() {

        if (customAltTextApplicator != null) {
            return customAltTextApplicator;
        }

        return new DefaultAltTextApplicator(
                namespaceAware,
                preserveDoctype
        );
    }

    /**
     * 접근성 Agent 구성을 생성한다.
     *
     * @param validateTool 접근성 검사 Tool
     * @param analyzeImageTool 이미지 분석 Tool
     * @param applyAltTextTool 대체 텍스트 적용 Tool
     * @return 접근성 Agent 구성
     */
    public AccessibilityAgentConfiguration
            createAgentConfiguration(
                    ValidateAccessibilityTool validateTool,
                    AnalyzeImageTool analyzeImageTool,
                    ApplyAltTextTool applyAltTextTool) {

        return AccessibilityAgentConfiguration
                .builder(
                        agentModel,
                        validateTool,
                        analyzeImageTool,
                        applyAltTextTool
                )
                .minimumAnalysisConfidence(
                        minimumAnalysisConfidence
                )
                .requireValidationBeforeModification(
                        validationBeforeModificationRequired
                )
                .requireValidationAfterModification(
                        validationAfterModificationRequired
                )
                .requireUniqueImageTarget(true)
                .requireProjectLocalFiles(true)
                .requireBackupBeforeModification(
                        backupRequired
                )
                .allowOverwriteExistingAlt(
                        overwriteExistingAltAllowed
                )
                .allowAutomaticModification(
                        automaticModificationAllowed
                )
                .preferDryRun(preferDryRun)
                .stopOnToolFailure(true)
                .includeInformationalIssues(
                        includeInformationalIssues
                )
                .build();
    }

    private void validateDependencies() {

        if (customImageAnalyzer == null) {
            Objects.requireNonNull(
                    llmClient,
                    "llmClient is required when "
                            + "customImageAnalyzer is not provided"
            );

            Objects.requireNonNull(
                    jsonMapper,
                    "jsonMapper is required when "
                            + "customImageAnalyzer is not provided"
            );
        }

        if (automaticModificationAllowed
                && !backupRequired) {

            /*
             * 백업 없이 자동 수정하는 정책도 기술적으로 가능하지만,
             * 기본 Factory에서는 안전성을 위해 명시적으로 막는다.
             */
            throw new IllegalArgumentException(
                    "Automatic modification requires backupRequired=true"
            );
        }

        if (overwriteExistingAltAllowed
                && !automaticModificationAllowed) {

            throw new IllegalArgumentException(
                    "overwriteExistingAltAllowed requires "
                            + "automaticModificationAllowed=true"
            );
        }
    }

    /**
     * 기본 Factory Builder를 생성한다.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 필수 의존성으로 Factory Builder를 생성한다.
     *
     * @param llmClient LLM Client
     * @param jsonMapper JSON Mapper
     * @param executionBridge Agent Framework Bridge
     * @return 초기화된 Builder
     */
    public static Builder builder(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            AgentExecutionBridge executionBridge) {

        return builder()
                .llmClient(llmClient)
                .jsonMapper(jsonMapper)
                .executionBridge(executionBridge);
    }

    /**
     * 기본 접근성 구성요소를 생성한다.
     *
     * @param llmClient LLM Client
     * @param jsonMapper JSON Mapper
     * @param executionBridge Agent Framework Bridge
     * @return 접근성 구성요소 묶음
     */
    public static AccessibilityComponents defaults(
            LlmClient llmClient,
            JsonMapper jsonMapper,
            AgentExecutionBridge executionBridge) {

        return builder(
                llmClient,
                jsonMapper,
                executionBridge
        ).build().create();
    }

    private static List<AccessibilityRule>
            immutableRules(
                    List<AccessibilityRule> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<AccessibilityRule> result =
                new ArrayList<>();

        for (AccessibilityRule rule : source) {
            if (rule != null) {
                result.add(rule);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(result);
    }

    private static String normalizeRequiredText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    private static double validateConfidence(
            double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)
                || value < 0.0d
                || value > 1.0d) {

            throw new IllegalArgumentException(
                    "minimumAnalysisConfidence must be "
                            + "between 0.0 and 1.0"
            );
        }

        return value;
    }

    /**
     * {@link AccessibilityComponentFactory} Builder.
     */
    public static final class Builder {

        private LlmClient llmClient;
        private JsonMapper jsonMapper;
        private AgentExecutionBridge executionBridge;

        private String agentModel =
                DEFAULT_AGENT_MODEL;

        private String visionModel =
                DEFAULT_VISION_MODEL;

        private final List<AccessibilityRule>
                additionalRules = new ArrayList<>();

        private ImageAnalyzer customImageAnalyzer;
        private AltTextApplicator customAltTextApplicator;
        private AccessibilityValidator customValidator;

        private boolean namespaceAware = true;
        private boolean preserveDoctype = true;

        private boolean automaticModificationAllowed = true;
        private boolean overwriteExistingAltAllowed;
        private boolean backupRequired = true;

        private boolean validationBeforeModificationRequired =
                true;

        private boolean validationAfterModificationRequired =
                true;

        private boolean preferDryRun;
        private boolean includeInformationalIssues = true;

        private double minimumAnalysisConfidence =
                AccessibilityAgentConfiguration
                        .DEFAULT_MINIMUM_ANALYSIS_CONFIDENCE;

        private Builder() {
        }

        public Builder llmClient(
                LlmClient llmClient) {

            this.llmClient = llmClient;
            return this;
        }

        public Builder jsonMapper(
                JsonMapper jsonMapper) {

            this.jsonMapper = jsonMapper;
            return this;
        }

        public Builder executionBridge(
                AgentExecutionBridge executionBridge) {

            this.executionBridge = executionBridge;
            return this;
        }

        public Builder agentModel(
                String agentModel) {

            this.agentModel = agentModel;
            return this;
        }

        public Builder visionModel(
                String visionModel) {

            this.visionModel = visionModel;
            return this;
        }

        /**
         * 기본 규칙 외의 추가 규칙을 등록한다.
         *
         * @param rule 추가 접근성 규칙
         * @return 현재 Builder
         */
        public Builder additionalRule(
                AccessibilityRule rule) {

            if (rule != null) {
                additionalRules.add(rule);
            }

            return this;
        }

        public Builder additionalRules(
                List<? extends AccessibilityRule> rules) {

            if (rules == null) {
                return this;
            }

            for (AccessibilityRule rule : rules) {
                additionalRule(rule);
            }

            return this;
        }

        /**
         * 기본 {@link VisionImageAnalyzer} 대신 사용할
         * 사용자 정의 ImageAnalyzer를 설정한다.
         *
         * @param imageAnalyzer 사용자 정의 이미지 분석기
         * @return 현재 Builder
         */
        public Builder imageAnalyzer(
                ImageAnalyzer imageAnalyzer) {

            this.customImageAnalyzer =
                    imageAnalyzer;

            return this;
        }

        /**
         * 기본 {@link DefaultAltTextApplicator} 대신 사용할
         * 사용자 정의 Applicator를 설정한다.
         *
         * @param altTextApplicator 사용자 정의 Applicator
         * @return 현재 Builder
         */
        public Builder altTextApplicator(
                AltTextApplicator altTextApplicator) {

            this.customAltTextApplicator =
                    altTextApplicator;

            return this;
        }

        /**
         * 기본 {@link DefaultAccessibilityValidator} 대신 사용할
         * 사용자 정의 Validator를 설정한다.
         *
         * @param validator 사용자 정의 Validator
         * @return 현재 Builder
         */
        public Builder validator(
                AccessibilityValidator validator) {

            this.customValidator = validator;
            return this;
        }

        public Builder namespaceAware(
                boolean namespaceAware) {

            this.namespaceAware = namespaceAware;
            return this;
        }

        public Builder preserveDoctype(
                boolean preserveDoctype) {

            this.preserveDoctype = preserveDoctype;
            return this;
        }

        public Builder automaticModificationAllowed(
                boolean value) {

            this.automaticModificationAllowed =
                    value;

            return this;
        }

        public Builder overwriteExistingAltAllowed(
                boolean value) {

            this.overwriteExistingAltAllowed =
                    value;

            return this;
        }

        public Builder backupRequired(
                boolean value) {

            this.backupRequired = value;
            return this;
        }

        public Builder validationBeforeModificationRequired(
                boolean value) {

            this.validationBeforeModificationRequired =
                    value;

            return this;
        }

        public Builder validationAfterModificationRequired(
                boolean value) {

            this.validationAfterModificationRequired =
                    value;

            return this;
        }

        public Builder preferDryRun(
                boolean value) {

            this.preferDryRun = value;
            return this;
        }

        public Builder includeInformationalIssues(
                boolean value) {

            this.includeInformationalIssues = value;
            return this;
        }

        public Builder minimumAnalysisConfidence(
                double value) {

            this.minimumAnalysisConfidence = value;
            return this;
        }

        public AccessibilityComponentFactory build() {
            return new AccessibilityComponentFactory(this);
        }
    }

    /**
     * 접근성 계층에서 생성된 구성요소 묶음.
     */
    public static final class AccessibilityComponents {

        private final List<AccessibilityRule> rules;
        private final AccessibilityValidator validator;
        private final ImageAnalyzer imageAnalyzer;
        private final AltTextApplicator altTextApplicator;

        private final ValidateAccessibilityTool
                validateAccessibilityTool;

        private final AnalyzeImageTool analyzeImageTool;
        private final ApplyAltTextTool applyAltTextTool;

        private final AccessibilityAgentConfiguration
                agentConfiguration;

        private final AccessibilityAgent accessibilityAgent;

        private AccessibilityComponents(
                List<AccessibilityRule> rules,
                AccessibilityValidator validator,
                ImageAnalyzer imageAnalyzer,
                AltTextApplicator altTextApplicator,
                ValidateAccessibilityTool validateAccessibilityTool,
                AnalyzeImageTool analyzeImageTool,
                ApplyAltTextTool applyAltTextTool,
                AccessibilityAgentConfiguration agentConfiguration,
                AccessibilityAgent accessibilityAgent) {

            this.rules = List.copyOf(
                    Objects.requireNonNull(
                            rules,
                            "rules must not be null"
                    )
            );

            this.validator = Objects.requireNonNull(
                    validator,
                    "validator must not be null"
            );

            this.imageAnalyzer = Objects.requireNonNull(
                    imageAnalyzer,
                    "imageAnalyzer must not be null"
            );

            this.altTextApplicator = Objects.requireNonNull(
                    altTextApplicator,
                    "altTextApplicator must not be null"
            );

            this.validateAccessibilityTool =
                    Objects.requireNonNull(
                            validateAccessibilityTool,
                            "validateAccessibilityTool must not be null"
                    );

            this.analyzeImageTool =
                    Objects.requireNonNull(
                            analyzeImageTool,
                            "analyzeImageTool must not be null"
                    );

            this.applyAltTextTool =
                    Objects.requireNonNull(
                            applyAltTextTool,
                            "applyAltTextTool must not be null"
                    );

            this.agentConfiguration =
                    Objects.requireNonNull(
                            agentConfiguration,
                            "agentConfiguration must not be null"
                    );

            this.accessibilityAgent =
                    Objects.requireNonNull(
                            accessibilityAgent,
                            "accessibilityAgent must not be null"
                    );
        }

        public List<AccessibilityRule> getRules() {
            return rules;
        }

        public AccessibilityValidator getValidator() {
            return validator;
        }

        public ImageAnalyzer getImageAnalyzer() {
            return imageAnalyzer;
        }

        public AltTextApplicator getAltTextApplicator() {
            return altTextApplicator;
        }

        public ValidateAccessibilityTool
                getValidateAccessibilityTool() {

            return validateAccessibilityTool;
        }

        public AnalyzeImageTool getAnalyzeImageTool() {
            return analyzeImageTool;
        }

        public ApplyAltTextTool getApplyAltTextTool() {
            return applyAltTextTool;
        }

        public AccessibilityAgentConfiguration
                getAgentConfiguration() {

            return agentConfiguration;
        }

        public AccessibilityAgent getAccessibilityAgent() {
            return accessibilityAgent;
        }

        /**
         * 접근성 Agent에 등록할 Tool 목록을 반환한다.
         *
         * @return 접근성 Tool 목록
         */
        public List<AgentTool> getAgentTools() {

            return List.of(
                    validateAccessibilityTool,
                    analyzeImageTool,
                    applyAltTextTool
            );
        }
    }
}