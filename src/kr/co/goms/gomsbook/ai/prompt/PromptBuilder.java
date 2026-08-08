/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link PromptTemplate}에 변수를 순차적으로 설정하고
 * 최종 Prompt 문자열을 생성하는 Builder 클래스입니다.
 *
 * <p>사용 예시:</p>
 *
 * <pre>
 * String prompt = PromptBuilder
 *         .from(PromptTemplates.XHTML_GENERATION)
 *         .put("title", "꽃은 자신을 재촉하지 않는다")
 *         .put("author", "한정훈")
 *         .put("chapter", "1부 1장")
 *         .put("content", "봄꽃은 피어날 때를 스스로 알고 있다.")
 *         .put("style", "문학적이고 차분한 문체")
 *         .put("instruction", "문단마다 고유한 id를 부여한다.")
 *         .build();
 * </pre>
 * 
 * String prompt = PromptBuilder
        .from(PromptTemplates.XHTML_GENERATION)
        .put("title", request.getTitle())
        .put("author", request.getAuthor())
        .put("chapter", request.getChapter())
        .put("content", request.getContent())
        .putDefault("style", "문학적이고 자연스러운 한국어 문체")
        .putDefault("instruction", "")
        .build();
 */
public final class PromptBuilder {

    private final PromptTemplate template;
    private final Map<String, Object> values;

    private PromptBuilder(PromptTemplate template) {
        this.template = Objects.requireNonNull(
                template,
                "template must not be null"
        );
        this.values = new LinkedHashMap<>();
    }

    /**
     * 지정한 PromptTemplate을 기반으로 Builder를 생성합니다.
     *
     * @param template Prompt 템플릿
     * @return PromptBuilder 인스턴스
     */
    public static PromptBuilder from(PromptTemplate template) {
        return new PromptBuilder(template);
    }

    /**
     * Prompt 변수를 추가하거나 기존 값을 변경합니다.
     *
     * @param name  템플릿 변수명
     * @param value 템플릿에 삽입할 값
     * @return 현재 Builder
     */
    public PromptBuilder put(String name, Object value) {
        validateVariableName(name);

        if (!template.containsVariable(name)) {
            throw new IllegalArgumentException(
                    "Unknown prompt template variable: " + name
            );
        }

        values.put(name, value);

        return this;
    }

    /**
     * 문자열 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value 문자열 값
     * @return 현재 Builder
     */
    public PromptBuilder put(String name, String value) {
        return put(name, (Object) value);
    }

    /**
     * 정수형 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value 정수 값
     * @return 현재 Builder
     */
    public PromptBuilder put(String name, int value) {
        return put(name, Integer.valueOf(value));
    }

    /**
     * long 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value long 값
     * @return 현재 Builder
     */
    public PromptBuilder put(String name, long value) {
        return put(name, Long.valueOf(value));
    }

    /**
     * boolean 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value boolean 값
     * @return 현재 Builder
     */
    public PromptBuilder put(String name, boolean value) {
        return put(name, Boolean.valueOf(value));
    }

    /**
     * null이 아닌 경우에만 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value 템플릿에 삽입할 값
     * @return 현재 Builder
     */
    public PromptBuilder putIfNotNull(String name, Object value) {
        if (value != null) {
            put(name, value);
        }

        return this;
    }

    /**
     * 값이 비어 있지 않은 경우에만 변수를 추가합니다.
     *
     * @param name  템플릿 변수명
     * @param value 문자열 값
     * @return 현재 Builder
     */
    public PromptBuilder putIfNotBlank(String name, String value) {
        if (value != null && !value.isBlank()) {
            put(name, value);
        }

        return this;
    }

    /**
     * 변수가 설정되지 않은 경우에만 기본값을 추가합니다.
     *
     * @param name         템플릿 변수명
     * @param defaultValue 기본값
     * @return 현재 Builder
     */
    public PromptBuilder putDefault(String name, Object defaultValue) {
        validateVariableName(name);

        if (!values.containsKey(name)) {
            put(name, defaultValue);
        }

        return this;
    }

    /**
     * 여러 변수를 한 번에 추가합니다.
     *
     * @param values 변수명과 값으로 구성된 Map
     * @return 현재 Builder
     */
    public PromptBuilder putAll(Map<String, ?> values) {
        Objects.requireNonNull(values, "values must not be null");

        values.forEach(this::put);

        return this;
    }

    /**
     * 설정된 변수를 제거합니다.
     *
     * @param name 제거할 변수명
     * @return 현재 Builder
     */
    public PromptBuilder remove(String name) {
        validateVariableName(name);
        values.remove(name);

        return this;
    }

    /**
     * 현재 설정된 모든 변수를 제거합니다.
     *
     * @return 현재 Builder
     */
    public PromptBuilder clear() {
        values.clear();

        return this;
    }

    /**
     * 변수가 설정되어 있는지 확인합니다.
     *
     * @param name 변수명
     * @return 설정되어 있으면 true
     */
    public boolean contains(String name) {
        return name != null && values.containsKey(name);
    }

    /**
     * 현재 설정된 변수값을 반환합니다.
     *
     * @param name 변수명
     * @return 변수값
     */
    public Object get(String name) {
        return values.get(name);
    }

    /**
     * 현재 설정된 변수 Map을 반환합니다.
     *
     * <p>반환된 Map은 수정할 수 없습니다.</p>
     *
     * @return 설정된 변수 Map
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(values)
        );
    }

    /**
     * 모든 필수 변수를 치환하여 최종 Prompt를 생성합니다.
     *
     * <p>템플릿에 필요한 변수가 누락된 경우
     * {@link IllegalArgumentException}이 발생합니다.</p>
     *
     * @return 완성된 Prompt 문자열
     */
    public String build() {
        return template.render(values);
    }

    /**
     * 현재 설정된 변수만 치환합니다.
     *
     * <p>설정되지 않은 변수는 ${변수명} 형태로 유지됩니다.</p>
     *
     * @return 일부 변수가 치환된 Prompt 문자열
     */
    public String buildPartial() {
        return template.renderPartial(values);
    }

    /**
     * 템플릿에 필요한 모든 변수가 설정되어 있는지 확인합니다.
     *
     * @return 모든 변수가 설정되어 있으면 true
     */
    public boolean isComplete() {
        return values.keySet().containsAll(
                template.getVariables()
        );
    }

    /**
     * 설정되지 않은 필수 변수 개수를 반환합니다.
     *
     * @return 누락된 변수 개수
     */
    public int getMissingVariableCount() {
        int count = 0;

        for (String variable : template.getVariables()) {
            if (!values.containsKey(variable)) {
                count++;
            }
        }

        return count;
    }

    private static void validateVariableName(String name) {
        Objects.requireNonNull(name, "name must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "name must not be blank"
            );
        }
    }
}