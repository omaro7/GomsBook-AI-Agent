/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.index;

import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

/**
 * EPUB 프로젝트의 RAG 인덱스를 동기화하는 서비스입니다.
 *
 * <p>
 * 현재 프로젝트의 TEXT 문서를 확인하여 VectorStore 상태를
 * 실제 파일 상태와 일치시키는 역할을 담당합니다.
 * </p>
 *
 * <p>
 * 증분 인덱싱 시 다음 상태를 처리합니다.
 * </p>
 *
 * <ul>
 *     <li>NEW - 새 문서를 인덱싱합니다.</li>
 *     <li>CHANGED - 변경된 문서를 다시 인덱싱합니다.</li>
 *     <li>UNCHANGED - 변경되지 않은 문서는 건너뜁니다.</li>
 *     <li>DELETED - 삭제된 문서의 Vector를 제거합니다.</li>
 * </ul>
 *
 * <pre>
 * EpubProjectContext
 *        ↓
 * ProjectRagIndexer
 *        ↓
 * NEW / CHANGED / UNCHANGED / DELETED
 *        ↓
 * VectorStore
 *        ↓
 * ProjectIndexResult
 * </pre>
 */
public interface ProjectRagIndexer {

    /**
     * 지정된 EPUB 프로젝트의 RAG 인덱스를
     * 현재 프로젝트 파일 상태와 동기화합니다.
     *
     * <p>
     * 구현체는 프로젝트의 XHTML 문서를 검사하고,
     * 필요한 문서만 Embedding하여 VectorStore를 갱신해야 합니다.
     * </p>
     *
     * @param project 동기화할 EPUB 프로젝트
     * @return 프로젝트 인덱스 동기화 결과
     * @throws ProjectIndexException 인덱싱 또는 동기화 실패 시
     */
    ProjectIndexResult synchronize(
            EpubProjectContext project
    ) throws ProjectIndexException;


    /**
     * 현재 인덱서가 사용 가능한 상태인지 확인합니다.
     *
     * <p>
     * EmbeddingClient 또는 VectorStore가 사용할 수 없는 경우
     * false를 반환할 수 있습니다.
     * </p>
     *
     * @return 사용 가능하면 true
     */
    default boolean isAvailable() {

        return true;
    }
}