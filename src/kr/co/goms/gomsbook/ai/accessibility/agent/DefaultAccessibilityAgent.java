/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.tools.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tools.image.AnalyzeImageTool;
import kr.co.goms.gomsbook.ai.tools.image.ApplyAltTextTool;

/**
 * 접근성 전용 Agent의 기본 구현체.
 *
 * <p>이 구현체는 접근성 요청을 기존 Agent Framework에서 실행할 수 있는
 * 형태로 변환하고, 실행 결과를 {@link AccessibilityAgentResult}로
 * 변환한다.</p>
 *
 * <p>LLM 호출과 Tool 반복 실행 자체는 {@link AgentExecutionBridge}에
 * 위임한다. 따라서 접근성 계층이 {@code DefaultAgentExecutor}의 구체적인
 * 요청·결과 타입에 직접 의존하지 않는다.</p>
 *
 * <p>다음 정책은 LLM 시스템 프롬프트뿐 아니라 코드 수준에서도
 * 검증된다.</p>
 *
 * <ul>
 *   <li>프로젝트 내부 파일만 처리</li>
 *   <li>요청과 Agent 구성의 수정 권한 결합</li>
 *   <li>기존 alt 덮어쓰기 제한</li>
 *   <li>수정 전 검사 요구</li>
 *   <li>수정 후 재검사 요구</li>
 *   <li>허용되지 않은 Tool 호출 차단</li>
 *   <li>반복 횟수 및 Tool 호출 수 제한</li>
 *   <li>실행 제한 시간 검증</li>
 * </ul>
 */
public final class DefaultAccessibilityAgent
        implements AccessibilityAgent {

    private final AccessibilityAgentConfiguration configuration;
    private final AgentExecutionBridge executionBridge;

    /**
     * 접근성 Agent를 생성한다.
     *
     * @param configuration 접근성 Agent 구성
     * @param executionBridge 기존 Agent Framework 실행 어댑터
     */
    public DefaultAccessibilityAgent(
            AccessibilityAgentConfiguration configuration,
            AgentExecutionBridge executionBridge) {

        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null"
        );

        this.executionBridge = Objects.requireNonNull(
                executionBridge,
                "executionBridge must not be null"
        );
    }

    @Override
    public AccessibilityAgentResult execute(
            AccessibilityAgentRequest request)
            throws AccessibilityAgentException {

        Instant startedAt = Instant.now();

        validateRequest(request);

        AccessibilityAgentExecutionRequest executionRequest =
                createExecutionRequest(request);

        AgentExecutionOutcome outcome;

        try {
            outcome = executionBridge.execute(
                    executionRequest
            );

        } catch (AccessibilityAgentException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .EXECUTION_FAILED,
                    configuration.getAgentName(),
                    null,
                    "Accessibility agent execution failed.",
                    exception
            );
        }

        if (outcome == null) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .EXECUTION_FAILED,
                    configuration.getAgentName(),
                    null,
                    "Agent execution bridge returned no result.",
                    null
            );
        }

        Instant completedAt = Instant.now();

        validateOutcome(
                request,
                outcome,
                startedAt,
                completedAt
        );

        return createResult(
                request,
                outcome,
                startedAt,
                completedAt
        );
    }

    @Override
    public boolean supports(
            AccessibilityAgentRequest request) {

        if (!AccessibilityAgent.super.supports(request)) {
            return false;
        }

        Path projectRoot =
                request.getProjectRoot();

        if (!Files.exists(projectRoot)
                || !Files.isDirectory(projectRoot)
                || !Files.isReadable(projectRoot)) {

            return false;
        }

        Path targetDocument =
                request.getTargetDocumentPath();

        if (targetDocument == null) {
            return true;
        }

        return targetDocument.startsWith(projectRoot)
                && Files.exists(targetDocument)
                && Files.isRegularFile(targetDocument)
                && Files.isReadable(targetDocument);
    }

    @Override
    public AccessibilityAgentConfiguration getConfiguration() {
        return configuration;
    }

    private void validateRequest(
            AccessibilityAgentRequest request) {

        if (request == null) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    "Accessibility agent request must not be null."
            );
        }

        if (!supports(request)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Accessibility agent request is not supported.",
                    null
            );
        }

        Path projectRoot =
                request.getProjectRoot()
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(projectRoot)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Project root does not exist: "
                            + projectRoot,
                    null
            );
        }

        if (!Files.isDirectory(projectRoot)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Project root is not a directory: "
                            + projectRoot,
                    null
            );
        }

        validateTargetDocument(
                request,
                projectRoot
        );

        validateModificationPolicy(request);
        validateRequiredTools();
    }

    private void validateTargetDocument(
            AccessibilityAgentRequest request,
            Path projectRoot) {

        Path documentPath =
                request.getTargetDocumentPath();

        if (documentPath == null) {
            return;
        }

        Path normalizedDocument =
                documentPath
                        .toAbsolutePath()
                        .normalize();

        if (configuration.isRequireProjectLocalFiles()
                && !normalizedDocument.startsWith(projectRoot)) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    null,
                    "Target document must be inside the current project.",
                    null
            );
        }

        if (!Files.exists(normalizedDocument)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Target document does not exist: "
                            + normalizedDocument,
                    null
            );
        }

        if (!Files.isRegularFile(normalizedDocument)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Target document is not a regular file: "
                            + normalizedDocument,
                    null
            );
        }

        if (!Files.isReadable(normalizedDocument)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .INVALID_REQUEST,
                    configuration.getAgentName(),
                    null,
                    "Target document is not readable: "
                            + normalizedDocument,
                    null
            );
        }

        if (request.canModifyFiles()
                && !Files.isWritable(normalizedDocument)) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    null,
                    "Target document is not writable: "
                            + normalizedDocument,
                    null
            );
        }
    }

    private void validateModificationPolicy(
            AccessibilityAgentRequest request) {

        if (request.isAllowModification()
                && !configuration
                        .isAllowAutomaticModification()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    null,
                    "The accessibility agent configuration "
                            + "does not permit automatic modification.",
                    null
            );
        }

        if (request.isOverwriteExistingAlt()
                && !configuration
                        .isAllowOverwriteExistingAlt()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ApplyAltTextTool.TOOL_NAME,
                    "Overwriting existing alternative text "
                            + "is not allowed by the agent configuration.",
                    null
            );
        }

        if (request.canModifyFiles()
                && configuration
                        .isRequireBackupBeforeModification()
                && !request.isCreateBackup()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ApplyAltTextTool.TOOL_NAME,
                    "A backup is required before modifying "
                            + "an XHTML document.",
                    null
            );
        }
    }

    private void validateRequiredTools() {

        requireTool(
                ValidateAccessibilityTool.TOOL_NAME
        );

        requireTool(
                AnalyzeImageTool.TOOL_NAME
        );

        requireTool(
                ApplyAltTextTool.TOOL_NAME
        );
    }

    private void requireTool(
            String toolName) {

        if (!configuration.isToolAllowed(toolName)) {
            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .CONFIGURATION_INVALID,
                    configuration.getAgentName(),
                    toolName,
                    "Required accessibility Tool is not configured: "
                            + toolName,
                    null
            );
        }
    }

    private AccessibilityAgentExecutionRequest
            createExecutionRequest(
                    AccessibilityAgentRequest request) {

        String instruction =
                createAgentInstruction(request);

        Map<String, String> executionMetadata =
                new LinkedHashMap<>();

        executionMetadata.putAll(
                request.getMetadata()
        );

        executionMetadata.put(
                "agentName",
                configuration.getAgentName()
        );

        executionMetadata.put(
                "model",
                configuration.getModel()
        );

        executionMetadata.put(
                "allowModification",
                Boolean.toString(
                        request.isAllowModification()
                )
        );

        executionMetadata.put(
                "dryRun",
                Boolean.toString(
                        request.isDryRun()
                )
        );

        executionMetadata.put(
                "createBackup",
                Boolean.toString(
                        request.isCreateBackup()
                )
        );

        executionMetadata.put(
                "overwriteExistingAlt",
                Boolean.toString(
                        request.isOverwriteExistingAlt()
                )
        );

        executionMetadata.put(
                "minimumAnalysisConfidence",
                Double.toString(
                        configuration
                                .getMinimumAnalysisConfidence()
                )
        );

        if (request.hasTargetDocument()) {
            executionMetadata.put(
                    "targetDocumentPath",
                    request
                            .getProjectRelativeTargetDocumentPath()
            );
        }

        return AccessibilityAgentExecutionRequest
                .builder()
                .agentName(
                        configuration.getAgentName()
                )
                .model(configuration.getModel())
                .systemPrompt(
                        configuration.getSystemPrompt()
                )
                .instruction(instruction)
                .projectRoot(
                        request.getProjectRoot()
                )
                .targetDocumentPath(
                        request.getTargetDocumentPath()
                )
                .allowedToolNames(
                        configuration.getAllowedToolNames()
                )
                .maxIterations(
                        configuration.getMaxIterations()
                )
                .maxToolCalls(
                        configuration.getMaxToolCalls()
                )
                .executionTimeout(
                        configuration.getExecutionTimeout()
                )
                .allowModification(
                        request.isAllowModification()
                )
                .dryRun(request.isDryRun())
                .createBackup(
                        request.isCreateBackup()
                )
                .overwriteExistingAlt(
                        request.isOverwriteExistingAlt()
                )
                .includeInformationalIssues(
                        request.isIncludeInformationalIssues()
                                && configuration
                                        .isIncludeInformationalIssues()
                )
                .requireValidationBeforeModification(
                        configuration
                                .isRequireValidationBeforeModification()
                )
                .requireValidationAfterModification(
                        configuration
                                .isRequireValidationAfterModification()
                )
                .requireUniqueImageTarget(
                        configuration
                                .isRequireUniqueImageTarget()
                )
                .minimumAnalysisConfidence(
                        configuration
                                .getMinimumAnalysisConfidence()
                )
                .stopOnToolFailure(
                        configuration.isStopOnToolFailure()
                )
                .metadata(executionMetadata)
                .build();
    }

    private String createAgentInstruction(
            AccessibilityAgentRequest request) {

        StringBuilder instruction =
                new StringBuilder();

        instruction.append(
                request.getInstruction()
        );

        instruction.append("\n\n");
        instruction.append(
                "Execution constraints supplied by the application:"
        );

        if (request.hasTargetDocument()) {
            instruction.append("\n- Target document: ");
            instruction.append(
                    request
                            .getProjectRelativeTargetDocumentPath()
            );
        }

        instruction.append("\n- File modification allowed: ");
        instruction.append(
                request.isAllowModification()
        );

        instruction.append("\n- Dry-run mode: ");
        instruction.append(
                request.isDryRun()
        );

        instruction.append("\n- Create backup: ");
        instruction.append(
                request.isCreateBackup()
        );

        instruction.append(
                "\n- Overwrite existing non-empty alt allowed: "
        );
        instruction.append(
                request.isOverwriteExistingAlt()
        );

        instruction.append(
                "\n- Include informational validation issues: "
        );
        instruction.append(
                request.isIncludeInformationalIssues()
                        && configuration
                                .isIncludeInformationalIssues()
        );

        instruction.append(
                "\n- Minimum image-analysis confidence for automatic "
                        + "application: "
        );
        instruction.append(
                configuration
                        .getMinimumAnalysisConfidence()
        );

        if (!request.isAllowModification()) {
            instruction.append(
                    "\nDo not call any file-modifying Tool. "
                            + "Inspect and report only."
            );

        } else if (request.isDryRun()) {
            instruction.append(
                    "\nAny apply_alt_text call must use dryRun=true."
            );
        }

        if (!request.isOverwriteExistingAlt()) {
            instruction.append(
                    "\nDo not replace an existing non-empty alt value."
            );
        }

        if (configuration
                .isRequireValidationBeforeModification()) {

            instruction.append(
                    "\nCall validate_accessibility before "
                            + "the first modifying Tool call."
            );
        }

        if (configuration
                .isRequireValidationAfterModification()) {

            instruction.append(
                    "\nAfter any successful file modification, "
                            + "call validate_accessibility again."
            );
        }

        return instruction.toString();
    }

    private void validateOutcome(
            AccessibilityAgentRequest request,
            AgentExecutionOutcome outcome,
            Instant startedAt,
            Instant completedAt) {

        validateExecutionLimits(outcome);
        validateToolAllowList(outcome);
        validateModificationOutcome(request, outcome);
        validateValidationSequence(outcome);

        Duration actualDuration =
                Duration.between(
                        startedAt,
                        completedAt
                );

        if (actualDuration.compareTo(
                configuration.getExecutionTimeout()) > 0) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .EXECUTION_TIMEOUT,
                    configuration.getAgentName(),
                    null,
                    "Accessibility agent execution exceeded "
                            + "the configured timeout.",
                    null
            );
        }
    }

    private void validateExecutionLimits(
            AgentExecutionOutcome outcome) {

        if (outcome.getIterationCount()
                > configuration.getMaxIterations()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .MAX_ITERATIONS_REACHED,
                    configuration.getAgentName(),
                    null,
                    "Accessibility agent exceeded the maximum "
                            + "iteration count.",
                    null
            );
        }

        if (outcome.getToolCallCount()
                > configuration.getMaxToolCalls()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .MAX_TOOL_CALLS_REACHED,
                    configuration.getAgentName(),
                    null,
                    "Accessibility agent exceeded the maximum "
                            + "Tool call count.",
                    null
            );
        }
    }

    private void validateToolAllowList(
            AgentExecutionOutcome outcome) {

        for (AgentToolCallOutcome toolCall
                : outcome.getToolCalls()) {

            if (!configuration.isToolAllowed(
                    toolCall.getToolName())) {

                throw new AccessibilityAgentException(
                        AccessibilityAgentErrorCode
                                .TOOL_NOT_ALLOWED,
                        configuration.getAgentName(),
                        toolCall.getToolName(),
                        "Accessibility agent attempted to call "
                                + "a Tool that is not allowed.",
                        null
                );
            }
        }
    }

    private void validateModificationOutcome(
            AccessibilityAgentRequest request,
            AgentExecutionOutcome outcome) {

        if (outcome.isFileModified()
                && !request.canModifyFiles()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ApplyAltTextTool.TOOL_NAME,
                    "A project file was modified although "
                            + "the request did not permit modification.",
                    null
            );
        }

        if (request.isDryRun()
                && outcome.isFileModified()) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ApplyAltTextTool.TOOL_NAME,
                    "A project file was modified during dry-run mode.",
                    null
            );
        }

        for (String modifiedDocument
                : outcome.getModifiedDocuments()) {

            Path resolved =
                    resolveProjectPath(
                            request.getProjectRoot(),
                            modifiedDocument
                    );

            if (configuration
                    .isRequireProjectLocalFiles()
                    && !resolved.startsWith(
                            request.getProjectRoot())) {

                throw new AccessibilityAgentException(
                        AccessibilityAgentErrorCode
                                .POLICY_VIOLATION,
                        configuration.getAgentName(),
                        ApplyAltTextTool.TOOL_NAME,
                        "A file outside the current project "
                                + "was reported as modified: "
                                + modifiedDocument,
                        null
                );
            }
        }
    }

    private void validateValidationSequence(
            AgentExecutionOutcome outcome) {

        if (!outcome.isFileModified()) {
            return;
        }

        List<AgentToolCallOutcome> toolCalls =
                outcome.getToolCalls();

        int firstModificationIndex = -1;
        int lastModificationIndex = -1;

        for (int index = 0;
                index < toolCalls.size();
                index++) {

            AgentToolCallOutcome toolCall =
                    toolCalls.get(index);

            if (ApplyAltTextTool.TOOL_NAME.equals(
                    toolCall.getToolName())
                    && toolCall.isFileModified()) {

                if (firstModificationIndex < 0) {
                    firstModificationIndex = index;
                }

                lastModificationIndex = index;
            }
        }

        if (firstModificationIndex < 0) {
            return;
        }

        if (configuration
                .isRequireValidationBeforeModification()
                && !containsSuccessfulToolBefore(
                        toolCalls,
                        ValidateAccessibilityTool.TOOL_NAME,
                        firstModificationIndex
                )) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ValidateAccessibilityTool.TOOL_NAME,
                    "Accessibility validation was not completed "
                            + "before file modification.",
                    null
            );
        }

        if (configuration
                .isRequireValidationAfterModification()
                && !containsSuccessfulToolAfter(
                        toolCalls,
                        ValidateAccessibilityTool.TOOL_NAME,
                        lastModificationIndex
                )) {

            throw new AccessibilityAgentException(
                    AccessibilityAgentErrorCode
                            .POLICY_VIOLATION,
                    configuration.getAgentName(),
                    ValidateAccessibilityTool.TOOL_NAME,
                    "Accessibility validation was not completed "
                            + "after file modification.",
                    null
            );
        }
    }

    private boolean containsSuccessfulToolBefore(
            List<AgentToolCallOutcome> toolCalls,
            String toolName,
            int exclusiveEndIndex) {

        for (int index = 0;
                index < exclusiveEndIndex;
                index++) {

            AgentToolCallOutcome toolCall =
                    toolCalls.get(index);

            if (toolName.equals(toolCall.getToolName())
                    && toolCall.isSuccessful()) {

                return true;
            }
        }

        return false;
    }

    private boolean containsSuccessfulToolAfter(
            List<AgentToolCallOutcome> toolCalls,
            String toolName,
            int exclusiveStartIndex) {

        for (int index = exclusiveStartIndex + 1;
                index < toolCalls.size();
                index++) {

            AgentToolCallOutcome toolCall =
                    toolCalls.get(index);

            if (toolName.equals(toolCall.getToolName())
                    && toolCall.isSuccessful()) {

                return true;
            }
        }

        return false;
    }

    private AccessibilityAgentResult createResult(
            AccessibilityAgentRequest request,
            AgentExecutionOutcome outcome,
            Instant startedAt,
            Instant completedAt) {

        AccessibilityAgentStatus status =
                resolveStatus(outcome);

        AccessibilityAgentResult.Builder builder =
                AccessibilityAgentResult.builder()
                        .status(status)
                        .agentName(
                                configuration.getAgentName()
                        )
                        .response(
                                resolveResponse(outcome)
                        )
                        .iterationCount(
                                outcome.getIterationCount()
                        )
                        .toolCallCount(
                                outcome.getToolCallCount()
                        )
                        .fileModified(
                                outcome.isFileModified()
                        )
                        .manualReviewRequired(
                                outcome.isManualReviewRequired()
                        )
                        .startedAt(startedAt)
                        .completedAt(completedAt)
                        .metadata(
                                "model",
                                configuration.getModel()
                        )
                        .metadata(
                                "targetDocument",
                                request
                                        .getProjectRelativeTargetDocumentPath()
                        )
                        .metadata(
                                "dryRun",
                                Boolean.toString(
                                        request.isDryRun()
                                )
                        );

        for (AgentToolCallOutcome toolCall
                : outcome.getToolCalls()) {

            builder.toolExecution(
                    new AccessibilityAgentToolExecution(
                            toolCall.getToolName(),
                            toolCall.isSuccessful(),
                            toolCall.getMessage(),
                            toolCall.getDuration(),
                            toolCall.getData()
                    )
            );
        }

        for (String warning : outcome.getWarnings()) {
            builder.warning(warning);
        }

        for (String document
                : outcome.getModifiedDocuments()) {

            builder.modifiedDocument(document);
        }

        return builder.build();
    }

    private AccessibilityAgentStatus resolveStatus(
            AgentExecutionOutcome outcome) {

        if (outcome.isLimitReached()) {
            return AccessibilityAgentStatus.LIMIT_REACHED;
        }

        if (!outcome.isSuccessful()) {
            return AccessibilityAgentStatus.FAILED;
        }

        boolean failedToolExists =
                outcome.getToolCalls()
                        .stream()
                        .anyMatch(
                                toolCall ->
                                        !toolCall.isSuccessful()
                        );

        if (failedToolExists) {
            return AccessibilityAgentStatus
                    .PARTIALLY_COMPLETED;
        }

        if (outcome.isManualReviewRequired()) {
            return AccessibilityAgentStatus
                    .REVIEW_REQUIRED;
        }

        return AccessibilityAgentStatus.COMPLETED;
    }

    private String resolveResponse(
            AgentExecutionOutcome outcome) {

        String response =
                normalizeOptionalText(
                        outcome.getResponse()
                );

        if (response != null) {
            return response;
        }

        if (outcome.isSuccessful()) {
            return "Accessibility agent execution completed.";
        }

        return "Accessibility agent execution failed.";
    }

    private Path resolveProjectPath(
            Path projectRoot,
            String pathValue) {

        Path root =
                projectRoot
                        .toAbsolutePath()
                        .normalize();

        Path path =
                Path.of(pathValue);

        if (path.isAbsolute()) {
            return path
                    .toAbsolutePath()
                    .normalize();
        }

        return root.resolve(path)
                .toAbsolutePath()
                .normalize();
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
     * 기존 Agent Framework 실행기를 접근성 Agent에 연결하는 Bridge.
     *
     * <p>구현체는 일반적으로 내부에서 {@code DefaultAgentExecutor}를
     * 호출하고 결과를 {@link AgentExecutionOutcome}으로 변환한다.</p>
     */
    @FunctionalInterface
    public interface AgentExecutionBridge {

        /**
         * 접근성 Agent 요청을 기존 Agent Framework로 실행한다.
         *
         * @param request 변환된 실행 요청
         * @return 실행 결과
         */
        AgentExecutionOutcome execute(
                AccessibilityAgentExecutionRequest request);
    }

    /**
     * 기존 Agent Framework에 전달되는 접근성 실행 요청.
     */
    public static final class
            AccessibilityAgentExecutionRequest {

        private final String agentName;
        private final String model;
        private final String systemPrompt;
        private final String instruction;

        private final Path projectRoot;
        private final Path targetDocumentPath;

        private final Set<String> allowedToolNames;

        private final int maxIterations;
        private final int maxToolCalls;
        private final Duration executionTimeout;

        private final boolean allowModification;
        private final boolean dryRun;
        private final boolean createBackup;
        private final boolean overwriteExistingAlt;
        private final boolean includeInformationalIssues;

        private final boolean requireValidationBeforeModification;
        private final boolean requireValidationAfterModification;
        private final boolean requireUniqueImageTarget;
        private final double minimumAnalysisConfidence;
        private final boolean stopOnToolFailure;

        private final Map<String, String> metadata;

        private AccessibilityAgentExecutionRequest(
                Builder builder) {

            this.agentName = normalizeRequiredText(
                    builder.agentName,
                    "agentName"
            );

            this.model = normalizeRequiredText(
                    builder.model,
                    "model"
            );

            this.systemPrompt = normalizeRequiredText(
                    builder.systemPrompt,
                    "systemPrompt"
            );

            this.instruction = normalizeRequiredText(
                    builder.instruction,
                    "instruction"
            );

            this.projectRoot = Objects.requireNonNull(
                    builder.projectRoot,
                    "projectRoot must not be null"
            ).toAbsolutePath().normalize();

            this.targetDocumentPath =
                    builder.targetDocumentPath == null
                            ? null
                            : builder.targetDocumentPath
                                    .toAbsolutePath()
                                    .normalize();

            this.allowedToolNames =
                    immutableStrings(
                            builder.allowedToolNames
                    );

            this.maxIterations =
                    validatePositive(
                            builder.maxIterations,
                            "maxIterations"
                    );

            this.maxToolCalls =
                    validatePositive(
                            builder.maxToolCalls,
                            "maxToolCalls"
                    );

            this.executionTimeout =
                    validateDuration(
                            builder.executionTimeout
                    );

            this.allowModification =
                    builder.allowModification;

            this.dryRun =
                    builder.dryRun;

            this.createBackup =
                    builder.createBackup;

            this.overwriteExistingAlt =
                    builder.overwriteExistingAlt;

            this.includeInformationalIssues =
                    builder.includeInformationalIssues;

            this.requireValidationBeforeModification =
                    builder.requireValidationBeforeModification;

            this.requireValidationAfterModification =
                    builder.requireValidationAfterModification;

            this.requireUniqueImageTarget =
                    builder.requireUniqueImageTarget;

            this.minimumAnalysisConfidence =
                    validateConfidence(
                            builder.minimumAnalysisConfidence
                    );

            this.stopOnToolFailure =
                    builder.stopOnToolFailure;

            this.metadata =
                    immutableMetadata(
                            builder.metadata
                    );
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

        public String getInstruction() {
            return instruction;
        }

        public Path getProjectRoot() {
            return projectRoot;
        }

        public Path getTargetDocumentPath() {
            return targetDocumentPath;
        }

        public Set<String> getAllowedToolNames() {
            return allowedToolNames;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public Duration getExecutionTimeout() {
            return executionTimeout;
        }

        public boolean isAllowModification() {
            return allowModification;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public boolean isCreateBackup() {
            return createBackup;
        }

        public boolean isOverwriteExistingAlt() {
            return overwriteExistingAlt;
        }

        public boolean isIncludeInformationalIssues() {
            return includeInformationalIssues;
        }

        public boolean isRequireValidationBeforeModification() {
            return requireValidationBeforeModification;
        }

        public boolean isRequireValidationAfterModification() {
            return requireValidationAfterModification;
        }

        public boolean isRequireUniqueImageTarget() {
            return requireUniqueImageTarget;
        }

        public double getMinimumAnalysisConfidence() {
            return minimumAnalysisConfidence;
        }

        public boolean isStopOnToolFailure() {
            return stopOnToolFailure;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public static Builder builder() {
            return new Builder();
        }

        private static int validatePositive(
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

        private static Set<String> immutableStrings(
                Set<String> source) {

            if (source == null || source.isEmpty()) {
                return Collections.emptySet();
            }

            Set<String> result =
                    new LinkedHashSet<>();

            for (String value : source) {
                String normalized =
                        normalizeOptionalText(value);

                if (normalized != null) {
                    result.add(normalized);
                }
            }

            return Collections.unmodifiableSet(result);
        }

        private static Map<String, String> immutableMetadata(
                Map<String, String> source) {

            if (source == null || source.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, String> result =
                    new LinkedHashMap<>();

            for (Map.Entry<String, String> entry
                    : source.entrySet()) {

                String key =
                        normalizeOptionalText(
                                entry.getKey()
                        );

                String value =
                        normalizeOptionalText(
                                entry.getValue()
                        );

                if (key != null && value != null) {
                    result.put(key, value);
                }
            }

            return Collections.unmodifiableMap(result);
        }

        public static final class Builder {

            private String agentName;
            private String model;
            private String systemPrompt;
            private String instruction;

            private Path projectRoot;
            private Path targetDocumentPath;

            private final Set<String> allowedToolNames =
                    new LinkedHashSet<>();

            private int maxIterations;
            private int maxToolCalls;
            private Duration executionTimeout;

            private boolean allowModification;
            private boolean dryRun;
            private boolean createBackup;
            private boolean overwriteExistingAlt;
            private boolean includeInformationalIssues;

            private boolean requireValidationBeforeModification;
            private boolean requireValidationAfterModification;
            private boolean requireUniqueImageTarget;
            private double minimumAnalysisConfidence;
            private boolean stopOnToolFailure;

            private final Map<String, String> metadata =
                    new LinkedHashMap<>();

            private Builder() {
            }

            public Builder agentName(String value) {
                this.agentName = value;
                return this;
            }

            public Builder model(String value) {
                this.model = value;
                return this;
            }

            public Builder systemPrompt(String value) {
                this.systemPrompt = value;
                return this;
            }

            public Builder instruction(String value) {
                this.instruction = value;
                return this;
            }

            public Builder projectRoot(Path value) {
                this.projectRoot = value;
                return this;
            }

            public Builder targetDocumentPath(Path value) {
                this.targetDocumentPath = value;
                return this;
            }

            public Builder allowedToolNames(
                    Set<String> values) {

                if (values != null) {
                    this.allowedToolNames.addAll(values);
                }

                return this;
            }

            public Builder maxIterations(int value) {
                this.maxIterations = value;
                return this;
            }

            public Builder maxToolCalls(int value) {
                this.maxToolCalls = value;
                return this;
            }

            public Builder executionTimeout(Duration value) {
                this.executionTimeout = value;
                return this;
            }

            public Builder allowModification(boolean value) {
                this.allowModification = value;
                return this;
            }

            public Builder dryRun(boolean value) {
                this.dryRun = value;
                return this;
            }

            public Builder createBackup(boolean value) {
                this.createBackup = value;
                return this;
            }

            public Builder overwriteExistingAlt(boolean value) {
                this.overwriteExistingAlt = value;
                return this;
            }

            public Builder includeInformationalIssues(
                    boolean value) {

                this.includeInformationalIssues = value;
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

            public Builder minimumAnalysisConfidence(
                    double value) {

                this.minimumAnalysisConfidence = value;
                return this;
            }

            public Builder stopOnToolFailure(boolean value) {
                this.stopOnToolFailure = value;
                return this;
            }

            public Builder metadata(
                    Map<String, String> values) {

                if (values != null) {
                    this.metadata.putAll(values);
                }

                return this;
            }

            public AccessibilityAgentExecutionRequest build() {
                return new AccessibilityAgentExecutionRequest(this);
            }
        }
    }

    /**
     * 기존 Agent Framework의 전체 실행 결과를 나타낸다.
     */
    public static final class AgentExecutionOutcome {

        private final boolean successful;
        private final boolean limitReached;
        private final boolean fileModified;
        private final boolean manualReviewRequired;

        private final String response;
        private final int iterationCount;
        private final int toolCallCount;

        private final List<AgentToolCallOutcome> toolCalls;
        private final List<String> warnings;
        private final List<String> modifiedDocuments;

        private AgentExecutionOutcome(Builder builder) {

            this.successful = builder.successful;
            this.limitReached = builder.limitReached;
            this.fileModified = builder.fileModified;
            this.manualReviewRequired =
                    builder.manualReviewRequired;

            this.response =
                    normalizeOptionalText(
                            builder.response
                    );

            this.iterationCount =
                    validateNonNegative(
                            builder.iterationCount,
                            "iterationCount"
                    );

            this.toolCallCount =
                    validateNonNegative(
                            builder.toolCallCount,
                            "toolCallCount"
                    );

            this.toolCalls =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    builder.toolCalls
                            )
                    );

            this.warnings =
                    immutableStringList(
                            builder.warnings
                    );

            this.modifiedDocuments =
                    immutableStringList(
                            builder.modifiedDocuments
                    );
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean isLimitReached() {
            return limitReached;
        }

        public boolean isFileModified() {
            return fileModified;
        }

        public boolean isManualReviewRequired() {
            return manualReviewRequired;
        }

        public String getResponse() {
            return response;
        }

        public int getIterationCount() {
            return iterationCount;
        }

        public int getToolCallCount() {
            return toolCallCount;
        }

        public List<AgentToolCallOutcome> getToolCalls() {
            return toolCalls;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getModifiedDocuments() {
            return modifiedDocuments;
        }

        public static Builder builder() {
            return new Builder();
        }

        private static int validateNonNegative(
                int value,
                String fieldName) {

            if (value < 0) {
                throw new IllegalArgumentException(
                        fieldName + " must not be negative"
                );
            }

            return value;
        }

        private static List<String> immutableStringList(
                List<String> source) {

            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> result =
                    new ArrayList<>();

            for (String value : source) {
                String normalized =
                        normalizeOptionalText(value);

                if (normalized != null
                        && !result.contains(normalized)) {

                    result.add(normalized);
                }
            }

            return Collections.unmodifiableList(result);
        }

        public static final class Builder {

            private boolean successful;
            private boolean limitReached;
            private boolean fileModified;
            private boolean manualReviewRequired;

            private String response;
            private int iterationCount;
            private int toolCallCount;

            private final List<AgentToolCallOutcome>
                    toolCalls = new ArrayList<>();

            private final List<String>
                    warnings = new ArrayList<>();

            private final List<String>
                    modifiedDocuments = new ArrayList<>();

            private Builder() {
            }

            public Builder successful(boolean value) {
                this.successful = value;
                return this;
            }

            public Builder limitReached(boolean value) {
                this.limitReached = value;
                return this;
            }

            public Builder fileModified(boolean value) {
                this.fileModified = value;
                return this;
            }

            public Builder manualReviewRequired(
                    boolean value) {

                this.manualReviewRequired = value;
                return this;
            }

            public Builder response(String value) {
                this.response = value;
                return this;
            }

            public Builder iterationCount(int value) {
                this.iterationCount = value;
                return this;
            }

            public Builder toolCallCount(int value) {
                this.toolCallCount = value;
                return this;
            }

            public Builder toolCall(
                    AgentToolCallOutcome value) {

                if (value != null) {
                    this.toolCalls.add(value);
                }

                return this;
            }

            public Builder warning(String value) {

                if (value != null) {
                    this.warnings.add(value);
                }

                return this;
            }

            public Builder modifiedDocument(
                    String value) {

                if (value != null) {
                    this.modifiedDocuments.add(value);
                }

                return this;
            }

            public AgentExecutionOutcome build() {
                return new AgentExecutionOutcome(this);
            }
        }
    }

    /**
     * 기존 Agent Framework에서 실행된 개별 Tool 호출 결과.
     */
    public static final class AgentToolCallOutcome {

        private final String toolName;
        private final boolean successful;
        private final boolean fileModified;
        private final String message;
        private final Duration duration;
        private final Map<String, Object> data;

        public AgentToolCallOutcome(
                String toolName,
                boolean successful,
                boolean fileModified,
                String message,
                Duration duration,
                Map<String, Object> data) {

            this.toolName = normalizeRequiredText(
                    toolName,
                    "toolName"
            );

            this.successful = successful;
            this.fileModified = fileModified;

            this.message =
                    normalizeOptionalText(message);

            if (duration != null
                    && duration.isNegative()) {

                throw new IllegalArgumentException(
                        "duration must not be negative"
                );
            }

            this.duration = duration;

            this.data = data == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(
                            new LinkedHashMap<>(data)
                    );
        }

        public String getToolName() {
            return toolName;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean isFileModified() {
            return fileModified;
        }

        public String getMessage() {
            return message;
        }

        public Duration getDuration() {
            return duration;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}