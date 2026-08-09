/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 문서 임베딩 벡터를 저장하고 검색하는 저장소 인터페이스입니다.
 *
 * <pre>
 * DocumentChunk
 *      ↓ embedding
 * VectorRecord
 *      ↓ save
 * VectorStore
 *
 * query text
 *      ↓ embedding
 * VectorSearchRequest
 *      ↓ search
 * List&lt;VectorSearchResult&gt;
 * </pre>
 */
public interface VectorStore extends AutoCloseable {

    /**
     * 벡터 레코드 하나를 저장합니다.
     *
     * 동일한 ID와 모델을 가진 레코드가 이미 존재하면 구현체 정책에 따라
     * 갱신 또는 대체합니다.
     *
     * @param record 저장할 벡터 레코드
     * @throws VectorStoreException 저장 실패 시
     */
    void save(
        VectorRecord record
    ) throws VectorStoreException;

    /**
     * 여러 벡터 레코드를 저장합니다.
     *
     * 기본 구현은 각 레코드에 대해 {@link #save(VectorRecord)}를
     * 순차적으로 호출합니다.
     *
     * 구현체가 트랜잭션이나 배치 쓰기를 지원하면 재정의하는 것이 좋습니다.
     *
     * @param records 저장할 벡터 레코드 목록
     * @throws VectorStoreException 저장 실패 시
     */
    default void saveAll(
        List<VectorRecord> records
    ) throws VectorStoreException {

        if (records == null || records.isEmpty()) {
            return;
        }

        for (VectorRecord record : records) {
            if (record == null) {
                continue;
            }

            save(record);
        }
    }

    /**
     * 벡터 검색을 수행합니다.
     *
     * 결과는 일반적으로 점수가 높은 순서로 정렬되고, rank는 1부터
     * 부여되어야 합니다.
     *
     * @param request 검색 요청
     * @return 검색 결과 목록
     * @throws VectorStoreException 검색 실패 시
     */
    List<VectorSearchResult> search(
        VectorSearchRequest request
    ) throws VectorStoreException;

    /**
     * ID와 모델명으로 벡터 레코드를 조회합니다.
     *
     * @param id 벡터 레코드 ID
     * @param model 임베딩 모델명
     * @return 조회된 레코드
     * @throws VectorStoreException 조회 실패 시
     */
    Optional<VectorRecord> findById(
        String id,
        String model
    ) throws VectorStoreException;

    /**
     * ID만으로 벡터 레코드를 조회합니다.
     *
     * 저장소가 하나의 임베딩 모델만 관리하는 경우 사용할 수 있습니다.
     * 여러 모델이 저장된 경우 구현체 정책에 따라 하나를 반환합니다.
     *
     * @param id 벡터 레코드 ID
     * @return 조회된 레코드
     * @throws VectorStoreException 조회 실패 시
     */
    default Optional<VectorRecord> findById(
        String id
    ) throws VectorStoreException {

        validateText(id, "id");

        List<VectorRecord> records =
            findByIds(List.of(id));

        if (records.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(records.get(0));
    }

    /**
     * 여러 ID에 해당하는 레코드를 조회합니다.
     *
     * 기본 구현은 {@link #findAll()}에서 필터링합니다.
     * 대용량 저장소 구현체에서는 SQL IN 절 등으로 재정의해야 합니다.
     *
     * @param ids 조회할 ID 목록
     * @return 조회된 레코드 목록
     * @throws VectorStoreException 조회 실패 시
     */
    default List<VectorRecord> findByIds(
        List<String> ids
    ) throws VectorStoreException {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        java.util.Set<String> normalizedIds =
            new java.util.LinkedHashSet<>();

        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                normalizedIds.add(id.trim());
            }
        }

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        List<VectorRecord> result =
            new ArrayList<>();

        for (VectorRecord record : findAll()) {
            if (normalizedIds.contains(record.getId())) {
                result.add(record);
            }
        }

        return List.copyOf(result);
    }

    /**
     * 저장소의 모든 레코드를 조회합니다.
     *
     * 대규모 저장소에서는 사용에 주의해야 합니다.
     *
     * @return 전체 레코드 목록
     * @throws VectorStoreException 조회 실패 시
     */
    List<VectorRecord> findAll()
        throws VectorStoreException;

    /**
     * 특정 임베딩 모델의 모든 레코드를 조회합니다.
     *
     * @param model 임베딩 모델명
     * @return 모델별 레코드 목록
     * @throws VectorStoreException 조회 실패 시
     */
    default List<VectorRecord> findByModel(
        String model
    ) throws VectorStoreException {

        String normalizedModel =
            validateText(model, "model");

        return findAll().stream()
            .filter(record ->
                record.isModel(normalizedModel)
            )
            .toList();
    }

    /**
     * 특정 원본 문서 경로의 모든 레코드를 조회합니다.
     *
     * @param sourcePath 원본 문서 상대 경로
     * @return 원본 문서별 레코드 목록
     * @throws VectorStoreException 조회 실패 시
     */
    default List<VectorRecord> findBySourcePath(
        String sourcePath
    ) throws VectorStoreException {

        String normalizedPath =
            normalizePath(
                validateText(sourcePath, "sourcePath")
            );

        return findAll().stream()
            .filter(record ->
                normalizePath(
                    record.getChunk().getSourcePath()
                ).equals(normalizedPath)
            )
            .toList();
    }

    /**
     * ID와 모델명에 해당하는 레코드가 존재하는지 확인합니다.
     *
     * @param id 레코드 ID
     * @param model 임베딩 모델명
     * @return 존재 여부
     * @throws VectorStoreException 조회 실패 시
     */
    default boolean contains(
        String id,
        String model
    ) throws VectorStoreException {

        return findById(id, model).isPresent();
    }

    /**
     * ID와 모델명으로 레코드를 삭제합니다.
     *
     * @param id 레코드 ID
     * @param model 임베딩 모델명
     * @return 삭제된 경우 true
     * @throws VectorStoreException 삭제 실패 시
     */
    boolean delete(
        String id,
        String model
    ) throws VectorStoreException;

    /**
     * 여러 ID를 삭제합니다.
     *
     * 기본 구현은 ID마다 {@link #delete(String, String)}를 호출합니다.
     *
     * @param ids 삭제할 레코드 ID 목록
     * @param model 임베딩 모델명
     * @return 삭제된 레코드 개수
     * @throws VectorStoreException 삭제 실패 시
     */
    default int deleteAll(
        List<String> ids,
        String model
    ) throws VectorStoreException {

        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int deletedCount = 0;

        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }

            if (delete(id, model)) {
                deletedCount++;
            }
        }

        return deletedCount;
    }

    /**
     * 특정 원본 문서에서 생성된 모든 벡터를 삭제합니다.
     *
     * XHTML 파일이 삭제되거나 재인덱싱될 때 사용합니다.
     *
     * @param sourcePath 원본 문서 상대 경로
     * @return 삭제된 레코드 개수
     * @throws VectorStoreException 삭제 실패 시
     */
    int deleteBySourcePath(
        String sourcePath
    ) throws VectorStoreException;

    /**
     * 특정 원본 문서와 임베딩 모델에 해당하는 벡터를 삭제합니다.
     *
     * @param sourcePath 원본 문서 상대 경로
     * @param model 임베딩 모델명
     * @return 삭제된 레코드 개수
     * @throws VectorStoreException 삭제 실패 시
     */
    default int deleteBySourcePath(
        String sourcePath,
        String model
    ) throws VectorStoreException {

        String normalizedPath =
            normalizePath(
                validateText(sourcePath, "sourcePath")
            );

        String normalizedModel =
            validateText(model, "model");

        List<VectorRecord> records =
            findBySourcePath(normalizedPath);

        int deletedCount = 0;

        for (VectorRecord record : records) {
            if (!record.isModel(normalizedModel)) {
                continue;
            }

            if (delete(
                record.getId(),
                normalizedModel
            )) {
                deletedCount++;
            }
        }

        return deletedCount;
    }

    /**
     * 특정 임베딩 모델로 생성된 모든 레코드를 삭제합니다.
     *
     * 임베딩 모델 변경 시 기존 벡터를 제거할 때 사용합니다.
     *
     * @param model 임베딩 모델명
     * @return 삭제된 레코드 개수
     * @throws VectorStoreException 삭제 실패 시
     */
    int deleteByModel(
        String model
    ) throws VectorStoreException;

    /**
     * 저장소의 모든 레코드를 삭제합니다.
     *
     * @throws VectorStoreException 초기화 실패 시
     */
    void clear() throws VectorStoreException;

    /**
     * 전체 레코드 수를 반환합니다.
     *
     * @return 전체 레코드 수
     * @throws VectorStoreException 조회 실패 시
     */
    long count() throws VectorStoreException;

    /**
     * 특정 모델의 레코드 수를 반환합니다.
     *
     * @param model 임베딩 모델명
     * @return 모델별 레코드 수
     * @throws VectorStoreException 조회 실패 시
     */
    default long countByModel(
        String model
    ) throws VectorStoreException {

        return findByModel(model).size();
    }

    /**
     * 특정 원본 문서의 레코드 수를 반환합니다.
     *
     * @param sourcePath 원본 문서 상대 경로
     * @return 원본 문서별 레코드 수
     * @throws VectorStoreException 조회 실패 시
     */
    default long countBySourcePath(
        String sourcePath
    ) throws VectorStoreException {

        return findBySourcePath(sourcePath).size();
    }

    /**
     * 저장소가 비어 있는지 확인합니다.
     *
     * @return 비어 있으면 true
     * @throws VectorStoreException 조회 실패 시
     */
    default boolean isEmpty()
        throws VectorStoreException {

        return count() == 0;
    }

    /**
     * 저장소 사용 가능 여부를 확인합니다.
     *
     * 메모리 구현체는 항상 true를 반환할 수 있고, SQLite 구현체는
     * 연결 및 스키마 상태를 점검할 수 있습니다.
     *
     * @return 사용 가능 여부
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 저장소 리소스를 해제합니다.
     *
     * 메모리 구현체는 별도 작업이 없을 수 있습니다.
     */
    @Override
    default void close()
        throws VectorStoreException {
        // 기본 구현은 해제할 리소스가 없습니다.
    }

    private static String validateText(
        String value,
        String fieldName
    ) {
        String normalized =
            value == null ? "" : value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String normalizePath(String value) {
        return value
            .trim()
            .replace('\\', '/');
    }
}