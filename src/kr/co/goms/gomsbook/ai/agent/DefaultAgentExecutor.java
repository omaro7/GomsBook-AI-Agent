/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmMessage;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;
import kr.co.goms.gomsbook.ai.llm.LlmToolCall;
import kr.co.goms.gomsbook.ai.llm.LlmToolDefinition;
import kr.co.goms.gomsbook.ai.llm.model.ChatModelProvider;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolDefinitionProvider;
import kr.co.goms.gomsbook.ai.tool.ToolExecutor;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * 기본 Agent 실행기입니다.
 *
 * <p>
 * LLM 호출 → Tool Call 확인 → Tool 실행 →
 * Tool 결과를 대화에 추가 → LLM 재호출 과정을 반복합니다.
 * </p>
 */
public final class DefaultAgentExecutor
        implements AgentExecutor {

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final LlmClient llmClient;

    private final ToolExecutor toolExecutor;

    private final ToolDefinitionProvider toolDefinitionProvider;

    private final ChatModelProvider chatModelProvider;

    private final int maxIterations;
    
    private final Gson gson = new Gson();


    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider) {

        this(
                llmClient,
                toolExecutor,
                toolDefinitionProvider,
                chatModelProvider,
                DEFAULT_MAX_ITERATIONS
        );
    }


    public DefaultAgentExecutor(
            LlmClient llmClient,
            ToolExecutor toolExecutor,
            ToolDefinitionProvider toolDefinitionProvider,
            ChatModelProvider chatModelProvider,
            int maxIterations) {

        this.llmClient =
                Objects.requireNonNull(
                        llmClient,
                        "llmClient must not be null"
                );

        this.toolExecutor =
                Objects.requireNonNull(
                        toolExecutor,
                        "toolExecutor must not be null"
                );

        this.toolDefinitionProvider =
                Objects.requireNonNull(
                        toolDefinitionProvider,
                        "toolDefinitionProvider must not be null"
                );

        this.chatModelProvider =
                Objects.requireNonNull(
                        chatModelProvider,
                        "chatModelProvider must not be null"
                );


        if (maxIterations <= 0) {

            throw new IllegalArgumentException(
                    "maxIterations must be greater than zero"
            );
        }


        this.maxIterations =
                maxIterations;
    }


    /**
     * AgentExecutor 인터페이스의 기본 진입점입니다.
     */
    @Override
    public AgentResponse execute(
            AgentRequest request) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );


        AgentContext context =
                new AgentContext(
                        request
                );


        return execute(
                context
        );
    }


    /**
     * AgentContext 기반으로 Agent를 실행합니다.
     *
     * <p>
     * 이 메서드는 내부 실행 및 향후 Runtime에서 사용할 수 있습니다.
     * </p>
     */
    @Override
    public AgentResponse execute(
            AgentContext context) {

        Objects.requireNonNull(
                context,
                "context must not be null"
        );


        AgentRequest request =
                Objects.requireNonNull(
                        context.getRequest(),
                        "context.request must not be null"
                );


        long startedNanos =
                System.nanoTime();


        try {

            /*
             * 초기 대화 메시지 구성
             */
            List<LlmMessage> messages =
                    createInitialMessages(
                            request
                    );


            /*
             * 현재 Agent에 공개할 Tool 정의 구성
             */
            List<LlmToolDefinition> tools =
                    resolveToolDefinitions();


            /*
             * ======================================================
             * Tool Definition 진단 로그
             * ======================================================
             */
            System.out.println(
                    "[GomsBook AI] Available tools = "
                            + tools.size()
            );


            for (LlmToolDefinition tool
                    : tools) {

                System.out.println(
                        "[GomsBook AI] Tool = "
                                + tool.getName()
                );
            }


            List<ToolResult> toolResults =
                    new ArrayList<>();


            LlmResponse lastResponse =
                    null;


            /*
             * Agent Loop
             */
            for (int iteration = 1;
                    iteration <= maxIterations;
                    iteration++) {


                System.out.println(
                        "[GomsBook AI] Agent iteration = "
                                + iteration
                );


                LlmRequest llmRequest =
                        createLlmRequest(
                                request,
                                messages,
                                tools
                        );


                /*
                 * LLM 호출
                 */
                lastResponse =
                        llmClient.chat(
                                llmRequest
                        );


                if (lastResponse == null) {

                    throw new AgentException(
                            "LLM returned null response."
                    );
                }


                System.out.println(
                        "[GomsBook AI] LLM response model = "
                                + lastResponse.getModel()
                );


                System.out.println(
                        "[GomsBook AI] LLM tool call count = "
                                + lastResponse.getToolCallCount()
                );


                /*
                 * Tool Call이 없다면 Agent 실행 완료입니다.
                 */
                if (!lastResponse.hasToolCalls()) {

                    System.out.println(
                            "[GomsBook AI] No Tool Call. "
                                    + "Agent execution completed."
                    );


                    return createCompletedResponse(
                            request,
                            lastResponse,
                            toolResults,
                            iteration,
                            startedNanos
                    );
                }


                /*
                 * Assistant의 Tool Call 메시지를
                 * 대화 이력에 추가합니다.
                 */
                messages.add(
                        createAssistantMessage(
                                lastResponse
                        )
                );


                /*
                 * LLM이 요청한 Tool들을 실행합니다.
                 */
                for (LlmToolCall toolCall
                        : lastResponse.getToolCalls()) {


                    /*
                     * ==================================================
                     * Tool Call 진단 로그
                     * ==================================================
                     */
                    System.out.println(
                            "[GomsBook AI] Tool Call = "
                                    + toolCall.getToolName()
                    );


                    System.out.println(
                            "[GomsBook AI] Tool Call ID = "
                                    + toolCall.getId()
                    );


                    System.out.println(
                            "[GomsBook AI] Tool Arguments = "
                                    + toolCall.getArguments()
                    );


                    ToolResult toolResult =
                            executeTool(
                                    request,
                                    context,
                                    toolCall
                            );


                    /*
                     * ==================================================
                     * Tool Result 진단 로그
                     * ==================================================
                     */
                    System.out.println(
                            "[GomsBook AI] Tool Result = "
                                    + toolResult
                    );


                    toolResults.add(
                            toolResult
                    );


                    /*
                     * Tool 실행 결과를 다시
                     * LLM 대화에 전달합니다.
                     */
                    messages.add(
                            createToolMessage(
                                    toolCall,
                                    toolResult
                            )
                    );
                }
            }


            /*
             * maxIterations까지 Tool Call이 계속되면
             * 무한 Tool 호출을 방지하기 위해 종료합니다.
             */
            System.out.println(
                    "[GomsBook AI] Agent iteration limit reached."
            );


            return createIterationLimitResponse(
                    request,
                    lastResponse,
                    toolResults,
                    startedNanos
            );


        } catch (AgentException exception) {

            throw exception;

        } catch (RuntimeException exception) {

            throw new AgentException(
                    "Agent execution failed.",
                    exception
            );
        }
    }


    /**
     * Agent 실행을 위한 초기 LLM 메시지를 생성합니다.
     *
     * <p>
     * 요청별 System Prompt가 있으면 먼저 추가하고,
     * 기존 대화 메시지를 추가한 후 마지막으로
     * 현재 사용자의 instruction을 User 메시지로 추가합니다.
     * </p>
     */
    private List<LlmMessage> createInitialMessages(
            AgentRequest request) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );


        List<LlmMessage> messages =
                new ArrayList<>();


        /*
         * 요청별 System Prompt
         */
        if (request.hasSystemPrompt()) {

            messages.add(
                    LlmMessage.system(
                            request.getSystemPrompt()
                    )
            );
        }


        /*
         * 이전 대화 이력
         */
        if (request.hasMessages()) {

            messages.addAll(
                    request.getMessages()
            );
        }


        /*
         * 현재 Agent 실행 명령
         */
        messages.add(
                LlmMessage.user(
                        request.getInstruction()
                )
        );


        return messages;
    }


    /**
     * 현재 등록된 Tool 정의를 가져옵니다.
     */
    private List<LlmToolDefinition>
            resolveToolDefinitions() {

        List<LlmToolDefinition> definitions =
                toolDefinitionProvider
                        .getToolDefinitions();


        if (definitions == null
                || definitions.isEmpty()) {

            return List.of();
        }


        return List.copyOf(
                definitions
        );
    }


    /**
     * LLM 요청을 생성합니다.
     */
    private LlmRequest createLlmRequest(
            AgentRequest request,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools) {

        LlmRequest.Builder builder =
                LlmRequest.builder()
                        .messages(
                                messages
                        )
                        .stream(
                                false
                        );


        if (request.hasModel()) {

            builder.model(
                    request.getModel()
            );

        } else {

            String model =
                    chatModelProvider.getModel();

            if (model != null
                    && !model.isBlank()) {

                builder.model(
                        model
                );
            }
        }


        if (tools != null
                && !tools.isEmpty()) {

            builder.tools(
                    tools
            );
        }


        return builder.build();
    }


    /**
     * Tool을 실행합니다.
     */
    private ToolResult executeTool(
            AgentRequest agentRequest,
            AgentContext agentContext,
            LlmToolCall toolCall) {

        if (toolCall == null) {

            throw new AgentException(
                    "LLM returned null Tool Call."
            );
        }


        if (!toolCall.isFunctionCall()) {

            throw new AgentException(
                    "Unsupported Tool Call type: "
                            + toolCall.getType()
            );
        }


        String toolName =
                toolCall.getToolName();


        if (toolName == null
                || toolName.isBlank()) {

            throw new AgentException(
                    "Tool Call name must not be blank."
            );
        }


        Map<String, Object> arguments =
                toolCall.getArguments();


        ToolContext toolContext =
                createToolContext(
                        agentRequest,
                        agentContext
                );


        ToolRequest toolRequest =
                ToolRequest.builder()
                        .requestId(
                                agentRequest.getRequestId()
                        )
                        .toolCallId(
                                toolCall.getId()
                        )
                        .toolName(
                                toolName
                        )
                        .arguments(
                                arguments == null
                                        ? Map.of()
                                        : arguments
                        )
                        .build();


        ToolResult result =
                toolExecutor.execute(
                        toolRequest,
                        toolContext
                );


        if (result == null) {

            throw new AgentException(
                    "Tool executor returned null. tool="
                            + toolName
            );
        }


        return result;
    }


    /**
     * AgentContext를 ToolContext로 변환합니다.
     */
    private ToolContext createToolContext(
            AgentRequest request,
            AgentContext agentContext) {

        ToolContext.Builder builder =
                ToolContext.builder();


        if (request.hasRequestId()) {

            builder.requestId(
                    request.getRequestId()
            );
        }


        /*
         * AgentRequest에 포함된 확장 속성을
         * ToolContext로 전달합니다.
         */
        if (request.getAttributes() != null
                && !request.getAttributes().isEmpty()) {

            builder.attributes(
                    request.getAttributes()
            );
        }


        return builder.build();
    }


    /**
     * LLM Tool Call 응답을 Assistant 메시지로 변환합니다.
     */
    private LlmMessage createAssistantMessage(
            LlmResponse response) {

        Objects.requireNonNull(
                response,
                "response must not be null"
        );


        String content =
                response.getContent();


        if (content == null) {

            content = "";
        }


        if (response.hasToolCalls()) {

            return LlmMessage.assistantToolCalls(
                    content,
                    response.getToolCalls()
            );
        }


        if (content.isBlank()) {

            throw new AgentException(
                    "LLM Assistant response content is empty."
            );
        }


        return LlmMessage.assistant(
                content
        );
    }


    /**
     * Tool 실행 결과를 LLM Tool 메시지로 변환합니다.
     */
    private LlmMessage createToolMessage(
            LlmToolCall toolCall,
            ToolResult toolResult) {

        Objects.requireNonNull(
                toolCall,
                "toolCall must not be null"
        );

        Objects.requireNonNull(
                toolResult,
                "toolResult must not be null"
        );


        Map<String, Object> payload =
                new LinkedHashMap<>();


        payload.put(
                "toolName",
                toolResult.getToolName()
        );

        payload.put(
                "status",
                toolResult.getStatus()
        );


        if (toolResult.hasMessage()) {

            payload.put(
                    "message",
                    toolResult.getMessage()
            );
        }


        /*
         * Tool의 실제 결과값을 LLM에게 전달합니다.
         */
        if (toolResult.hasData()) {

            Map<String, Object> data =
                    new LinkedHashMap<>(
                            toolResult.getData()
                    );

            /*
             * InspectEpubTool의 inspectionResult 객체에는 Path 등
             * 복합 객체가 포함되므로 이미 평탄화된 개별 data 값만
             * LLM에 전달합니다.
             */
            data.remove(
                    "inspectionResult"
            );

            payload.put(
                    "data",
                    data
            );
        }


        if (toolResult.hasError()) {

            payload.put(
                    "errorCode",
                    toolResult.getErrorCode()
            );

            payload.put(
                    "errorMessage",
                    toolResult.getErrorMessage()
            );
        }


        String content =
                gson.toJson(
                        payload
                );


        System.out.println(
                "[GomsBook AI] Tool Message = "
                        + content
        );


        return LlmMessage.toolResult(
                toolCall.getId(),
                toolCall.getToolName(),
                content
        );
    }

    /**
     * 정상 완료 응답을 생성합니다.
     */
    private AgentResponse createCompletedResponse(
            AgentRequest request,
            LlmResponse llmResponse,
            List<ToolResult> toolResults,
            int iterations,
            long startedNanos) {

        return AgentResponse.builder()
                .requestId(
                        request.getRequestId()
                )
                .sessionId(
                        request.getSessionId()
                )
                .status(
                        AgentStatus.COMPLETED
                )
                .content(
                        llmResponse.getContent()
                )
                .model(
                        llmResponse.getModel()
                )
                .toolResults(
                        toolResults
                )
                .iterations(
                        iterations
                )
                .build();
    }


    /**
     * 최대 Tool 호출 반복 횟수에 도달한 응답을 생성합니다.
     */
    private AgentResponse createIterationLimitResponse(
            AgentRequest request,
            LlmResponse lastResponse,
            List<ToolResult> toolResults,
            long startedNanos) {

        return AgentResponse.builder()
                .requestId(
                        request.getRequestId()
                )
                .sessionId(
                        request.getSessionId()
                )
                .status(
                        AgentStatus.ITERATION_LIMIT_REACHED
                )
                .content(
                        lastResponse == null
                                ? ""
                                : lastResponse.getContent()
                )
                .model(
                        lastResponse == null
                                ? null
                                : lastResponse.getModel()
                )
                .toolResults(
                        toolResults
                )
                .iterations(
                        maxIterations
                )
                .errorCode(
                        "AGENT_ITERATION_LIMIT_REACHED"
                )
                .errorMessage(
                        "Agent reached maximum Tool Call iterations."
                )
                .build();
    }
}