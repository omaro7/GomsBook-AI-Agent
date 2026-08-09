/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tools.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tools.image.AnalyzeImageTool;
import kr.co.goms.gomsbook.ai.tools.image.ApplyAltTextTool;

/**
 * 접근성 전용 Agent의 실행 구성을 정의한다.
 *
 * <p>사용 모델, 시스템 프롬프트, 허용 Tool, 최대 실행 단계,
 * 이미지 분석 신뢰도와 파일 수정 정책을 포함하는 불변 객체이다.</p>
 *
 * <p>접근성 Agent는 이 구성에 등록된 Tool만 호출할 수 있으며,
 * 기본적으로 검사 후 수정하고 수정 후 다시 검사하는 흐름을 따른다.</p>
 */
public final class AccessibilityAgentConfiguration {

    public static final String DEFAULT_AGENT_NAME =
            "accessibility-agent";

    public static final String DEFAULT_AGENT_DESCRIPTION =
            "EPUB 3 accessibility validation and remediation agent";

    public static final int DEFAULT_MAX_ITERATIONS = 12;

    public static final int DEFAULT_MAX_TOOL_CALLS = 20;

    public static final double DEFAULT_MINIMUM_ANALYSIS_CONFIDENCE =
            0.80d;

    public static final Duration DEFAULT_EXECUTION_TIMEOUT =
            Duration.ofMinutes(5);

    private final String agentName;
    private final String description;
    private final String model;
    private final String systemPrompt;

    private final List<AgentTool> tools;
    private final Set<String> allowedToolNames;

    private final int maxIterations;
    private final int maxToolCalls;
    private final Duration executionTimeout;

    private final double minimumAnalysisConfidence;

    private final boolean requireValidationBeforeModification;
    private final boolean requireValidationAfterModification;
    private final boolean requireUniqueImageTarget;
    private final boolean requireProjectLocalFiles;
    private final boolean requireBackupBeforeModification;
    private final boolean allowOverwriteExistingAlt;
    private final boolean allowAutomaticModification;
    private final boolean preferDryRun;
    private final boolean stopOnToolFailure;
    private final boolean includeInformationalIssues;

    private AccessibilityAgentConfiguration(
            Builder builder) {

        this.agentName = normalizeRequiredText(
                builder.agentName,
                "agentName"
        );

        this.description = normalizeRequiredText(
                builder.description,
                "description"
        );

        this.model = normalizeRequiredText(
                builder.model,
                "model"
        );

        this.systemPrompt = normalizeRequiredText(
                builder.systemPrompt,
                "systemPrompt"
        );

        this.tools = immutableTools(
                builder.tools
        );

        this.allowedToolNames = createAllowedToolNames(
                tools
        );

        this.maxIterations = validatePositiveInteger(
                builder.maxIterations,
                "maxIterations"
        );

        this.maxToolCalls = validatePositiveInteger(
                builder.maxToolCalls,
                "maxToolCalls"
        );

        this.executionTimeout = validateDuration(
                builder.executionTimeout
        );

        this.minimumAnalysisConfidence =
                validateConfidence(
                        builder.minimumAnalysisConfidence
                );

        this.requireValidationBeforeModification =
                builder.requireValidationBeforeModification;

        this.requireValidationAfterModification =
                builder.requireValidationAfterModification;

        this.requireUniqueImageTarget =
                builder.requireUniqueImageTarget;

        this.requireProjectLocalFiles =
                builder.requireProjectLocalFiles;

        this.requireBackupBeforeModification =
                builder.requireBackupBeforeModification;

        this.allowOverwriteExistingAlt =
                builder.allowOverwriteExistingAlt;

        this.allowAutomaticModification =
                builder.allowAutomaticModification;

        this.preferDryRun =
                builder.preferDryRun;

        this.stopOnToolFailure =
                builder.stopOnToolFailure;

        this.includeInformationalIssues =
                builder.includeInformationalIssues;

        validateRequiredTools();
        validatePolicy();
    }

    /**
     * 접근성 Agent 이름을 반환한다.
     *
     * @return Agent 이름
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * 접근성 Agent 설명을 반환한다.
     *
     * @return Agent 설명
     */
    public String getDescription() {
        return description;
    }

    /**
     * 접근성 Agent가 사용하는 LLM 모델명을 반환한다.
     *
     * @return 모델명
     */
    public String getModel() {
        return model;
    }

    /**
     * 접근성 Agent 시스템 프롬프트를 반환한다.
     *
     * @return 시스템 프롬프트
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * 접근성 Agent에 등록된 Tool 목록을 반환한다.
     *
     * @return 수정할 수 없는 Tool 목록
     */
    public List<AgentTool> getTools() {
        return tools;
    }

    /**
     * 접근성 Agent가 호출할 수 있는 Tool 이름을 반환한다.
     *
     * @return 수정할 수 없는 Tool 이름 집합
     */
    public Set<String> getAllowedToolNames() {
        return allowedToolNames;
    }

    /**
     * 최대 Agent 반복 횟수를 반환한다.
     *
     * @return 최대 반복 횟수
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * 한 번의 Agent 실행에서 허용할 최대 Tool 호출 수를 반환한다.
     *
     * @return 최대 Tool 호출 수
     */
    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    /**
     * Agent 전체 실행 제한 시간을 반환한다.
     *
     * @return 실행 제한 시간
     */
    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    /**
     * 이미지 분석 결과 자동 적용에 필요한 최소 신뢰도를 반환한다.
     *
     * @return 0.0 이상 1.0 이하 신뢰도
     */
    public double getMinimumAnalysisConfidence() {
        return minimumAnalysisConfidence;
    }

    /**
     * 파일 수정 전에 접근성 검사가 필요한지 반환한다.
     *
     * @return 사전 검사 필수 여부
     */
    public boolean isRequireValidationBeforeModification() {
        return requireValidationBeforeModification;
    }

    /**
     * 파일 수정 후 접근성 재검사가 필요한지 반환한다.
     *
     * @return 사후 검사 필수 여부
     */
    public boolean isRequireValidationAfterModification() {
        return requireValidationAfterModification;
    }

    /**
     * 이미지 수정 대상이 하나로 확정되어야 하는지 반환한다.
     *
     * @return 단일 대상 필수 여부
     */
    public boolean isRequireUniqueImageTarget() {
        return requireUniqueImageTarget;
    }

    /**
     * 프로젝트 내부 파일만 처리할 수 있는지 반환한다.
     *
     * @return 프로젝트 내부 파일 제한 여부
     */
    public boolean isRequireProjectLocalFiles() {
        return requireProjectLocalFiles;
    }

    /**
     * 수정 전 백업 생성이 필요한지 반환한다.
     *
     * @return 백업 필수 여부
     */
    public boolean isRequireBackupBeforeModification() {
        return requireBackupBeforeModification;
    }

    /**
     * 기존의 비어 있지 않은 alt 값을 자동으로 덮어쓸 수 있는지 반환한다.
     *
     * @return 기존 alt 덮어쓰기 허용 여부
     */
    public boolean isAllowOverwriteExistingAlt() {
        return allowOverwriteExistingAlt;
    }

    /**
     * Agent가 사용자 개입 없이 파일을 수정할 수 있는지 반환한다.
     *
     * @return 자동 수정 허용 여부
     */
    public boolean isAllowAutomaticModification() {
        return allowAutomaticModification;
    }

    /**
     * 실제 수정 전에 dry-run을 우선할지 반환한다.
     *
     * @return dry-run 우선 여부
     */
    public boolean isPreferDryRun() {
        return preferDryRun;
    }

    /**
     * Tool 호출 실패 시 Agent 실행을 중단할지 반환한다.
     *
     * @return Tool 실패 시 중단 여부
     */
    public boolean isStopOnToolFailure() {
        return stopOnToolFailure;
    }

    /**
     * 정보 수준 접근성 문제를 검사 결과에 포함할지 반환한다.
     *
     * @return 정보 문제 포함 여부
     */
    public boolean isIncludeInformationalIssues() {
        return includeInformationalIssues;
    }

    /**
     * 지정한 Tool 호출이 허용되는지 반환한다.
     *
     * @param toolName Tool 이름
     * @return 허용된 Tool이면 {@code true}
     */
    public boolean isToolAllowed(
            String toolName) {

        String normalized =
                normalizeOptionalText(toolName);

        return normalized != null
                && allowedToolNames.contains(normalized);
    }

    /**
     * 지정한 Tool 이름으로 등록된 Tool을 찾는다.
     *
     * @param toolName Tool 이름
     * @return 등록된 Tool, 없으면 {@code null}
     */
    public AgentTool findTool(
            String toolName) {

        String normalized =
                normalizeOptionalText(toolName);

        if (normalized == null) {
            return null;
        }

        for (AgentTool tool : tools) {
            if (normalized.equals(tool.getName())) {
                return tool;
            }
        }

        return null;
    }

    /**
     * 이미지 분석 결과를 자동으로 적용할 수 있는 신뢰도인지 반환한다.
     *
     * @param confidence 이미지 분석 신뢰도
     * @return 자동 적용 가능한 신뢰도이면 {@code true}
     */
    public boolean satisfiesConfidence(
            double confidence) {

        if (Double.isNaN(confidence)
                || Double.isInfinite(confidence)) {

            return false;
        }

        return confidence
                >= minimumAnalysisConfidence;
    }

    /**
     * 현재 구성을 Builder로 복사한다.
     *
     * @return 복사 Builder
     */
    public Builder toBuilder() {

        return builder()
                .agentName(agentName)
                .description(description)
                .model(model)
                .systemPrompt(systemPrompt)
                .tools(tools)
                .maxIterations(maxIterations)
                .maxToolCalls(maxToolCalls)
                .executionTimeout(executionTimeout)
                .minimumAnalysisConfidence(
                        minimumAnalysisConfidence
                )
                .requireValidationBeforeModification(
                        requireValidationBeforeModification
                )
                .requireValidationAfterModification(
                        requireValidationAfterModification
                )
                .requireUniqueImageTarget(
                        requireUniqueImageTarget
                )
                .requireProjectLocalFiles(
                        requireProjectLocalFiles
                )
                .requireBackupBeforeModification(
                        requireBackupBeforeModification
                )
                .allowOverwriteExistingAlt(
                        allowOverwriteExistingAlt
                )
                .allowAutomaticModification(
                        allowAutomaticModification
                )
                .preferDryRun(preferDryRun)
                .stopOnToolFailure(stopOnToolFailure)
                .includeInformationalIssues(
                        includeInformationalIssues
                );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 필수 구성요소로 Builder를 생성한다.
     *
     * @param model Agent가 사용할 LLM 모델
     * @param validateAccessibilityTool 접근성 검사 Tool
     * @param analyzeImageTool 이미지 분석 Tool
     * @param applyAltTextTool 대체 텍스트 적용 Tool
     * @return 초기화된 Builder
     */
    public static Builder builder(
            String model,
            ValidateAccessibilityTool validateAccessibilityTool,
            AnalyzeImageTool analyzeImageTool,
            ApplyAltTextTool applyAltTextTool) {

        return builder()
                .model(model)
                .tool(validateAccessibilityTool)
                .tool(analyzeImageTool)
                .tool(applyAltTextTool);
    }

    /**
     * 기본 접근성 Agent 구성을 생성한다.
     *
     * @param model Agent가 사용할 LLM 모델
     * @param validateAccessibilityTool 접근성 검사 Tool
     * @param analyzeImageTool 이미지 분석 Tool
     * @param applyAltTextTool 대체 텍스트 적용 Tool
     * @return 기본 접근성 Agent 구성
     */
    public static AccessibilityAgentConfiguration defaults(
            String model,
            ValidateAccessibilityTool validateAccessibilityTool,
            AnalyzeImageTool analyzeImageTool,
            ApplyAltTextTool applyAltTextTool) {

        return builder(
                model,
                validateAccessibilityTool,
                analyzeImageTool,
                applyAltTextTool
        ).build();
    }

    private void validateRequiredTools() {

        if (!allowedToolNames.contains(
                ValidateAccessibilityTool.TOOL_NAME)) {

            throw new IllegalArgumentException(
                    "Required accessibility tool is missing: "
                            + ValidateAccessibilityTool.TOOL_NAME
            );
        }

        if (!allowedToolNames.contains(
                AnalyzeImageTool.TOOL_NAME)) {

            throw new IllegalArgumentException(
                    "Required accessibility tool is missing: "
                            + AnalyzeImageTool.TOOL_NAME
            );
        }

        if (!allowedToolNames.contains(
                ApplyAltTextTool.TOOL_NAME)) {

            throw new IllegalArgumentException(
                    "Required accessibility tool is missing: "
                            + ApplyAltTextTool.TOOL_NAME
            );
        }
    }

    private void validatePolicy() {

        if (allowAutomaticModification
                && !requireUniqueImageTarget) {

            throw new IllegalArgumentException(
                    "Automatic modification requires "
                            + "requireUniqueImageTarget=true"
            );
        }

        if (allowAutomaticModification
                && !requireProjectLocalFiles) {

            throw new IllegalArgumentException(
                    "Automatic modification requires "
                            + "requireProjectLocalFiles=true"
            );
        }

        if (requireValidationAfterModification
                && !allowedToolNames.contains(
                        ValidateAccessibilityTool.TOOL_NAME)) {

            throw new IllegalArgumentException(
                    "Post-modification validation requires "
                            + ValidateAccessibilityTool.TOOL_NAME
            );
        }
    }

    private static List<AgentTool> immutableTools(
            List<AgentTool> source) {

        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one accessibility tool is required"
            );
        }

        List<AgentTool> result =
                new ArrayList<>();

        Set<String> toolNames =
                new LinkedHashSet<>();

        for (AgentTool tool : source) {

            if (tool == null) {
                continue;
            }

            String toolName =
                    normalizeRequiredText(
                            tool.getName(),
                            "tool.name"
                    );

            if (!toolNames.add(toolName)) {
                throw new IllegalArgumentException(
                        "Duplicate accessibility tool name: "
                                + toolName
                );
            }

            result.add(tool);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one accessibility tool is required"
            );
        }

        return Collections.unmodifiableList(result);
    }

    private static Set<String> createAllowedToolNames(
            List<AgentTool> tools) {

        Set<String> result =
                new LinkedHashSet<>();

        for (AgentTool tool : tools) {
            result.add(tool.getName());
        }

        return Collections.unmodifiableSet(result);
    }

    private static int validatePositiveInteger(
            int value,
            String fieldName) {

        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }

        return value;
    }

    private static Duration validateDuration(
            Duration value) {

        Objects.requireNonNull(
                value,
                "executionTimeout must not be null"
        );

        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "executionTimeout must be greater than zero"
            );
        }

        return value;
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

    private static String normalizeRequiredText(
            String value,
            String fieldName) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    /**
     * {@link AccessibilityAgentConfiguration} Builder.
     */
    public static final class Builder {

        private String agentName =
                DEFAULT_AGENT_NAME;

        private String description =
                DEFAULT_AGENT_DESCRIPTION;

        private String model;

        private String systemPrompt =
                AccessibilitySystemPrompt.getPrompt();

        private final List<AgentTool> tools =
                new ArrayList<>();

        private int maxIterations =
                DEFAULT_MAX_ITERATIONS;

        private int maxToolCalls =
                DEFAULT_MAX_TOOL_CALLS;

        private Duration executionTimeout =
                DEFAULT_EXECUTION_TIMEOUT;

        private double minimumAnalysisConfidence =
                DEFAULT_MINIMUM_ANALYSIS_CONFIDENCE;

        private boolean requireValidationBeforeModification =
                true;

        private boolean requireValidationAfterModification =
                true;

        private boolean requireUniqueImageTarget =
                true;

        private boolean requireProjectLocalFiles =
                true;

        private boolean requireBackupBeforeModification =
                true;

        private boolean allowOverwriteExistingAlt =
                false;

        private boolean allowAutomaticModification =
                true;

        private boolean preferDryRun =
                false;

        private boolean stopOnToolFailure =
                true;

        private boolean includeInformationalIssues =
                true;

        private Builder() {
        }

        public Builder agentName(
                String agentName) {

            this.agentName = agentName;
            return this;
        }

        public Builder description(
                String description) {

            this.description = description;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(
                String systemPrompt) {

            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder tool(AgentTool tool) {

            if (tool != null) {
                this.tools.add(tool);
            }

            return this;
        }

        public Builder tools(
                List<? extends AgentTool> tools) {

            if (tools == null) {
                return this;
            }

            for (AgentTool tool : tools) {
                tool(tool);
            }

            return this;
        }

        public Builder maxIterations(
                int maxIterations) {

            this.maxIterations = maxIterations;
            return this;
        }

        public Builder maxToolCalls(
                int maxToolCalls) {

            this.maxToolCalls = maxToolCalls;
            return this;
        }

        public Builder executionTimeout(
                Duration executionTimeout) {

            this.executionTimeout =
                    executionTimeout;

            return this;
        }

        public Builder minimumAnalysisConfidence(
                double minimumAnalysisConfidence) {

            this.minimumAnalysisConfidence =
                    minimumAnalysisConfidence;

            return this;
        }

        public Builder requireValidationBeforeModification(
                boolean value) {

            this.requireValidationBeforeModification =
                    value;

            return this;
        }

        public Builder requireValidationAfterModification(
                boolean value) {

            this.requireValidationAfterModification =
                    value;

            return this;
        }

        public Builder requireUniqueImageTarget(
                boolean value) {

            this.requireUniqueImageTarget = value;
            return this;
        }

        public Builder requireProjectLocalFiles(
                boolean value) {

            this.requireProjectLocalFiles = value;
            return this;
        }

        public Builder requireBackupBeforeModification(
                boolean value) {

            this.requireBackupBeforeModification =
                    value;

            return this;
        }

        public Builder allowOverwriteExistingAlt(
                boolean value) {

            this.allowOverwriteExistingAlt = value;
            return this;
        }

        public Builder allowAutomaticModification(
                boolean value) {

            this.allowAutomaticModification =
                    value;

            return this;
        }

        public Builder preferDryRun(
                boolean preferDryRun) {

            this.preferDryRun = preferDryRun;
            return this;
        }

        public Builder stopOnToolFailure(
                boolean stopOnToolFailure) {

            this.stopOnToolFailure = stopOnToolFailure;
            return this;
        }

        public Builder includeInformationalIssues(
                boolean value) {

            this.includeInformationalIssues = value;
            return this;
        }

        public AccessibilityAgentConfiguration build() {
            return new AccessibilityAgentConfiguration(this);
        }
    }
}