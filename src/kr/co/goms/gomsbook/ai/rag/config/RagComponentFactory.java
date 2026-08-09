/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.config;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.model.ChatModelProvider;
import kr.co.goms.gomsbook.ai.llm.model.DefaultChatModelProvider;
import kr.co.goms.gomsbook.ai.rag.DefaultRagService;
import kr.co.goms.gomsbook.ai.rag.RagService;
import kr.co.goms.gomsbook.ai.rag.context.RagContextBuilder;
import kr.co.goms.gomsbook.ai.rag.embedding.DefaultEmbeddingModelProvider;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingModelProvider;
import kr.co.goms.gomsbook.ai.rag.hash.HashService;
import kr.co.goms.gomsbook.ai.rag.hash.Sha256HashService;
import kr.co.goms.gomsbook.ai.rag.index.DefaultRagIndexer;
import kr.co.goms.gomsbook.ai.rag.index.DocumentIndexer;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexRequest;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexer;
import kr.co.goms.gomsbook.ai.rag.pipeline.DefaultRagPipeline;
import kr.co.goms.gomsbook.ai.rag.pipeline.RagPipeline;
import kr.co.goms.gomsbook.ai.rag.prompt.DefaultPromptAugmentor;
import kr.co.goms.gomsbook.ai.rag.prompt.PromptAugmentor;
import kr.co.goms.gomsbook.ai.rag.retrieval.DefaultRetriever;
import kr.co.goms.gomsbook.ai.rag.retrieval.RetrievalRequest;
import kr.co.goms.gomsbook.ai.rag.retrieval.Retriever;
import kr.co.goms.gomsbook.ai.rag.vector.InMemoryVectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;

/**
 * {@link RagConfiguration}을 기준으로 RAG 구성요소를 생성하고
 * 연결하는 팩토리입니다.
 *
 * <p>외부에서 제공해야 하는 구성요소:</p>
 *
 * <ul>
 *     <li>{@link DocumentIndexer}</li>
 *     <li>{@link EmbeddingClient}</li>
 *     <li>{@link LlmClient}</li>
 * </ul>
 *
 * <p>기본 생성 구성요소:</p>
 *
 * <ul>
 *     <li>{@link ChatModelProvider}</li>
 *     <li>{@link EmbeddingModelProvider}</li>
 *     <li>{@link HashService}</li>
 *     <li>{@link VectorStore}</li>
 *     <li>{@link RagIndexer}</li>
 *     <li>{@link Retriever}</li>
 *     <li>{@link RagService}</li>
 *     <li>{@link RagPipeline}</li>
 * </ul>
 */
public final class RagComponentFactory {

    public static final String DEFAULT_PIPELINE_SYSTEM_PROMPT = """
        당신은 GomsBook Editor에 통합된 AI Agent입니다.
        검색된 프로젝트 문서를 우선하여 정확하게 답하십시오.
        프로젝트 문서에 없는 내용을 사실처럼 생성하지 마십시오.
        """;

    public static final String DEFAULT_AUGMENTOR_SYSTEM_INSTRUCTION = """
        당신은 GomsBook Editor에 통합된 EPUB 제작 AI Agent입니다.

        아래 참고 문서는 현재 EPUB 프로젝트에서 검색된 실제 자료입니다.
        참고 문서 내용을 우선하여 사용자 요청을 처리하십시오.

        참고 문서에 없는 프로젝트 정보를 임의로 생성하지 마십시오.
        XHTML, EPUB, 접근성 또는 메타데이터를 다룰 때는
        원본 파일 경로와 요소 식별자를 보존하십시오.
        """;

    public static final String DEFAULT_RESPONSE_INSTRUCTION = """
        응답 규칙:
        1. 사용자 요청에 직접 답하십시오.
        2. 참고 문서에 근거한 내용과 일반적인 제안을 구분하십시오.
        3. 문서 위치를 언급할 때는 파일 경로와 요소 ID를 사용하십시오.
        4. 수정이 필요한 경우 대상, 변경 내용과 이유를 명확히 제시하십시오.
        5. 참고 문서가 부족한 경우 임의로 추측하지 마십시오.
        """;

    private final RagConfiguration configuration;
    private final DocumentIndexer documentIndexer;
    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;

    private final VectorStore suppliedVectorStore;
    private final HashService suppliedHashService;

    private final String pipelineSystemPrompt;
    private final String augmentorSystemInstruction;
    private final String responseInstruction;

    private RagComponentFactory(Builder builder) {
        this.configuration = Objects.requireNonNullElseGet(
            builder.configuration,
            RagConfiguration::defaults
        );

        this.documentIndexer = Objects.requireNonNull(
            builder.documentIndexer,
            "documentIndexer must not be null"
        );

        this.embeddingClient = Objects.requireNonNull(
            builder.embeddingClient,
            "embeddingClient must not be null"
        );

        this.llmClient = Objects.requireNonNull(
            builder.llmClient,
            "llmClient must not be null"
        );

        this.suppliedVectorStore =
            builder.vectorStore;

        this.suppliedHashService =
            builder.hashService;

        this.pipelineSystemPrompt =
            resolveText(
                builder.pipelineSystemPrompt,
                DEFAULT_PIPELINE_SYSTEM_PROMPT
            );

        this.augmentorSystemInstruction =
            resolveText(
                builder.augmentorSystemInstruction,
                DEFAULT_AUGMENTOR_SYSTEM_INSTRUCTION
            );

        this.responseInstruction =
            resolveText(
                builder.responseInstruction,
                DEFAULT_RESPONSE_INSTRUCTION
            );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 전체 RAG 구성요소를 생성합니다.
     *
     * @return 조립된 RAG 구성요소
     */
    public RagComponents create() {
        ChatModelProvider chatModelProvider =
            createChatModelProvider();

        EmbeddingModelProvider embeddingModelProvider =
            createEmbeddingModelProvider();

        VectorStore vectorStore =
            createVectorStore();

        HashService hashService =
            createHashService();

        RagIndexer ragIndexer =
            createRagIndexer(
                embeddingModelProvider,
                vectorStore,
                hashService
            );

        Retriever retriever =
            createRetriever(
                embeddingModelProvider,
                vectorStore
            );

        RagContextBuilder ragContextBuilder =
            createRagContextBuilder();

        PromptAugmentor promptAugmentor =
            createPromptAugmentor();

        RagService ragService =
            createRagService(
                retriever,
                ragContextBuilder,
                promptAugmentor
            );

        RagPipeline ragPipeline =
            createRagPipeline(
                ragService,
                chatModelProvider
            );

        RagIndexRequest defaultIndexRequest =
            createDefaultIndexRequest();

        return RagComponents.builder()
            .configuration(configuration)
            .chatModelProvider(chatModelProvider)
            .embeddingModelProvider(
                embeddingModelProvider
            )
            .vectorStore(vectorStore)
            .hashService(hashService)
            .ragIndexer(ragIndexer)
            .retriever(retriever)
            .ragContextBuilder(ragContextBuilder)
            .promptAugmentor(promptAugmentor)
            .ragService(ragService)
            .ragPipeline(ragPipeline)
            .defaultIndexRequest(
                defaultIndexRequest
            )
            .build();
    }

    public ChatModelProvider createChatModelProvider() {
        return new DefaultChatModelProvider(
            configuration.getChatModel()
        );
    }

    public EmbeddingModelProvider
        createEmbeddingModelProvider() {

        return new DefaultEmbeddingModelProvider(
            configuration.getEmbeddingModel()
        );
    }

    /**
     * 외부에서 VectorStore를 주입하지 않으면 메모리 저장소를 생성합니다.
     */
    public VectorStore createVectorStore() {
        if (suppliedVectorStore != null) {
            return suppliedVectorStore;
        }

        return new InMemoryVectorStore();
    }

    /**
     * 외부에서 HashService를 주입하지 않으면 SHA-256 구현을 생성합니다.
     */
    public HashService createHashService() {
        if (suppliedHashService != null) {
            return suppliedHashService;
        }

        return new Sha256HashService();
    }

    public RagIndexer createRagIndexer(
        EmbeddingModelProvider embeddingModelProvider,
        VectorStore vectorStore,
        HashService hashService
    ) {
        return new DefaultRagIndexer(
            documentIndexer,
            embeddingClient,
            embeddingModelProvider,
            vectorStore,
            hashService
        );
    }

    public Retriever createRetriever(
        EmbeddingModelProvider embeddingModelProvider,
        VectorStore vectorStore
    ) {
        return new DefaultRetriever(
            embeddingClient,
            embeddingModelProvider,
            vectorStore,
            configuration.getTopK(),
            configuration.getMinimumScore()
        );
    }

    public RagContextBuilder createRagContextBuilder() {
        return new RagContextBuilder(
            configuration
                .getMaximumContextCharacters()
        );
    }

    public PromptAugmentor createPromptAugmentor() {
        return new DefaultPromptAugmentor(
            configuration
                .getMaximumPromptCharacters(),
            configuration
                .getMaximumContextCharacters(),
            configuration
                .isAllowEmptyContext(),
            configuration
                .isIncludeSources(),
            augmentorSystemInstruction,
            responseInstruction
        );
    }

    public RagService createRagService(
        Retriever retriever,
        RagContextBuilder ragContextBuilder,
        PromptAugmentor promptAugmentor
    ) {
        return new DefaultRagService(
            retriever,
            ragContextBuilder,
            promptAugmentor
        );
    }

    public RagPipeline createRagPipeline(
        RagService ragService,
        ChatModelProvider chatModelProvider
    ) {
        return new DefaultRagPipeline(
            ragService,
            llmClient,
            chatModelProvider,
            pipelineSystemPrompt,
            configuration
                .getMaximumOutputTokens()
        );
    }

    /**
     * 설정값을 적용한 기본 인덱싱 요청을 생성합니다.
     */
    public RagIndexRequest createDefaultIndexRequest() {
        return RagIndexRequest.builder()
            .batchSize(
                configuration.getIndexBatchSize()
            )
            .reuseUnchanged(
                configuration
                    .isReuseUnchangedChunks()
            )
            .replaceSource(
                configuration
                    .isReplaceSourceOnIndex()
            )
            .continueOnError(
                configuration
                    .isContinueOnIndexError()
            )
            .normalize(
                configuration
                    .isNormalizeEmbeddings()
            )
            .truncate(
                configuration
                    .isTruncateEmbeddingInput()
            )
            .version(
                configuration.getIndexVersion()
            )
            .build();
    }

    /**
     * 설정값을 적용한 기본 검색 요청을 생성합니다.
     *
     * @param query 사용자 검색 질의
     * @return 검색 요청
     */
    public RetrievalRequest createRetrievalRequest(
        String query
    ) {
        return RetrievalRequest.builder()
            .query(query)
            .topK(
                configuration.getTopK()
            )
            .minimumScore(
                configuration.getMinimumScore()
            )
            .similarityType(
                configuration.getSimilarityType()
            )
            .preserveDocumentOrder(
                configuration
                    .isPreserveDocumentOrder()
            )
            .build();
    }

    public RagConfiguration getConfiguration() {
        return configuration;
    }

    public DocumentIndexer getDocumentIndexer() {
        return documentIndexer;
    }

    public EmbeddingClient getEmbeddingClient() {
        return embeddingClient;
    }

    public LlmClient getLlmClient() {
        return llmClient;
    }

    private static String resolveText(
        String value,
        String defaultValue
    ) {
        if (value == null) {
            return defaultValue.trim();
        }

        return normalizeMultiline(value);
    }

    private static String normalizeMultiline(
        String value
    ) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t ]+", " ")
            .replaceAll("\\n[\\t ]+", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    @Override
    public String toString() {
        return "RagComponentFactory{" +
            "configuration=" + configuration +
            ", documentIndexer="
                + documentIndexer
                    .getClass()
                    .getSimpleName() +
            ", embeddingClient="
                + embeddingClient
                    .getClass()
                    .getSimpleName() +
            ", llmClient="
                + llmClient
                    .getClass()
                    .getSimpleName() +
            ", suppliedVectorStore="
                + (suppliedVectorStore == null
                    ? "default"
                    : suppliedVectorStore
                        .getClass()
                        .getSimpleName()) +
            ", suppliedHashService="
                + (suppliedHashService == null
                    ? "default"
                    : suppliedHashService
                        .getClass()
                        .getSimpleName()) +
            '}';
    }

    public static final class Builder {

        private RagConfiguration configuration;
        private DocumentIndexer documentIndexer;
        private EmbeddingClient embeddingClient;
        private LlmClient llmClient;
        private VectorStore vectorStore;
        private HashService hashService;

        private String pipelineSystemPrompt;
        private String augmentorSystemInstruction;
        private String responseInstruction;

        private Builder() {
        }

        public Builder configuration(
            RagConfiguration configuration
        ) {
            this.configuration = configuration;
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

        /**
         * VectorStore를 직접 지정합니다.
         *
         * 지정하지 않으면 InMemoryVectorStore가 사용됩니다.
         */
        public Builder vectorStore(
            VectorStore vectorStore
        ) {
            this.vectorStore = vectorStore;
            return this;
        }

        /**
         * HashService를 직접 지정합니다.
         *
         * 지정하지 않으면 Sha256HashService가 사용됩니다.
         */
        public Builder hashService(
            HashService hashService
        ) {
            this.hashService = hashService;
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

        public RagComponentFactory build() {
            return new RagComponentFactory(this);
        }
    }
}