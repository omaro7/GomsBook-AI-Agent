/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.util.Locale;
import java.util.Optional;

/**
 * 접근성 검사에서 발견할 수 있는 문제 유형을 식별하는 코드.
 *
 * <p>각 코드는 기본 심각도, 자동 수정 가능 여부, 사용자 검토 필요 여부,
 * 적용 대상 영역을 함께 정의한다.</p>
 */
public enum AccessibilityIssueCode {

    /*
     * 이미지 접근성
     */

    IMAGE_ALT_MISSING(
            "image_alt_missing",
            "이미지 대체 텍스트 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            true,
            false
    ),

    IMAGE_ALT_EMPTY(
            "image_alt_empty",
            "정보성 이미지의 빈 대체 텍스트",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    IMAGE_ALT_TOO_LONG(
            "image_alt_too_long",
            "지나치게 긴 이미지 대체 텍스트",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    IMAGE_ALT_REDUNDANT_PREFIX(
            "image_alt_redundant_prefix",
            "불필요한 이미지 설명 접두어",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            true,
            false
    ),

    IMAGE_ALT_DUPLICATES_CAPTION(
            "image_alt_duplicates_caption",
            "대체 텍스트와 캡션의 중복",
            AccessibilitySeverity.INFO,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    IMAGE_ALT_FILENAME_USED(
            "image_alt_filename_used",
            "파일명이 대체 텍스트로 사용됨",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    IMAGE_SOURCE_MISSING(
            "image_source_missing",
            "이미지 파일 참조 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    IMAGE_TYPE_UNKNOWN(
            "image_type_unknown",
            "이미지 접근성 유형을 판단할 수 없음",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    DECORATIVE_IMAGE_ALT_NOT_EMPTY(
            "decorative_image_alt_not_empty",
            "장식 이미지의 대체 텍스트가 비어 있지 않음",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            true,
            false
    ),

    DECORATIVE_IMAGE_ROLE_INVALID(
            "decorative_image_role_invalid",
            "장식 이미지 역할 속성 오류",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            true,
            false
    ),

    MEANINGFUL_IMAGE_HIDDEN(
            "meaningful_image_hidden",
            "정보성 이미지가 보조기기에서 숨겨짐",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            true,
            true
    ),

    COMPLEX_IMAGE_DESCRIPTION_MISSING(
            "complex_image_description_missing",
            "복합 이미지의 상세 설명 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    TEXT_IMAGE_TRANSCRIPTION_MISSING(
            "text_image_transcription_missing",
            "텍스트 이미지의 내용 제공 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    COVER_ALT_MISSING(
            "cover_alt_missing",
            "표지 이미지 대체 텍스트 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    FIGURE_CAPTION_REFERENCE_INVALID(
            "figure_caption_reference_invalid",
            "figure와 figcaption 연결 오류",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.IMAGE,
            false,
            true
    ),

    /*
     * 문서 언어 및 메타데이터
     */

    DOCUMENT_LANGUAGE_MISSING(
            "document_language_missing",
            "문서 언어 속성 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.DOCUMENT,
            true,
            false
    ),

    DOCUMENT_LANGUAGE_INVALID(
            "document_language_invalid",
            "문서 언어 코드 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.DOCUMENT,
            false,
            true
    ),

    DOCUMENT_LANGUAGE_MISMATCH(
            "document_language_mismatch",
            "lang과 xml:lang 값 불일치",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.DOCUMENT,
            true,
            false
    ),

    TITLE_MISSING(
            "title_missing",
            "문서 title 요소 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.DOCUMENT,
            false,
            true
    ),

    DOCUMENT_TITLE_EMPTY(
            "document_title_empty",
            "문서 title 요소가 비어 있음",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.DOCUMENT,
            false,
            true
    ),

    ACCESSIBILITY_METADATA_MISSING(
            "accessibility_metadata_missing",
            "EPUB 접근성 메타데이터 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.METADATA,
            false,
            true
    ),

    ACCESS_MODE_METADATA_MISSING(
            "access_mode_metadata_missing",
            "accessMode 메타데이터 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.METADATA,
            false,
            true
    ),

    ACCESSIBILITY_FEATURE_METADATA_MISSING(
            "accessibility_feature_metadata_missing",
            "accessibilityFeature 메타데이터 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.METADATA,
            false,
            true
    ),

    ACCESSIBILITY_HAZARD_METADATA_MISSING(
            "accessibility_hazard_metadata_missing",
            "accessibilityHazard 메타데이터 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.METADATA,
            false,
            true
    ),

    ACCESSIBILITY_SUMMARY_MISSING(
            "accessibility_summary_missing",
            "accessibilitySummary 메타데이터 누락",
            AccessibilitySeverity.INFO,
            AccessibilityCategory.METADATA,
            false,
            true
    ),

    /*
     * 제목 구조
     */

    HEADING_MISSING(
            "heading_missing",
            "문서 제목 구조 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.HEADING,
            false,
            true
    ),

    HEADING_LEVEL_SKIPPED(
            "heading_level_skipped",
            "제목 단계 건너뛰기",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.HEADING,
            false,
            true
    ),

    HEADING_EMPTY(
            "heading_empty",
            "빈 제목 요소",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.HEADING,
            false,
            true
    ),

    MULTIPLE_PRIMARY_HEADINGS(
            "multiple_primary_headings",
            "최상위 제목이 여러 개 존재함",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.HEADING,
            false,
            true
    ),

    HEADING_ORDER_INVALID(
            "heading_order_invalid",
            "제목 구조 순서 오류",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.HEADING,
            false,
            true
    ),

    /*
     * 링크
     */

    LINK_TEXT_EMPTY(
            "link_text_empty",
            "링크 텍스트 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.LINK,
            false,
            true
    ),

    LINK_TEXT_AMBIGUOUS(
            "link_text_ambiguous",
            "의미가 불명확한 링크 텍스트",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.LINK,
            false,
            true
    ),

    LINK_TARGET_MISSING(
            "link_target_missing",
            "링크 대상 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.LINK,
            false,
            true
    ),

    LINK_TARGET_NOT_FOUND(
            "link_target_not_found",
            "링크 대상 파일 또는 요소를 찾을 수 없음",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.LINK,
            false,
            true
    ),

    IMAGE_LINK_NAME_MISSING(
            "image_link_name_missing",
            "이미지 링크의 접근 가능한 이름 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.LINK,
            false,
            true
    ),

    /*
     * 표
     */

    TABLE_CAPTION_MISSING(
            "table_caption_missing",
            "표 제목 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.TABLE,
            false,
            true
    ),

    TABLE_HEADER_MISSING(
            "table_header_missing",
            "표 머리글 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.TABLE,
            false,
            true
    ),

    TABLE_HEADER_SCOPE_MISSING(
            "table_header_scope_missing",
            "표 머리글 scope 속성 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.TABLE,
            true,
            false
    ),

    TABLE_HEADER_REFERENCE_INVALID(
            "table_header_reference_invalid",
            "표 머리글 참조 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.TABLE,
            false,
            true
    ),

    TABLE_STRUCTURE_COMPLEX(
            "table_structure_complex",
            "복합 표 구조 사용자 검토 필요",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.TABLE,
            false,
            true
    ),

    TABLE_LAYOUT_USAGE(
            "table_layout_usage",
            "레이아웃 목적으로 사용된 표",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.TABLE,
            false,
            true
    ),

    /*
     * ARIA
     */

    ARIA_REFERENCE_INVALID(
            "aria_reference_invalid",
            "ARIA 참조 대상 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    ARIA_ROLE_INVALID(
            "aria_role_invalid",
            "유효하지 않은 ARIA role",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    ARIA_ATTRIBUTE_INVALID(
            "aria_attribute_invalid",
            "유효하지 않은 ARIA 속성",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    ARIA_ATTRIBUTE_NOT_ALLOWED(
            "aria_attribute_not_allowed",
            "role에 허용되지 않는 ARIA 속성",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    ARIA_LABEL_EMPTY(
            "aria_label_empty",
            "비어 있는 aria-label",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    ARIA_HIDDEN_FOCUSABLE(
            "aria_hidden_focusable",
            "숨겨진 요소가 포커스를 받을 수 있음",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.ARIA,
            false,
            true
    ),

    REDUNDANT_ARIA_ROLE(
            "redundant_aria_role",
            "불필요한 ARIA role",
            AccessibilitySeverity.INFO,
            AccessibilityCategory.ARIA,
            true,
            false
    ),

    /*
     * 내비게이션
     */

    NAVIGATION_DOCUMENT_MISSING(
            "navigation_document_missing",
            "EPUB 내비게이션 문서 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.NAVIGATION,
            false,
            true
    ),

    TABLE_OF_CONTENTS_MISSING(
            "table_of_contents_missing",
            "목차 내비게이션 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.NAVIGATION,
            false,
            true
    ),

    TABLE_OF_CONTENTS_LINK_INVALID(
            "table_of_contents_link_invalid",
            "목차 링크 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.NAVIGATION,
            false,
            true
    ),

    LANDMARK_NAVIGATION_MISSING(
            "landmark_navigation_missing",
            "랜드마크 내비게이션 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.NAVIGATION,
            false,
            true
    ),

    PAGE_LIST_MISSING(
            "page_list_missing",
            "페이지 목록 내비게이션 누락",
            AccessibilitySeverity.INFO,
            AccessibilityCategory.NAVIGATION,
            false,
            true
    ),

    /*
     * 목록과 구조
     */

    LIST_STRUCTURE_INVALID(
            "list_structure_invalid",
            "목록 구조 오류",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.STRUCTURE,
            false,
            true
    ),

    LIST_ITEM_OUTSIDE_LIST(
            "list_item_outside_list",
            "목록 외부의 li 요소",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.STRUCTURE,
            false,
            true
    ),

    LANDMARK_MISSING(
            "landmark_missing",
            "문서 랜드마크 누락",
            AccessibilitySeverity.INFO,
            AccessibilityCategory.STRUCTURE,
            false,
            true
    ),

    EPUB_TYPE_INVALID(
            "epub_type_invalid",
            "유효하지 않은 epub:type 값",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.STRUCTURE,
            false,
            true
    ),

    DUPLICATE_ELEMENT_ID(
            "duplicate_element_id",
            "중복된 요소 id",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.STRUCTURE,
            false,
            true
    ),

    /*
     * 폼 및 사용자 입력
     */

    FORM_LABEL_MISSING(
            "form_label_missing",
            "입력 요소 레이블 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.FORM,
            false,
            true
    ),

    FORM_CONTROL_NAME_MISSING(
            "form_control_name_missing",
            "폼 컨트롤의 접근 가능한 이름 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.FORM,
            false,
            true
    ),

    FORM_FIELDSET_LEGEND_MISSING(
            "form_fieldset_legend_missing",
            "그룹 입력 요소의 legend 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.FORM,
            false,
            true
    ),

    /*
     * 오디오와 비디오
     */

    AUDIO_TRANSCRIPT_MISSING(
            "audio_transcript_missing",
            "오디오 대체 텍스트 또는 대본 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.MEDIA,
            false,
            true
    ),

    VIDEO_CAPTION_MISSING(
            "video_caption_missing",
            "비디오 자막 누락",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.MEDIA,
            false,
            true
    ),

    VIDEO_AUDIO_DESCRIPTION_MISSING(
            "video_audio_description_missing",
            "비디오 화면해설 누락",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.MEDIA,
            false,
            true
    ),

    MEDIA_CONTROLS_INACCESSIBLE(
            "media_controls_inaccessible",
            "미디어 제어 접근성 오류",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.MEDIA,
            false,
            true
    ),

    /*
     * 색상과 시각 표현
     */

    COLOR_ONLY_INFORMATION(
            "color_only_information",
            "색상만으로 정보 전달",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.VISUAL,
            false,
            true
    ),

    COLOR_CONTRAST_INSUFFICIENT(
            "color_contrast_insufficient",
            "텍스트 색상 대비 부족",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.VISUAL,
            false,
            true
    ),

    TEXT_RESIZE_RESTRICTED(
            "text_resize_restricted",
            "텍스트 크기 조절 제한",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.VISUAL,
            false,
            true
    ),

    /*
     * 기타
     */

    MANUAL_REVIEW_REQUIRED(
            "manual_review_required",
            "자동 판단이 어려워 사용자 검토가 필요함",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.GENERAL,
            false,
            true
    ),

    VALIDATION_FAILED(
            "validation_failed",
            "접근성 검사 실행 실패",
            AccessibilitySeverity.ERROR,
            AccessibilityCategory.GENERAL,
            false,
            true
    ),

    UNKNOWN(
            "unknown",
            "알 수 없는 접근성 문제",
            AccessibilitySeverity.WARNING,
            AccessibilityCategory.GENERAL,
            false,
            true
    );

    private final String code;
    private final String displayName;
    private final AccessibilitySeverity defaultSeverity;
    private final AccessibilityCategory category;
    private final boolean automaticallyFixable;
    private final boolean manualReviewRequired;

    AccessibilityIssueCode(
            String code,
            String displayName,
            AccessibilitySeverity defaultSeverity,
            AccessibilityCategory category,
            boolean automaticallyFixable,
            boolean manualReviewRequired) {

        this.code = code;
        this.displayName = displayName;
        this.defaultSeverity = defaultSeverity;
        this.category = category;
        this.automaticallyFixable = automaticallyFixable;
        this.manualReviewRequired = manualReviewRequired;
    }

    /**
     * 직렬화 및 외부 응답에 사용하는 고정 코드를 반환한다.
     *
     * @return 문제 코드
     */
    public String getCode() {
        return code;
    }

    /**
     * UI에 표시할 문제 이름을 반환한다.
     *
     * @return 표시 이름
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 문제의 기본 심각도를 반환한다.
     *
     * @return 기본 심각도
     */
    public AccessibilitySeverity getDefaultSeverity() {
        return defaultSeverity;
    }

    /**
     * 문제 범주를 반환한다.
     *
     * @return 접근성 범주
     */
    public AccessibilityCategory getCategory() {
        return category;
    }

    /**
     * 규칙 기반 자동 수정이 가능한 문제인지 반환한다.
     *
     * @return 자동 수정 가능 여부
     */
    public boolean isAutomaticallyFixable() {
        return automaticallyFixable;
    }

    /**
     * 사용자 검토가 필요한 문제인지 반환한다.
     *
     * @return 사용자 검토 필요 여부
     */
    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    /**
     * 출판 또는 접근성 완료 처리를 차단할 수 있는 문제인지 반환한다.
     *
     * @return 차단 수준이면 {@code true}
     */
    public boolean blocksPublication() {
        return defaultSeverity.isBlocksPublication();
    }

    /**
     * 이미지 관련 문제인지 반환한다.
     *
     * @return 이미지 범주이면 {@code true}
     */
    public boolean isImageIssue() {
        return category == AccessibilityCategory.IMAGE;
    }

    /**
     * 메타데이터 관련 문제인지 반환한다.
     *
     * @return 메타데이터 범주이면 {@code true}
     */
    public boolean isMetadataIssue() {
        return category == AccessibilityCategory.METADATA;
    }

    /**
     * 코드, enum 이름 또는 표시 이름으로 문제 코드를 찾는다.
     *
     * @param value 변환할 문자열
     * @return 일치하는 문제 코드
     */
    public static Optional<AccessibilityIssueCode> fromValue(
            String value) {

        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);

        for (AccessibilityIssueCode issueCode : values()) {
            if (normalize(issueCode.code).equals(normalized)
                    || normalize(issueCode.name()).equals(normalized)
                    || normalize(issueCode.displayName).equals(normalized)) {

                return Optional.of(issueCode);
            }
        }

        return Optional.empty();
    }

    /**
     * 문자열을 문제 코드로 변환하고, 일치하지 않으면 UNKNOWN을 반환한다.
     *
     * @param value 변환할 문자열
     * @return 접근성 문제 코드
     */
    public static AccessibilityIssueCode fromValueOrUnknown(
            String value) {

        return fromValue(value).orElse(UNKNOWN);
    }

    private static String normalize(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    /**
     * 접근성 문제의 기능적 범주.
     */
    public enum AccessibilityCategory {

        IMAGE("image", "이미지"),
        DOCUMENT("document", "문서"),
        METADATA("metadata", "메타데이터"),
        HEADING("heading", "제목 구조"),
        LINK("link", "링크"),
        TABLE("table", "표"),
        ARIA("aria", "ARIA"),
        NAVIGATION("navigation", "내비게이션"),
        STRUCTURE("structure", "문서 구조"),
        FORM("form", "폼"),
        MEDIA("media", "오디오·비디오"),
        VISUAL("visual", "시각 표현"),
        GENERAL("general", "일반");

        private final String code;
        private final String displayName;

        AccessibilityCategory(
                String code,
                String displayName) {

            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Optional<AccessibilityCategory> fromValue(
                String value) {

            if (value == null || value.isBlank()) {
                return Optional.empty();
            }

            String normalized = normalize(value);

            for (AccessibilityCategory category : values()) {
                if (normalize(category.code).equals(normalized)
                        || normalize(category.name()).equals(normalized)
                        || normalize(category.displayName).equals(normalized)) {

                    return Optional.of(category);
                }
            }

            return Optional.empty();
        }
    }
}