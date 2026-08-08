/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tool 실행 중 공유되는 컨텍스트 정보입니다.
 *
 * <p>Agent 요청과 Tool 실행 사이에서 다음 정보를 전달합니다.</p>
 *
 * <ul>
 *     <li>Agent 요청 식별자</li>
 *     <li>대화 세션 식별자</li>
 *     <li>프로젝트 경로, 현재 문서 등 확장 속성</li>
 * </ul>
 *
 * <p>사용 예시:</p>
 *
 * <pre>
 * ToolContext context = ToolContext.builder()
 *         .requestId("request-001")
 *         .sessionId("session-001")
 *         .attribute("projectPath", "C:/workspace/GomsBook")
 *         .attribute("currentFile", "chapter01.xhtml")
 *         .build();
 * </pre>
 */
public final class ToolContext {

    private final String requestId;
    private final String sessionId;
    private final Map<String, Object> attributes;

    private ToolContext(Builder builder) {
        this.requestId = normalizeOptional(
                builder.requestId
        );

        this.sessionId = normalizeOptional(
                builder.sessionId
        );

        this.attributes = immutableAttributes(
                builder.attributes
        );
    }

    /**
     * 빈 Tool Context를 생성합니다.
     */
    public ToolContext() {
        this(builder());
    }

    /**
     * 확장 속성만 포함하는 Tool Context를 생성합니다.
     *
     * @param attributes 확장 속성
     */
    public ToolContext(
            Map<String, Object> attributes) {

        this(
                builder()
                        .attributes(attributes)
        );
    }

    /**
     * Builder를 생성합니다.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기존 Context를 기반으로 Builder를 생성합니다.
     *
     * @param source 원본 Tool Context
     * @return 원본 값이 복사된 Builder
     */
    public static Builder builder(ToolContext source) {
        Objects.requireNonNull(
                source,
                "source must not be null"
        );

        return new Builder(source);
    }

    /**
     * Agent 요청 식별자를 반환합니다.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 대화 세션 식별자를 반환합니다.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 확장 속성 목록을 반환합니다.
     *
     * @return 수정할 수 없는 속성 Map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 요청 식별자가 존재하는지 확인합니다.
     */
    public boolean hasRequestId() {
        return requestId != null;
    }

    /**
     * 세션 식별자가 존재하는지 확인합니다.
     */
    public boolean hasSessionId() {
        return sessionId != null;
    }

    /**
     * 확장 속성이 존재하는지 확인합니다.
     */
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    /**
     * 지정한 속성이 존재하는지 확인합니다.
     *
     * @param name 속성명
     * @return 속성이 존재하면 {@code true}
     */
    public boolean containsAttribute(String name) {
        return name != null
                && attributes.containsKey(name);
    }

    /**
     * 속성값을 반환합니다.
     *
     * @param name 속성명
     * @return 속성값 또는 {@code null}
     */
    public Object getAttribute(String name) {
        if (name == null) {
            return null;
        }

        return attributes.get(name);
    }

    /**
     * 속성값을 지정한 타입으로 반환합니다.
     *
     * @param name 속성명
     * @param type 반환 타입
     * @param <T>  반환 타입
     * @return 속성값 또는 {@code null}
     */
    public <T> T getAttribute(
            String name,
            Class<T> type) {

        Objects.requireNonNull(
                type,
                "type must not be null"
        );

        Object value = getAttribute(name);

        if (value == null) {
            return null;
        }

        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Tool context attribute type mismatch. "
                            + "name=" + name
                            + ", expected=" + type.getName()
                            + ", actual="
                            + value.getClass().getName()
            );
        }

        return type.cast(value);
    }

    /**
     * 속성값을 기본값과 함께 반환합니다.
     */
    public <T> T getAttributeOrDefault(
            String name,
            Class<T> type,
            T defaultValue) {

        T value = getAttribute(name, type);

        return value != null
                ? value
                : defaultValue;
    }

    /**
     * 필수 속성값을 반환합니다.
     *
     * @param name 속성명
     * @return 속성값
     * @throws IllegalArgumentException 속성이 없거나 값이 null인 경우
     */
    public Object requireAttribute(String name) {
        validateAttributeName(name);

        if (!attributes.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Required Tool context attribute is missing: "
                            + name
            );
        }

        Object value = attributes.get(name);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Required Tool context attribute "
                            + "must not be null: "
                            + name
            );
        }

        return value;
    }

    /**
     * 필수 속성값을 지정한 타입으로 반환합니다.
     */
    public <T> T requireAttribute(
            String name,
            Class<T> type) {

        Objects.requireNonNull(
                type,
                "type must not be null"
        );

        Object value = requireAttribute(name);

        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Required Tool context attribute type mismatch. "
                            + "name=" + name
                            + ", expected=" + type.getName()
                            + ", actual="
                            + value.getClass().getName()
            );
        }

        return type.cast(value);
    }

    /**
     * 필수 문자열 속성을 반환합니다.
     */
    public String requireStringAttribute(String name) {
        Object value = requireAttribute(name);

        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(
                    "Required Tool context attribute "
                            + "must be a string: "
                            + name
            );
        }

        if (stringValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Required Tool context string attribute "
                            + "must not be blank: "
                            + name
            );
        }

        return stringValue;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static Map<String, Object> immutableAttributes(
            Map<String, Object> source) {

        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> copied =
                new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry
                : source.entrySet()) {

            String name = entry.getKey();

            validateAttributeName(name);

            copied.put(
                    name.trim(),
                    deepCopyValue(entry.getValue())
            );
        }

        return Collections.unmodifiableMap(copied);
    }

    /**
     * 중첩 Map과 Iterable을 복사하여 외부 변경 영향을 줄입니다.
     */
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied =
                    new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException(
                            "Nested Tool context attribute Map "
                                    + "must not contain null keys"
                    );
                }

                copied.put(
                        String.valueOf(entry.getKey()),
                        deepCopyValue(entry.getValue())
                );
            }

            return Collections.unmodifiableMap(copied);
        }

        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> copied =
                    new java.util.ArrayList<>();

            for (Object item : iterable) {
                copied.add(deepCopyValue(item));
            }

            return Collections.unmodifiableList(copied);
        }

        return value;
    }

    private static void validateAttributeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "attribute name must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "ToolContext{"
                + "requestId='" + requestId + '\''
                + ", sessionId='" + sessionId + '\''
                + ", attributeNames="
                + attributes.keySet()
                + '}';
    }

    /**
     * ToolContext Builder입니다.
     */
    public static final class Builder {

        private String requestId;
        private String sessionId;

        private final Map<String, Object> attributes =
                new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(ToolContext source) {
            this.requestId = source.requestId;
            this.sessionId = source.sessionId;
            this.attributes.putAll(source.attributes);
        }

        /**
         * Agent 요청 식별자를 설정합니다.
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * 세션 식별자를 설정합니다.
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * 확장 속성을 추가하거나 변경합니다.
         */
        public Builder attribute(
                String name,
                Object value) {

            validateAttributeName(name);

            this.attributes.put(
                    name.trim(),
                    value
            );

            return this;
        }

        /**
         * 여러 확장 속성을 추가합니다.
         */
        public Builder attributes(
                Map<String, ?> attributes) {

            Objects.requireNonNull(
                    attributes,
                    "attributes must not be null"
            );

            for (Map.Entry<String, ?> entry
                    : attributes.entrySet()) {

                attribute(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            return this;
        }

        /**
         * 확장 속성을 제거합니다.
         */
        public Builder removeAttribute(String name) {
            validateAttributeName(name);
            this.attributes.remove(name);
            return this;
        }

        /**
         * 모든 확장 속성을 제거합니다.
         */
        public Builder clearAttributes() {
            this.attributes.clear();
            return this;
        }

        /**
         * ToolContext를 생성합니다.
         */
        public ToolContext build() {
            return new ToolContext(this);
        }
    }
}