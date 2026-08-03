/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GomsBook AI Agent에서 사용할 Tool을 등록하고 조회하는 Registry입니다.
 *
 * <p>
 * Tool 이름을 기준으로 Tool 구현체를 관리하며,
 * 동일한 이름의 Tool이 중복 등록되는 것을 방지합니다.
 * </p>
 *
 * <p>
 * Tool 이름은 다음 형식을 권장합니다.
 * </p>
 *
 * <pre>
 * xhtml.generate
 * xhtml.validate
 * epub.validate
 * accessibility.check
 * metadata.generate
 * </pre>
 */
public final class ToolRegistry {

    /**
     * Tool 이름과 Tool 구현체를 저장합니다.
     *
     * <p>
     * 등록 순서를 유지하기 위해 {@link LinkedHashMap}을 사용합니다.
     * </p>
     */
    private final Map<
            String,
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > tools = new LinkedHashMap<>();

    /**
     * Tool을 Registry에 등록합니다.
     *
     * @param tool 등록할 Tool
     * @throws NullPointerException Tool이 null인 경우
     * @throws IllegalArgumentException Tool 이름이 비어 있는 경우
     * @throws IllegalStateException 동일한 이름의 Tool이 이미 등록된 경우
     */
    public synchronized void register(
            AgentTool<? extends ToolRequest, ? extends ToolResponse> tool
    ) {
        Objects.requireNonNull(
                tool,
                "tool must not be null."
        );

        String toolName = normalizeToolName(
                tool.getName()
        );

        AgentTool<? extends ToolRequest, ? extends ToolResponse>
                previous = tools.putIfAbsent(
                        toolName,
                        tool
                );

        if (previous != null) {
            throw new IllegalStateException(
                    "Tool is already registered: "
                            + toolName
                            + " (existing version: "
                            + previous.getVersion()
                            + ", new version: "
                            + tool.getVersion()
                            + ")"
            );
        }
    }

    /**
     * 여러 Tool을 한 번에 등록합니다.
     *
     * @param tools 등록할 Tool 목록
     */
    public synchronized void registerAll(
            Collection<
                    ? extends AgentTool<
                            ? extends ToolRequest,
                            ? extends ToolResponse
                    >
            > tools
    ) {
        if (tools == null || tools.isEmpty()) {
            return;
        }

        for (AgentTool<
                ? extends ToolRequest,
                ? extends ToolResponse> tool : tools) {
            register(tool);
        }
    }

    /**
     * Tool 이름으로 Tool을 조회합니다.
     *
     * @param toolName Tool 이름
     * @return Tool이 존재하면 Optional로 반환
     */
    public synchronized Optional<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > findByName(
            String toolName
    ) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                tools.get(
                        toolName.trim()
                )
        );
    }

    /**
     * Tool 이름으로 Tool을 조회합니다.
     *
     * <p>
     * Tool이 존재하지 않으면 예외를 발생시킵니다.
     * </p>
     *
     * @param toolName Tool 이름
     * @return 등록된 Tool
     * @throws ToolNotFoundException Tool이 존재하지 않는 경우
     */
    public synchronized AgentTool<
            ? extends ToolRequest,
            ? extends ToolResponse
    > getRequired(
            String toolName
    ) {
        return findByName(toolName)
                .orElseThrow(
                        () -> new ToolNotFoundException(
                                toolName
                        )
                );
    }

    /**
     * 지정한 이름의 Tool이 등록되어 있는지 확인합니다.
     *
     * @param toolName Tool 이름
     * @return 등록되어 있으면 true
     */
    public synchronized boolean contains(
            String toolName
    ) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }

        return tools.containsKey(
                toolName.trim()
        );
    }

    /**
     * 지정한 Tool을 Registry에서 제거합니다.
     *
     * @param toolName Tool 이름
     * @return 제거된 Tool이 있으면 Optional로 반환
     */
    public synchronized Optional<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > unregister(
            String toolName
    ) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                tools.remove(
                        toolName.trim()
                )
        );
    }

    /**
     * 등록된 모든 Tool을 반환합니다.
     *
     * <p>
     * 반환된 목록은 불변입니다.
     * </p>
     *
     * @return 등록된 Tool 목록
     */
    public synchronized List<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > getAll() {
        return List.copyOf(
                tools.values()
        );
    }

    /**
     * 현재 사용 가능한 Tool만 반환합니다.
     *
     * @return 사용 가능한 Tool 목록
     */
    public synchronized List<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > getAvailableTools() {
        return tools.values()
                .stream()
                .filter(AgentTool::isAvailable)
                .toList();
    }

    /**
     * 등록된 Tool의 개수를 반환합니다.
     *
     * @return Tool 개수
     */
    public synchronized int size() {
        return tools.size();
    }

    /**
     * Registry가 비어 있는지 확인합니다.
     *
     * @return 비어 있으면 true
     */
    public synchronized boolean isEmpty() {
        return tools.isEmpty();
    }

    /**
     * 모든 Tool을 제거합니다.
     */
    public synchronized void clear() {
        tools.clear();
    }

    /**
     * 요청 타입을 처리할 수 있는 Tool을 조회합니다.
     *
     * <p>
     * 여러 Tool이 같은 요청 타입을 처리할 수 있으므로
     * 목록으로 반환합니다.
     * </p>
     *
     * @param requestType 요청 타입
     * @return 요청 타입을 지원하는 Tool 목록
     */
    public synchronized List<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > findByRequestType(
            Class<? extends ToolRequest> requestType
    ) {
        Objects.requireNonNull(
                requestType,
                "requestType must not be null."
        );

        return tools.values()
                .stream()
                .filter(tool ->
                        tool.getRequestType().equals(
                                requestType
                        )
                )
                .toList();
    }

    /**
     * 응답 타입을 반환하는 Tool을 조회합니다.
     *
     * @param responseType 응답 타입
     * @return 응답 타입이 일치하는 Tool 목록
     */
    public synchronized List<
            AgentTool<? extends ToolRequest, ? extends ToolResponse>
    > findByResponseType(
            Class<? extends ToolResponse> responseType
    ) {
        Objects.requireNonNull(
                responseType,
                "responseType must not be null."
        );

        return tools.values()
                .stream()
                .filter(tool ->
                        tool.getResponseType().equals(
                                responseType
                        )
                )
                .toList();
    }

    /**
     * 등록된 Tool 이름 목록을 반환합니다.
     *
     * @return Tool 이름 목록
     */
    public synchronized List<String> getToolNames() {
        return List.copyOf(
                tools.keySet()
        );
    }

    /**
     * Tool 이름을 검증하고 정규화합니다.
     *
     * @param toolName Tool 이름
     * @return 정규화된 Tool 이름
     */
    private static String normalizeToolName(
            String toolName
    ) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tool name must not be blank."
            );
        }

        String normalized = toolName.trim();

        if (!normalized.matches(
                "[a-z][a-z0-9]*(\\.[a-z][a-z0-9-]*)+"
        )) {
            throw new IllegalArgumentException(
                    "Invalid Tool name: "
                            + normalized
                            + ". Expected format: domain.operation"
            );
        }

        return normalized;
    }
}