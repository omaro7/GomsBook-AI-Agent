/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.agent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 접근성 Agent 실행 요청.
 *
 * <p>현재 프로젝트, 사용자 지시문, 대상 문서와 실행 정책을 포함하는
 * 불변 객체이다.</p>
 */
public final class AccessibilityAgentRequest {

    private final Path projectRoot;
    private final String instruction;
    private final Path targetDocumentPath;

    private final boolean allowModification;
    private final boolean dryRun;
    private final boolean createBackup;
    private final boolean overwriteExistingAlt;
    private final boolean includeInformationalIssues;

    private final Map<String, String> metadata;

    private AccessibilityAgentRequest(
            Builder builder) {

        this.projectRoot = normalizeRequiredPath(
                builder.projectRoot,
                "projectRoot"
        );

        this.instruction = normalizeRequiredText(
                builder.instruction,
                "instruction"
        );

        this.targetDocumentPath = normalizeOptionalProjectPath(
                projectRoot,
                builder.targetDocumentPath
        );

        this.allowModification =
                builder.allowModification;

        this.dryRun =
                builder.dryRun;

        this.createBackup =
                builder.createBackup;

        this.overwriteExistingAlt =
                builder.overwriteExistingAlt;

        this.includeInformationalIssues =
                builder.includeInformationalIssues;

        this.metadata = immutableMetadata(
                builder.metadata
        );

        validatePolicy();
    }

    /**
     * 현재 GomsBook 프로젝트 루트를 반환한다.
     *
     * @return 프로젝트 루트
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 사용자의 접근성 작업 지시문을 반환한다.
     *
     * @return 사용자 지시문
     */
    public String getInstruction() {
        return instruction;
    }

    /**
     * 명시적으로 지정된 대상 문서 경로를 반환한다.
     *
     * @return 대상 문서 절대 경로, 없으면 {@code null}
     */
    public Path getTargetDocumentPath() {
        return targetDocumentPath;
    }

    /**
     * 프로젝트 기준 대상 문서 상대 경로를 반환한다.
     *
     * @return 상대 문서 경로, 없으면 {@code null}
     */
    public String getProjectRelativeTargetDocumentPath() {

        if (targetDocumentPath == null) {
            return null;
        }

        return projectRoot
                .relativize(targetDocumentPath)
                .toString()
                .replace('\\', '/');
    }

    /**
     * Agent가 프로젝트 파일을 수정할 수 있는지 반환한다.
     *
     * @return 파일 수정 허용 여부
     */
    public boolean isAllowModification() {
        return allowModification;
    }

    /**
     * 실제 저장 없이 변경 예정 결과만 확인하는지 반환한다.
     *
     * @return dry-run 여부
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * 파일 수정 전에 백업을 생성할지 반환한다.
     *
     * @return 백업 생성 여부
     */
    public boolean isCreateBackup() {
        return createBackup;
    }

    /**
     * 기존의 비어 있지 않은 alt 값을 덮어쓸 수 있는지 반환한다.
     *
     * @return 기존 alt 덮어쓰기 허용 여부
     */
    public boolean isOverwriteExistingAlt() {
        return overwriteExistingAlt;
    }

    /**
     * 정보 수준 접근성 문제를 결과에 포함할지 반환한다.
     *
     * @return 정보 문제 포함 여부
     */
    public boolean isIncludeInformationalIssues() {
        return includeInformationalIssues;
    }

    /**
     * 요청 메타데이터를 반환한다.
     *
     * @return 수정할 수 없는 메타데이터
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 지정한 메타데이터 값을 반환한다.
     *
     * @param key 메타데이터 키
     * @return 메타데이터 값, 없으면 {@code null}
     */
    public String getMetadata(
            String key) {

        if (key == null) {
            return null;
        }

        return metadata.get(key);
    }

    /**
     * 명시적인 대상 문서가 있는지 반환한다.
     *
     * @return 대상 문서가 있으면 {@code true}
     */
    public boolean hasTargetDocument() {
        return targetDocumentPath != null;
    }

    /**
     * 실제 파일 수정이 가능한 요청인지 반환한다.
     *
     * @return 수정 허용이며 dry-run이 아니면 {@code true}
     */
    public boolean canModifyFiles() {
        return allowModification && !dryRun;
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validatePolicy() {

        if (!allowModification
                && overwriteExistingAlt) {

            throw new IllegalArgumentException(
                    "overwriteExistingAlt requires allowModification=true"
            );
        }
    }

    private static Path normalizeRequiredPath(
            Path value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " must not be null"
        );

        return value
                .toAbsolutePath()
                .normalize();
    }

    private static Path normalizeOptionalProjectPath(
            Path projectRoot,
            Path value) {

        if (value == null) {
            return null;
        }

        Path normalized = value.isAbsolute()
                ? value.toAbsolutePath().normalize()
                : projectRoot.resolve(value)
                        .toAbsolutePath()
                        .normalize();

        if (!normalized.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "targetDocumentPath must be inside projectRoot"
            );
        }

        return normalized;
    }

    private static String normalizeRequiredText(
            String value,
            String fieldName) {

        String normalized =
                normalizeOptionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String normalizeOptionalText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private static Map<String, String> immutableMetadata(
            Map<String, String> source) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry
                : source.entrySet()) {

            String key = normalizeOptionalText(
                    entry.getKey()
            );

            String value = normalizeOptionalText(
                    entry.getValue()
            );

            if (key != null && value != null) {
                result.put(key, value);
            }
        }

        if (result.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * {@link AccessibilityAgentRequest} Builder.
     */
    public static final class Builder {

        private Path projectRoot;
        private String instruction;
        private Path targetDocumentPath;

        private boolean allowModification;
        private boolean dryRun;
        private boolean createBackup = true;
        private boolean overwriteExistingAlt;
        private boolean includeInformationalIssues = true;

        private final Map<String, String> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder projectRoot(
                Path projectRoot) {

            this.projectRoot = projectRoot;
            return this;
        }

        public Builder instruction(
                String instruction) {

            this.instruction = instruction;
            return this;
        }

        public Builder targetDocumentPath(
                Path targetDocumentPath) {

            this.targetDocumentPath =
                    targetDocumentPath;

            return this;
        }

        /**
         * 프로젝트 상대 대상 문서 경로를 설정한다.
         *
         * @param relativePath 프로젝트 상대 경로
         * @return 현재 Builder
         */
        public Builder targetDocument(
                Path relativePath) {

            this.targetDocumentPath =
                    relativePath;

            return this;
        }

        public Builder allowModification(
                boolean allowModification) {

            this.allowModification =
                    allowModification;

            return this;
        }

        public Builder dryRun(
                boolean dryRun) {

            this.dryRun = dryRun;
            return this;
        }

        public Builder createBackup(
                boolean createBackup) {

            this.createBackup = createBackup;
            return this;
        }

        public Builder overwriteExistingAlt(
                boolean overwriteExistingAlt) {

            this.overwriteExistingAlt =
                    overwriteExistingAlt;

            return this;
        }

        public Builder includeInformationalIssues(
                boolean includeInformationalIssues) {

            this.includeInformationalIssues =
                    includeInformationalIssues;

            return this;
        }

        public Builder metadata(
                String key,
                String value) {

            String normalizedKey =
                    normalizeOptionalText(key);

            String normalizedValue =
                    normalizeOptionalText(value);

            if (normalizedKey != null
                    && normalizedValue != null) {

                metadata.put(
                        normalizedKey,
                        normalizedValue
                );
            }

            return this;
        }

        public Builder metadata(
                Map<String, String> metadata) {

            if (metadata == null) {
                return this;
            }

            for (Map.Entry<String, String> entry
                    : metadata.entrySet()) {

                metadata(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            return this;
        }

        public AccessibilityAgentRequest build() {
            return new AccessibilityAgentRequest(this);
        }
    }
}