# GomsBook RAGAS Java Evaluation Guide

> GomsBook AI Agent의 RAG 품질을 Java 환경에서 평가하기 위한 설계 및 구현 가이드

---

## 1. 개요

GomsBook AI Agent의 RAG(Retrieval-Augmented Generation) Core가 구축되었다면,
다음 단계는 검색과 답변의 품질을 정량적으로 측정하는 평가 계층을 추가하는 것이다.

RAGAS(Retrieval Augmented Generation Assessment)는 RAG 시스템 평가에 널리 사용되는
평가 프레임워크이지만, 현재 공식 구현은 Python 패키지 중심이다.

따라서 GomsBook에서는 RAGAS 라이브러리를 Java로 직접 포팅하는 대신,
**RAGAS의 핵심 평가 개념을 Java Native Evaluation Layer로 구현**한다.

이 문서에서 말하는 `RAGAS Java`는 공식 RAGAS Java SDK가 아니다.

```text
Official RAGAS
    └─ Python

GomsBook RAGAS Java
    └─ RAGAS evaluation concepts implemented in Java
```

이 접근 방식의 목적은 다음과 같다.

- 기존 GomsBook RAG Core를 유지한다.
- Python Runtime 의존성을 제거한다.
- 기존 `LlmClient`와 Ollama 모델을 평가에도 재사용한다.
- 검색 품질과 생성 품질을 분리하여 측정한다.
- Golden Dataset을 기반으로 회귀 테스트를 수행한다.
- 필요할 경우 동일 Dataset을 Python RAGAS로 교차검증한다.

---

# 2. 평가 대상

RAG 시스템은 크게 두 단계로 나눌 수 있다.

```text
Question
   │
   ▼
Retriever
   │
   ├─ Context 1
   ├─ Context 2
   └─ Context N
   │
   ▼
LLM
   │
   ▼
Answer
```

따라서 평가 역시 두 영역으로 분리한다.

## Retrieval Evaluation

Retriever가 올바른 문서를 검색했는지를 평가한다.

주요 Metric:

- Context Precision
- Context Recall

## Generation Evaluation

LLM이 검색된 Context를 근거로 올바르게 답변했는지를 평가한다.

주요 Metric:

- Faithfulness
- Answer Relevancy
- Factual Correctness

---

# 3. GomsBook 권장 초기 Metric

첫 버전에서는 다음 네 가지 Metric을 구현하는 것을 권장한다.

| Metric | 평가 대상 | 설명 |
|---|---|---|
| Faithfulness | Answer | 답변의 주장들이 검색 Context에 의해 뒷받침되는가 |
| Answer Relevancy | Answer | 질문에 적합한 답변인가 |
| Context Precision | Retriever | 검색된 Context 중 실제 관련 Context 비율이 높은가 |
| Context Recall | Retriever | 정답에 필요한 정보를 충분히 검색했는가 |

향후 다음 Metric을 추가할 수 있다.

- Factual Correctness
- Semantic Similarity
- Noise Sensitivity
- Citation Accuracy
- EPUB Domain Correctness
- Accessibility Rule Correctness

---

# 4. 공식 RAGAS 데이터 구조와 Java 대응

공식 RAGAS 평가에서 일반적으로 사용하는 데이터는 다음 네 요소로 구성된다.

```text
user_input
retrieved_contexts
response
reference
```

GomsBook Java에서는 다음과 같이 대응한다.

| RAGAS | GomsBook Java |
|---|---|
| user_input | question |
| retrieved_contexts | retrievedContexts |
| response | answer |
| reference | referenceAnswer |

예:

```json
{
  "id": "RAG-001",
  "user_input": "덕수궁 돌담길의 특징은 무엇인가요?",
  "retrieved_contexts": [
    "덕수궁 돌담길은 서울 정동을 대표하는 산책길이다.",
    "돌담길 주변에는 근대 건축물과 문화 공간이 자리한다."
  ],
  "response": "덕수궁 돌담길은 정동을 대표하는 산책길로 근대 건축물과 문화 공간을 함께 만날 수 있습니다.",
  "reference": "덕수궁 돌담길은 정동의 대표적인 산책길이며 주변에 역사·문화 공간이 있다."
}
```

---

# 5. 권장 패키지 구조

```text
kr.co.goms.gomsbook.ai.rag
│
├─ core
│
├─ retriever
│
├─ index
│
└─ eval
    │
    ├─ RagEvaluationCase.java
    ├─ RagEvaluationContext.java
    ├─ RagEvaluationResult.java
    ├─ RagEvaluator.java
    ├─ DefaultRagEvaluator.java
    │
    ├─ metric
    │   ├─ RagMetric.java
    │   ├─ FaithfulnessMetric.java
    │   ├─ AnswerRelevancyMetric.java
    │   ├─ ContextPrecisionMetric.java
    │   └─ ContextRecallMetric.java
    │
    ├─ judge
    │   ├─ RagJudge.java
    │   ├─ LlmRagJudge.java
    │   └─ RagJudgeResult.java
    │
    ├─ dataset
    │   ├─ RagEvaluationDataset.java
    │   ├─ RagEvaluationDatasetLoader.java
    │   └─ RagEvaluationDatasetWriter.java
    │
    └─ report
        ├─ RagEvaluationReport.java
        └─ RagEvaluationReportWriter.java
```

---

# 6. 핵심 데이터 클래스

## 6.1 RagEvaluationCase

Golden Dataset의 단일 테스트 Case를 표현한다.

```java
public final class RagEvaluationCase {

    private final String id;
    private final String question;
    private final String referenceAnswer;

    public RagEvaluationCase(
            String id,
            String question,
            String referenceAnswer) {

        this.id = id;
        this.question = question;
        this.referenceAnswer = referenceAnswer;
    }

    public String getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }
}
```

---

# 7. RagEvaluationContext

Metric이 평가할 전체 정보를 전달한다.

```java
public final class RagEvaluationContext {

    private final String question;

    private final List<String> retrievedContexts;

    private final String answer;

    private final String referenceAnswer;

    public RagEvaluationContext(
            String question,
            List<String> retrievedContexts,
            String answer,
            String referenceAnswer) {

        this.question = question;

        this.retrievedContexts =
                retrievedContexts == null
                        ? List.of()
                        : List.copyOf(retrievedContexts);

        this.answer = answer;
        this.referenceAnswer = referenceAnswer;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getRetrievedContexts() {
        return retrievedContexts;
    }

    public String getAnswer() {
        return answer;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }
}
```

---

# 8. RagMetric

모든 평가 Metric이 구현해야 하는 공통 인터페이스이다.

```java
public interface RagMetric {

    String getName();

    RagMetricResult evaluate(
            RagEvaluationContext context
    );
}
```

Metric별 결과를 문자열이 아닌 객체로 관리하는 것을 권장한다.

```java
public final class RagMetricResult {

    private final String metricName;

    private final double score;

    private final String reason;

    public RagMetricResult(
            String metricName,
            double score,
            String reason) {

        this.metricName = metricName;
        this.score = score;
        this.reason = reason;
    }

    public String getMetricName() {
        return metricName;
    }

    public double getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }
}
```

---

# 9. Faithfulness

Faithfulness는 생성된 Answer의 주장이 검색된 Context에 의해
얼마나 뒷받침되는지를 평가한다.

개념적으로 다음과 같이 계산할 수 있다.

```text
Faithfulness =
    Context에 의해 뒷받침되는 Answer Claim 수
    -----------------------------------------
    Answer의 전체 Claim 수
```

예:

```text
Context

덕수궁 돌담길은 서울 정동에 위치한다.
돌담길 주변에는 근대 문화유산이 있다.

Answer

덕수궁 돌담길은 정동에 있으며
주변에서 근대 문화유산을 볼 수 있다.
```

두 Claim 모두 Context에서 확인 가능하므로 높은 점수를 부여한다.

---

# 10. LLM Judge 기반 Faithfulness

자연어 Claim 분해와 entailment 판정은 단순 문자열 비교보다
LLM Judge를 사용하는 것이 현실적이다.

```text
Question
+
Retrieved Context
+
Generated Answer
        │
        ▼
Evaluation LLM
        │
        ▼
Claims
        │
        ├─ Supported
        ├─ Supported
        └─ Unsupported
        │
        ▼
Score
```

권장 Judge 결과 형식:

```json
{
  "claims": [
    {
      "claim": "덕수궁 돌담길은 정동에 있다.",
      "supported": true
    },
    {
      "claim": "덕수궁 돌담길은 조선 최초의 도로이다.",
      "supported": false
    }
  ],
  "score": 0.5
}
```

평가 LLM의 자유로운 텍스트를 직접 파싱하기보다
가능한 한 JSON 구조화 출력을 사용하는 것을 권장한다.

---

# 11. Answer Relevancy

Answer Relevancy는 Answer가 질문의 의도에 얼마나 직접적으로
응답하는지를 평가한다.

```text
Question

덕수궁 돌담길은 어디에 있나요?

Good Answer

덕수궁 돌담길은 서울 중구 정동의 덕수궁 주변에 있습니다.

Bad Answer

덕수궁은 조선시대 궁궐이며 주변에는 많은 관광지가 있습니다.
```

LLM Judge에게 다음을 평가하도록 한다.

- 질문에 직접 답했는가
- 불필요한 내용이 과도하지 않은가
- 핵심 질문을 회피하지 않았는가

점수 범위:

```text
0.0 ~ 1.0
```

---

# 12. Context Precision

Context Precision은 검색된 Context의 순위 품질을 평가한다.

예:

```text
Top-1 Relevant
Top-2 Relevant
Top-3 Irrelevant
Top-4 Irrelevant
Top-5 Irrelevant
```

관련 Context가 상위에 집중될수록 좋은 Retriever이다.

초기 Java 구현에서는 단순 비율로 시작할 수 있다.

```text
Context Precision =
    Relevant Retrieved Context Count
    --------------------------------
    Retrieved Context Count
```

이후에는 Ranking Position을 반영하는
Average Precision 방식으로 확장할 수 있다.

---

# 13. Context Recall

Context Recall은 정답 작성에 필요한 정보를 Retriever가 얼마나
충분히 가져왔는지를 평가한다.

```text
Reference Answer Claims
        │
        ├─ Claim 1 → Context에서 발견
        ├─ Claim 2 → Context에서 발견
        └─ Claim 3 → 없음
```

```text
Context Recall = 2 / 3
               = 0.6667
```

Context Recall 역시 LLM Judge 기반 Claim Matching이 유용하다.

---

# 14. RagJudge

LLM Judge와 Metric을 분리한다.

```java
public interface RagJudge {

    RagJudgeResult judge(
            String systemPrompt,
            String evaluationPrompt
    );
}
```

기존 GomsBook `LlmClient`를 Adapter 방식으로 연결할 수 있다.

```text
FaithfulnessMetric
        │
        ▼
RagJudge
        │
        ▼
LlmRagJudge
        │
        ▼
GomsBook LlmClient
        │
        ▼
Ollama
        │
        ▼
Evaluator Model
```

이렇게 하면 Metric 계층이 특정 LLM 구현에 종속되지 않는다.

---

# 15. 평가용 LLM 분리 권장

가능하면 Answer 생성 모델과 Evaluation 모델을 논리적으로 구분한다.

예:

```text
Generation

gemma4:31b-cloud

Evaluation

gemma4:31b-cloud
또는
별도 Judge Model
```

초기 개발 단계에서는 동일 모델을 사용할 수 있다.

다만 최종 벤치마크에서는 동일 모델이 자기 답변을 평가하는
self-evaluation bias 가능성을 고려해야 한다.

따라서 `EvaluatorModelProvider` 같은 추상화를 두는 것이 좋다.

---

# 16. DefaultRagEvaluator

```java
public final class DefaultRagEvaluator
        implements RagEvaluator {

    private final List<RagMetric> metrics;

    public DefaultRagEvaluator(
            List<RagMetric> metrics) {

        this.metrics = List.copyOf(metrics);
    }

    @Override
    public RagEvaluationResult evaluate(
            RagEvaluationContext context) {

        List<RagMetricResult> results =
                new ArrayList<>();

        for (RagMetric metric : metrics) {

            results.add(
                    metric.evaluate(context)
            );
        }

        return new RagEvaluationResult(
                results
        );
    }
}
```

Metric을 Plugin 형태로 추가할 수 있어야 한다.

```java
List<RagMetric> metrics =
        List.of(
                new FaithfulnessMetric(judge),
                new AnswerRelevancyMetric(judge),
                new ContextPrecisionMetric(judge),
                new ContextRecallMetric(judge)
        );
```

---

# 17. 전체 Evaluation Pipeline

GomsBook에서 권장하는 실제 실행 흐름은 다음과 같다.

```text
RagEvaluationCase
        │
        ▼
question
        │
        ▼
DefaultRetriever
        │
        ├─ RetrievedChunk #1
        ├─ RetrievedChunk #2
        ├─ RetrievedChunk #3
        ├─ RetrievedChunk #4
        └─ RetrievedChunk #5
        │
        ▼
PromptAugmentor
        │
        ▼
LlmClient
        │
        ▼
answer
        │
        ▼
RagEvaluationContext
        │
        ▼
DefaultRagEvaluator
        │
        ├─ Faithfulness
        ├─ Answer Relevancy
        ├─ Context Precision
        └─ Context Recall
        │
        ▼
RagEvaluationResult
```

---

# 18. Golden Dataset

RAG 평가에서 가장 중요한 자산 중 하나이다.

Golden Dataset은 사람이 검증한 질문과 기대 정답으로 구성한다.

예:

```json
[
  {
    "id": "LUNCH-001",
    "question": "덕수궁 돌담길은 어떤 장소인가요?",
    "referenceAnswer": "덕수궁 돌담길은 서울 정동의 대표적인 산책길이다."
  },
  {
    "id": "LUNCH-002",
    "question": "서울시립미술관은 책에서 어떤 공간으로 소개되나요?",
    "referenceAnswer": "서울시립미술관은 점심시간에 예술을 접할 수 있는 장소로 소개된다."
  }
]
```

초기에는 약 10~20문항으로 시작한다.

이후 다음 카테고리로 확장한다.

```text
Golden Dataset
│
├─ Simple Fact
├─ Multiple Context
├─ Semantic Search
├─ Similar Terminology
├─ Negative Question
├─ No Answer
├─ Chapter Boundary
├─ Accessibility
└─ EPUB Structure
```

---

# 19. GomsBook에 특히 필요한 No-Answer 테스트

RAG 시스템에서 중요한 테스트이다.

프로젝트 문서에 없는 내용을 질문한다.

```text
Question

책에서 제주도 성산일출봉을 어떻게 설명하나요?
```

현재 프로젝트가 서울 산책 관련 EPUB이고 제주도 내용이 없다면
RAG는 근거 없는 답변을 생성해서는 안 된다.

기대 결과:

```text
해당 프로젝트 문서에서 관련 정보를 찾을 수 없습니다.
```

이 테스트는 Hallucination 방지에 매우 중요하다.

---

# 20. Score 기준

초기 기준값 예:

| Metric | 권장 초기 Threshold |
|---|---:|
| Faithfulness | >= 0.90 |
| Answer Relevancy | >= 0.85 |
| Context Precision | >= 0.80 |
| Context Recall | >= 0.85 |

Overall Score는 단순 평균부터 시작할 수 있다.

```java
overall =
        (
            faithfulness
            + answerRelevancy
            + contextPrecision
            + contextRecall
        ) / 4.0;
```

다만 최종적으로는 Metric별 점수를 별도로 보는 것을 권장한다.

예를 들어:

```text
Faithfulness       0.98
Answer Relevancy   0.94
Context Precision  0.55
Context Recall     1.00
```

Overall은 높게 보일 수 있지만,
Context Precision이 낮으므로 Retriever 개선이 필요하다.

---

# 21. 평가 결과 예

```text
================================================
GomsBook RAG Evaluation
================================================

Case ID
LUNCH-001

Question
덕수궁 돌담길은 어떤 장소인가요?

Retrieved Contexts
5

Faithfulness
0.96

Answer Relevancy
0.93

Context Precision
0.80

Context Recall
1.00

Overall
0.9225

Status
PASS
================================================
```

---

# 22. Retriever Parameter 비교

RAG Evaluation을 구현하는 가장 큰 이유 중 하나이다.

현재 설정:

```text
Embedding
nomic-embed-text

Top-K
5

Rerank
enabled
```

평가를 통해 다음을 비교할 수 있다.

## Top-K

```text
Top-K = 3
vs
Top-K = 5
vs
Top-K = 10
```

## Chunk Size

```text
300
vs
500
vs
800
```

## Embedding Model

```text
nomic-embed-text
vs
other embedding model
```

## Reranking

```text
Rerank OFF
vs
Rerank ON
```

---

# 23. 실험 결과 저장

다음과 같은 결과 모델을 권장한다.

```json
{
  "experimentId": "EXP-2026-001",
  "embeddingModel": "nomic-embed-text",
  "topK": 5,
  "rerank": true,
  "average": {
    "faithfulness": 0.94,
    "answerRelevancy": 0.91,
    "contextPrecision": 0.83,
    "contextRecall": 0.95
  }
}
```

향후 다음 비교가 가능해진다.

```text
EXP-001
TopK=3

EXP-002
TopK=5

EXP-003
TopK=10
```

---

# 24. Regression Test

RAG Core를 수정할 때마다 Golden Dataset을 다시 평가한다.

```text
Code Change
    │
    ▼
Index
    │
    ▼
Run Golden Dataset
    │
    ▼
Calculate Metrics
    │
    ▼
Compare Baseline
```

예:

```text
Before

Faithfulness       0.94
Context Precision  0.86

After

Faithfulness       0.96
Context Precision  0.91
```

개선 여부를 정량적으로 판단할 수 있다.

---

# 25. Baseline

첫 번째 정상 평가 결과를 Baseline으로 저장한다.

```json
{
  "baseline": "RAG-BASELINE-001",
  "metrics": {
    "faithfulness": 0.93,
    "answerRelevancy": 0.90,
    "contextPrecision": 0.84,
    "contextRecall": 0.92
  }
}
```

향후 변경사항은 이 Baseline과 비교한다.

---

# 26. Python RAGAS와 교차검증

Java Native 평가기를 구현한 뒤 동일 Dataset을
공식 Python RAGAS에서도 평가할 수 있다.

```text
                Golden Dataset
                     │
              ┌──────┴──────┐
              ▼             ▼

       Java Evaluator   Python RAGAS

              │             │
              ▼             ▼

          Java Score     RAGAS Score

              └──────┬──────┘
                     ▼

               Cross Validation
```

Java와 Python 점수가 완전히 같을 필요는 없다.

LLM Judge, Prompt, Metric 세부 알고리즘이 다르면 결과 차이가 발생한다.

목적은 다음을 확인하는 것이다.

- 평가 방향이 일관적인가
- 개선 전후 추세가 동일한가
- 특정 Metric이 비정상적으로 편향되지 않았는가

---

# 27. 공식 RAGAS와 1:1 복제를 목표로 하지 않는 이유

RAGAS 내부 알고리즘과 Prompt는 버전에 따라 변경될 수 있다.

Java 구현이 공식 RAGAS 내부 코드를 그대로 재현하도록 만들면
다음 문제가 발생한다.

- RAGAS 버전 변경에 강하게 종속됨
- Java 코드 복잡도 증가
- Python 구현 세부사항을 계속 추적해야 함
- GomsBook 도메인 Metric 추가가 어려워짐

따라서 다음 원칙을 사용한다.

```text
RAGAS concept compatible

but

RAGAS implementation independent
```

즉, 평가 철학과 데이터 모델은 호환하되
구현은 GomsBook Java Architecture에 맞춘다.

---

# 28. EPUB Domain Metric

GomsBook에는 일반 RAG Metric 외에 도메인 Metric을 추가할 수 있다.

예:

```text
EpubAccessibilityMetric

XhtmlValidityMetric

AltTextCorrectnessMetric

NavigationCorrectnessMetric

ProjectCitationMetric
```

예를 들어:

```text
Question

장식용 이미지의 alt는 어떻게 해야 하나요?

Reference

장식용 이미지는 alt=""를 사용한다.

Answer

alt 속성을 제거한다.
```

일반 의미 유사도만으로는 어느 정도 관련 답변으로 판단될 수 있지만,
EPUB Accessibility 관점에서는 잘못된 답변이다.

따라서 Domain Metric이 필요하다.

---

# 29. 추천 최종 Architecture

```text
GomsBook AI Agent
│
├─ Agent
├─ LLM
├─ Tool
├─ MCP
├─ Chat
│
└─ RAG
    │
    ├─ Loader
    ├─ Chunker
    ├─ Embedding
    ├─ VectorStore
    ├─ Retriever
    ├─ Reranker
    ├─ PromptAugmentor
    ├─ RagService
    │
    └─ Evaluation
        │
        ├─ Dataset
        │   └─ Golden Dataset
        │
        ├─ Evaluator
        │
        ├─ Judge
        │
        ├─ Metric
        │   ├─ Faithfulness
        │   ├─ Answer Relevancy
        │   ├─ Context Precision
        │   ├─ Context Recall
        │   └─ EPUB Domain Metrics
        │
        ├─ Report
        │
        └─ Regression
```

---

# 30. 구현 단계

권장 구현 순서:

```text
Phase 1

RagEvaluationCase
RagEvaluationContext
RagMetricResult

        ↓

Phase 2

RagMetric
RagJudge
LlmRagJudge

        ↓

Phase 3

FaithfulnessMetric

        ↓

Phase 4

AnswerRelevancyMetric
ContextPrecisionMetric
ContextRecallMetric

        ↓

Phase 5

RagEvaluator
DefaultRagEvaluator

        ↓

Phase 6

Golden Dataset
Dataset Loader

        ↓

Phase 7

Evaluation Report

        ↓

Phase 8

Baseline / Regression Test

        ↓

Phase 9

Python RAGAS Cross Validation

        ↓

Phase 10

EPUB Domain Metrics
```

---

# 31. 첫 구현 목표

처음부터 모든 Metric을 만들기보다
Faithfulness 하나를 End-to-End로 먼저 완성한다.

```text
RagEvaluationCase
        ↓
Retriever
        ↓
RAG Answer
        ↓
RagEvaluationContext
        ↓
FaithfulnessMetric
        ↓
LlmRagJudge
        ↓
Faithfulness Score
```

이 흐름이 정상 동작한 후 나머지 Metric을 추가한다.

---

# 32. GomsBook 권장 평가 전략

최종적으로 다음 세 단계 평가 체계를 권장한다.

```text
Level 1
Retriever Evaluation

Context Precision
Context Recall

        ↓

Level 2
Answer Evaluation

Faithfulness
Answer Relevancy
Factual Correctness

        ↓

Level 3
Domain Evaluation

EPUB
Accessibility
XHTML
Navigation
Project Citation
```

이 구조는 일반적인 RAG 품질과
GomsBook 고유의 전자책 도메인 정확성을 동시에 평가할 수 있다.

---

# 33. 주의사항

## LLM Judge는 절대적인 정답 판정기가 아니다.

평가 모델도 오류를 낼 수 있다.

따라서 중요한 테스트 Case는 사람이 검증한
Golden Dataset을 사용한다.

## Metric Prompt를 버전 관리한다.

Prompt 변경만으로 점수가 달라질 수 있다.

예:

```text
eval/prompts/
├─ faithfulness-v1.txt
├─ answer-relevancy-v1.txt
├─ context-precision-v1.txt
└─ context-recall-v1.txt
```

## 평가 환경을 기록한다.

최소 다음 값을 함께 저장한다.

```text
Generation Model
Evaluation Model
Embedding Model
Chunk Size
Chunk Overlap
Top-K
Reranker
Prompt Version
Dataset Version
```

그래야 실험 결과를 재현할 수 있다.

---

# 34. Java Native 평가의 장점

GomsBook에서 Java Native Evaluation Layer를 구축하면 다음 장점이 있다.

```text
No Python Runtime

No LangChain dependency

No LangChain4j dependency

Existing LlmClient reuse

Existing Retriever reuse

Eclipse RCP integration

Tool integration

CI regression evaluation

Domain-specific metrics
```

즉 단순한 RAGAS 대체 구현이 아니라,
GomsBook AI Agent의 품질 관리 계층으로 사용할 수 있다.

---

# 35. 최종 목표

```text
EPUB Project
    │
    ▼
Automatic Indexing
    │
    ▼
RAG Query
    │
    ▼
Retriever
    │
    ▼
LLM Answer
    │
    ▼
Automatic Evaluation
    │
    ├─ Retrieval Quality
    ├─ Answer Quality
    ├─ Hallucination Detection
    └─ EPUB Domain Accuracy
    │
    ▼
Regression Report
```

최종적으로 GomsBook AI Agent는 단순히
"RAG가 동작한다" 수준이 아니라,

**RAG 품질을 측정하고, 비교하고, 회귀를 탐지할 수 있는 시스템**

으로 발전하는 것을 목표로 한다.

---

# 36. 참고

공식 RAGAS는 Python 패키지로 제공되며, 설치는 `pip install ragas` 방식이다.

공식 RAGAS의 최신 Quick Start와 RAG 평가 예제는 다음 개념을 중심으로 한다.

- Evaluation Dataset
- user_input
- retrieved_contexts
- response
- reference
- Faithfulness
- Context Recall
- Factual Correctness
- LLM 기반 Metric
- Dataset 기반 반복 평가
- 실험 결과 저장

Java Native 구현에서는 이러한 개념을 참고하되,
GomsBook의 기존 RAG Core와 LLM 추상화에 맞추어 독립적으로 구현한다.

---

## 다음 구현

다음 단계:

```text
RagEvaluationCase.java
```

이후:

```text
RagEvaluationContext.java
RagMetricResult.java
RagMetric.java
```

순서로 구현한다.

