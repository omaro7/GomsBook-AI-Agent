/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPathConfiguration;

/**
 * EPUB 접근성 검증 기능의 계약입니다.
 *
 * <p>EPUB 출판물에 포함된 XHTML, 이미지, 링크, 표, 문서 언어,
 * heading 구조 등의 접근성을 검증합니다.</p>
 *
 * <p>이 인터페이스는 EPUB 계층에서 접근성 규칙을 직접 구현하기 위한
 * 것이 아닙니다. 기존 GomsBook Accessibility 계층의
 * {@code AccessibilityValidator}를 호출하고 그 결과를
 * {@link EpubValidationResult}로 변환하는 어댑터 역할을 담당합니다.</p>
 *
 * <p>대표적인 검증 대상은 다음과 같습니다.</p>
 *
 * <ul>
 *     <li>이미지 alt 및 대체 텍스트</li>
 *     <li>문서 언어(lang / xml:lang)</li>
 *     <li>heading 구조</li>
 *     <li>링크 접근성</li>
 *     <li>표 구조 및 헤더</li>
 *     <li>ARIA 속성</li>
 *     <li>EPUB Navigation Document</li>
 *     <li>접근성 메타데이터</li>
 * </ul>
 *
 * <p>검증은 두 단계에서 수행할 수 있습니다.</p>
 *
 * <ol>
 *     <li>생성 전 작업 디렉터리 및 패키지 모델 검증</li>
 *     <li>생성 후 최종 EPUB 아카이브 검증</li>
 * </ol>
 */
public interface EpubAccessibilityValidator
        extends EpubValidator {

    /**
     * EPUB 생성 전 접근성을 검증합니다.
     *
     * <p>manifest에 등록된 XHTML 및 관련 리소스와
     * EPUB 패키지 메타데이터를 대상으로 검증합니다.</p>
     *
     * @param epubPackage EPUB 패키지 모델
     * @param pathConfiguration EPUB 경로 설정
     * @param options EPUB 생성 옵션
     * @return 접근성 검증 결과
     */
    @Override
    EpubValidationResult validate(
            Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    );

    /**
     * 최종 생성된 EPUB 파일의 접근성을 검증합니다.
     *
     * <p>구현체가 archive 직접 검증을 지원하지 않는 경우
     * {@link EpubValidationResult#notPerformed()}를 반환할 수 있습니다.</p>
     *
     * @param epubFile 최종 EPUB 파일
     * @param options EPUB 생성 옵션
     * @return 접근성 검증 결과
     */
    @Override
    EpubValidationResult validate(
            Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    );

    /**
     * 개별 XHTML 리소스의 접근성을 검증합니다.
     *
     * <p>EPUB 생성 전에 특정 문서만 검사하거나,
     * Editor에서 현재 열려 있는 XHTML을 검사할 때 사용할 수 있습니다.</p>
     *
     * @param xhtmlFile XHTML 파일
     * @param epubPath EPUB 내부 경로
     * @param options EPUB 생성 옵션
     * @return 접근성 검증 결과
     */
    EpubValidationResult validateXhtml(
            Path projectRoot,
            Path xhtmlFile,
            String epubPath,
            EpubGenerationOptions options
    );
    
    /**
     * 작업 디렉터리 전체의 접근성을 검증합니다.
     *
     * <p>최종 ZIP 패키징 전에 실제 생성된 XHTML과 리소스를
     * 기준으로 검증할 때 사용합니다.</p>
     *
     * @param workingDirectory EPUB 작업 디렉터리
     * @param packageDocumentPath OPF 패키지 문서 경로
     * @param options EPUB 생성 옵션
     * @return 접근성 검증 결과
     */
    EpubValidationResult validateWorkspace(
            Path projectRoot,
            Path workingDirectory,
            Path packageDocumentPath,
            EpubGenerationOptions options
    );

    /**
     * XHTML 접근성 검증 입력을 검사합니다.
     *
     * @param xhtmlFile XHTML 파일
     * @param epubPath EPUB 내부 경로
     * @param options EPUB 생성 옵션
     */
    default void validateXhtmlInput(
            Path xhtmlFile,
            String epubPath,
            EpubGenerationOptions options
    ) {
        Objects.requireNonNull(
                xhtmlFile,
                "EPUB XHTML file must not be null."
        );

        Objects.requireNonNull(
                options,
                "EPUB generation options must not be null."
        );

        Path normalized =
                xhtmlFile.toAbsolutePath().normalize();

        if (!java.nio.file.Files.exists(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB XHTML file does not exist: "
                            + normalized
            );
        }

        if (!java.nio.file.Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB XHTML path is not a regular file: "
                            + normalized
            );
        }

        if (!java.nio.file.Files.isReadable(normalized)) {
            throw new IllegalArgumentException(
                    "EPUB XHTML file is not readable: "
                            + normalized
            );
        }

        String fileName =
                normalized.getFileName() == null
                        ? ""
                        : normalized.getFileName()
                                .toString()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                );

        if (!fileName.endsWith(".xhtml")
                && !fileName.endsWith(".html")
                && !fileName.endsWith(".htm")) {

            throw new IllegalArgumentException(
                    "Unsupported EPUB content document type: "
                            + normalized
            );
        }

        if (epubPath == null || epubPath.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB XHTML internal path must not be blank."
            );
        }
    }

    /**
     * 작업 디렉터리 접근성 검증 입력을 검사합니다.
     *
     * @param workingDirectory 작업 디렉터리
     * @param packageDocumentPath OPF 파일
     * @param options 생성 옵션
     */
    default void validateWorkspaceInput(
            Path workingDirectory,
            Path packageDocumentPath,
            EpubGenerationOptions options
    ) {
        Objects.requireNonNull(
                workingDirectory,
                "EPUB working directory must not be null."
        );

        Objects.requireNonNull(
                packageDocumentPath,
                "EPUB package document path must not be null."
        );

        Objects.requireNonNull(
                options,
                "EPUB generation options must not be null."
        );

        Path working =
                workingDirectory
                        .toAbsolutePath()
                        .normalize();

        Path packageDocument =
                packageDocumentPath
                        .toAbsolutePath()
                        .normalize();

        if (!java.nio.file.Files.isDirectory(working)) {
            throw new IllegalArgumentException(
                    "EPUB working directory does not exist: "
                            + working
            );
        }

        if (!packageDocument.startsWith(working)) {
            throw new IllegalArgumentException(
                    "EPUB package document must be located "
                            + "inside the working directory: "
                            + packageDocument
            );
        }

        if (!java.nio.file.Files.isRegularFile(
                packageDocument
        )) {
            throw new IllegalArgumentException(
                    "EPUB package document does not exist: "
                            + packageDocument
            );
        }
    }

    /**
     * 접근성 오류가 EPUB 생성을 차단해야 하는지 결정합니다.
     *
     * <p>기본 정책에서는 ERROR 또는 FATAL 수준의 접근성 문제가 있으면
     * 생성을 차단합니다. 생성 옵션이나 배포 정책에 따라 구현체에서
     * 재정의할 수 있습니다.</p>
     *
     * @param result 접근성 검증 결과
     * @return 생성 차단 여부
     */
    default boolean shouldBlockGeneration(
            EpubValidationResult result
    ) {
        if (result == null
                || !result.isPerformed()) {
            return false;
        }

        return result.hasBlockingIssues();
    }

    /**
     * alt 관련 오류가 존재하는지 확인합니다.
     *
     * @param result 검증 결과
     * @return alt 관련 오류 존재 여부
     */
    default boolean hasAlternativeTextIssues(
            EpubValidationResult result
    ) {
        if (result == null) {
            return false;
        }

        return result.getIssues(
                EpubValidationIssue.Category
                        .ALTERNATIVE_TEXT
        )
                .stream()
                .anyMatch(
                        EpubValidationIssue::isBlocking
                );
    }

    /**
     * 문서 언어 관련 이슈가 존재하는지 확인합니다.
     */
    default boolean hasLanguageIssues(
            EpubValidationResult result
    ) {
        if (result == null) {
            return false;
        }

        return result.getCategoryCount(
                EpubValidationIssue.Category.LANGUAGE
        ) > 0;
    }

    /**
     * heading 관련 이슈가 존재하는지 확인합니다.
     */
    default boolean hasHeadingIssues(
            EpubValidationResult result
    ) {
        if (result == null) {
            return false;
        }

        return result.getCategoryCount(
                EpubValidationIssue.Category.HEADING
        ) > 0;
    }

    /**
     * 링크 관련 접근성 이슈가 존재하는지 확인합니다.
     */
    default boolean hasLinkIssues(
            EpubValidationResult result
    ) {
        if (result == null) {
            return false;
        }

        return result.getCategoryCount(
                EpubValidationIssue.Category.LINK
        ) > 0;
    }

    /**
     * 자동 수정 가능한 접근성 문제가 존재하는지 확인합니다.
     */
    default boolean hasAutoFixableIssues(
            EpubValidationResult result
    ) {
        return result != null
                && result.getAutoFixableIssueCount() > 0;
    }

    /**
     * EPUB Accessibility Validator는 패키지 검증을 지원합니다.
     */
    @Override
    default boolean supportsPackageValidation() {
        return true;
    }

    /**
     * 기본적으로 최종 EPUB 아카이브 접근성 검증도 지원한다고
     * 선언합니다.
     *
     * <p>구현체에서 지원하지 않는 경우 false로 재정의할 수 있습니다.</p>
     */
    @Override
    default boolean supportsArchiveValidation() {
        return true;
    }

    /**
     * Validator 유형은 ACCESSIBILITY입니다.
     */
    @Override
    default Type getType() {
        return Type.ACCESSIBILITY;
    }

    /**
     * 기본 Validator 이름입니다.
     */
    @Override
    default String getName() {
        return "GomsBook EPUB Accessibility Validator";
    }

    /**
     * 기본 버전입니다.
     */
    @Override
    default String getVersion() {
        return "1.0";
    }
}