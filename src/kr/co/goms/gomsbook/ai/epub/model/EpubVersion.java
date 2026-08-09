/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * EPUB 문서의 표준 버전을 정의합니다.
 *
 * <p>EPUB 패키지 문서의 {@code package} 요소에 지정되는
 * {@code version} 속성과 대응합니다.</p>
 *
 * <pre>
 * {@code
 * <package
 *     xmlns="http://www.idpf.org/2007/opf"
 *     version="3.0"
 *     unique-identifier="book-id">
 * }
 * </pre>
 */
public enum EpubVersion {

    /**
     * EPUB 2.0.1.
     *
     * <p>기존 전자책과의 호환성을 위해 지원합니다.</p>
     */
    EPUB_2_0_1(
            "2.0.1",
            "2.0",
            2,
            0,
            1,
            false,
            false
    ),

    /**
     * EPUB 3.0.
     */
    EPUB_3_0(
            "3.0",
            "3.0",
            3,
            0,
            0,
            true,
            true
    ),

    /**
     * EPUB 3.1.
     */
    EPUB_3_1(
            "3.1",
            "3.1",
            3,
            1,
            0,
            true,
            true
    ),

    /**
     * EPUB 3.2.
     */
    EPUB_3_2(
            "3.2",
            "3.2",
            3,
            2,
            0,
            true,
            true
    ),

    /**
     * EPUB 3.3.
     *
     * <p>GomsBook AI EPUB 계층의 기본 권장 버전입니다.</p>
     */
    EPUB_3_3(
            "3.3",
            "3.0",
            3,
            3,
            0,
            true,
            true
    );

    /**
     * 애플리케이션에서 사용하는 상세 EPUB 버전 문자열입니다.
     */
    private final String specificationVersion;

    /**
     * OPF package 요소의 version 속성에 기록할 값입니다.
     *
     * <p>EPUB 3.3 문서의 패키지 버전은 일반적으로 {@code 3.0}을 사용합니다.</p>
     */
    private final String packageVersion;

    private final int major;

    private final int minor;

    private final int patch;

    /**
     * EPUB Navigation Document 지원 여부입니다.
     */
    private final boolean navigationDocumentSupported;

    /**
     * EPUB 접근성 메타데이터 및 EPUB 3 접근성 기능 지원 여부입니다.
     */
    private final boolean accessibilitySupported;

    EpubVersion(
            String specificationVersion,
            String packageVersion,
            int major,
            int minor,
            int patch,
            boolean navigationDocumentSupported,
            boolean accessibilitySupported
    ) {
        this.specificationVersion = specificationVersion;
        this.packageVersion = packageVersion;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.navigationDocumentSupported = navigationDocumentSupported;
        this.accessibilitySupported = accessibilitySupported;
    }

    /**
     * EPUB 표준의 상세 버전을 반환합니다.
     *
     * @return EPUB 표준 버전
     */
    public String getSpecificationVersion() {
        return specificationVersion;
    }

    /**
     * OPF package 요소에 기록할 version 값을 반환합니다.
     *
     * @return package version 속성값
     */
    public String getPackageVersion() {
        return packageVersion;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    /**
     * EPUB 3 계열인지 확인합니다.
     *
     * @return EPUB 3 이상이면 {@code true}
     */
    public boolean isEpub3() {
        return major >= 3;
    }

    /**
     * EPUB 2 계열인지 확인합니다.
     *
     * @return EPUB 2이면 {@code true}
     */
    public boolean isEpub2() {
        return major == 2;
    }

    /**
     * EPUB Navigation Document를 지원하는지 확인합니다.
     *
     * @return 지원하면 {@code true}
     */
    public boolean supportsNavigationDocument() {
        return navigationDocumentSupported;
    }

    /**
     * EPUB 3 기반 접근성 메타데이터 및 접근성 기능을 지원하는지 확인합니다.
     *
     * @return 지원하면 {@code true}
     */
    public boolean supportsAccessibility() {
        return accessibilitySupported;
    }

    /**
     * 현재 버전이 전달된 버전 이상인지 확인합니다.
     *
     * @param other 비교할 EPUB 버전
     * @return 현재 버전이 같거나 높으면 {@code true}
     */
    public boolean isAtLeast(EpubVersion other) {
        if (other == null) {
            return true;
        }

        if (major != other.major) {
            return major > other.major;
        }

        if (minor != other.minor) {
            return minor > other.minor;
        }

        return patch >= other.patch;
    }

    /**
     * 버전 문자열을 EPUB 버전 열거형으로 변환합니다.
     *
     * <p>다음 형식을 지원합니다.</p>
     *
     * <ul>
     *     <li>{@code 2.0}</li>
     *     <li>{@code 2.0.1}</li>
     *     <li>{@code EPUB_2_0_1}</li>
     *     <li>{@code 3.0}</li>
     *     <li>{@code 3.1}</li>
     *     <li>{@code 3.2}</li>
     *     <li>{@code 3.3}</li>
     *     <li>{@code EPUB_3_3}</li>
     * </ul>
     *
     * @param value 버전 문자열
     * @return 일치하는 EPUB 버전
     */
    public static Optional<EpubVersion> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);

        if ("2.0".equals(normalized)) {
            return Optional.of(EPUB_2_0_1);
        }

        return Arrays.stream(values())
                .filter(version ->
                        version.specificationVersion.equals(normalized)
                                || version.name().equals(normalized)
                )
                .findFirst();
    }

    /**
     * 버전 문자열을 EPUB 버전으로 변환합니다.
     *
     * @param value 버전 문자열
     * @return 일치하는 EPUB 버전
     * @throws IllegalArgumentException 지원하지 않는 버전인 경우
     */
    public static EpubVersion require(String value) {
        return from(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported EPUB version: " + value
                ));
    }

    /**
     * 프로젝트의 기본 EPUB 버전을 반환합니다.
     *
     * @return EPUB 3.3
     */
    public static EpubVersion defaultVersion() {
        return EPUB_3_3;
    }

    private static String normalize(String value) {
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if (normalized.startsWith("EPUB_")) {
            return normalized;
        }

        if (normalized.startsWith("EPUB")) {
            normalized = normalized.substring(4);

            while (normalized.startsWith("_")
                    || normalized.startsWith("-")
                    || normalized.startsWith(" ")) {
                normalized = normalized.substring(1);
            }
        }

        return normalized;
    }

    @Override
    public String toString() {
        return specificationVersion;
    }
}