/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.dto.xhtml;

import java.util.Objects;
import kr.co.goms.gomsbook.ai.validation.xhtml.XhtmlValidationResult;

/**
 * XHTML 생성 Tool의 응답 데이터입니다.
 *
 * <p>
 * 생성된 XHTML 본문과 생성 과정에서 사용된 문단 ID,
 * 적용된 XHTML 규칙 등의 결과 정보를 포함합니다.
 * </p>
 *
 * <p>
 * Tool 실행 상태, 오류, 경고 및 실행 시간은
 * {@code ToolResult<XhtmlGenerationResponse>}에서 관리합니다.
 * </p>
 * 
 * String xhtml = """
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml"
              xmlns:epub="http://www.idpf.org/2007/ops"
              lang="ko"
              xml:lang="ko">
        <head>
            <meta charset="utf-8" />
            <title>꽃은 자신을 재촉하지 않는다</title>
        </head>
        <body aria-labelledby="chapter_title">
            <section epub:type="chapter">
                <h1 id="chapter_title">꽃은 자신을 재촉하지 않는다</h1>
                <p id="p_01">꽃은 자신의 계절이 올 때까지 조용히 기다린다.</p>
            </section>
        </body>
        </html>
        """;

 */

public final class XhtmlGenerationResponse {

    private final String xhtml;
    private final String rawContent;
    private final boolean valid;

    private final XhtmlValidationResult validationResult;

    private final String model;

    /**
     * XHTML 생성 결과를 생성합니다.
     */
    public XhtmlGenerationResponse(
            String xhtml,
            String rawContent,
            boolean valid,
            XhtmlValidationResult validationResult,
            String model) {

        this.xhtml =
                normalizeRequired(
                        xhtml,
                        "xhtml"
                );

        this.rawContent =
                normalizeOptional(rawContent);

        this.valid = valid;

        this.validationResult =
                validationResult;

        this.model =
                normalizeOptional(model);
    }

    /**
     * XHTML을 반환합니다.
     */
    public String getXhtml() {
        return xhtml;
    }

    /**
     * LLM 원본 응답을 반환합니다.
     */
    public String getRawContent() {
        return rawContent;
    }

    /**
     * XHTML 유효 여부를 반환합니다.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * XHTML 검증 결과를 반환합니다.
     */
    public XhtmlValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * XHTML 생성에 사용한 모델명을 반환합니다.
     */
    public String getModel() {
        return model;
    }

    // =========================================================
    // 기존 record 스타일 호환
    // =========================================================

    public String xhtml() {
        return xhtml;
    }

    public String rawContent() {
        return rawContent;
    }

    public boolean valid() {
        return valid;
    }

    public XhtmlValidationResult validationResult() {
        return validationResult;
    }

    public String model() {
        return model;
    }

    // =========================================================
    // 편의 메서드
    // =========================================================

    public boolean hasXhtml() {
        return xhtml != null
                && !xhtml.isBlank();
    }

    public boolean hasRawContent() {
        return rawContent != null
                && !rawContent.isBlank();
    }

    public boolean hasValidationResult() {
        return validationResult != null;
    }

    public boolean hasModel() {
        return model != null
                && !model.isBlank();
    }

    public boolean isInvalid() {
        return !valid;
    }

    private static String normalizeRequired(
            String value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    private static String normalizeOptional(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return "XhtmlGenerationResponse{"
                + "xhtmlLength="
                + xhtml.length()
                + ", rawContentLength="
                + (rawContent == null
                        ? 0
                        : rawContent.length())
                + ", valid="
                + valid
                + ", validationResult="
                + validationResult
                + ", model='"
                + model + '\''
                + '}';
    }
}