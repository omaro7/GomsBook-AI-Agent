/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * LLM 대화 메시지입니다.
 *
 * <p>일반적인 system, user, assistant 메시지뿐 아니라
 * Assistant의 Tool Call 요청과 Tool 실행 결과 메시지를
 * 함께 표현합니다.</p>
 */
public final class LlmMessage {

    private final LlmRole role;
    private final String content;

    /**
     * 메시지 또는 Tool 이름입니다.
     *
     * <p>일반 메시지에서는 {@code null}이며, Tool 결과 메시지에서는
     * 실행한 Tool 이름을 저장합니다.</p>
     */
    private final String name;

    /**
     * Tool Call 식별자입니다.
     *
     * <p>Tool 실행 결과를 Assistant의 Tool Call과 연결할 때
     * 사용합니다.</p>
     */
    private final String toolCallId;

    /**
     * Assistant가 요청한 Tool Call 목록입니다.
     */
    private final List<LlmToolCall> toolCalls;

    /**
     * 일반 LLM 메시지를 생성합니다.
     *
     * @param role    메시지 역할
     * @param content 메시지 본문
     */
    public LlmMessage(
            LlmRole role,
            String content) {

        this(
                role,
                content,
                null,
                null,
                List.of()
        );
    }

    /**
     * 전체 정보를 포함하는 LLM 메시지를 생성합니다.
     *
     * @param role       메시지 역할
     * @param content    메시지 본문
     * @param name       Tool 또는 메시지 이름
     * @param toolCallId Tool Call 식별자
     * @param toolCalls  Assistant Tool Call 목록
     */
    public LlmMessage(
            LlmRole role,
            String content,
            String name,
            String toolCallId,
            List<LlmToolCall> toolCalls) {

        this.role = Objects.requireNonNull(
                role,
                "role must not be null"
        );

        this.content = content == null
                ? ""
                : content;

        this.name = normalizeOptional(name);
        this.toolCallId = normalizeOptional(toolCallId);
        this.toolCalls = immutableToolCalls(toolCalls);

        validate();
    }

    /**
     * 시스템 메시지를 생성합니다.
     */
    public static LlmMessage system(String content) {
        return new LlmMessage(
                LlmRole.SYSTEM,
                requireContent(content, "system content")
        );
    }

    /**
     * 사용자 메시지를 생성합니다.
     */
    public static LlmMessage user(String content) {
        return new LlmMessage(
                LlmRole.USER,
                requireContent(content, "user content")
        );
    }

    /**
     * Assistant 일반 응답 메시지를 생성합니다.
     */
    public static LlmMessage assistant(String content) {
        return new LlmMessage(
                LlmRole.ASSISTANT,
                requireContent(content, "assistant content")
        );
    }

    /**
     * Assistant Tool Call 메시지를 생성합니다.
     *
     * <p>Tool Calling 응답에서는 본문이 비어 있을 수 있습니다.</p>
     *
     * @param toolCalls Tool Call 목록
     */
    public static LlmMessage assistantToolCalls(
            List<LlmToolCall> toolCalls) {

        return assistantToolCalls("", toolCalls);
    }

    /**
     * 설명 본문과 Tool Call을 함께 포함하는 Assistant 메시지를 생성합니다.
     *
     * @param content   Assistant 본문
     * @param toolCalls Tool Call 목록
     */
    public static LlmMessage assistantToolCalls(
            String content,
            List<LlmToolCall> toolCalls) {

        return new LlmMessage(
                LlmRole.ASSISTANT,
                content,
                null,
                null,
                toolCalls
        );
    }

    /**
     * Tool 실행 결과 메시지를 생성합니다.
     *
     * @param toolCallId 원본 Tool Call 식별자
     * @param toolName   실행한 Tool 이름
     * @param content    Tool 실행 결과
     */
    public static LlmMessage toolResult(
            String toolCallId,
            String toolName,
            String content) {

        return new LlmMessage(
                LlmRole.TOOL,
                requireContent(content, "tool result content"),
                requireText(toolName, "toolName"),
                normalizeOptional(toolCallId),
                List.of()
        );
    }

    /**
     * Tool Call ID가 없는 Tool 실행 결과 메시지를 생성합니다.
     */
    public static LlmMessage toolResult(
            String toolName,
            String content) {

        return toolResult(
                null,
                toolName,
                content
        );
    }

    /**
     * 메시지 역할을 반환합니다.
     */
    public LlmRole getRole() {
        return role;
    }

    /**
     * 메시지 본문을 반환합니다.
     */
    public String getContent() {
        return content;
    }

    /**
     * Tool 또는 메시지 이름을 반환합니다.
     */
    public String getName() {
        return name;
    }

    /**
     * Tool 이름을 반환합니다.
     *
     * <p>{@link #getName()}과 동일하며 Tool 메시지 처리 코드의
     * 가독성을 위해 제공합니다.</p>
     */
    public String getToolName() {
        return name;
    }

    /**
     * Tool Call 식별자를 반환합니다.
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * Assistant Tool Call 목록을 반환합니다.
     */
    public List<LlmToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * Tool 또는 메시지 이름이 있는지 확인합니다.
     */
    public boolean hasName() {
        return name != null;
    }

    /**
     * Tool Call 식별자가 있는지 확인합니다.
     */
    public boolean hasToolCallId() {
        return toolCallId != null;
    }

    /**
     * Tool Call 목록이 있는지 확인합니다.
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 본문이 있는지 확인합니다.
     */
    public boolean hasContent() {
        return content != null
                && !content.isBlank();
    }

    /**
     * 시스템 메시지인지 확인합니다.
     */
    public boolean isSystem() {
        return role == LlmRole.SYSTEM;
    }

    /**
     * 사용자 메시지인지 확인합니다.
     */
    public boolean isUser() {
        return role == LlmRole.USER;
    }

    /**
     * Assistant 메시지인지 확인합니다.
     */
    public boolean isAssistant() {
        return role == LlmRole.ASSISTANT;
    }

    /**
     * Tool 결과 메시지인지 확인합니다.
     */
    public boolean isTool() {
        return role == LlmRole.TOOL;
    }

    /**
     * Assistant Tool Call 메시지인지 확인합니다.
     */
    public boolean isAssistantToolCall() {
        return isAssistant()
                && hasToolCalls();
    }

    /**
     * 메시지 상태를 검증합니다.
     */
    private void validate() {
        if (hasToolCalls() && !isAssistant()) {
            throw new IllegalArgumentException(
                    "toolCalls are only allowed "
                            + "for assistant messages"
            );
        }

        if (isTool()) {
            if (name == null) {
                throw new IllegalArgumentException(
                        "tool message must contain tool name"
                );
            }

            if (!hasContent()) {
                throw new IllegalArgumentException(
                        "tool message content must not be blank"
                );
            }
        }

        if (!isTool()
                && (name != null || toolCallId != null)) {

            throw new IllegalArgumentException(
                    "name and toolCallId are only allowed "
                            + "for tool messages"
            );
        }

        if ((isSystem() || isUser())
                && !hasContent()) {

            throw new IllegalArgumentException(
                    role.name().toLowerCase()
                            + " message content must not be blank"
            );
        }

        if (isAssistant()
                && !hasContent()
                && !hasToolCalls()) {

            throw new IllegalArgumentException(
                    "assistant message must contain "
                            + "content or tool calls"
            );
        }
    }

    private static List<LlmToolCall> immutableToolCalls(
            List<LlmToolCall> toolCalls) {

        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        List<LlmToolCall> copied =
                new ArrayList<>(toolCalls.size());

        for (LlmToolCall toolCall : toolCalls) {
            copied.add(
                    Objects.requireNonNull(
                            toolCall,
                            "toolCalls must not contain null"
                    )
            );
        }

        return Collections.unmodifiableList(copied);
    }

    private static String requireContent(
            String content,
            String fieldName) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return content;
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "LlmMessage{"
                + "role=" + role
                + ", contentLength=" + content.length()
                + ", name='" + name + '\''
                + ", toolCallId='" + toolCallId + '\''
                + ", toolCallCount=" + toolCalls.size()
                + '}';
    }
}