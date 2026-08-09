/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.AgentExecutor;
import kr.co.goms.gomsbook.ai.agent.AgentRequest;
import kr.co.goms.gomsbook.ai.agent.AgentResponse;
import kr.co.goms.gomsbook.ai.epub.runtime.EpubRuntime;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;
import kr.co.goms.gomsbook.ai.tools.epub.GenerateEpubTool;
import kr.co.goms.gomsbook.ai.tools.epub.InspectEpubTool;
import kr.co.goms.gomsbook.ai.tools.epub.ValidateEpubTool;

/**
 * EPUB Agent의 기본 구현체입니다.
 *
 * <p>EPUB Agent 설정, EPUB Runtime,
 * AgentExecutor 및 EPUB 전용 Tool을 연결합니다.</p>
 *
 * <p>주요 EPUB Tool:</p>
 *
 * <ul>
 *     <li>GenerateEpubTool</li>
 *     <li>ValidateEpubTool</li>
 *     <li>InspectEpubTool</li>
 * </ul>
 */
public final class DefaultEpubAgent
        implements EpubAgent {

    /**
     * EPUB Agent 설정입니다.
     */
    private final EpubAgentConfiguration configuration;

    /**
     * EPUB Runtime입니다.
     */
    private final EpubRuntime epubRuntime;

    /**
     * 공통 Agent 실행기입니다.
     */
    private final AgentExecutor agentExecutor;

    /**
     * Agent에서 사용하는 Tool Registry입니다.
     */
    private final ToolRegistry toolRegistry;

    /**
     * EPUB Agent에 등록된 Tool 목록입니다.
     */
    private final List<AgentTool> tools;

    /**
     * EPUB Agent를 생성합니다.
     *
     * <p>ToolRegistry는 외부에서 전달받습니다.</p>
     *
     * <p>공통 Agent Framework에서 ToolRegistry를 중앙 관리하는
     * 구조를 유지하기 위해 EPUB Agent가 Registry를 직접
     * 생성하지 않습니다.</p>
     */
    public DefaultEpubAgent(
            EpubAgentConfiguration configuration,
            EpubRuntime epubRuntime,
            AgentExecutor agentExecutor,
            ToolRegistry toolRegistry
    ) {

        this.configuration =
                Objects.requireNonNull(
                        configuration,
                        "EPUB agent configuration must not be null."
                );

        this.epubRuntime =
                Objects.requireNonNull(
                        epubRuntime,
                        "EPUB runtime must not be null."
                );

        this.agentExecutor =
                Objects.requireNonNull(
                        agentExecutor,
                        "Agent executor must not be null."
                );

        this.toolRegistry =
                Objects.requireNonNull(
                        toolRegistry,
                        "Tool registry must not be null."
                );

        this.tools =
                initializeTools();
    }

    /**
     * EPUB Agent 요청을 실행합니다.
     */
    @Override
    public AgentResponse execute(
            AgentRequest request
    ) {

        Objects.requireNonNull(
                request,
                "EPUB agent request must not be null."
        );

        AgentRequest resolvedRequest =
                prepareRequest(
                        request
                );

        /*
         * AgentExecutor가 자체적으로 ToolRegistry를 가지고 있는
         * 구조라면 execute(resolvedRequest)만 사용합니다.
         *
         * 현재 공통 Agent 계층과의 중복 Registry 전달을
         * EPUB Agent에서 만들지 않습니다.
         */
        return agentExecutor.execute(
                resolvedRequest
        );
    }

    /**
     * 전달된 AgentRequest에 EPUB Agent 설정을 적용합니다.
     *
     * <p>기존 요청의 instruction, sessionId, messages,
     * attachments, attributes는 그대로 유지합니다.</p>
     */
    private AgentRequest prepareRequest(
            AgentRequest source
    ) {

        EpubAgentConfiguration config =
                configuration;

        /*
         * 실제 AgentRequest에는 toBuilder()가 없고
         * builder(source)가 존재합니다.
         */
        AgentRequest.Builder builder =
                AgentRequest.builder(
                        source
                );

        /*
         * EPUB Agent 전용 System Prompt
         */
        builder.systemPrompt(
                config.getSystemPrompt()
        );

        /*
         * EPUB Agent 모델
         */
        builder.model(
                config.getModel()
        );

        /*
         * AgentRequest의 실제 반복 설정은
         * maxIterations 하나입니다.
         */
        builder.maxIterations(
                resolveMaxIterations(
                        config
                )
        );

        /*
         * EPUB Agent Tool 활성화 여부
         */
        builder.toolCallingEnabled(
                config.isToolCallingEnabled()
                        && config.hasEnabledTools()
        );

        /*
         * EPUB Agent는 생성/검증 계층을 사용하므로
         * 기본적으로 validation을 활성화합니다.
         */
        builder.validationEnabled(
                true
        );

        return builder.build();
    }

    /**
     * EpubAgentConfiguration의 step 설정을
     * AgentRequest.maxIterations 값으로 변환합니다.
     *
     * <p>AgentRequest의 상한을 초과하지 않도록 제한합니다.</p>
     */
    private int resolveMaxIterations(
            EpubAgentConfiguration config
    ) {

        int value =
                config.getMaxSteps();

        if (value < 1) {
            return AgentRequest.DEFAULT_MAX_ITERATIONS;
        }

        return Math.min(
                value,
                AgentRequest.MAX_ALLOWED_ITERATIONS
        );
    }

    /**
     * EPUB Agent Configuration을 기준으로
     * Tool을 생성하고 Registry에 등록합니다.
     */
    private List<AgentTool> initializeTools() {

        List<AgentTool> registered =
                new ArrayList<>();

        if (!configuration
                .isToolCallingEnabled()) {

            return List.of();
        }

        if (configuration
                .isGenerateEpubToolEnabled()) {

            registerTool(
                    new GenerateEpubTool(
                            epubRuntime
                    ),
                    registered
            );
        }

        if (configuration
                .isValidateEpubToolEnabled()) {

            registerTool(
                    new ValidateEpubTool(
                            epubRuntime
                    ),
                    registered
            );
        }

        if (configuration
                .isInspectEpubToolEnabled()) {

            registerTool(
                    new InspectEpubTool(),
                    registered
            );
        }

        return List.copyOf(
                registered
        );
    }

    /**
     * EPUB Tool을 Registry에 등록합니다.
     *
     * <p>중복 Tool 등록 여부는 ToolRegistry 자체가
     * 관리하는 것을 기본 정책으로 합니다.</p>
     */
    private void registerTool(
            AgentTool tool,
            List<AgentTool> registered
    ) {

        Objects.requireNonNull(
                tool,
                "EPUB Agent Tool must not be null."
        );

        /*
         * 실제 ToolRegistry API가 register(AgentTool)를
         * 제공하는 기존 구조를 사용합니다.
         */
        toolRegistry.register(
                tool
        );

        registered.add(
                tool
        );
    }

    /**
     * EPUB Agent 설정을 반환합니다.
     */
    @Override
    public EpubAgentConfiguration
            getConfiguration() {

        return configuration;
    }

    /**
     * EPUB Runtime을 반환합니다.
     */
    public EpubRuntime getEpubRuntime() {
        return epubRuntime;
    }

    /**
     * 공통 AgentExecutor를 반환합니다.
     */
    public AgentExecutor getAgentExecutor() {
        return agentExecutor;
    }

    /**
     * EPUB Agent가 사용하는 ToolRegistry를 반환합니다.
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * 등록된 EPUB Tool 목록을 반환합니다.
     */
    public List<AgentTool> getTools() {
        return tools;
    }

    /**
     * 등록된 EPUB Tool 개수를 반환합니다.
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Tool 이름 기준 등록 여부를 확인합니다.
     *
     * <p>ToolRegistry의 조회 API에 의존하지 않고
     * 현재 Agent가 실제 등록한 Tool 목록을 기준으로
     * 판단합니다.</p>
     */
    public boolean hasTool(
            String toolName
    ) {

        if (toolName == null
                || toolName.isBlank()) {

            return false;
        }

        String normalized =
                toolName.trim();

        return tools.stream()
                .anyMatch(
                        tool ->
                                normalized.equals(
                                        tool.getName()
                                )
                );
    }

    /**
     * GenerateEpubTool 등록 여부입니다.
     */
    public boolean hasGenerateTool() {

        return hasTool(
                GenerateEpubTool.NAME
        );
    }

    /**
     * ValidateEpubTool 등록 여부입니다.
     */
    public boolean hasValidateTool() {

        return hasTool(
                ValidateEpubTool.NAME
        );
    }

    /**
     * InspectEpubTool 등록 여부입니다.
     */
    public boolean hasInspectTool() {

        return hasTool(
                InspectEpubTool.NAME
        );
    }

    /**
     * EPUB Agent의 현재 상태를 반환합니다.
     */
    public AgentStatus getStatus() {

        return new AgentStatus(
                configuration.getAgentName(),
                configuration.getModel(),
                configuration.isToolCallingEnabled(),
                hasGenerateTool(),
                hasValidateTool(),
                hasInspectTool(),
                tools.size(),
                epubRuntime.getStatus()
        );
    }

    @Override
    public String toString() {

        return "DefaultEpubAgent{"
                + "agentName='"
                + configuration.getAgentName()
                + '\''
                + ", model='"
                + configuration.getModel()
                + '\''
                + ", toolCallingEnabled="
                + configuration
                        .isToolCallingEnabled()
                + ", tools="
                + tools.stream()
                        .map(
                                AgentTool::getName
                        )
                        .toList()
                + '}';
    }

    /**
     * EPUB Agent 상태입니다.
     */
    public record AgentStatus(
            String agentName,
            String model,
            boolean toolCallingEnabled,
            boolean generateToolAvailable,
            boolean validateToolAvailable,
            boolean inspectToolAvailable,
            int toolCount,
            EpubRuntime.RuntimeStatus runtimeStatus
    ) {

        public AgentStatus {

            Objects.requireNonNull(
                    agentName,
                    "EPUB agent name must not be null."
            );

            Objects.requireNonNull(
                    model,
                    "EPUB agent model must not be null."
            );

            Objects.requireNonNull(
                    runtimeStatus,
                    "EPUB runtime status must not be null."
            );
        }

        /**
         * Agent 실행 준비 여부입니다.
         */
        public boolean isReady() {

            return runtimeStatus.isReady();
        }

        /**
         * 하나 이상의 Tool을 사용할 수 있는지 확인합니다.
         */
        public boolean hasTools() {

            return toolCallingEnabled
                    && toolCount > 0;
        }

        /**
         * EPUB 생성 가능 여부입니다.
         */
        public boolean canGenerate() {

            return toolCallingEnabled
                    && generateToolAvailable
                    && runtimeStatus.isReady();
        }

        /**
         * EPUB 검증 가능 여부입니다.
         */
        public boolean canValidate() {

            return toolCallingEnabled
                    && validateToolAvailable;
        }

        /**
         * EPUB inspection 가능 여부입니다.
         */
        public boolean canInspect() {

            return toolCallingEnabled
                    && inspectToolAvailable;
        }

        /**
         * EPUB Tool 3종이 모두 등록됐는지 확인합니다.
         */
        public boolean isFullToolSetAvailable() {

            return toolCallingEnabled
                    && generateToolAvailable
                    && validateToolAvailable
                    && inspectToolAvailable;
        }
    }
}