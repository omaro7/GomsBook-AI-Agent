/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.application;

import java.nio.file.Path;
import java.util.Objects;

/**
 * XHTML 이미지 요소에 대체 텍스트를 적용하는 과정에서 발생하는 예외.
 */
public class AltTextApplicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AltTextApplicationErrorCode errorCode;
    private final Path xhtmlPath;
    private final String imageElementId;
    private final String imageSource;

    public AltTextApplicationException(
            AltTextApplicationErrorCode errorCode,
            String message) {

        super(message);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.xhtmlPath = null;
        this.imageElementId = null;
        this.imageSource = null;
    }

    public AltTextApplicationException(
            AltTextApplicationErrorCode errorCode,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.xhtmlPath = null;
        this.imageElementId = null;
        this.imageSource = null;
    }

    public AltTextApplicationException(
            AltTextApplicationErrorCode errorCode,
            Path xhtmlPath,
            String imageElementId,
            String imageSource,
            String message) {

        super(message);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.xhtmlPath = xhtmlPath;
        this.imageElementId = imageElementId;
        this.imageSource = imageSource;
    }

    public AltTextApplicationException(
            AltTextApplicationErrorCode errorCode,
            Path xhtmlPath,
            String imageElementId,
            String imageSource,
            String message,
            Throwable cause) {

        super(message, cause);

        this.errorCode = Objects.requireNonNull(
                errorCode,
                "errorCode must not be null"
        );

        this.xhtmlPath = xhtmlPath;
        this.imageElementId = imageElementId;
        this.imageSource = imageSource;
    }

    public AltTextApplicationErrorCode getErrorCode() {
        return errorCode;
    }

    public Path getXhtmlPath() {
        return xhtmlPath;
    }

    public String getImageElementId() {
        return imageElementId;
    }

    public String getImageSource() {
        return imageSource;
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    @Override
    public String toString() {
        return "AltTextApplicationException{" +
                "errorCode=" + errorCode +
                ", xhtmlPath=" + xhtmlPath +
                ", imageElementId='" + imageElementId + '\'' +
                ", imageSource='" + imageSource + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}