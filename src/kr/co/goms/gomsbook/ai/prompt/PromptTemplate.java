/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.prompt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Prompt 문자열을 표준화하기 위한 템플릿 클래스입니다.
 *
 * <p>템플릿 내부에서는 다음 형식의 변수를 사용할 수 있습니다.</p>
 *
 * <pre>
 * ${title}
 * ${content}
 * ${language}
 * </pre>
 *
 * <p>사용 예시:</p>
 *
 * <pre>
 * PromptTemplate template = PromptTemplate.of(
 *     "제목: ${title}\n내용: ${content}"
 * );
 *
 * String prompt = template.render(Map.of(
 *     "title", "첫 번째 장",
 *     "content", "본문 내용"
 * ));
 * </pre>
 * 
 * PromptTemplate template = PromptTemplate.of("""
        다음 조건에 따라 XHTML 문서를 생성하세요.

        [문서 제목]
        ${title}

        [본문]
        ${content}

        [언어]
        ${language}

        XHTML 코드만 반환하세요.
        """);

	String prompt = template.render(Map.of(
	        "title", "꽃은 자신을 재촉하지 않는다",
	        "content", "봄꽃은 피어날 때를 스스로 알고 있다.",
	        "language", "ko"
	));

 */
public final class PromptTemplate {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_.-]*)}");

    private final String template;
    private final Set<String> variables;

    private PromptTemplate(String template) {
        this.template = requireTemplate(template);
        this.variables = Collections.unmodifiableSet(
                extractVariables(this.template)
        );
    }

    /**
     * 새로운 PromptTemplate을 생성합니다.
     *
     * @param template ${변수명} 형식의 변수를 포함하는 템플릿
     * @return PromptTemplate 인스턴스
     */
    public static PromptTemplate of(String template) {
        return new PromptTemplate(template);
    }

    /**
     * 단일 변수를 치환하여 프롬프트를 생성합니다.
     *
     * @param name  변수명
     * @param value 변수값
     * @return 완성된 프롬프트
     */
    public String render(String name, Object value) {
        Objects.requireNonNull(name, "name must not be null");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(name, value);

        return render(values);
    }

    /**
     * 여러 변수를 치환하여 프롬프트를 생성합니다.
     *
     * <p>템플릿에 선언된 모든 변수값이 제공되지 않으면 예외가 발생합니다.</p>
     *
     * @param values 변수명과 변수값
     * @return 완성된 프롬프트
     */
    public String render(Map<String, ?> values) {
        Objects.requireNonNull(values, "values must not be null");

        validateRequiredVariables(values);

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = values.get(variableName);

            String replacement = value == null
                    ? ""
                    : String.valueOf(value);

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 일부 변수만 치환합니다.
     *
     * <p>값이 제공되지 않은 변수는 ${변수명} 형태로 유지됩니다.</p>
     *
     * @param values 치환할 변수값
     * @return 일부 변수가 치환된 프롬프트
     */
    public String renderPartial(Map<String, ?> values) {
        Objects.requireNonNull(values, "values must not be null");

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);

            if (!values.containsKey(variableName)) {
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(matcher.group())
                );
                continue;
            }

            Object value = values.get(variableName);

            String replacement = value == null
                    ? ""
                    : String.valueOf(value);

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 템플릿에 포함된 변수명을 반환합니다.
     *
     * @return 수정할 수 없는 변수명 집합
     */
    public Set<String> getVariables() {
        return variables;
    }

    /**
     * 원본 템플릿 문자열을 반환합니다.
     */
    public String getTemplate() {
        return template;
    }

    /**
     * 특정 변수가 템플릿에 존재하는지 확인합니다.
     */
    public boolean containsVariable(String variableName) {
        return variableName != null
                && variables.contains(variableName);
    }

    private void validateRequiredVariables(Map<String, ?> values) {
        for (String variable : variables) {
            if (!values.containsKey(variable)) {
                throw new IllegalArgumentException(
                        "Missing prompt template variable: " + variable
                );
            }
        }
    }

    private static Set<String> extractVariables(String template) {
        Set<String> variables = new java.util.LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }

    private static String requireTemplate(String template) {
        Objects.requireNonNull(template, "template must not be null");

        if (template.isBlank()) {
            throw new IllegalArgumentException(
                    "template must not be blank"
            );
        }

        return template;
    }

    @Override
    public String toString() {
        return template;
    }
}