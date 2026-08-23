# GomsBook AI Agent - RAGAS형 RAG Evaluation 테스트 가이드

## 1. 개요

GomsBook AI Agent의 RAG(Retrieval-Augmented Generation) 품질을
정량적으로 검증하기 위한 테스트 문서입니다.

본 Evaluation 계층은 Python RAGAS 라이브러리를 직접 사용하는 구조가
아니라, **RAGAS의 핵심 평가 개념을 Java 기반 GomsBook AI Agent 구조에
맞게 구현한 평가 계층**입니다.

현재 `rag-smoke-v1` Golden Dataset 5문항 기준 최종 평균 점수는
**0.9250**입니다.

## 2. 테스트 목적

RAG Evaluation은 LLM 답변뿐 아니라 전체 파이프라인을 분리하여
검증합니다.

``` text
EPUB Project
    ↓
Document Loading / Chunking
    ↓
Embedding
    ↓
Project Scoped VectorStore
    ↓
Semantic Candidate Retrieval
    ↓
Hybrid Rerank
    ↓
TOP-K Context
    ↓
LLM Answer
    ↓
RAG Evaluation
```

진단 기준:

``` text
정답 Chunk 자체가 없음
→ Loader / Chunker / Indexer 문제

정답 Chunk는 존재하지만 Candidate에 없음
→ Embedding / Vector Retrieval 문제

Candidate에는 있지만 FINAL TOP-K에 없음
→ Rerank / Context Selection 문제

올바른 Context가 있는데 답변이 틀림
→ Prompt / LLM Generation 문제

문서에 답이 없는데 답변을 생성함
→ No-Answer / Hallucination 문제
```

## 3. Evaluation 계층

주요 구성은 다음과 같습니다.

-   Dataset: `RagEvaluationDataset`, `RagEvaluationDatasetLoader`,
    `RagEvaluationDatasetWriter`, `RagEvaluationCase`
-   Execution: `RagExecutionResult`, `DefaultRagExecutionAdapter`,
    `RagEvaluationRunner`
-   Report: `RagEvaluationReport`, `RagEvaluationReportWriter`
-   Regression: `RagEvaluationBaseline`, `RagEvaluationBaselineLoader`,
    `RagEvaluationBaselineWriter`, `RagEvaluationRegressionResult`,
    `RagEvaluationRegressionChecker`, `RagEvaluationRegressionWriter`
-   Runtime: `RagEvaluationRuntime`, `RagEvaluationComponentFactory`

주요 Metric:

-   `faithfulness`
-   `answer_relevancy`
-   `context_precision`
-   `context_recall`
-   `no_answer_detection`

## 4. Golden Dataset

현재 Smoke Test Dataset:

``` text
eval/dataset/rag-smoke-v1.json
```

기본 구조:

``` json
{
  "name": "rag-smoke-v1",
  "projectId": "lunchwork_seoul",
  "cases": [
    {
      "id": "RAG-SMOKE-001",
      "question": "서울시립미술관은 점심시간에 어떤 장점이 있는 공간으로 소개되나요?",
      "referenceAnswer": "..."
    }
  ]
}
```

Dataset의 `projectId`와 현재 EPUB Project ID가 반드시 일치해야 합니다.

``` text
Dataset Project ID = lunchwork_seoul
Current Project ID = lunchwork_seoul
```

## 5. Smoke Test 5문항

### RAG-SMOKE-001

**질문:** 서울시립미술관은 점심시간에 어떤 장점이 있는 공간으로
소개되나요?

목적: 일반 설명형 질문, 관련 Context 검색, 복수 사실의 Context Recall
확인.

### RAG-SMOKE-002

**질문:** 서울시립미술관에서 본 전시 제목은 무엇인가요?

핵심 정답 Chunk:

``` text
chapter10_2.xhtml#p_15
그날 가장 먼저 만난 전시는 『유영국: 산의 내 안에 있다』였다.
```

목적: Factual Query, 제목 검색, Candidate Retrieval 및 Hybrid Rerank
검증.

### RAG-SMOKE-003

**질문:** 예전의 저자에게 덕수궁 돌담길은 어떤 의미였나요?

핵심 내용:

``` text
'여유'가 아니라 '시간과의 경주'
막차를 향해 뛰어가던 기억
```

목적: 서술적 의미 검색, Heading 기반 검색, 복수 Context 결합 검증.

### RAG-SMOKE-004

**질문:** 정동길에서 우연히 만난 음악회는 어떤 모습이었나요?

핵심 내용:

``` text
이화여고 앞
첼로 소리
작은 무대
네 명의 첼리스트
평화를 위한 화목 음악회
시민들이 발걸음을 멈추고 음악을 들음
```

목적: 여러 Paragraph에 분산된 정보와 Context Precision/Recall 검증. 향후
Context Expansion의 핵심 기준 Case입니다.

### RAG-SMOKE-005

**질문:** 이 책에서는 부산 해운대 해수욕장을 어떻게 소개하나요?

Reference:

``` text
해당 프로젝트 문서에는 부산 해운대 해수욕장에 대한 정보가 없다.
```

목적: Hallucination 방지 및 No-Answer Detection 검증.

## 6. Case Type과 Metric 적용

ANSWERABLE Case:

``` text
Faithfulness
Answer Relevancy
Context Precision
Context Recall
No-Answer Detection = N/A
```

NO_ANSWER Case:

``` text
Faithfulness
Answer Relevancy
Context Precision = N/A
Context Recall = N/A
No-Answer Detection
```

NO_ANSWER Case에 Context Precision/Recall을 적용하지 않음으로써 존재하지
않는 정답 검색을 강제하는 평가 왜곡을 방지합니다.

## 7. Metric 해석

### Faithfulness

Answer의 사실적 주장이 Retrieved Context에 의해 뒷받침되는지 평가합니다.
`1.0`은 답변의 사실적 내용이 Context에 의해 완전히 지원됨을 의미합니다.

### Answer Relevancy

Answer가 Question에 직접적이고 충분하게 답하는지 평가합니다.

### Context Precision

검색된 Context 중 실제 질문 및 Reference Answer에 유용한 Context의
품질을 평가합니다. 낮은 값은 TOP-K에 잡음 Chunk가 많다는 신호입니다.

### Context Recall

Reference Answer에 필요한 정보가 Retrieved Context에 얼마나 포함되어
있는지 평가합니다. 낮은 값은 필요한 정보 일부가 TOP-K 밖에 있음을 의미할
수 있습니다.

### No-Answer Detection

NO_ANSWER Case에서 LLM이 문서에 없는 내용을 생성하지 않고 정보 부재를
정확하게 판단하는지 평가합니다.

## 8. Retriever 진단

RAW와 FINAL 결과를 함께 출력하여 Vector Retrieval과 Hybrid Rerank를
분리해서 확인합니다.

``` text
[RAG] Retrieval query = [RAW] ...
[RAG][TOP-N]
[RAG] score      = ...
[RAG] sourcePath = ...
[RAG] chunkId    = ...
[RAG] heading    = ...
[RAG] type       = ...
[RAG] sequence   = ...
[RAG] text       = ...
```

FINAL:

``` text
[RAG] Retrieval query = [FINAL] ...
```

`RAW Rank`는 Vector Similarity 기준, `FINAL Rank`는 Hybrid Rerank 적용
후 순위입니다.

## 9. Hybrid Rerank 검증

### RAG-SMOKE-002

정답 `chapter10_2.xhtml#p_15`은 초기 Vector Search에서 **RAW Rank
28**이었습니다.

Candidate 범위 확대 및 Hybrid Rerank 후:

``` text
RAW Rank   = 28
FINAL Rank = 2
```

최종 평가:

``` text
Faithfulness      = 1.0000
Answer Relevancy  = 1.0000
Context Precision = 1.0000
Context Recall    = 1.0000
Overall           = 1.0000
```

### RAG-SMOKE-003

초기 `Overall = 0.2500`에서 Hybrid Rerank 개선 후 `Overall = 1.0000`으로
향상되었습니다.

### RAG-SMOKE-004

관련 Chunk들이 RAW 약 28\~44위권에서 Hybrid Rerank 후 상위권으로
이동했습니다. 다만 최종 LLM Context가 TOP-5이므로 일부 정보가 제외되어
Precision/Recall이 각각 0.6000입니다.

## 10. 최종 Smoke Test 결과

  ---------------------------------------------------------------------------------------
  Case              Faithfulness      Answer     Context    Context  No Answer    Overall
                                   Relevancy   Precision     Recall            
  --------------- -------------- ----------- ----------- ---------- ---------- ----------
  RAG-SMOKE-001           1.0000      1.0000      0.6000     0.7000        N/A     0.8250

  RAG-SMOKE-002           1.0000      1.0000      1.0000     1.0000        N/A     1.0000

  RAG-SMOKE-003           1.0000      1.0000      1.0000     1.0000        N/A     1.0000

  RAG-SMOKE-004           1.0000      1.0000      0.6000     0.6000        N/A     0.8000

  RAG-SMOKE-005           1.0000      1.0000         N/A        N/A     1.0000     1.0000
  ---------------------------------------------------------------------------------------

``` text
Dataset       = rag-smoke-v1
Cases         = 5
Average Score = 0.9250
```

## 11. 개선 이력

``` text
초기 평가
0.4500
    ↓
Project Scope / Retrieval 점검
0.5350
    ↓
Hybrid Rerank 1차
0.6800
    ↓
Candidate Recall 확대 + Hybrid Rerank
0.9250
```

점수 향상은 단순 Prompt 조정이 아니라 실제 정답 Chunk 검색 순위 개선과
함께 발생했습니다.

## 12. 현재 Retriever 기준

현재 `DefaultRetriever`는 RAG Retrieval 1차 기준 버전으로 유지합니다.

``` text
Final Top-K          = 5
Candidate Top-K      = Final Top-K × 10
Hybrid Rerank        = 사용
Heading Boost        = 사용
Content Keyword      = 사용
Title Pattern Boost  = 사용
RAW / FINAL Log      = 사용
Minimum Score        = 0.45
```

현재 성공 Case를 유지하기 위해 Retriever의 추가 미세조정보다는 다음
개선을 별도 Context 계층으로 진행합니다.

## 13. Baseline 및 Regression

현재:

``` text
Average Score = 0.9250
```

을 신규 Baseline 후보로 사용합니다.

향후:

``` text
Baseline = 0.9250
Current  = 신규 평가 점수
Delta    = Current - Baseline
```

으로 Regression 여부를 판단합니다.

재평가 대상 변경 예:

-   Chunker
-   Embedding Model
-   VectorStore
-   Retriever
-   Rerank
-   Context Expansion
-   Prompt
-   LLM

## 14. 테스트 실행 절차

``` text
1. GomsBook Editor에서 대상 EPUB 프로젝트 Open
2. Current Project ID 확인
3. RAG Index Synchronize
4. eval/dataset/rag-smoke-v1.json 확인
5. Dataset projectId 검증
6. RagEvaluationRuntime 생성
7. RagEvaluationRunner 실행
8. Case별 RAG 실행
9. LLM Judge Metric 실행
10. RagEvaluationReport 생성
11. Console 결과 확인
12. Report JSON 저장
13. Baseline / Regression 비교
```

## 15. 정상 로그

``` text
[RAG-EVAL] ========================================
[RAG-EVAL] Smoke Test Complete
[RAG-EVAL] Dataset       = rag-smoke-v1
[RAG-EVAL] Cases         = 5
[RAG-EVAL] Average Score = 0.9250
[RAG-EVAL] ========================================
```

Metric 점수뿐 아니라 각 `reason`도 함께 검토해야 합니다.

## 16. 문제 진단 가이드

**Faithfulness 낮음**

Context는 맞지만 Answer가 Context 밖의 내용을 생성할 가능성이 있습니다.
Prompt / LLM Generation / Context Formatting을 확인합니다.

**Answer Relevancy 낮음**

답변이 질문을 직접 해결하지 못합니다. Question-Context 관계와 Prompt를
확인합니다.

**Context Precision 낮음**

TOP-K에 불필요한 Chunk가 많습니다. Hybrid Rerank와 Context Selection을
확인합니다.

**Context Recall 낮음**

Reference Answer 일부가 TOP-K에 없습니다. Candidate Top-K, FINAL Top-K,
Neighbor Chunk, Same Heading Context를 확인합니다.

**No-Answer Detection 낮음**

문서에 없는 내용을 생성합니다. RAG System Prompt와 No-Answer 정책을
확인합니다.

## 17. 현재 완료 상태

``` text
[ RAG Core ]

Document Loading              완료
Chunking                      완료
Embedding                     완료
Project Scoped VectorStore    완료
Incremental Indexing          완료
Semantic Retrieval            완료
Candidate Retrieval           완료
Hybrid Rerank                 1차 완료
No-Answer Generation          완료

[ RAG Evaluation ]

Golden Dataset                완료
ANSWERABLE / NO_ANSWER        완료
Faithfulness                  완료
Answer Relevancy              완료
Context Precision             완료
Context Recall                완료
No-Answer Detection           완료
N/A Metric 처리               완료
Evaluation Report             완료
Baseline / Regression 구조    완료
Smoke Test                    완료

[ rag-smoke-v1 ]

Cases                         5
Average Score                 0.9250
```

## 18. 다음 단계

Retriever 1차 개선은 현재 상태로 고정하고 다음 단계는 Context
Expansion으로 진행합니다.

``` text
Retriever
    ↓
FINAL TOP-K
    ↓
Context Expansion
    ├─ Neighbor Chunk Expansion
    ├─ Same Heading Expansion
    └─ Source Continuity
    ↓
Prompt Context
    ↓
LLM
```

`RAG-SMOKE-001`과 `RAG-SMOKE-004`의 Context Precision/Recall을 주요 개선
지표로 사용합니다. 기존 성공 Case 002, 003, 005는 Regression 없이
유지해야 합니다.

## 19. 결론

GomsBook AI Agent의 RAGAS형 Evaluation 계층은 현재 Golden Dataset
기반으로 RAG 검색부터 LLM Answer까지 End-to-End 평가할 수 있습니다.

현재 기준선:

``` text
rag-smoke-v1
Cases         = 5
Average Score = 0.9250
```

검증된 항목:

-   Project Scoped RAG
-   정답 Chunk 검색
-   Candidate Recall
-   Hybrid Rerank
-   Context 기반 LLM Answer
-   Hallucination 억제
-   No-Answer Detection
-   Metric별 평가
-   Baseline / Regression 기반 품질 관리

이 결과를 **RAG Retrieval 및 Evaluation 1차 Baseline**으로 사용하고,
이후 Context Expansion 결과를 동일 Golden Dataset으로 비교합니다.
