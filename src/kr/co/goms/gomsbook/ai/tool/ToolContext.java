/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tool 실행에 필요한 공통 실행 환경을 전달합니다.
 *
 * <p>
 * ToolContext는 현재 프로젝트, 문서, 요청, 실행 시작 시각 및
 * 추가 속성을 Tool 구현체에 전달하는 불변 객체입니다.
 * </p>
 *
 * <p>
 * AI Core의 독립성을 유지하기 위해 SWT, JFace, Workbench와 같은
 * Eclipse UI 객체를 직접 포함하지 않습니다.
 * </p>
 * 
 * ToolContext context = ToolContext.of(
        "REQ-20260803-0001",
        "gomsbook-project-001",
        Path.of("C:/workspace/books/my-book")
	)
	.withCurrentDocument(
	        Path.of("Text/chapter01.xhtml")
	)
	.withAttribute(
	        "language",
	        "ko"
	)
	.withAttribute(
	        "epubVersion",
	        "3.3"
	);
 */
public record ToolContext(

        /**
         * 현재 실행 요청 ID입니다.
         *
         * <p>
         * Prompt, LLM, Tool, Validation 실행을 하나의 흐름으로
         * 추적할 때 사용합니다.
         * </p>
         */
        String requestId,

        /**
         * 현재 GomsBookEditor 프로젝트 ID입니다.
         */
        String projectId,

        /**
         * 현재 책 또는 출판물 ID입니다.
         */
        String bookId,

        /**
         * 현재 프로젝트의 루트 경로입니다.
         */
        Path projectRoot,

        /**
         * 현재 작업 대상 문서의 프로젝트 상대 경로입니다.
         *
         * <p>
         * 예: {@code Text/chapter01.xhtml}
         * </p>
         */
        Path currentDocument,

        /**
         * 현재 실행을 요청한 사용자 또는 세션 ID입니다.
         */
        String userId,

        /**
         * Tool 실행 시작 시각입니다.
         */
        Instant startedAt,

        /**
         * 추가 실행 속성입니다.
         *
         * <p>
         * 예:
         * </p>
         *
         * <ul>
         *   <li>현재 선택 영역</li>
         *   <li>프로젝트 언어</li>
         *   <li>EPUB 버전</li>
         *   <li>렌더러 종류</li>
         *   <li>사용자 설정</li>
         * </ul>
         */
        Map<String, Object> attributes

) implements Serializable {

    /**
     * 정규 생성자입니다.
     */
    public ToolContext {
        requestId = requireText(
                requestId,
                "requestId"
        );

        projectId = normalizeText(projectId);
        bookId = normalizeText(bookId);
        userId = normalizeText(userId);

        projectRoot = normalizePath(projectRoot);
        currentDocument = normalizeRelativePath(currentDocument);

        startedAt = startedAt == null
                ? Instant.now()
                : startedAt;

        attributes = attributes == null
                ? Map.of()
                : Map.copyOf(attributes);

        validateCurrentDocument(
                projectRoot,
                currentDocument
        );
    }

    /**
     * 최소 정보로 ToolContext를 생성합니다.
     *
     * @param requestId  요청 ID
     * @param projectId  프로젝트 ID
     * @param projectRoot 프로젝트 루트
     * @return ToolContext
     */
    public static ToolContext of(
            String requestId,
            String projectId,
            Path projectRoot
    ) {
        return new ToolContext(
                requestId,
                projectId,
                null,
                projectRoot,
                null,
                null,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * 현재 문서의 절대 경로를 반환합니다.
     *
     * @return 현재 문서의 절대 경로
     */
    public Optional<Path> currentDocumentPath() {
        if (projectRoot == null || currentDocument == null) {
            return Optional.empty();
        }

        return Optional.of(
                projectRoot
                        .resolve(currentDocument)
                        .normalize()
        );
    }

    /**
     * 지정한 이름의 추가 속성을 조회합니다.
     *
     * @param name 속성 이름
     * @return 속성 값
     */
    public Optional<Object> attribute(
            String name
    ) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                attributes.get(name)
        );
    }

    /**
     * 지정한 타입으로 추가 속성을 조회합니다.
     *
     * @param name 속성 이름
     * @param type 기대 타입
     * @param <T> 반환 타입
     * @return 타입이 일치하는 속성 값
     */
    public <T> Optional<T> attribute(
            String name,
            Class<T> type
    ) {
        Objects.requireNonNull(
                type,
                "type must not be null."
        );

        return attribute(name)
                .filter(type::isInstance)
                .map(type::cast);
    }

    /**
     * 추가 속성을 포함하는 새로운 ToolContext를 반환합니다.
     *
     * @param name 속성 이름
     * @param value 속성 값
     * @return 새로운 ToolContext
     */
    public ToolContext withAttribute(
            String name,
            Object value
    ) {
        String normalizedName = requireText(
                name,
                "name"
        );

        Map<String, Object> newAttributes =
                new java.util.LinkedHashMap<>(
                        attributes
                );

        if (value == null) {
            newAttributes.remove(normalizedName);
        } else {
            newAttributes.put(
                    normalizedName,
                    value
            );
        }

        return new ToolContext(
                requestId,
                projectId,
                bookId,
                projectRoot,
                currentDocument,
                userId,
                startedAt,
                newAttributes
        );
    }

    /**
     * 현재 문서를 변경한 새로운 ToolContext를 반환합니다.
     *
     * @param document 프로젝트 상대 문서 경로
     * @return 새로운 ToolContext
     */
    public ToolContext withCurrentDocument(
            Path document
    ) {
        return new ToolContext(
                requestId,
                projectId,
                bookId,
                projectRoot,
                document,
                userId,
                startedAt,
                attributes
        );
    }

    /**
     * 문자열이 비어 있지 않은지 확인합니다.
     */
    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }

        return value.trim();
    }

    /**
     * 선택 입력 문자열을 정규화합니다.
     */
    private static String normalizeText(
            String value
    ) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    /**
     * 경로를 절대 정규화합니다.
     */
    private static Path normalizePath(
            Path path
    ) {
        return path == null
                ? null
                : path.toAbsolutePath().normalize();
    }

    /**
     * 현재 문서는 프로젝트 상대 경로만 허용합니다.
     */
    private static Path normalizeRelativePath(
            Path path
    ) {
        if (path == null) {
            return null;
        }

        Path normalized = path.normalize();

        if (normalized.isAbsolute()) {
            throw new IllegalArgumentException(
                    "currentDocument must be a project-relative path."
            );
        }

        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException(
                    "currentDocument must not escape the project directory."
            );
        }

        return normalized;
    }

    /**
     * 현재 문서가 프로젝트 경로 밖으로 벗어나지 않는지 확인합니다.
     */
    private static void validateCurrentDocument(
            Path projectRoot,
            Path currentDocument
    ) {
        if (projectRoot == null || currentDocument == null) {
            return;
        }

        Path resolved = projectRoot
                .resolve(currentDocument)
                .normalize();

        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "currentDocument must be located inside projectRoot."
            );
        }
    }
}