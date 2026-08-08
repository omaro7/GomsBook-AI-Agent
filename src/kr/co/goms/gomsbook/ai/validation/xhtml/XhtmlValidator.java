/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.validation.xhtml;

/**
 * EPUB3 XHTML 문서를 검증하는 공통 인터페이스입니다.
 *
 * <p>
 * 구현체는 XML 구문, XHTML 구조, EPUB 네임스페이스,
 * 접근성 속성 및 GomsBookEditor 내부 규칙을 검사할 수 있습니다.
 * </p>
 *
 * <p>
 * 검증은 LLM이 아니라 결정적인 Java 코드로 수행하는 것을 원칙으로 합니다.
 * </p>
 * 
 * XhtmlValidator validator =
        new DefaultXhtmlValidator();

	XhtmlValidationResult result = validator.validate(
	                "chapter01.xhtml",
	                xhtml
	        );
	
	if (!result.valid()) {
	    result.issues().forEach(
	            issue -> System.err.println(
	                    issue.code()
	                            + ": "
	                            + issue.message()
	            )
	    );
	}

 */
public interface XhtmlValidator {

    /**
     * XHTML 문자열을 검증합니다.
     *
     * @param xhtml 검증할 전체 XHTML 문자열
     * @return XHTML 검증 결과
     */
    XhtmlValidationResult validate(String xhtml);

    /**
     * 파일 이름과 XHTML 문자열을 함께 전달하여 검증합니다.
     *
     * <p>
     * 기본 구현은 파일 이름을 사용하지 않고
     * {@link #validate(String)}를 호출합니다.
     * 구현체는 파일 위치를 Issue에 포함하려면 이 메서드를 재정의할 수 있습니다.
     * </p>
     *
     * @param fileName 검증 대상 파일 이름
     * @param xhtml 검증할 전체 XHTML 문자열
     * @return XHTML 검증 결과
     */
    default XhtmlValidationResult validate(
            String fileName,
            String xhtml
    ) {
        return validate(xhtml);
    }

    /**
     * Validator 이름을 반환합니다.
     *
     * @return Validator 이름
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Validator 버전을 반환합니다.
     *
     * @return Validator 버전
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Validator가 현재 사용 가능한지 반환합니다.
     *
     * @return 사용 가능하면 true
     */
    default boolean isAvailable() {
        return true;
    }
}