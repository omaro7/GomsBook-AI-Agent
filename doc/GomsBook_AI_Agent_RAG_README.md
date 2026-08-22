# GomsBook AI Agent --- RAG Core

> EPUB 프로젝트 문서를 대상으로 한 Project-Scoped Retrieval-Augmented
> Generation Core

## 1. Overview

GomsBook AI Agent의 RAG Core는 현재 GomsBookEditor에서 열려 있는 EPUB
프로젝트의 XHTML 문서를 로드하고, Chunking → Embedding → Vector Indexing
→ Retrieval → Reranking → Context 생성까지 수행하는 검색 증강 생성 기반
계층입니다.

RAG Core v1.0의 핵심 목표는 다음과 같습니다.

-   EPUB `OEBPS/Text` XHTML 문서 자동 수집
-   문서 Chunk 생성
-   Ollama Embedding 연동
-   VectorStore 저장 및 Semantic Search
-   EPUB 프로젝트별 `projectId` 격리
-   NEW / CHANGED / UNCHANGED / DELETED 증분 인덱싱
-   검색 직전 자동 인덱스 동기화
-   Candidate Pool 기반 Hybrid Reranking
-   Score Threshold 및 Final Top-K 적용
-   Agent Tool Calling을 통한 RAG 검색

현재 구현은 `InMemoryVectorStore`를 기본 VectorStore로 사용합니다.

------------------------------------------------------------------------

## 2. Architecture

``` text
┌─────────────────────────────────────────────┐
│               GomsBookEditor                │
│          Current EPUB Project               │
└─────────────────────┬───────────────────────┘
                      │
                      ▼
             CurrentProjectProvider
                      │
                      ▼
              EpubProjectContext
                      │
                      ▼
             ProjectRagIndexer
                      │
          ┌───────────┴───────────┐
          │                       │
          ▼                       ▼
   DocumentLoader           VectorStore State
          │                       │
          ▼                       │
   DocumentIndexer                │
      / Chunking                  │
          │                       │
          ├── sourceHash ─────────┤
          │                       │
          ▼                       ▼
 NEW / CHANGED / UNCHANGED / DELETED
          │
          ▼
   EmbeddingClient
          │
          ▼
      VectorStore
          │
          ▼
       Retriever
          │
          ├─ Query Embedding
          ├─ Project Scope
          ├─ Candidate Pool
          ├─ Semantic Search
          ├─ Hybrid Rerank
          ├─ Final Threshold
          └─ Final Top-K
          │
          ▼
      RagContext
          │
          ▼
      RagService
          │
          ▼
 SearchProjectDocumentsTool
          │
          ▼
     Agent / LLM / Chat
```

------------------------------------------------------------------------

## 3. Core Components

### 3.1 Project Context

`CurrentProjectProvider`는 GomsBookEditor에서 현재 열려 있는 EPUB
프로젝트를 제공합니다.

``` text
CurrentProjectProvider
        ↓
EpubProjectContext
        ├─ projectName
        ├─ projectRoot
        └─ textDirectory
```

RAG Core는 이 Context를 기준으로 검색 및 인덱싱 범위를 결정합니다.

### 3.2 Document Loading

`DocumentLoader`는 EPUB 프로젝트의 XHTML 파일을 `DocumentSource`로
로드합니다.

기본 구현:

``` text
DocumentLoader
└─ DefaultDocumentLoader
```

대상은 현재 EPUB 프로젝트의 `OEBPS/Text` 영역입니다.

### 3.3 Document Indexing / Chunking

`DocumentIndexer`는 로드된 문서를 검색 가능한 `DocumentChunk` 단위로
변환합니다.

``` text
DocumentSource
      ↓
DocumentIndexer
      ↓
DocumentChunk[]
```

Chunk에는 검색 및 결과 추적에 필요한 문서 정보가 포함됩니다.

예:

``` text
sourcePath
chunkId
heading
type
text
```

### 3.4 Embedding

문서 Chunk와 검색 Query는 `EmbeddingClient`를 통해 벡터로 변환됩니다.

현재 구성은 Ollama 기반입니다.

``` text
EmbeddingClient
└─ OllamaEmbeddingClient
```

문서 인덱싱 시:

``` java
EmbeddingPurpose.DOCUMENT
```

를 사용합니다.

Embedding 모델은 인덱싱과 검색에서 반드시 동일한 모델을 사용해야 합니다.

### 3.5 Vector Store

현재 기본 구현:

``` text
VectorStore
└─ InMemoryVectorStore
```

VectorRecord에는 다음과 같은 RAG 상태 정보가 포함됩니다.

``` text
projectId
chunk
vector
model
contentHash
sourceHash
normalized
indexedAt
version
```

`InMemoryVectorStore`는 애플리케이션 재시작 시 데이터가 유지되지
않습니다. 영속 Vector DB는 후속 고도화 범위입니다.

------------------------------------------------------------------------

## 4. Project Scope

RAG Core는 여러 EPUB 프로젝트의 Vector가 섞이지 않도록 프로젝트별
Scope를 적용합니다.

`projectId`는 정규화된 프로젝트 Root Path를 SHA-256으로 변환하여
생성합니다.

``` text
projectRoot
   ↓
absolute / normalize
   ↓
normalizePath
   ↓
SHA-256
   ↓
projectId
```

동일한 `projectId` 생성 규칙을 Indexing과 Retrieval 양쪽에서 사용합니다.

Vector 검색 시:

``` text
RetrievalRequest.projectId
        ↓
VectorSearchRequest.projectId
        ↓
VectorRecord.isProject(projectId)
```

흐름으로 현재 EPUB 프로젝트의 Vector만 검색합니다.

------------------------------------------------------------------------

## 5. Incremental Indexing

RAG Core는 매 검색마다 모든 XHTML을 다시 Embedding하지 않습니다.

각 문서의 `sourceHash`를 기존 VectorRecord와 비교하여 상태를 판정합니다.

### NEW

VectorStore에 기존 문서 Vector가 없는 경우입니다.

``` text
XHTML
 ↓
Chunking
 ↓
Embedding
 ↓
VectorStore.saveAll()
```

### CHANGED

동일한 sourcePath가 존재하지만 `sourceHash`가 변경된 경우입니다.

``` text
XHTML 변경
 ↓
새 Chunk / Embedding 생성
 ↓
기존 Vector 삭제
 ↓
새 Vector 저장
```

Embedding 생성에 성공한 뒤 기존 Vector를 교체하여 Embedding 실패 시 기존
정상 Vector를 가능한 한 보존하도록 구성합니다.

### UNCHANGED

기존 VectorRecord의 `sourceHash`와 현재 XHTML의 Hash가 동일하면
재Embedding하지 않습니다.

``` text
sourceHash 동일
 ↓
SKIP
 ↓
Embedding 호출 0
```

### DELETED

VectorStore에는 존재하지만 실제 `OEBPS/Text`에서 사라진 sourcePath를
탐지합니다.

``` text
VectorStore sourcePath
        ↓
현재 XHTML 목록과 비교
        ↓
파일 없음
        ↓
deleteByProjectAndSourcePath()
```

이를 통해 stale vector를 자동 제거합니다.

------------------------------------------------------------------------

## 6. Automatic RAG Synchronization

RAG Core v1.0에서는 사용자가 검색 전에 별도로 Embedding 명령을 실행할
필요가 없습니다.

검색 Tool이 실행되면 먼저 현재 프로젝트 인덱스를 자동 동기화합니다.

``` text
User Question
      ↓
SearchProjectDocumentsTool
      ↓
ProjectRagIndexer.synchronize(project)
      ↓
NEW / CHANGED / UNCHANGED / DELETED
      ↓
RagService
      ↓
Retriever
      ↓
RAG Context
      ↓
LLM Answer
```

따라서 사용자는 다음과 같이 바로 질문할 수 있습니다.

``` text
현재 문서에서 덕수궁과 관련된 내용을 찾아줘.
```

변경이 없다면 XHTML Hash 확인 후 기존 Vector를 그대로 사용하며 새로운
Embedding은 생성하지 않습니다.

명시적 인덱싱이 필요한 경우에는 `IndexProjectDocumentsTool`을 사용할 수
있습니다.

------------------------------------------------------------------------

## 7. ProjectRagIndexer

RAG 자동 동기화의 핵심 서비스입니다.

``` text
ProjectRagIndexer
└─ DefaultProjectRagIndexer
```

주요 메서드:

``` java
ProjectIndexResult synchronize(
        EpubProjectContext project
) throws ProjectIndexException;
```

`DefaultProjectRagIndexer`가 담당하는 기능:

``` text
TEXT XHTML 탐색
Project ID 생성
DELETED 탐지
sourceHash 비교
NEW 판정
CHANGED 판정
UNCHANGED 판정
Chunk 생성
Embedding 생성
Vector 저장
ProjectIndexResult 생성
```

------------------------------------------------------------------------

## 8. ProjectIndexResult

자동/수동 인덱싱 결과는 `ProjectIndexResult`로 반환합니다.

대표 통계:

``` text
projectId
projectName
embeddingModel

discoveredFiles
processedFiles

newFiles
reindexedFiles
skippedFiles
deletedFiles

createdChunks
createdEmbeddings
storedVectors
deletedVectors

vectorStoreSize
```

파일 목록도 추적합니다.

``` text
indexedFiles
skippedFilePaths
deletedFilePaths
```

이를 통해 자동 인덱싱이 실제로 어떤 작업을 수행했는지 Tool Result 및
로그에서 확인할 수 있습니다.

------------------------------------------------------------------------

## 9. Retrieval Pipeline

현재 Retrieval Core의 기본 흐름은 다음과 같습니다.

``` text
Query
 ↓
Query Embedding
 ↓
Vector Candidate Search
 ↓
Hybrid Rerank
 ↓
Final Score Threshold
 ↓
Final Top-K
 ↓
RetrievalResult
```

### Candidate Pool

최종 `topK`보다 넓은 후보군을 VectorStore에서 가져온 후 Reranking합니다.

현재 기본 전략:

``` text
candidateTopK = max(topK, topK × 3)
```

예를 들어:

``` text
topK = 5
candidateTopK = 15
```

이면 Semantic Search 후보 15개를 대상으로 Reranking한 뒤 최종 5개를
선택합니다.

### Hybrid Reranking

Semantic Vector Score에 Query와 문서의 lexical match를 추가 반영합니다.

현재 기본 Boost:

``` text
TITLE_EXACT_MATCH_BOOST   = 0.15
CONTENT_EXACT_MATCH_BOOST = 0.10
```

개념적으로:

``` text
finalScore
    = vectorScore
    + titleBoost
    + contentBoost
```

형태입니다.

### Final Threshold

Raw Vector Search 단계에서 최종 판정을 하지 않고 Hybrid Reranking 이후
`minimumScore`를 적용합니다.

``` text
Semantic Candidates
       ↓
Hybrid Rerank
       ↓
minimumScore
       ↓
Final Top-K
```

------------------------------------------------------------------------

## 10. Agent Tools

### IndexProjectDocumentsTool

현재 EPUB 프로젝트의 RAG 인덱스를 명시적으로 동기화합니다.

Tool name:

``` text
index_project_documents
```

실제 인덱싱 로직은 Tool 내부가 아니라 `ProjectRagIndexer`에 위임합니다.

### SearchProjectDocumentsTool

현재 EPUB 프로젝트의 인덱싱된 문서를 검색합니다.

Tool name:

``` text
search_project_documents
```

검색 전에:

``` java
projectRagIndexer.synchronize(project)
```

를 수행하여 VectorStore를 현재 프로젝트 상태와 자동 동기화합니다.

그 후 `RagService`를 통해 RAG Context를 생성합니다.

------------------------------------------------------------------------

## 11. Runtime Wiring

GomsBookEditor의 AI 초기화 과정에서 RAG Core 구성요소를 생성합니다.

``` text
DocumentLoader
DocumentIndexer
EmbeddingClient
VectorStore
       │
       ▼
DefaultProjectRagIndexer
       │
       ├──────────────┐
       ▼              ▼
IndexProject      SearchProject
DocumentsTool     DocumentsTool
                       │
                       ▼
                   RagService
```

핵심 Wiring:

``` java
ProjectRagIndexer projectRagIndexer =
        new DefaultProjectRagIndexer(
                documentLoader,
                documentIndexer,
                embeddingClient,
                vectorStore
        );

AgentToolRegistrar toolRegistrar =
        new DefaultAgentToolRegistrar(
                currentProjectProvider,
                ragService,
                projectRagIndexer
        );
```

동일한 `ProjectRagIndexer` 인스턴스를 두 Tool이 공유합니다.

------------------------------------------------------------------------

## 12. Automatic RAG Test Scenarios

### Test 1 --- Empty VectorStore

GomsBookEditor 재시작 후 별도의 Embedding 명령 없이 바로 질문합니다.

``` text
현재 문서에서 덕수궁과 관련된 내용을 찾아줘.
```

기대 결과:

``` text
XHTML → NEW
Embedding 생성
VectorStore 저장
RAG Retrieval
정상 Chat 응답
```

### Test 2 --- UNCHANGED

같은 세션에서 문서를 수정하지 않고 다시 검색합니다.

기대:

``` text
newFiles          = 0
reindexedFiles    = 0
deletedFiles      = 0
createdEmbeddings = 0
```

기존 XHTML은 `SKIP` 처리되어야 합니다.

### Test 3 --- CHANGED

XHTML 하나를 수정한 뒤 별도 Embedding 명령 없이 바로 검색합니다.

기대:

``` text
reindexedFiles = 1
나머지 파일     = SKIP
```

검색 결과에는 변경된 내용이 반영되어야 합니다.

### Test 4 --- NEW

새 XHTML을 추가한 뒤 바로 관련 내용을 검색합니다.

기대:

``` text
newFiles = 1
```

새 문서가 검색 결과에 포함되어야 합니다.

### Test 5 --- DELETED

인덱싱된 XHTML을 삭제하고 다시 검색합니다.

기대:

``` text
deletedFiles   = 1
deletedVectors > 0
```

삭제된 문서의 stale vector가 이후 검색 결과에 나타나지 않아야 합니다.

### Test 6 --- Project Scope

프로젝트 A를 인덱싱한 후 프로젝트 B에서 검색합니다.

프로젝트 B 검색 결과에 프로젝트 A의 Vector가 포함되지 않아야 합니다.

------------------------------------------------------------------------

## 13. Package Structure

대표적인 RAG 계층 구조는 다음과 같습니다.

``` text
kr.co.goms.gomsbook.ai.rag
│
├─ RagService
├─ DefaultRagService
│
├─ context
│  └─ RagContextBuilder
│
├─ document
│  ├─ DocumentLoader
│  └─ DefaultDocumentLoader
│
├─ embedding
│  ├─ EmbeddingClient
│  ├─ EmbeddingRequest
│  ├─ EmbeddingResponse
│  ├─ EmbeddingPurpose
│  └─ EmbeddingModelProvider
│
├─ index
│  ├─ DocumentIndexer
│  ├─ DefaultDocumentIndexer
│  ├─ ProjectRagIndexer
│  ├─ DefaultProjectRagIndexer
│  ├─ ProjectIndexResult
│  └─ ProjectIndexException
│
├─ retrieval
│  ├─ Retriever
│  └─ DefaultRetriever
│
├─ prompt
│  ├─ PromptAugmentor
│  └─ DefaultPromptAugmentor
│
└─ vector
   ├─ VectorStore
   ├─ InMemoryVectorStore
   ├─ VectorRecord
   ├─ VectorSearchRequest
   └─ VectorSearchResult
```

Agent Tool 계층:

``` text
tools/rag
├─ IndexProjectDocumentsTool
└─ SearchProjectDocumentsTool
```

------------------------------------------------------------------------

## 14. RAG Core v1.0 Completion Scope

현재 RAG Core v1.0에서 구현 및 검증한 범위:

-   [x] XHTML Document Loading
-   [x] Document Chunking
-   [x] Ollama Embedding
-   [x] VectorStore
-   [x] Semantic Retrieval
-   [x] Query Embedding
-   [x] Project Scope
-   [x] sourceHash 기반 증분 인덱싱
-   [x] NEW 처리
-   [x] CHANGED 처리
-   [x] UNCHANGED / SKIP 처리
-   [x] DELETED / stale vector 제거
-   [x] 자동 인덱스 동기화
-   [x] Candidate Pool
-   [x] Hybrid Reranking
-   [x] Final Score Threshold
-   [x] Final Top-K
-   [x] Agent Tool Calling 연동
-   [x] Chat 기반 End-to-End RAG 테스트

**Status: RAG Core v1.0 Complete**

------------------------------------------------------------------------

## 15. Future Work

RAG Core 이후의 기능은 Core와 분리하여 고도화 단계에서 진행합니다.

### Persistent Vector Store

현재 `InMemoryVectorStore`는 프로세스 종료 시 Vector가 사라집니다.

후속 버전에서는 다음과 같은 영속 Vector Store 연동을 검토할 수 있습니다.

``` text
Chroma
Qdrant
FAISS 기반 별도 저장 계층
기타 Vector Database
```

### Retrieval Quality Evaluation

정량적인 검색 품질 평가를 추가할 수 있습니다.

예:

``` text
Recall@K
Precision@K
MRR
Hit Rate
```

EPUB 프로젝트 기반 테스트 Query/Expected Source 데이터셋을 만들어 RAG
변경 전후 품질을 비교하는 방식이 적합합니다.

### Chunking Strategy Evaluation

현재 Chunking을 기준선으로 두고 다음 전략을 비교할 수 있습니다.

``` text
Paragraph Chunking
Heading-aware Chunking
Section Chunking
Sliding Window
Semantic Chunking
```

### Reranker Enhancement

현재 lightweight hybrid reranking 이후에는 별도 Reranker 모델 또는
Cross-Encoder 기반 재정렬을 검토할 수 있습니다.

### Performance

대규모 EPUB 프로젝트를 고려하여 다음을 고도화할 수 있습니다.

``` text
Batch Embedding
Parallel Embedding
Index Persistence
Embedding Cache
Background Synchronization
```

------------------------------------------------------------------------

## 16. Summary

GomsBook AI Agent의 RAG Core는 단순한 Vector Search 호출을 넘어 EPUB
프로젝트를 하나의 독립적인 RAG 검색 범위로 관리합니다.

핵심 특징은 다음과 같습니다.

``` text
EPUB-aware Document Loading
        +
Chunking / Embedding
        +
Project-Scoped Vector Index
        +
Incremental Indexing
        +
Automatic Synchronization
        +
Semantic Retrieval
        +
Hybrid Reranking
        +
Agent Tool Calling
```

이를 통해 GomsBookEditor에서 사용자는 별도의 인덱싱 절차를 의식하지 않고
현재 EPUB 프로젝트의 내용을 기반으로 질문하고 검색할 수 있습니다.

------------------------------------------------------------------------

**GomsBook AI Agent --- RAG Core v1.0**
