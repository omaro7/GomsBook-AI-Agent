# GomsBook AI Agent - RAG Golden Dataset Evaluation

## 1. 개요

GomsBook AI Agent의 RAG(Retrieval-Augmented Generation) 품질을 정량적으로 검증하기 위한 Golden Dataset 기반 평가 문서입니다.

본 테스트는 EPUB 프로젝트의 실제 XHTML 콘텐츠를 대상으로 검색, Context Expansion, 답변 생성을 수행하고, 생성 결과를 RAGAS 관점의 지표로 평가합니다.

현재 평가 데이터셋은 `rag-golden-v1`이며 총 40개 테스트 케이스로 구성됩니다.

---

## 2. 평가 목적

Golden Dataset 평가는 다음 항목을 검증하는 것을 목적으로 합니다.

- 질문에 필요한 문서가 검색되는가
- 검색된 Context가 정답을 충분히 포함하는가
- 불필요한 Context가 과도하게 포함되지 않는가
- LLM 답변이 검색 Context에 근거하는가
- 질문에 직접적인 답변을 생성하는가
- 문서에 없는 질문에 대해 답을 만들어내지 않는가
- Context Expansion이 Retrieval 품질을 실제로 개선하는가

---

## 3. 평가 구조

```text
Golden Dataset
      |
      v
RagEvaluationRuntime
      |
      v
ProjectRagIndexer.synchronize()
      |
      v
Retriever.retrieve()
      |
      v
Top-K Retrieved Documents
      |
      v
ContextExpander.expand()
      |
      v
Expanded Contexts
      |
      v
LLM Answer Generation
      |
      v
RAG Evaluation
      |
      +-- Faithfulness
      +-- Answer Relevancy
      +-- Context Precision
      +-- Context Recall
      +-- No-Answer Detection
      |
      v
RagEvaluationReport
```

---

## 4. 주요 구성 요소

### RagEvaluationGoldenTest

Golden Dataset 평가 실행의 진입점입니다.

평가 Dataset과 결과 Report 경로를 지정하고 `RagEvaluationRuntime`을 통해 전체 평가를 실행합니다.

### RagEvaluationRuntime

Golden Dataset 전체 테스트 케이스를 순차적으로 실행하고 평가 결과를 집계합니다.

### ProjectRagIndexer

현재 EPUB 프로젝트의 XHTML 문서를 RAG Index와 동기화합니다.

증분 인덱싱 기준은 다음과 같습니다.

- `NEW`: 신규 문서 인덱싱
- `CHANGED`: 변경 문서 재인덱싱
- `UNCHANGED`: 기존 Vector 재사용
- `DELETED`: 삭제 문서의 stale vector 제거

### Retriever

질문을 임베딩한 후 현재 프로젝트 범위에서 유사도가 높은 DocumentChunk를 검색합니다.

현재 Golden Test의 기본 검색 설정:

```text
Top-K = 5
```

### ContextExpander

Retriever가 선택한 parent chunk의 앞뒤 인접 chunk를 추가하여 문맥을 확장합니다.

현재 설정:

```text
before = 1
after  = 1
```

즉 하나의 검색 결과에 대해 다음 구조의 Context를 구성할 수 있습니다.

```text
previous chunk
parent retrieved chunk
next chunk
```

중복 Context는 제거됩니다.

### ChunkContextProvider

Context Expansion이 동일 source의 주변 DocumentChunk를 조회할 수 있도록 원본 Chunk 집합을 제공합니다.

`DefaultProjectRagIndexer`와 `DefaultContextExpander`는 동일한 `ChunkContextProvider` 인스턴스를 공유해야 합니다.

---

## 5. Context Expansion 데이터 흐름

```text
XHTML
  |
  v
DefaultDocumentIndexer
  |
  v
DocumentChunk
  |
  +--------------------------+
  |                          |
  v                          v
VectorRecord          ChunkContextProvider
  |                          |
  v                          |
VectorStore                   |
  |                          |
  v                          |
Retriever                    |
  |                          |
  v                          |
RetrievedDocument            |
  |                          |
  +------------+-------------+
               |
               v
       DefaultContextExpander
               |
               v
       Expanded Documents
```

프로젝트 Scope는 `VectorRecord.projectId`와 `RetrievedDocument.projectId`를 통해 유지합니다.

`DocumentChunk` 자체는 프로젝트 Scope를 책임지지 않습니다.

---

## 6. RetrievedDocument Score 의미

Context Expansion 이후 score의 의미를 명확히 구분합니다.

### 원본 검색 Chunk

```text
expanded=false
retrievalScore=<similarity score>
parentRetrievalScore=null
parentChunkId=null
```

`retrievalScore`는 해당 Chunk가 Vector Retrieval에서 직접 받은 similarity score입니다.

### 확장 Chunk

```text
expanded=true
retrievalScore=null
parentRetrievalScore=<parent similarity score>
parentChunkId=<retrieved parent chunk id>
```

확장 Chunk는 Vector Search에서 직접 검색된 결과가 아니므로 자체 `retrievalScore`를 갖지 않습니다.

`parentRetrievalScore`는 **확장을 발생시킨 parent retrieval chunk의 similarity score**입니다.

따라서 `parentRetrievalScore=0.955198`은 확장 Chunk 자체의 similarity score가 아닙니다.

---

## 7. 평가 지표

### Faithfulness

생성 답변이 검색된 Context에 근거하는지를 평가합니다.

높을수록 hallucination 가능성이 낮습니다.

### Answer Relevancy

생성 답변이 사용자 질문에 직접적이고 적절하게 답했는지를 평가합니다.

### Context Precision

검색된 Context 중 실제 정답 생성에 유용한 Context의 비율과 관련성을 평가합니다.

Context Expansion을 과도하게 적용하면 Recall은 높아지더라도 Precision이 낮아질 수 있습니다.

### Context Recall

Reference Answer를 구성하는 데 필요한 정보가 검색 Context에 충분히 포함되었는지를 평가합니다.

Context Expansion의 핵심 개선 목표 중 하나입니다.

### No-Answer Detection

원본 문서에 답이 존재하지 않는 질문에 대해 LLM이 임의로 답을 생성하지 않고 정보 부재를 정확히 판단하는지를 평가합니다.

---

## 8. Golden Dataset

현재 Dataset:

```text
Dataset : rag-golden-v1
Cases   : 40
```

테스트에는 다음 유형이 포함됩니다.

- 단일 사실 검색
- 여러 Chunk에 걸친 문맥 검색
- 과거와 현재의 비교
- 원인 및 이유 질문
- 의미 및 해석 질문
- 인접 문맥이 필요한 질문
- 문서에 존재하지 않는 NO_ANSWER 질문

주요 테스트 대상 콘텐츠에는 서울시립미술관, 덕수궁 돌담길, 정동길 음악회, 서소문성지 역사박물관, 서울도서관 등의 내용이 포함됩니다.

---

## 9. Context Expansion 적용 전 결과

Context Expansion을 실제 Chunk Provider에 연결하기 전 평가 결과:

```text
Average Score = 0.7125
```

이 단계에서는 `ContextExpander.expand()` 호출 자체는 존재했지만 주변 DocumentChunk가 `ChunkContextProvider`에 공급되지 않아 실질적인 확장이 발생하지 않았습니다.

대표 로그:

```text
Retrieved Count = 5
Expanded Count  = 5

expanded=false
parentRetrievalScore=null
parentChunkId=null
```

즉 Context Expansion 기능은 호출되었지만 실제 검색 Context는 확장되지 않은 상태였습니다.

---

## 10. Context Expansion 적용 후 결과

`DefaultProjectRagIndexer`와 `ChunkContextProvider`를 연결한 후 Golden Dataset을 다시 평가했습니다.

```text
Golden Test Complete

Dataset       = rag-golden-v1
Cases         = 40
Average Score = 0.8488
```

비교:

| 구분 | Average Score |
|---|---:|
| Context Expansion 적용 전 | 0.7125 |
| Context Expansion 적용 후 | 0.8488 |
| 절대 개선 | +0.1363 |
| 상대 개선 | 약 +19.1% |

Context Expansion 적용 후 전체 RAGAS 평균이 유의미하게 상승했습니다.

---

## 11. 주요 개선 사례

### RAG-GOLD-004

질문:

> 정동길에서 우연히 만난 음악회는 어떤 모습이었나요?

Context Expansion 적용 후:

```text
Overall Score     = 1.0000
Faithfulness      = 1.0000
Answer Relevancy  = 1.0000
Context Precision = 1.0000
Context Recall    = 1.0000
```

이화여고 앞, 작은 무대, 음악회 이름, 네 명의 첼리스트 등 여러 인접 문맥이 함께 확보되어 완전한 답변이 가능해졌습니다.

### RAG-GOLD-009

질문:

> 정동극장 무료 시사회를 본 뒤 저자가 덕수궁 돌담길을 뛰어야 했던 이유는 무엇인가요?

Context Expansion 적용 후:

```text
Overall Score     = 1.0000
Faithfulness      = 1.0000
Answer Relevancy  = 1.0000
Context Precision = 1.0000
Context Recall    = 1.0000
```

무료 시사회, 늦은 밤, 막차를 놓치지 않아야 했던 이유가 여러 Chunk에 분산되어 있었으나 인접 Context 확장으로 하나의 답변 문맥으로 결합되었습니다.

### RAG-GOLD-006

한여름 점심시간에 서울시립미술관으로 향한 이유에 필요한 인접 문맥이 추가되면서 모든 주요 지표가 1.0을 기록했습니다.

---

## 12. 현재 결과 분석

Context Expansion 적용 후 다음 특징이 확인되었습니다.

### 장점

- 전체 평균 점수 상승
- 다중 Chunk 질문의 Context Recall 개선
- 인접 문맥 누락 감소
- Faithfulness가 거의 모든 테스트에서 1.0 유지
- NO_ANSWER 테스트 안정적 처리
- Answer Relevancy 개선
- 검색된 parent chunk와 확장 chunk의 provenance 구분 가능

### 남은 문제

일부 케이스는 여전히 낮은 점수를 기록했습니다.

대표 사례:

```text
RAG-GOLD-008 = 0.2500
RAG-GOLD-015 = 0.2750
RAG-GOLD-024 = 0.4000
RAG-GOLD-028 = 0.2500
RAG-GOLD-034 = 0.5000
RAG-GOLD-035 = 0.3000
```

이 케이스들은 단순한 인접 Context 부족보다는 **초기 Retrieval에서 정답이 존재하는 source/chunk를 Top-K에 포함시키지 못한 문제**가 중심입니다.

Context Expansion은 검색된 parent chunk 주변을 확장하는 기능이므로 잘못된 source가 검색되면 다른 source의 정답 Chunk까지 이동할 수 없습니다.

```text
Wrong Parent Retrieval
        |
        v
Expansion within Wrong Source
        |
        v
Correct Context Not Reached
```

따라서 다음 개선 대상은 Retrieval Recall입니다.

---

## 13. Context Expansion의 Precision Trade-off

현재 설정은:

```text
Top-K  = 5
before = 1
after  = 1
```

이므로 최악의 경우 최대 약 15개의 Context 후보가 생성될 수 있습니다.

```text
5 parent chunks x 3 contexts = 15 contexts
```

중복 제거로 실제 수는 감소할 수 있습니다.

Context Expansion은 Recall 개선에는 효과적이지만, 일부 질문에서는 관련성이 낮은 주변 Context까지 포함되어 Context Precision이 감소할 수 있습니다.

예를 들어 일부 테스트는 Context Recall `1.0`을 달성하면서도 Context Precision이 상대적으로 낮게 평가되었습니다.

따라서 무조건 Expansion 범위를 증가시키는 것보다 Expanded Context Selection 또는 Reranking이 필요합니다.

---

## 14. 테스트 실행 시 확인 로그

Context Expansion이 실제 동작하는지 다음 로그를 확인합니다.

```text
[RAG-EVAL] Retrieved Count = 5
[RAG-EVAL] Expanded Count  = N
```

정상적인 Expansion이 발생했다면 일반적으로:

```text
Expanded Count > Retrieved Count
```

가 됩니다.

확장 Chunk는 다음과 같이 표시됩니다.

```text
expanded=true
retrievalScore=null
parentRetrievalScore=<parent score>
parentChunkId=<parent chunk id>
```

원본 검색 Chunk는:

```text
expanded=false
retrievalScore=<similarity score>
parentRetrievalScore=null
parentChunkId=null
```

로 표시됩니다.

---

## 15. 테스트 실행 절차

### 1. EPUB 프로젝트 준비

현재 프로젝트의 XHTML 문서와 TEXT 디렉터리를 확인합니다.

### 2. RAG Index 동기화

```text
projectRagIndexer.synchronize(project)
```

를 실행하여 VectorStore와 ChunkContextProvider를 동기화합니다.

### 3. Golden Dataset 로드

`rag-golden-v1` Dataset을 로드합니다.

### 4. Retrieval

각 질문에 대해 현재 프로젝트 ID와 Top-K를 사용하여 검색합니다.

### 5. Context Expansion

검색 결과를 `ContextExpansionRequest`로 변환하여 확장합니다.

개념적으로:

```java
ContextExpansionRequest expansionRequest =
        new ContextExpansionRequest(
                projectId,
                retrievedDocuments,
                1,
                1
        );

List<RetrievedDocument> expandedDocuments =
        contextExpander.expand(expansionRequest);
```

### 6. Answer Generation

확장된 Context를 LLM Prompt에 포함하여 답변을 생성합니다.

### 7. RAGAS 평가

생성 답변과 Context를 Golden Reference와 비교하여 평가합니다.

### 8. Report 확인

전체 평균과 케이스별 지표 및 reason을 분석합니다.

---

## 16. 현재 모델 구성

현재 Golden Dataset 평가 구성:

```text
Chat / Answer Model : gemma4:31b-cloud
Embedding Model     : nomic-embed-text
Retriever Top-K     : 5
Context Expansion   : before=1, after=1
```

---

## 17. 다음 개선 단계

현재 결과 기준 권장 순서는 다음과 같습니다.

```text
1. Golden Dataset Baseline
       완료

2. Context Expansion ±1
       완료

3. Expanded Context Selection / Reranking
       다음 단계

4. Retrieval Query Expansion
       예정

5. Retrieval Threshold 최적화
       예정

6. 실패 Case 분석 및 Golden Dataset 보강
       예정

7. 동일 Dataset 기반 Regression Test
       예정
```

특히 다음 단계에서는 Expansion 범위를 단순히 `±2`로 증가시키기보다, 확장된 Context를 다시 평가하여 불필요한 Context를 제거하는 전략을 우선 검토합니다.

---

## 18. 평가 결론

Golden Dataset 40건을 기준으로 Context Expansion 적용 전후를 비교한 결과:

```text
0.7125 -> 0.8488
```

로 평가 점수가 상승했습니다.

절대 개선폭은:

```text
+0.1363
```

상대 개선율은 약:

```text
+19.1%
```

입니다.

특히 여러 Chunk에 분산된 정보를 함께 요구하는 질문에서 Context Recall과 Answer Relevancy가 크게 개선되었습니다.

또한 Faithfulness와 NO_ANSWER Detection이 안정적으로 유지되어, Context를 추가하면서도 hallucination 억제 특성이 유지되는 것을 확인했습니다.

따라서 현재 `before=1 / after=1` Context Expansion은 GomsBook AI Agent RAG 구조에서 **유효한 품질 개선 단계로 평가**할 수 있습니다.

다음 최적화의 핵심은 Context Expansion 자체를 더 넓히는 것이 아니라:

```text
Retrieval Recall 개선
        +
Expanded Context Reranking / Selection
```

입니다.

---

## 19. 포트폴리오 요약

본 실험은 GomsBook AI Agent에서 RAG 품질 개선을 단순 기능 구현이 아닌 Golden Dataset 기반 정량 평가로 검증한 사례입니다.

핵심 결과:

```text
Dataset               : rag-golden-v1
Test Cases            : 40
Baseline RAGAS Score  : 0.7125
Context Expansion     : ±1 chunk
Improved RAGAS Score  : 0.8488
Absolute Improvement  : +0.1363
Relative Improvement  : +19.1%
```

이를 통해 EPUB XHTML 기반 RAG에서 인접 문맥 확장이 다중 Chunk 질의의 Context Recall과 최종 답변 품질을 개선한다는 것을 확인했습니다.
