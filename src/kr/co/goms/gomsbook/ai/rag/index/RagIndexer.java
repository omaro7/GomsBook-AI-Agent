/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.index;

import java.util.ArrayList;
import java.util.List;

import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.model.DocumentSource;

/**
 * EPUB 프로젝트 문서를 RAG VectorStore에 인덱싱하는 서비스입니다.
 *
 * <p>구현체는 다음 단계를 수행합니다.</p>
 *
 * <ol>
 *     <li>DocumentSource를 DocumentChunk로 분할</li>
 *     <li>각 Chunk의 임베딩 텍스트 생성</li>
 *     <li>EmbeddingClient를 이용한 벡터 생성</li>
 *     <li>VectorRecord 생성</li>
 *     <li>VectorStore 저장</li>
 * </ol>
 */
public interface RagIndexer {

    /**
     * 문서 하나를 인덱싱합니다.
     *
     * <p>동일한 문서가 이미 인덱싱되어 있으면 구현체의 정책에 따라
     * 기존 레코드를 재사용하거나 갱신합니다.</p>
     *
     * @param source 인덱싱할 원본 문서
     * @return 인덱싱 결과
     * @throws RagIndexException 인덱싱 실패 시
     */
    RagIndexResult index(
        DocumentSource source
    ) throws RagIndexException;

    /**
     * 옵션을 지정하여 문서 하나를 인덱싱합니다.
     *
     * @param source 인덱싱할 원본 문서
     * @param request 인덱싱 설정
     * @return 인덱싱 결과
     * @throws RagIndexException 인덱싱 실패 시
     */
    RagIndexResult index(
        DocumentSource source,
        RagIndexRequest request
    ) throws RagIndexException;

    /**
     * 여러 문서를 인덱싱합니다.
     *
     * @param sources 인덱싱할 문서 목록
     * @return 문서별 인덱싱 결과
     * @throws RagIndexException 인덱싱 실패 시
     */
    default List<RagIndexResult> indexAll(
        List<DocumentSource> sources
    ) throws RagIndexException {

        return indexAll(
            sources,
            RagIndexRequest.defaults()
        );
    }

    /**
     * 여러 문서를 동일한 설정으로 인덱싱합니다.
     *
     * <p>기본 구현은 문서를 순차 처리합니다. 구현체에서 배치 임베딩이나
     * 트랜잭션 저장을 지원한다면 재정의할 수 있습니다.</p>
     *
     * @param sources 인덱싱할 문서 목록
     * @param request 인덱싱 설정
     * @return 문서별 인덱싱 결과
     * @throws RagIndexException 인덱싱 실패 시
     */
    default List<RagIndexResult> indexAll(
        List<DocumentSource> sources,
        RagIndexRequest request
    ) throws RagIndexException {

        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        if (request == null) {
            throw new IllegalArgumentException(
                "request must not be null"
            );
        }

        List<RagIndexResult> results =
            new ArrayList<>(sources.size());

        for (DocumentSource source : sources) {
            if (source == null) {
                continue;
            }

            try {
                results.add(
                    index(source, request)
                );

            } catch (RagIndexException exception) {
                if (!request.isContinueOnError()) {
                    throw exception;
                }

                results.add(
                    RagIndexResult.failed(
                        source.getRelativePath(),
                        exception.getMessage()
                    )
                );
            }
        }

        return List.copyOf(results);
    }

    /**
     * 이미 생성된 Chunk 목록을 직접 인덱싱합니다.
     *
     * <p>에디터 내부에서 Chunk를 별도로 생성했거나 테스트에서
     * DocumentIndexer 단계를 생략할 때 사용할 수 있습니다.</p>
     *
     * @param source 원본 문서
     * @param chunks 인덱싱할 Chunk 목록
     * @param request 인덱싱 설정
     * @return 인덱싱 결과
     * @throws RagIndexException 인덱싱 실패 시
     */
    RagIndexResult indexChunks(
        DocumentSource source,
        List<DocumentChunk> chunks,
        RagIndexRequest request
    ) throws RagIndexException;

    /**
     * 특정 원본 문서의 기존 인덱스를 삭제합니다.
     *
     * @param sourcePath 프로젝트 기준 원본 문서 경로
     * @return 삭제된 벡터 레코드 수
     * @throws RagIndexException 삭제 실패 시
     */
    int remove(
        String sourcePath
    ) throws RagIndexException;

    /**
     * 특정 원본 문서와 현재 임베딩 모델의 인덱스를 삭제합니다.
     *
     * @param sourcePath 프로젝트 기준 원본 문서 경로
     * @param model 임베딩 모델명
     * @return 삭제된 벡터 레코드 수
     * @throws RagIndexException 삭제 실패 시
     */
    int remove(
        String sourcePath,
        String model
    ) throws RagIndexException;

    /**
     * 현재 임베딩 모델로 생성된 전체 인덱스를 삭제합니다.
     *
     * @return 삭제된 벡터 레코드 수
     * @throws RagIndexException 삭제 실패 시
     */
    int removeCurrentModel()
        throws RagIndexException;

    /**
     * VectorStore의 모든 인덱스를 삭제합니다.
     *
     * @throws RagIndexException 삭제 실패 시
     */
    void clear() throws RagIndexException;

    /**
     * 문서가 현재 인덱서에서 처리 가능한지 확인합니다.
     *
     * @param source 확인할 원본 문서
     * @return 인덱싱 가능 여부
     */
    boolean supports(
        DocumentSource source
    );

    /**
     * 인덱싱 구성요소가 사용 가능한지 확인합니다.
     *
     * @return 사용 가능 여부
     */
    boolean isAvailable();
}