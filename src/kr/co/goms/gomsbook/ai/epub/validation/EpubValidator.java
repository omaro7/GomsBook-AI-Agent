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
 * EPUB 검증 기능의 공통 계약입니다.
 *
 * <p>구현체는 다음 두 종류의 검증을 수행할 수 있습니다.</p>
 *
 * <ul>
 *     <li>생성 전 {@link EpubPackage} 모델 검증</li>
 *     <li>생성 후 실제 {@code .epub} 파일 검증</li>
 * </ul>
 *
 * <p>내부 구조 검증, 접근성 검증, EPUBCheck 연동 등은
 * 이 인터페이스를 구현하여 동일한 결과 형식인
 * {@link EpubValidationResult}로 반환할 수 있습니다.</p>
 */
public interface EpubValidator {

    /**
     * EPUB 패키지 모델을 검증합니다.
     *
     * @param epubPackage EPUB 패키지 모델
     * @param pathConfiguration EPUB 경로 설정
     * @param options 생성 옵션
     * @return 검증 결과
     */
    EpubValidationResult validate(
            Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    );

    /**
     * 생성된 EPUB 파일을 검증합니다.
     *
     * @param epubFile 생성된 EPUB 파일
     * @param options 생성 옵션
     * @return 검증 결과
     */
    EpubValidationResult validate(
            Path projectRoot,
            Path epubFile,
            EpubGenerationOptions options
    );

    /**
     * 패키지 모델을 기본 옵션으로 검증합니다.
     *
     * @param epubPackage EPUB 패키지
     * @param pathConfiguration 경로 설정
     * @return 검증 결과
     */
    default EpubValidationResult validate(
            Path projectRoot,
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration
    ) {
        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        EpubGenerationOptions options =
                EpubGenerationOptions.builder()
                        .version(epubPackage.getVersion())
                        .build();

        return validate(
        		projectRoot,
                epubPackage,
                pathConfiguration,
                options
        );
    }

    /**
     * 생성된 EPUB 파일을 기본 옵션으로 검증합니다.
     *
     * @param epubFile EPUB 파일
     * @return 검증 결과
     */
    default EpubValidationResult validate(
            Path projectRoot,
            Path epubFile
    ) {
        return validate(
        		projectRoot,
                epubFile,
                EpubGenerationOptions.defaultOptions()
        );
    }

    /**
     * 현재 Validator가 패키지 모델 검증을 지원하는지 반환합니다.
     *
     * @return 지원하면 {@code true}
     */
    default boolean supportsPackageValidation() {
        return true;
    }

    /**
     * 현재 Validator가 실제 EPUB 파일 검증을 지원하는지 반환합니다.
     *
     * @return 지원하면 {@code true}
     */
    default boolean supportsArchiveValidation() {
        return true;
    }

    /**
     * 현재 Validator가 지정한 패키지를 처리할 수 있는지 확인합니다.
     *
     * @param epubPackage EPUB 패키지
     * @param options 생성 옵션
     * @return 지원 여부
     */
    default boolean supports(
            EpubPackage epubPackage,
            EpubGenerationOptions options
    ) {
        return epubPackage != null
                && options != null;
    }

    /**
     * 현재 Validator가 지정한 EPUB 파일을 처리할 수 있는지 확인합니다.
     *
     * @param epubFile EPUB 파일
     * @param options 생성 옵션
     * @return 지원 여부
     */
    default boolean supports(
            Path epubFile,
            EpubGenerationOptions options
    ) {
        if (epubFile == null || options == null) {
            return false;
        }

        Path fileName = epubFile.getFileName();

        return fileName != null
                && fileName.toString()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".epub");
    }

    /**
     * 패키지 모델 검증 입력값을 기본 검증합니다.
     *
     * @param epubPackage EPUB 패키지
     * @param pathConfiguration 경로 설정
     * @param options 생성 옵션
     * @throws IllegalArgumentException 입력값이 잘못된 경우
     */
    default void validateInput(
            EpubPackage epubPackage,
            EpubPathConfiguration pathConfiguration,
            EpubGenerationOptions options
    ) {
        Objects.requireNonNull(
                epubPackage,
                "EPUB package must not be null."
        );

        Objects.requireNonNull(
                pathConfiguration,
                "EPUB path configuration must not be null."
        );

        Objects.requireNonNull(
                options,
                "EPUB generation options must not be null."
        );

        if (epubPackage.getVersion()
                != options.getVersion()) {
            throw new IllegalArgumentException(
                    "EPUB package version and validation option "
                            + "version do not match: "
                            + epubPackage.getVersion()
                            + " != "
                            + options.getVersion()
            );
        }

        if (!supports(
                epubPackage,
                options
        )) {
            throw new IllegalArgumentException(
                    "This EPUB validator does not support "
                            + "the specified package."
            );
        }
    }

    /**
     * 실제 EPUB 파일 검증 입력값을 기본 검증합니다.
     *
     * @param epubFile EPUB 파일
     * @param options 생성 옵션
     * @throws IllegalArgumentException 입력값이 잘못된 경우
     */
    default void validateInput(
            Path epubFile,
            EpubGenerationOptions options
    ) {
        Objects.requireNonNull(
                epubFile,
                "EPUB file must not be null."
        );

        Objects.requireNonNull(
                options,
                "EPUB generation options must not be null."
        );

        Path normalized =
                epubFile.toAbsolutePath().normalize();

        if (!supports(normalized, options)) {
            throw new IllegalArgumentException(
                    "This EPUB validator does not support "
                            + "the specified EPUB file: "
                            + normalized
            );
        }
    }

    /**
     * Validator의 고유 이름을 반환합니다.
     *
     * @return Validator 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Validator 버전을 반환합니다.
     *
     * @return 버전 문자열
     */
    default String getVersion() {
        return "1.0";
    }

    /**
     * Validator 종류를 반환합니다.
     *
     * @return Validator 종류
     */
    default Type getType() {
        return Type.INTERNAL;
    }

    /**
     * Validator의 기능 유형입니다.
     */
    enum Type {

        /**
         * manifest, spine, OPF, archive 구조 등의 내부 검증입니다.
         */
        INTERNAL,

        /**
         * 접근성 검증입니다.
         */
        ACCESSIBILITY,

        /**
         * EPUBCheck 기반 검증입니다.
         */
        EPUB_CHECK,

        /**
         * XHTML/XML 문법 검증입니다.
         */
        XHTML,

        /**
         * 사용자 정의 검증기입니다.
         */
        CUSTOM
    }
}