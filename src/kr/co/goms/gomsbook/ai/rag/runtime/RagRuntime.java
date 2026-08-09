/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.rag.config.RagComponentFactory;
import kr.co.goms.gomsbook.ai.rag.config.RagComponents;
import kr.co.goms.gomsbook.ai.rag.config.RagConfiguration;
import kr.co.goms.gomsbook.ai.rag.document.DocumentLoader;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.index.DocumentIndexer;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexException;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexRequest;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexResult;
import kr.co.goms.gomsbook.ai.rag.model.DocumentSource;
import kr.co.goms.gomsbook.ai.rag.pipeline.RagPipeline;
import kr.co.goms.gomsbook.ai.rag.pipeline.RagPipelineException;
import kr.co.goms.gomsbook.ai.rag.pipeline.RagPipelineResponse;
import kr.co.goms.gomsbook.ai.rag.retrieval.RetrievalRequest;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStoreException;

/**
 * GomsBook Editor에서 RAG 구성요소의 생성, 접근 및 종료를 관리하는
 * 런타임 클래스입니다.
 *
 * <p>애플리케이션 또는 플러그인 실행 중 하나의 인스턴스를 생성하여
 * 공유하는 것을 권장합니다.</p>
 *
 * <pre>
 * RagRuntime
 *   ├─ RagConfiguration
 *   ├─ RagComponents
 *   ├─ DocumentLoader
 *   ├─ RagIndexer
 *   └─ RagPipeline
 * </pre>
 */
public final class RagRuntime implements AutoCloseable {

    /**
     * 기본 전역 런타임 참조입니다.
     *
     * Eclipse RCP에서 OSGi 서비스로 관리하지 않는 경우 사용할 수 있습니다.
     */
    private static final AtomicReference<RagRuntime> DEFAULT_INSTANCE =
        new AtomicReference<>();

    private final RagConfiguration configuration;
    private final DocumentLoader documentLoader;
    private final RagComponents components;

    /**
     * 프로젝트 전환, 인덱싱 및 종료 작업 간 충돌을 방지합니다.
     */
    private final ReentrantReadWriteLock lifecycleLock =
        new ReentrantReadWriteLock();

    private volatile RagRuntimeState state =
        RagRuntimeState.CREATED;

    private volatile Path projectRoot;

    private RagRuntime(Builder builder) {
        this.configuration =
            Objects.requireNonNullElseGet(
                builder.configuration,
                RagConfiguration::defaults
            );

        this.documentLoader = Objects.requireNonNull(
            builder.documentLoader,
            "documentLoader must not be null"
        );

        DocumentIndexer documentIndexer =
            Objects.requireNonNull(
                builder.documentIndexer,
                "documentIndexer must not be null"
            );

        EmbeddingClient embeddingClient =
            Objects.requireNonNull(
                builder.embeddingClient,
                "embeddingClient must not be null"
            );

        LlmClient llmClient =
            Objects.requireNonNull(
                builder.llmClient,
                "llmClient must not be null"
            );

        RagComponentFactory.Builder factoryBuilder =
            RagComponentFactory.builder()
                .configuration(configuration)
                .documentIndexer(documentIndexer)
                .embeddingClient(embeddingClient)
                .llmClient(llmClient);

        if (builder.vectorStore != null) {
            factoryBuilder.vectorStore(
                builder.vectorStore
            );
        }

        if (builder.pipelineSystemPrompt != null) {
            factoryBuilder.pipelineSystemPrompt(
                builder.pipelineSystemPrompt
            );
        }

        if (builder.augmentorSystemInstruction != null) {
            factoryBuilder.augmentorSystemInstruction(
                builder.augmentorSystemInstruction
            );
        }

        if (builder.responseInstruction != null) {
            factoryBuilder.responseInstruction(
                builder.responseInstruction
            );
        }

        this.components =
            factoryBuilder
                .build()
                .create();

        if (builder.projectRoot != null) {
            this.projectRoot =
                normalizeProjectRoot(
                    builder.projectRoot
                );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 전역 런타임을 등록합니다.
     *
     * @param runtime 등록할 런타임
     * @throws IllegalStateException 이미 등록된 런타임이 있는 경우
     */
    public static void installDefault(
        RagRuntime runtime
    ) {
        Objects.requireNonNull(
            runtime,
            "runtime must not be null"
        );

        if (!DEFAULT_INSTANCE.compareAndSet(
            null,
            runtime
        )) {
            throw new IllegalStateException(
                "Default RagRuntime is already installed"
            );
        }
    }

    /**
     * 기본 전역 런타임을 반환합니다.
     *
     * @return 기본 런타임
     * @throws IllegalStateException 초기화되지 않은 경우
     */
    public static RagRuntime getDefault() {
        RagRuntime runtime =
            DEFAULT_INSTANCE.get();

        if (runtime == null) {
            throw new IllegalStateException(
                "Default RagRuntime is not installed"
            );
        }

        return runtime;
    }

    public static boolean hasDefault() {
        return DEFAULT_INSTANCE.get() != null;
    }

    /**
     * 기본 런타임을 해제하고 종료합니다.
     */
    public static void closeDefault()
        throws RagRuntimeException {

        RagRuntime runtime =
            DEFAULT_INSTANCE.getAndSet(null);

        if (runtime != null) {
            runtime.close();
        }
    }

    /**
     * 런타임을 시작합니다.
     *
     * <p>구성요소의 사용 가능 여부를 확인하고 실행 상태로 전환합니다.</p>
     */
    public void start()
        throws RagRuntimeException {

        lifecycleLock.writeLock().lock();

        try {
            if (state == RagRuntimeState.RUNNING) {
                return;
            }

            if (state == RagRuntimeState.CLOSED) {
                throw new RagRuntimeException(
                    "RagRuntime is already closed",
                    RagRuntimeOperation.START,
                    state
                );
            }

            state = RagRuntimeState.STARTING;

            if (!components.isAvailable()) {
                state = RagRuntimeState.FAILED;

                throw new RagRuntimeException(
                    "RAG components are not available",
                    RagRuntimeOperation.START,
                    state
                );
            }

            state = RagRuntimeState.RUNNING;

        } catch (RagRuntimeException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            state = RagRuntimeState.FAILED;

            throw new RagRuntimeException(
                "Failed to start RagRuntime",
                RagRuntimeOperation.START,
                state,
                exception
            );

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    /**
     * 현재 EPUB 프로젝트 루트를 설정합니다.
     *
     * <p>프로젝트가 변경되면 메모리 VectorStore의 기존 인덱스를
     * 자동으로 제거할 수 있습니다.</p>
     *
     * @param newProjectRoot 새 프로젝트 루트
     * @param clearExistingIndex 기존 인덱스 초기화 여부
     */
    public void openProject(
        Path newProjectRoot,
        boolean clearExistingIndex
    ) throws RagRuntimeException {

        Path normalizedRoot =
            normalizeProjectRoot(
                newProjectRoot
            );

        lifecycleLock.writeLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.OPEN_PROJECT
            );

            boolean changed =
                projectRoot == null
                    || !projectRoot.equals(
                        normalizedRoot
                    );

            if (changed && clearExistingIndex) {
                clearIndexInternal();
            }

            this.projectRoot =
                normalizedRoot;

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public void openProject(
        Path newProjectRoot
    ) throws RagRuntimeException {

        openProject(
            newProjectRoot,
            true
        );
    }

    /**
     * 현재 프로젝트를 닫습니다.
     *
     * @param clearExistingIndex 기존 벡터 인덱스 제거 여부
     */
    public void closeProject(
        boolean clearExistingIndex
    ) throws RagRuntimeException {

        lifecycleLock.writeLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.CLOSE_PROJECT
            );

            if (clearExistingIndex) {
                clearIndexInternal();
            }

            projectRoot = null;

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public void closeProject()
        throws RagRuntimeException {

        closeProject(true);
    }

    /**
     * 현재 프로젝트의 문서 하나를 로드하고 인덱싱합니다.
     *
     * @param relativePath 프로젝트 기준 문서 경로
     * @return 인덱싱 결과
     */
    public RagIndexResult index(
        Path relativePath
    ) throws RagRuntimeException {

        return index(
            relativePath,
            components.getDefaultIndexRequest()
        );
    }

    /**
     * 현재 프로젝트의 문서 하나를 지정된 설정으로 인덱싱합니다.
     */
    public RagIndexResult index(
        Path relativePath,
        RagIndexRequest request
    ) throws RagRuntimeException {

        Objects.requireNonNull(
            relativePath,
            "relativePath must not be null"
        );

        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        lifecycleLock.readLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.INDEX
            );

            Path root =
                requireProjectRoot();

            DocumentSource source;

            try {
                source = documentLoader.load(
                    root,
                    relativePath
                );

            } catch (Exception exception) {
                throw new RagRuntimeException(
                    "Failed to load document: "
                        + relativePath,
                    RagRuntimeOperation.LOAD_DOCUMENT,
                    state,
                    exception
                );
            }

            try {
                return components
                    .getRagIndexer()
                    .index(
                        source,
                        request
                    );

            } catch (RagIndexException exception) {
                throw new RagRuntimeException(
                    "Failed to index document: "
                        + relativePath,
                    RagRuntimeOperation.INDEX,
                    state,
                    exception
                );
            }

        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * 여러 문서를 순차적으로 인덱싱합니다.
     */
    public List<RagIndexResult> indexAll(
        List<Path> relativePaths
    ) throws RagRuntimeException {

        return indexAll(
            relativePaths,
            components.getDefaultIndexRequest()
        );
    }

    public List<RagIndexResult> indexAll(
        List<Path> relativePaths,
        RagIndexRequest request
    ) throws RagRuntimeException {

        if (relativePaths == null
            || relativePaths.isEmpty()) {

            return List.of();
        }

        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        List<RagIndexResult> results =
            new ArrayList<>(
                relativePaths.size()
            );

        for (Path relativePath : relativePaths) {
            if (relativePath == null) {
                continue;
            }

            try {
                results.add(
                    index(
                        relativePath,
                        request
                    )
                );

            } catch (RagRuntimeException exception) {
                if (!request.isContinueOnError()) {
                    throw exception;
                }

                results.add(
                    RagIndexResult.failed(
                        normalizePath(
                            relativePath
                        ),
                        safeMessage(exception)
                    )
                );
            }
        }

        return List.copyOf(results);
    }

    /**
     * 사용자 요청을 RAG 파이프라인으로 실행합니다.
     */
    public RagPipelineResponse execute(
        String userPrompt
    ) throws RagRuntimeException {

        lifecycleLock.readLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.EXECUTE
            );

            try {
                return components
                    .getRagPipeline()
                    .execute(userPrompt);

            } catch (RagPipelineException exception) {
                throw new RagRuntimeException(
                    "Failed to execute RAG pipeline",
                    RagRuntimeOperation.EXECUTE,
                    state,
                    exception
                );
            }

        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * 별도의 검색 조건으로 RAG 파이프라인을 실행합니다.
     */
    public RagPipelineResponse execute(
        String userPrompt,
        RetrievalRequest retrievalRequest
    ) throws RagRuntimeException {

        lifecycleLock.readLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.EXECUTE
            );

            try {
                return components
                    .getRagPipeline()
                    .execute(
                        userPrompt,
                        retrievalRequest
                    );

            } catch (RagPipelineException exception) {
                throw new RagRuntimeException(
                    "Failed to execute RAG pipeline",
                    RagRuntimeOperation.EXECUTE,
                    state,
                    exception
                );
            }

        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public String chat(
        String userPrompt
    ) throws RagRuntimeException {

        return execute(userPrompt)
            .getAnswer();
    }

    public String chat(
        String userPrompt,
        RetrievalRequest retrievalRequest
    ) throws RagRuntimeException {

        return execute(
            userPrompt,
            retrievalRequest
        ).getAnswer();
    }

    /**
     * 특정 문서의 현재 모델 인덱스를 제거합니다.
     */
    public int removeIndex(
        String sourcePath
    ) throws RagRuntimeException {

        lifecycleLock.writeLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.REMOVE_INDEX
            );

            try {
                return components
                    .getRagIndexer()
                    .remove(
                        requireText(
                            sourcePath,
                            "sourcePath"
                        ),
                        configuration
                            .getEmbeddingModel()
                    );

            } catch (RagIndexException exception) {
                throw new RagRuntimeException(
                    "Failed to remove document index",
                    RagRuntimeOperation.REMOVE_INDEX,
                    state,
                    exception
                );
            }

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    /**
     * 모든 벡터 인덱스를 제거합니다.
     */
    public void clearIndex()
        throws RagRuntimeException {

        lifecycleLock.writeLock().lock();

        try {
            ensureRunning(
                RagRuntimeOperation.CLEAR_INDEX
            );

            clearIndexInternal();

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void clearIndexInternal()
        throws RagRuntimeException {

        try {
            components
                .getRagIndexer()
                .clear();

        } catch (RagIndexException exception) {
            throw new RagRuntimeException(
                "Failed to clear RAG index",
                RagRuntimeOperation.CLEAR_INDEX,
                state,
                exception
            );
        }
    }

    /**
     * RAG 구성요소 사용 가능 여부를 반환합니다.
     */
    public boolean isAvailable() {
        lifecycleLock.readLock().lock();

        try {
            return state == RagRuntimeState.RUNNING
                && components.isAvailable();

        } catch (RuntimeException exception) {
            return false;

        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public RagRuntimeState getState() {
        return state;
    }

    public boolean isRunning() {
        return state == RagRuntimeState.RUNNING;
    }

    public boolean isClosed() {
        return state == RagRuntimeState.CLOSED;
    }

    public boolean hasProject() {
        return projectRoot != null;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public RagConfiguration getConfiguration() {
        return configuration;
    }

    public RagComponents getComponents() {
        return components;
    }

    public RagPipeline getRagPipeline() {
        return components.getRagPipeline();
    }

    public DocumentLoader getDocumentLoader() {
        return documentLoader;
    }

    /**
     * 런타임과 VectorStore 리소스를 종료합니다.
     */
    @Override
    public void close()
        throws RagRuntimeException {

        lifecycleLock.writeLock().lock();

        try {
            if (state == RagRuntimeState.CLOSED) {
                return;
            }

            state = RagRuntimeState.STOPPING;

            try {
                components.close();

            } catch (VectorStoreException exception) {
                state = RagRuntimeState.FAILED;

                throw new RagRuntimeException(
                    "Failed to close RAG components",
                    RagRuntimeOperation.CLOSE,
                    state,
                    exception
                );
            }

            projectRoot = null;
            state = RagRuntimeState.CLOSED;

            DEFAULT_INSTANCE.compareAndSet(
                this,
                null
            );

        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void ensureRunning(
        RagRuntimeOperation operation
    ) throws RagRuntimeException {

        if (state != RagRuntimeState.RUNNING) {
            throw new RagRuntimeException(
                "RagRuntime is not running. state="
                    + state,
                operation,
                state
            );
        }
    }

    private Path requireProjectRoot()
        throws RagRuntimeException {

        Path root = projectRoot;

        if (root == null) {
            throw new RagRuntimeException(
                "No EPUB project is currently open",
                RagRuntimeOperation.LOAD_DOCUMENT,
                state
            );
        }

        return root;
    }

    private static Path normalizeProjectRoot(
        Path path
    ) {
        Objects.requireNonNull(
            path,
            "projectRoot must not be null"
        );

        return path
            .toAbsolutePath()
            .normalize();
    }

    private static String normalizePath(
        Path path
    ) {
        return path
            .normalize()
            .toString()
            .replace('\\', '/');
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        String normalized =
            value == null
                ? ""
                : value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String safeMessage(
        Throwable throwable
    ) {
        if (throwable == null) {
            return "Unknown error";
        }

        String message =
            throwable.getMessage();

        return message == null
            || message.isBlank()
                ? throwable
                    .getClass()
                    .getSimpleName()
                : message.trim();
    }

    @Override
    public String toString() {
        return "RagRuntime{" +
            "state=" + state +
            ", projectRoot=" + projectRoot +
            ", configuration=" + configuration +
            ", components=" + components +
            '}';
    }

    public static final class Builder {

        private RagConfiguration configuration;
        private DocumentLoader documentLoader;
        private DocumentIndexer documentIndexer;
        private EmbeddingClient embeddingClient;
        private LlmClient llmClient;
        private VectorStore vectorStore;
        private Path projectRoot;

        private String pipelineSystemPrompt;
        private String augmentorSystemInstruction;
        private String responseInstruction;

        private Builder() {
        }

        public Builder configuration(
            RagConfiguration configuration
        ) {
            this.configuration =
                configuration;

            return this;
        }

        public Builder documentLoader(
            DocumentLoader documentLoader
        ) {
            this.documentLoader =
                documentLoader;

            return this;
        }

        public Builder documentIndexer(
            DocumentIndexer documentIndexer
        ) {
            this.documentIndexer =
                documentIndexer;

            return this;
        }

        public Builder embeddingClient(
            EmbeddingClient embeddingClient
        ) {
            this.embeddingClient =
                embeddingClient;

            return this;
        }

        public Builder llmClient(
            LlmClient llmClient
        ) {
            this.llmClient = llmClient;
            return this;
        }

        public Builder vectorStore(
            VectorStore vectorStore
        ) {
            this.vectorStore =
                vectorStore;

            return this;
        }

        public Builder projectRoot(
            Path projectRoot
        ) {
            this.projectRoot =
                projectRoot;

            return this;
        }

        public Builder pipelineSystemPrompt(
            String pipelineSystemPrompt
        ) {
            this.pipelineSystemPrompt =
                pipelineSystemPrompt;

            return this;
        }

        public Builder augmentorSystemInstruction(
            String augmentorSystemInstruction
        ) {
            this.augmentorSystemInstruction =
                augmentorSystemInstruction;

            return this;
        }

        public Builder responseInstruction(
            String responseInstruction
        ) {
            this.responseInstruction =
                responseInstruction;

            return this;
        }

        public RagRuntime build() {
            return new RagRuntime(this);
        }
    }
}