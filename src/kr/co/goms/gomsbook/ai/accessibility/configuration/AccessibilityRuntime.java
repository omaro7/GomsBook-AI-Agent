/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import kr.co.goms.gomsbook.ai.accessibility.agent.AccessibilityAgent;
import kr.co.goms.gomsbook.ai.accessibility.agent.AccessibilityAgentConfiguration;
import kr.co.goms.gomsbook.ai.accessibility.analysis.ImageAnalyzer;
import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicator;
import kr.co.goms.gomsbook.ai.accessibility.configuration.AccessibilityComponentFactory.AccessibilityComponents;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityRule;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolRegistry;
import kr.co.goms.gomsbook.ai.tools.accessibility.ValidateAccessibilityTool;
import kr.co.goms.gomsbook.ai.tools.image.AnalyzeImageTool;
import kr.co.goms.gomsbook.ai.tools.image.ApplyAltTextTool;

/**
 * 접근성 계층의 구성요소를 애플리케이션 실행 중 보관하고 제공하는
 * 런타임 컨테이너.
 *
 * <p>{@link AccessibilityComponentFactory}가 생성한 구성요소를
 * 하나의 런타임 단위로 관리하며 다음 기능을 제공한다.</p>
 *
 * <ul>
 *   <li>접근성 Agent 제공</li>
 *   <li>접근성 Validator 제공</li>
 *   <li>이미지 분석기 제공</li>
 *   <li>대체 텍스트 적용기 제공</li>
 *   <li>접근성 Tool 조회</li>
 *   <li>ToolRegistry 등록</li>
 *   <li>런타임 시작 및 종료 상태 관리</li>
 * </ul>
 *
 * <p>기본적으로 생성 이후 구성요소는 변경되지 않는다. 런타임을
 * 교체해야 하는 경우에는 새로운 {@code AccessibilityRuntime}을
 * 생성하여 애플리케이션 참조를 교체하는 방식이 권장된다.</p>
 */
public final class AccessibilityRuntime
        implements AutoCloseable {

    private final AccessibilityComponents components;

    private final Map<String, AgentTool> toolsByName;

    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    /**
     * 접근성 구성요소로 런타임을 생성한다.
     *
     * @param components 접근성 구성요소 묶음
     */
    public AccessibilityRuntime(
            AccessibilityComponents components) {

        this.components = Objects.requireNonNull(
                components,
                "components must not be null"
        );

        this.toolsByName = createToolMap(
                components.getAgentTools()
        );

        validateComponents();
    }

    /**
     * Factory를 이용하여 접근성 런타임을 생성한다.
     *
     * @param factory 접근성 구성요소 Factory
     * @return 생성된 접근성 런타임
     */
    public static AccessibilityRuntime create(
            AccessibilityComponentFactory factory) {

        Objects.requireNonNull(
                factory,
                "factory must not be null"
        );

        return new AccessibilityRuntime(
                factory.create()
        );
    }

    /**
     * 이미 생성된 구성요소로 런타임을 생성한다.
     *
     * @param components 접근성 구성요소
     * @return 접근성 런타임
     */
    public static AccessibilityRuntime of(
            AccessibilityComponents components) {

        return new AccessibilityRuntime(components);
    }

    /**
     * 런타임을 시작 상태로 전환한다.
     *
     * <p>현재 구현에서 구성요소는 지연 초기화 자원을 가지지 않으므로
     * 별도의 네트워크 연결은 수행하지 않는다. 다만 애플리케이션
     * 생명주기와 상태 검증을 일관되게 관리하기 위해 명시적인
     * 시작 단계를 제공한다.</p>
     *
     * @throws IllegalStateException 이미 종료된 런타임인 경우
     */
    public void start() {

        ensureNotClosed();

        started.compareAndSet(
                false,
                true
        );
    }

    /**
     * 런타임이 시작되었는지 반환한다.
     *
     * @return 시작 상태이면 {@code true}
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * 런타임이 종료되었는지 반환한다.
     *
     * @return 종료 상태이면 {@code true}
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 런타임을 사용할 수 있는 상태인지 반환한다.
     *
     * @return 시작되었고 종료되지 않았으면 {@code true}
     */
    public boolean isAvailable() {
        return started.get()
                && !closed.get();
    }

    /**
     * 접근성 구성요소 묶음을 반환한다.
     *
     * @return 접근성 구성요소
     */
    public AccessibilityComponents getComponents() {

        ensureNotClosed();

        return components;
    }

    /**
     * 접근성 Agent를 반환한다.
     *
     * @return 접근성 Agent
     */
    public AccessibilityAgent getAccessibilityAgent() {

        ensureAvailable();

        return components.getAccessibilityAgent();
    }

    /**
     * 접근성 Agent 구성을 반환한다.
     *
     * @return 접근성 Agent 구성
     */
    public AccessibilityAgentConfiguration
            getAgentConfiguration() {

        ensureNotClosed();

        return components.getAgentConfiguration();
    }

    /**
     * 접근성 Validator를 반환한다.
     *
     * @return 접근성 Validator
     */
    public AccessibilityValidator
            getAccessibilityValidator() {

        ensureAvailable();

        return components.getValidator();
    }

    /**
     * 이미지 분석기를 반환한다.
     *
     * @return 이미지 분석기
     */
    public ImageAnalyzer getImageAnalyzer() {

        ensureAvailable();

        return components.getImageAnalyzer();
    }

    /**
     * 대체 텍스트 적용기를 반환한다.
     *
     * @return 대체 텍스트 적용기
     */
    public AltTextApplicator getAltTextApplicator() {

        ensureAvailable();

        return components.getAltTextApplicator();
    }

    /**
     * 등록된 접근성 검사 규칙 목록을 반환한다.
     *
     * @return 수정할 수 없는 규칙 목록
     */
    public List<AccessibilityRule> getRules() {

        ensureNotClosed();

        return components.getRules();
    }

    /**
     * 접근성 Agent Tool 목록을 반환한다.
     *
     * @return 수정할 수 없는 Tool 목록
     */
    public List<AgentTool> getTools() {

        ensureNotClosed();

        return components.getAgentTools();
    }

    /**
     * Tool 이름별 접근성 Tool 맵을 반환한다.
     *
     * @return 수정할 수 없는 Tool 맵
     */
    public Map<String, AgentTool> getToolsByName() {

        ensureNotClosed();

        return toolsByName;
    }

    /**
     * 지정한 이름의 접근성 Tool을 반환한다.
     *
     * @param toolName Tool 이름
     * @return Tool, 없으면 빈 Optional
     */
    public Optional<AgentTool> findTool(
            String toolName) {

        ensureNotClosed();

        String normalized =
                normalizeOptionalText(toolName);

        if (normalized == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                toolsByName.get(normalized)
        );
    }

    /**
     * 지정한 이름의 접근성 Tool을 반환한다.
     *
     * @param toolName Tool 이름
     * @return 등록된 Tool
     * @throws IllegalArgumentException 등록되지 않은 Tool인 경우
     */
    public AgentTool requireTool(
            String toolName) {

        return findTool(toolName)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Accessibility Tool is not registered: "
                                        + toolName
                        )
                );
    }

    /**
     * 접근성 검사 Tool을 반환한다.
     *
     * @return 접근성 검사 Tool
     */
    public ValidateAccessibilityTool
            getValidateAccessibilityTool() {

        ensureAvailable();

        return components
                .getValidateAccessibilityTool();
    }

    /**
     * 이미지 분석 Tool을 반환한다.
     *
     * @return 이미지 분석 Tool
     */
    public AnalyzeImageTool getAnalyzeImageTool() {

        ensureAvailable();

        return components.getAnalyzeImageTool();
    }

    /**
     * 대체 텍스트 적용 Tool을 반환한다.
     *
     * @return 대체 텍스트 적용 Tool
     */
    public ApplyAltTextTool getApplyAltTextTool() {

        ensureAvailable();

        return components.getApplyAltTextTool();
    }

    /**
     * 접근성 Tool을 ToolRegistry에 등록한다.
     *
     * <p>런타임이 시작된 이후 호출해야 한다.</p>
     *
     * @param toolRegistry Tool Registry
     */
    public void registerTools(
            ToolRegistry toolRegistry) {

        ensureAvailable();

        Objects.requireNonNull(
                toolRegistry,
                "toolRegistry must not be null"
        );

        for (AgentTool tool
                : components.getAgentTools()) {

            toolRegistry.register(tool);
        }
    }

    /**
     * 접근성 Tool을 ToolRegistry에 등록하고 현재 런타임을 반환한다.
     *
     * <p>런타임 생성 코드를 연속 호출 방식으로 구성할 때 사용할 수 있다.</p>
     *
     * @param toolRegistry Tool Registry
     * @return 현재 런타임
     */
    public AccessibilityRuntime register(
            ToolRegistry toolRegistry) {

        registerTools(toolRegistry);
        return this;
    }

    /**
     * 접근성 Agent가 지정한 Tool을 호출할 수 있는지 반환한다.
     *
     * @param toolName Tool 이름
     * @return 허용된 Tool이면 {@code true}
     */
    public boolean isToolAllowed(
            String toolName) {

        ensureNotClosed();

        return components
                .getAgentConfiguration()
                .isToolAllowed(toolName);
    }

    /**
     * 접근성 Agent의 자동 수정 허용 여부를 반환한다.
     *
     * @return 자동 수정 허용 여부
     */
    public boolean isAutomaticModificationAllowed() {

        ensureNotClosed();

        return components
                .getAgentConfiguration()
                .isAllowAutomaticModification();
    }

    /**
     * 접근성 Agent에서 사용하는 최소 이미지 분석 신뢰도를 반환한다.
     *
     * @return 최소 분석 신뢰도
     */
    public double getMinimumAnalysisConfidence() {

        ensureNotClosed();

        return components
                .getAgentConfiguration()
                .getMinimumAnalysisConfidence();
    }

    /**
     * 런타임 상태 정보를 반환한다.
     *
     * @return 런타임 상태 스냅샷
     */
    public AccessibilityRuntimeStatus getStatus() {

        return new AccessibilityRuntimeStatus(
                started.get(),
                closed.get(),
                components
                        .getAgentConfiguration()
                        .getAgentName(),
                components
                        .getAgentConfiguration()
                        .getModel(),
                components.getRules().size(),
                toolsByName.size(),
                components
                        .getAgentConfiguration()
                        .isAllowAutomaticModification(),
                components
                        .getAgentConfiguration()
                        .getMinimumAnalysisConfidence()
        );
    }

    /**
     * 런타임을 종료한다.
     *
     * <p>현재 기본 구성요소는 별도의 종료 처리가 필요하지 않지만,
     * 사용자 정의 구성요소가 {@link AutoCloseable}을 구현하는 경우
     * 종료를 시도한다.</p>
     */
    @Override
    public void close() {

        if (!closed.compareAndSet(
                false,
                true)) {

            return;
        }

        started.set(false);

        List<Throwable> failures =
                new java.util.ArrayList<>();

        closeIfNecessary(
                components.getAccessibilityAgent(),
                failures
        );

        closeIfNecessary(
                components.getAltTextApplicator(),
                failures
        );

        closeIfNecessary(
                components.getImageAnalyzer(),
                failures
        );

        closeIfNecessary(
                components.getValidator(),
                failures
        );

        if (!failures.isEmpty()) {
            IllegalStateException exception =
                    new IllegalStateException(
                            "One or more accessibility runtime "
                                    + "components failed to close."
                    );

            for (Throwable failure : failures) {
                exception.addSuppressed(failure);
            }

            throw exception;
        }
    }

    private void validateComponents() {

        requireComponent(
                components.getAccessibilityAgent(),
                "accessibilityAgent"
        );

        requireComponent(
                components.getAgentConfiguration(),
                "agentConfiguration"
        );

        requireComponent(
                components.getValidator(),
                "validator"
        );

        requireComponent(
                components.getImageAnalyzer(),
                "imageAnalyzer"
        );

        requireComponent(
                components.getAltTextApplicator(),
                "altTextApplicator"
        );

        requireToolRegistration(
                ValidateAccessibilityTool.TOOL_NAME
        );

        requireToolRegistration(
                AnalyzeImageTool.TOOL_NAME
        );

        requireToolRegistration(
                ApplyAltTextTool.TOOL_NAME
        );

        for (String allowedToolName
                : components
                        .getAgentConfiguration()
                        .getAllowedToolNames()) {

            if (!toolsByName.containsKey(
                    allowedToolName)) {

                throw new IllegalArgumentException(
                        "Agent configuration allows an "
                                + "unregistered Tool: "
                                + allowedToolName
                );
            }
        }
    }

    private void requireToolRegistration(
            String toolName) {

        if (!toolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException(
                    "Required accessibility Tool is missing: "
                            + toolName
            );
        }
    }

    private void requireComponent(
            Object component,
            String componentName) {

        if (component == null) {
            throw new IllegalArgumentException(
                    componentName + " must not be null"
            );
        }
    }

    private void ensureAvailable() {

        ensureNotClosed();

        if (!started.get()) {
            throw new IllegalStateException(
                    "AccessibilityRuntime has not been started."
            );
        }
    }

    private void ensureNotClosed() {

        if (closed.get()) {
            throw new IllegalStateException(
                    "AccessibilityRuntime is already closed."
            );
        }
    }

    private void closeIfNecessary(
            Object component,
            List<Throwable> failures) {

        if (!(component instanceof AutoCloseable closeable)) {
            return;
        }

        try {
            closeable.close();

        } catch (Throwable exception) {
            failures.add(exception);
        }
    }

    private static Map<String, AgentTool> createToolMap(
            List<? extends AgentTool> tools) {

        if (tools == null || tools.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, AgentTool> result =
                new LinkedHashMap<>();

        for (AgentTool tool : tools) {

            if (tool == null) {
                continue;
            }

            String toolName =
                    normalizeRequiredText(
                            tool.getName(),
                            "tool.name"
                    );

            AgentTool previous =
                    result.putIfAbsent(
                            toolName,
                            tool
                    );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate accessibility Tool name: "
                                + toolName
                );
            }
        }

        return Collections.unmodifiableMap(result);
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
     * 접근성 런타임 상태 스냅샷.
     */
    public static final class AccessibilityRuntimeStatus {

        private final boolean started;
        private final boolean closed;
        private final String agentName;
        private final String model;
        private final int ruleCount;
        private final int toolCount;
        private final boolean automaticModificationAllowed;
        private final double minimumAnalysisConfidence;

        private AccessibilityRuntimeStatus(
                boolean started,
                boolean closed,
                String agentName,
                String model,
                int ruleCount,
                int toolCount,
                boolean automaticModificationAllowed,
                double minimumAnalysisConfidence) {

            this.started = started;
            this.closed = closed;
            this.agentName = agentName;
            this.model = model;
            this.ruleCount = ruleCount;
            this.toolCount = toolCount;
            this.automaticModificationAllowed =
                    automaticModificationAllowed;
            this.minimumAnalysisConfidence =
                    minimumAnalysisConfidence;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isClosed() {
            return closed;
        }

        public boolean isAvailable() {
            return started && !closed;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getModel() {
            return model;
        }

        public int getRuleCount() {
            return ruleCount;
        }

        public int getToolCount() {
            return toolCount;
        }

        public boolean isAutomaticModificationAllowed() {
            return automaticModificationAllowed;
        }

        public double getMinimumAnalysisConfidence() {
            return minimumAnalysisConfidence;
        }

        @Override
        public String toString() {

            return "AccessibilityRuntimeStatus{"
                    + "started=" + started
                    + ", closed=" + closed
                    + ", agentName='" + agentName + '\''
                    + ", model='" + model + '\''
                    + ", ruleCount=" + ruleCount
                    + ", toolCount=" + toolCount
                    + ", automaticModificationAllowed="
                    + automaticModificationAllowed
                    + ", minimumAnalysisConfidence="
                    + minimumAnalysisConfidence
                    + '}';
        }
    }

    /**
     * 애플리케이션 전역에서 접근성 런타임을 보관해야 할 때 사용할 수 있는
     * 단일 런타임 참조 관리자.
     *
     * <p>의존성 주입 컨테이너를 사용하는 환경이라면 이 Holder 대신
     * 애플리케이션 서비스에 {@link AccessibilityRuntime}을 직접
     * 주입하는 방식을 권장한다.</p>
     */
    public static final class Holder {

        private static final AtomicReference<AccessibilityRuntime>
                INSTANCE = new AtomicReference<>();

        private Holder() {
            throw new AssertionError(
                    "AccessibilityRuntime.Holder "
                            + "must not be instantiated."
            );
        }

        /**
         * 전역 접근성 런타임을 설정한다.
         *
         * @param runtime 접근성 런타임
         * @throws IllegalStateException 이미 설정된 경우
         */
        public static void initialize(
                AccessibilityRuntime runtime) {

            Objects.requireNonNull(
                    runtime,
                    "runtime must not be null"
            );

            if (!runtime.isStarted()) {
                runtime.start();
            }

            if (!INSTANCE.compareAndSet(
                    null,
                    runtime)) {

                throw new IllegalStateException(
                        "AccessibilityRuntime is already initialized."
                );
            }
        }

        /**
         * 설정된 접근성 런타임을 반환한다.
         *
         * @return 접근성 런타임
         * @throws IllegalStateException 초기화되지 않은 경우
         */
        public static AccessibilityRuntime get() {

            AccessibilityRuntime runtime =
                    INSTANCE.get();

            if (runtime == null) {
                throw new IllegalStateException(
                        "AccessibilityRuntime is not initialized."
                );
            }

            return runtime;
        }

        /**
         * 접근성 런타임이 초기화되었는지 반환한다.
         *
         * @return 초기화 여부
         */
        public static boolean isInitialized() {
            return INSTANCE.get() != null;
        }

        /**
         * 전역 런타임 참조를 제거하고 런타임을 종료한다.
         */
        public static void shutdown() {

            AccessibilityRuntime runtime =
                    INSTANCE.getAndSet(null);

            if (runtime != null) {
                runtime.close();
            }
        }
    }
}