/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.analysis;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 이미지 접근성 분석 과정에서 발생하는 예외.
 *
 * <p>Vision 모델 호출, 이미지 파일 읽기, 응답 파싱 등
 * 이미지 분석 과정에서 발생하는 오류를 표현한다.</p>
 */
public class ImageAnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ImageAnalysisErrorCode errorCode;
    private final Path imagePath;
    private final String model;

    /**
     * 오류 코드와 메시지로 예외를 생성한다.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     */
    public ImageAnalysisException(
            ImageAnalysisErrorCode errorCode,
            String message) {

        super(message);

        this.errorCode = Objects.requireNonNull(errorCode);
        this.imagePath = null;
        this.model = null;
    }

    /**
     * 오류 코드, 메시지 및 원인 예외로 생성한다.
     *
     * @param errorCode 오류 코드
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public ImageAnalysisException(
            ImageAnalysisErrorCode errorCode,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(errorCode);
        this.imagePath = null;
        this.model = null;
    }

    /**
     * 오류 코드와 이미지 정보를 포함한 예외를 생성한다.
     *
     * @param errorCode 오류 코드
     * @param imagePath 이미지 경로
     * @param message 오류 메시지
     */
    public ImageAnalysisException(
            ImageAnalysisErrorCode errorCode,
            Path imagePath,
            String message) {

        super(message);

        this.errorCode = Objects.requireNonNull(errorCode);
        this.imagePath = imagePath;
        this.model = null;
    }

    /**
     * 모든 정보를 포함한 예외를 생성한다.
     *
     * @param errorCode 오류 코드
     * @param imagePath 이미지 경로
     * @param model Vision 모델명
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public ImageAnalysisException(
            ImageAnalysisErrorCode errorCode,
            Path imagePath,
            String model,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(errorCode);
        this.imagePath = imagePath;
        this.model = model;
    }

    /**
     * 오류 코드를 반환한다.
     *
     * @return 오류 코드
     */
    public ImageAnalysisErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 문제가 발생한 이미지 경로를 반환한다.
     *
     * @return 이미지 경로, 없으면 {@code null}
     */
    public Path getImagePath() {
        return imagePath;
    }

    /**
     * Vision 모델명을 반환한다.
     *
     * @return 모델명, 없으면 {@code null}
     */
    public String getModel() {
        return model;
    }

    /**
     * 재시도 가능한 오류인지 반환한다.
     *
     * @return 재시도 가능 여부
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    @Override
    public String toString() {

        return "ImageAnalysisException{" +
                "errorCode=" + errorCode +
                ", imagePath=" + imagePath +
                ", model='" + model + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}