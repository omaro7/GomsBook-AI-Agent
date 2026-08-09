/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;
import kr.co.goms.gomsbook.ai.epub.model.EpubResource;


/**
 * EPUB manifest 리소스를 실제 작업 디렉터리에 기록하는 계약입니다.
 *
 * <p>리소스는 다음 두 방식 중 하나로 처리할 수 있습니다.</p>
 *
 * <ul>
 *     <li>로컬 원본 파일을 EPUB 작업 디렉터리로 복사</li>
 *     <li>메모리에 존재하는 byte[] 콘텐츠를 파일로 기록</li>
 * </ul>
 *
 * <p>원격 리소스는 생성 옵션에 따라 그대로 유지하거나,
 * 별도 구현에서 다운로드할 수 있습니다.</p>
 */
public interface EpubResourceWriter {

    /**
     * 단일 EPUB 리소스를 작업 디렉터리에 기록합니다.
     *
     * @param resource 리소스
     * @param pathConfiguration EPUB 경로 설정
     * @param options 생성 옵션
     * @return 리소스 기록 결과
     * @throws EpubGenerationException 리소스 처리 실패 시
     */
    EpubResourceWriteResult write(
            EpubResource resource,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * manifest의 모든 리소스를 작업 디렉터리에 기록합니다.
     *
     * @param manifest EPUB manifest
     * @param pathConfiguration EPUB 경로 설정
     * @param options 생성 옵션
     * @return 개별 리소스 기록 결과 목록
     * @throws EpubGenerationException 처리 실패 시
     */
    List<EpubResourceWriteResult> writeAll(
            EpubManifest manifest,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException;

    /**
     * 단일 리소스 기록 가능 여부를 검증합니다.
     *
     * @param resource 리소스
     * @param pathConfiguration 경로 설정
     * @param options 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 경우
     */
    default void validate(
            EpubResource resource,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (resource == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB resource must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .build();
        }

        if (pathConfiguration == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB path configuration must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .build();
        }

        if (options == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB generation options must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .build();
        }

        if (!resource.isIncluded()) {
            return;
        }

        if (resource.isRemote()
                && !options.isAllowRemoteResources()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_PROCESSING_FAILED,
                    "Remote EPUB resources are not allowed: "
                            + resource.getHref()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .build();
        }

        if (resource.isRemote()
                && options.isDownloadRemoteResources()) {
            /*
             * 실제 다운로드 구현은 별도의 downloader 또는
             * DefaultEpubResourceWriter 구현에서 처리합니다.
             */
            return;
        }

        if (!resource.isRemote()
                && !resource.hasLocalSource()
                && options.isFailOnMissingResource()) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode
                            .RESOURCE_NOT_FOUND,
                    "EPUB resource has no local source: "
                            + resource.getId()
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .resourceId(resource.getId())
                    .epubPath(resource.getHref())
                    .build();
        }
    }

    /**
     * manifest 전체를 처리할 수 있는지 검증합니다.
     *
     * @param manifest EPUB manifest
     * @param pathConfiguration 경로 설정
     * @param options 생성 옵션
     * @throws EpubGenerationException 유효하지 않은 경우
     */
    default void validate(
            EpubManifest manifest,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) throws EpubGenerationException {

        if (manifest == null) {
            throw EpubGenerationException.builder(
                    EpubGenerationException.ErrorCode.INVALID_REQUEST,
                    "EPUB manifest must not be null."
            )
                    .stage(
                            EpubGenerationException.Stage
                                    .RESOURCE_PROCESSING
                    )
                    .build();
        }

        for (EpubResource resource : manifest.getResources()) {
            validate(
                    resource,
                    pathConfiguration,
                    options
            );
        }
    }

    /**
     * 리소스의 최종 로컬 출력 경로를 계산합니다.
     *
     * <p>manifest href는 OPF 패키지 문서를 기준으로 하므로
     * 콘텐츠 루트 아래에 배치합니다.</p>
     *
     * @param resource EPUB 리소스
     * @param pathConfiguration 경로 설정
     * @return 최종 로컬 경로
     */
    default Path resolveTargetPath(
            EpubResource resource,
            EpubPathConfiguration pathConfiguration
    ) {
        if (resource == null) {
            throw new IllegalArgumentException(
                    "EPUB resource must not be null."
            );
        }

        if (pathConfiguration == null) {
            throw new IllegalArgumentException(
                    "EPUB path configuration must not be null."
            );
        }

        return pathConfiguration.resolveHrefToLocalPath(
                resource.getHref()
        );
    }

    /**
     * 구현체 이름을 반환합니다.
     *
     * @return 구현체 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * EPUB 리소스 기록 결과입니다.
     */
    final class EpubResourceWriteResult {

        private final String resourceId;

        private final String href;

        private final Path targetPath;

        private final Status status;

        private final long writtenBytes;

        private final String message;

        public EpubResourceWriteResult(
                String resourceId,
                String href,
                Path targetPath,
                Status status,
                long writtenBytes,
                String message
        ) {
            this.resourceId = resourceId;
            this.href = href;
            this.targetPath = targetPath;
            this.status = status == null
                    ? Status.SKIPPED
                    : status;
            this.writtenBytes = Math.max(0L, writtenBytes);
            this.message = message;
        }

        public static EpubResourceWriteResult copied(
                EpubResource resource,
                Path targetPath,
                long writtenBytes
        ) {
            return new EpubResourceWriteResult(
                    resource.getId(),
                    resource.getHref(),
                    targetPath,
                    Status.COPIED,
                    writtenBytes,
                    null
            );
        }

        public static EpubResourceWriteResult written(
                EpubResource resource,
                Path targetPath,
                long writtenBytes
        ) {
            return new EpubResourceWriteResult(
                    resource.getId(),
                    resource.getHref(),
                    targetPath,
                    Status.WRITTEN,
                    writtenBytes,
                    null
            );
        }

        public static EpubResourceWriteResult skipped(
                EpubResource resource,
                String message
        ) {
            return new EpubResourceWriteResult(
                    resource.getId(),
                    resource.getHref(),
                    null,
                    Status.SKIPPED,
                    0L,
                    message
            );
        }

        public String getResourceId() {
            return resourceId;
        }

        public String getHref() {
            return href;
        }

        public Path getTargetPath() {
            return targetPath;
        }

        public Status getStatus() {
            return status;
        }

        public long getWrittenBytes() {
            return writtenBytes;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCopied() {
            return status == Status.COPIED;
        }

        public boolean isWritten() {
            return status == Status.WRITTEN;
        }

        public boolean isSkipped() {
            return status == Status.SKIPPED;
        }

        public boolean isSuccess() {
            return status == Status.COPIED
                    || status == Status.WRITTEN;
        }

        @Override
        public String toString() {
            return "EpubResourceWriteResult{"
                    + "resourceId='" + resourceId + '\''
                    + ", href='" + href + '\''
                    + ", targetPath=" + targetPath
                    + ", status=" + status
                    + ", writtenBytes=" + writtenBytes
                    + ", message='" + message + '\''
                    + '}';
        }

        public enum Status {
            COPIED,
            WRITTEN,
            SKIPPED
        }
    }
}