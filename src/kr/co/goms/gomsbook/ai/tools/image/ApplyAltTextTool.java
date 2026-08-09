/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.image;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicationException;
import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicationRequest;
import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicationResult;
import kr.co.goms.gomsbook.ai.accessibility.application.AltTextApplicator;
import kr.co.goms.gomsbook.ai.accessibility.model.ImageAccessibilityType;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

/**
 * 이미지 대체 텍스트와 관련 접근성 속성을 XHTML img 요소에
 * 적용하는 Agent Tool.
 *
 * <p>
 * XHTML 파싱, img 요소 탐색, 기존 alt 충돌 검사,
 * 접근성 속성 변경, 백업 및 저장은 직접 처리하지 않고
 * {@link AltTextApplicator} 구현체에 위임합니다.
 * </p>
 *
 * <pre>
 * ApplyAltTextTool
 *      ↓
 * AltTextApplicationRequest
 *      ↓
 * AltTextApplicator
 *      ↓
 * DefaultAltTextApplicator
 * </pre>
 */
public final class ApplyAltTextTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "apply_alt_text";

    private static final String TOOL_DESCRIPTION =
            "이미지 대체 텍스트와 접근성 속성을 "
                    + "XHTML img 요소에 적용합니다.";

    private static final int DEFAULT_MAX_ALT_LENGTH =
            100;

    private static final int MAX_ALT_LENGTH =
            2000;

    private final AltTextApplicator altTextApplicator;

    /**
     * AltTextApplicator 기반 Tool을 생성합니다.
     *
     * @param altTextApplicator 대체 텍스트 적용 서비스
     */
    public ApplyAltTextTool(
            AltTextApplicator altTextApplicator) {

        this.altTextApplicator =
                Objects.requireNonNull(
                        altTextApplicator,
                        "altTextApplicator must not be null"
                );
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public ToolResult execute(
            ToolRequest toolRequest,
            ToolContext toolContext) {

        if (toolRequest == null) {
            return failure(
                    ToolStatus.FAILED,
                    "TOOL_REQUEST_REQUIRED",
                    "ToolRequest가 없습니다."
            );
        }

        try {
            ApplyAltTextRequest request =
                    parseRequest(
                            toolRequest
                    );

            List<ToolIssue> validationIssues =
                    validateRequest(
                            request
                    );

            if (!validationIssues.isEmpty()) {
                return ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message(
                                "대체 텍스트 적용 요청이 "
                                        + "올바르지 않습니다."
                        )
                        .issues(validationIssues)
                        .build();
            }

            Path projectRoot =
                    resolveProjectRoot(
                            request,
                            toolContext
                    );

            Path xhtmlPath =
                    resolveXhtmlPath(
                            request,
                            projectRoot
                    );

            AltTextApplicationRequest
                    applicationRequest =
                    createApplicationRequest(
                            request,
                            projectRoot,
                            xhtmlPath
                    );

            /*
             * Applicator가 현재 요청을 처리할 수 있는지
             * 먼저 확인합니다.
             */
            if (!altTextApplicator.supports(
                    applicationRequest)) {

                return failure(
                        ToolStatus.FAILED,
                        "ALT_TEXT_APPLICATION_UNSUPPORTED",
                        "현재 AltTextApplicator가 "
                                + "이 요청을 지원하지 않습니다."
                );
            }

            AltTextApplicationResult result =
                    altTextApplicator.apply(
                            applicationRequest
                    );

            if (result == null) {
                return failure(
                        ToolStatus.FAILED,
                        "ALT_TEXT_APPLICATION_EMPTY",
                        "대체 텍스트 적용 결과가 없습니다."
                );
            }

            Map<String, Object> data =
                    createOutput(
                            result
                    );

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(
                            ToolStatus.SUCCESS
                    )
                    .message(
                            createSuccessMessage(
                                    result
                            )
                    )
                    .data(data)
                    .build();

        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_XHTML_PATH",
                    "유효하지 않은 XHTML 경로입니다: "
                            + exception.getInput()
            );

        } catch (AltTextApplicationException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "대체 텍스트 적용에 실패했습니다: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .cause(exception)
                    .build();

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_PATH_ACCESS_DENIED",
                    safeMessage(exception)
            );

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_ALT_TEXT_REQUEST",
                    safeMessage(exception)
            );

        } catch (RuntimeException exception) {
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.FAILED)
                    .message(
                            "대체 텍스트 적용 중 "
                                    + "예상하지 못한 오류가 발생했습니다: "
                                    + safeMessage(
                                            exception
                                    )
                    )
                    .cause(exception)
                    .build();
        }
    }

    /**
     * ToolRequest arguments를 Tool 입력 DTO로 변환합니다.
     *
     * <p>
     * JsonMapper 의존성을 제거하고 Map 기반으로 직접 읽습니다.
     * </p>
     */
    private ApplyAltTextRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments =
                toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "대체 텍스트 적용 인자가 없습니다."
            );
        }

        if (arguments
                instanceof ApplyAltTextRequest request) {

            return request;
        }

        if (!(arguments
                instanceof Map<?, ?> map)) {

            throw new IllegalArgumentException(
                    "대체 텍스트 적용 인자는 "
                            + "Object 또는 Map 형식이어야 합니다."
            );
        }

        ApplyAltTextRequest request =
                new ApplyAltTextRequest();

        request.setProjectRoot(
                readString(
                        map,
                        "projectRoot"
                )
        );

        /*
         * 기존 Tool 계약과 새 접근성 Tool 계약을
         * 모두 받을 수 있도록 별칭을 지원합니다.
         */
        request.setXhtmlPath(
                firstNonBlank(
                        readString(
                                map,
                                "xhtmlPath"
                        ),
                        readString(
                                map,
                                "documentPath"
                        )
                )
        );

        request.setImageElementId(
                readString(
                        map,
                        "imageElementId"
                )
        );

        request.setImageSrc(
                firstNonBlank(
                        readString(
                                map,
                                "imageSrc"
                        ),
                        readString(
                                map,
                                "imageSource"
                        )
                )
        );

        request.setAccessibilityType(
                readString(
                        map,
                        "accessibilityType"
                )
        );

        request.setAltText(
                readString(
                        map,
                        "altText"
                )
        );

        request.setDetailedDescription(
                readString(
                        map,
                        "detailedDescription"
                )
        );

        request.setExpectedCurrentAlt(
                readStringAllowEmpty(
                        map,
                        "expectedCurrentAlt"
                )
        );

        Integer maxAltLength =
                firstNonNull(
                        readInteger(
                                map,
                                "maxAltLength"
                        ),
                        readInteger(
                                map,
                                "maxAltTextLength"
                        )
                );

        if (maxAltLength != null) {
            request.setMaxAltLength(
                    maxAltLength
            );
        }

        Boolean decorative =
                readBoolean(
                        map,
                        "decorative"
                );

        if (decorative != null) {
            request.setDecorative(
                    decorative
            );
        }

        Boolean overwriteExisting =
                readBoolean(
                        map,
                        "overwriteExisting"
                );

        if (overwriteExisting != null) {
            request.setOverwriteExisting(
                    overwriteExisting
            );
        }

        Boolean removeTitle =
                readBoolean(
                        map,
                        "removeTitle"
                );

        if (removeTitle != null) {
            request.setRemoveTitle(
                    removeTitle
            );
        }

        Boolean removeAriaLabel =
                readBoolean(
                        map,
                        "removeAriaLabel"
                );

        if (removeAriaLabel != null) {
            request.setRemoveAriaLabel(
                    removeAriaLabel
            );
        }

        Boolean applyPresentationRole =
                readBoolean(
                        map,
                        "applyPresentationRole"
                );

        if (applyPresentationRole != null) {
            request.setApplyPresentationRole(
                    applyPresentationRole
            );
        }

        Boolean applyAriaHidden =
                readBoolean(
                        map,
                        "applyAriaHidden"
                );

        if (applyAriaHidden != null) {
            request.setApplyAriaHidden(
                    applyAriaHidden
            );
        }

        Boolean createBackup =
                readBoolean(
                        map,
                        "createBackup"
                );

        if (createBackup != null) {
            request.setCreateBackup(
                    createBackup
            );
        }

        Boolean dryRun =
                readBoolean(
                        map,
                        "dryRun"
                );

        if (dryRun != null) {
            request.setDryRun(
                    dryRun
            );
        }

        Boolean restrictToProject =
                readBoolean(
                        map,
                        "restrictToProject"
                );

        if (restrictToProject != null) {
            request.setRestrictToProject(
                    restrictToProject
            );
        }

        return request;
    }

    private List<ToolIssue> validateRequest(
            ApplyAltTextRequest request) {

        if (request == null) {
            return Collections.singletonList(
                    issue(
                            "REQUEST_REQUIRED",
                            "대체 텍스트 적용 요청이 없습니다."
                    )
            );
        }

        List<ToolIssue> issues =
                new ArrayList<>();

        if (isBlank(
                request.getXhtmlPath())) {

            issues.add(
                    issue(
                            "XHTML_PATH_REQUIRED",
                            "대상 XHTML 경로가 필요합니다."
                    )
            );
        }

        if (isBlank(
                request.getImageElementId())
                && isBlank(
                        request.getImageSrc())) {

            issues.add(
                    issue(
                            "IMAGE_SELECTOR_REQUIRED",
                            "imageElementId 또는 "
                                    + "imageSource가 필요합니다."
                    )
            );
        }

        ImageAccessibilityType accessibilityType =
                resolveAccessibilityType(
                        request
                );

        if (accessibilityType
                == ImageAccessibilityType.UNKNOWN) {

            issues.add(
                    issue(
                            "ACCESSIBILITY_TYPE_REQUIRED",
                            "유효한 이미지 접근성 유형이 필요합니다."
                    )
            );
        }

        if (accessibilityType
                != ImageAccessibilityType.UNKNOWN
                && accessibilityType
                        .isAltTextRequired()
                && isBlank(
                        request.getAltText())) {

            issues.add(
                    issue(
                            "ALT_TEXT_REQUIRED",
                            "비장식 이미지에는 "
                                    + "대체 텍스트가 필요합니다."
                    )
            );
        }

        if (accessibilityType
                != ImageAccessibilityType.UNKNOWN
                && accessibilityType.isDecorative()
                && !isBlank(
                        request.getAltText())) {

            issues.add(
                    issue(
                            "DECORATIVE_ALT_NOT_EMPTY",
                            "장식 이미지는 빈 alt를 사용해야 합니다."
                    )
            );
        }

        if (request.getMaxAltLength() < 0
                || request.getMaxAltLength()
                        > MAX_ALT_LENGTH) {

            issues.add(
                    issue(
                            "INVALID_MAX_ALT_LENGTH",
                            "대체 텍스트 최대 길이는 0~"
                                    + MAX_ALT_LENGTH
                                    + " 사이여야 합니다."
                    )
            );
        }

        if (request.getMaxAltLength() > 0
                && !isBlank(
                        request.getAltText())
                && request
                        .getAltText()
                        .trim()
                        .length()
                        > request
                                .getMaxAltLength()) {

            issues.add(
                    issue(
                            "ALT_TEXT_TOO_LONG",
                            "대체 텍스트가 최대 길이 "
                                    + request
                                            .getMaxAltLength()
                                    + "자를 초과했습니다."
                    )
            );
        }

        return issues;
    }

    private Path resolveProjectRoot(
            ApplyAltTextRequest request,
            ToolContext toolContext) {

        String projectRootValue =
                trimToNull(
                        request.getProjectRoot()
                );

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectRoot"
                    );
        }

        if (projectRootValue == null) {
            projectRootValue =
                    getContextString(
                            toolContext,
                            "projectPath"
                    );
        }

        if (projectRootValue == null) {
            throw new IllegalArgumentException(
                    "프로젝트 루트가 필요합니다."
            );
        }

        return Path.of(
                projectRootValue
        )
        .toAbsolutePath()
        .normalize();
    }

    private Path resolveXhtmlPath(
            ApplyAltTextRequest request,
            Path projectRoot) {

        Path requestedPath =
                Path.of(
                        request
                                .getXhtmlPath()
                                .trim()
                );

        Path xhtmlPath;

        if (requestedPath.isAbsolute()) {
            xhtmlPath =
                    requestedPath
                            .toAbsolutePath()
                            .normalize();

        } else {
            xhtmlPath =
                    projectRoot
                            .resolve(
                                    requestedPath
                            )
                            .toAbsolutePath()
                            .normalize();
        }

        if (request.isRestrictToProject()
                && !xhtmlPath.startsWith(
                        projectRoot)) {

            throw new SecurityException(
                    "프로젝트 루트 외부 XHTML은 "
                            + "수정할 수 없습니다."
            );
        }

        return xhtmlPath;
    }

    private AltTextApplicationRequest
            createApplicationRequest(
                    ApplyAltTextRequest request,
                    Path projectRoot,
                    Path xhtmlPath) {

        ImageAccessibilityType accessibilityType =
                resolveAccessibilityType(
                        request
                );

        String altText =
                accessibilityType.isDecorative()
                        ? ""
                        : normalizeAltText(
                                request.getAltText(),
                                request.getMaxAltLength()
                        );

        AltTextApplicationRequest.Builder builder =
                AltTextApplicationRequest.builder()
                        .projectRoot(
                                projectRoot
                        )
                        .xhtmlPath(
                                xhtmlPath
                        )
                        .imageElementId(
                                trimToNull(
                                        request
                                                .getImageElementId()
                                )
                        )
                        .imageSource(
                                trimToNull(
                                        request
                                                .getImageSrc()
                                )
                        )
                        .accessibilityType(
                                accessibilityType
                        )
                        .altText(
                                altText
                        )
                        .detailedDescription(
                                trimToNull(
                                        request
                                                .getDetailedDescription()
                                )
                        )
                        .overwriteExisting(
                                request
                                        .isOverwriteExisting()
                        )
                        .removeTitle(
                                request.isRemoveTitle()
                        )
                        .removeAriaLabel(
                                request
                                        .isRemoveAriaLabel()
                        )
                        .applyPresentationRole(
                                request
                                        .isApplyPresentationRole()
                        )
                        .applyAriaHidden(
                                request
                                        .isApplyAriaHidden()
                        )
                        .createBackup(
                                request
                                        .isCreateBackup()
                        )
                        .dryRun(
                                request.isDryRun()
                        )
                        .metadata(
                                "toolName",
                                TOOL_NAME
                        );

        /*
         * null과 ""은 의미가 다릅니다.
         *
         * null:
         * 현재 alt 값 비교를 하지 않음.
         *
         * "":
         * 현재 alt="" 상태일 때만 적용.
         */
        if (request.hasExpectedCurrentAlt()) {
            builder.expectedCurrentAlt(
                    request
                            .getExpectedCurrentAlt()
            );
        }

        return builder.build();
    }

    private ImageAccessibilityType
            resolveAccessibilityType(
                    ApplyAltTextRequest request) {

        String value =
                trimToNull(
                        request.getAccessibilityType()
                );

        /*
         * 이전 Tool 계약과 호환하기 위해
         * decorative=true인 경우 우선 DECORATIVE로 처리합니다.
         */
        if (request.isDecorative()) {
            return ImageAccessibilityType.DECORATIVE;
        }

        if (value == null) {
            /*
             * 기존 Tool 요청은 accessibilityType이 없었으므로
             * altText가 있으면 INFORMATIVE로 간주합니다.
             */
            if (!isBlank(
                    request.getAltText())) {

                return ImageAccessibilityType.INFORMATIVE;
            }

            return ImageAccessibilityType.UNKNOWN;
        }

        return ImageAccessibilityType
                .fromValueOrUnknown(
                        value
                );
    }

    private Map<String, Object> createOutput(
            AltTextApplicationResult result) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "documentPath",
                result.getProjectRelativeXhtmlPath()
        );

        output.put(
                "imageElementId",
                result.getImageElementId()
        );

        output.put(
                "imageSource",
                result.getImageSource()
        );

        if (result.getAccessibilityType()
                != null) {

            output.put(
                    "accessibilityType",
                    result
                            .getAccessibilityType()
                            .getCode()
            );
        }

        output.put(
                "matched",
                result.isMatched()
        );

        output.put(
                "matchedElementCount",
                result.getMatchedElementCount()
        );

        output.put(
                "ambiguousMatch",
                result.isAmbiguousMatch()
        );

        output.put(
                "changed",
                result.isChanged()
        );

        output.put(
                "noChange",
                result.isNoChange()
        );

        output.put(
                "fileUpdated",
                result.isFileUpdated()
        );

        output.put(
                "dryRun",
                result.isDryRun()
        );

        output.put(
                "successful",
                result.isSuccessful()
        );

        output.put(
                "decorative",
                result.isDecorative()
        );

        output.put(
                "previousAltText",
                result.getPreviousAltText()
        );

        output.put(
                "appliedAltText",
                result.getAppliedAltText()
        );

        output.put(
                "previousRole",
                result.getPreviousRole()
        );

        output.put(
                "appliedRole",
                result.getAppliedRole()
        );

        output.put(
                "previousAriaHidden",
                result.getPreviousAriaHidden()
        );

        output.put(
                "appliedAriaHidden",
                result.getAppliedAriaHidden()
        );

        output.put(
                "changedAttributes",
                result.getChangedAttributes()
        );

        output.put(
                "backupCreated",
                result.isBackupCreated()
        );

        if (result.getBackupPath() != null) {
            output.put(
                    "backupPath",
                    result
                            .getBackupPath()
                            .toString()
                            .replace('\\', '/')
            );
        }

        output.put(
                "warnings",
                result.getWarnings()
        );

        output.put(
                "metadata",
                result.getMetadata()
        );

        return Collections.unmodifiableMap(
                output
        );
    }

    private String createSuccessMessage(
            AltTextApplicationResult result) {

        if (!result.isMatched()) {
            return "대상 img 요소를 찾지 못했습니다.";
        }

        if (result.isAmbiguousMatch()) {
            return "여러 img 요소가 일치하여 "
                    + "파일을 수정하지 않았습니다.";
        }

        if (result.isDryRun()) {
            if (result.isChanged()) {
                return "대체 텍스트 변경 내용을 계산했습니다. "
                        + "dry-run이므로 파일은 수정하지 않았습니다.";
            }

            return "dry-run을 완료했습니다. "
                    + "변경할 내용이 없습니다.";
        }

        if (result.isNoChange()) {
            return "요청한 접근성 속성이 이미 적용되어 있습니다.";
        }

        if (result.isFileUpdated()) {
            return "이미지 대체 텍스트와 "
                    + "접근성 속성을 XHTML에 적용했습니다.";
        }

        return "대체 텍스트 적용 처리를 완료했습니다.";
    }

    private String normalizeAltText(
            String altText,
            int maxLength) {

        if (altText == null) {
            return null;
        }

        String normalized =
                altText
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (maxLength <= 0
                || normalized.length()
                        <= maxLength) {

            return normalized;
        }

        /*
         * Tool에서 임의 절삭하지 않습니다.
         * 접근성 의미가 손실될 수 있으므로 Validation 단계에서
         * 오류로 처리합니다.
         */
        return normalized;
    }

    private ToolResult failure(
            ToolStatus status,
            String code,
            String message) {

        return ToolResult.builder()
                .toolName(
                        TOOL_NAME
                )
                .status(
                        status
                )
                .message(
                        message
                )
                .issues(
                        Collections.singletonList(
                                issue(
                                        code,
                                        message
                                )
                        )
                )
                .build();
    }

    private ToolIssue issue(
            String code,
            String message) {

        return ToolIssue.builder()
                .code(
                        code
                )
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .message(
                        message
                )
                .build();
    }

    private String getContextString(
            ToolContext context,
            String key) {

        if (context == null
                || isBlank(key)) {

            return null;
        }

        try {
            String value =
                    context.getAttribute(
                            key,
                            String.class
                    );

            return trimToNull(
                    value
            );

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String readString(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        return String.valueOf(
                value
        );
    }

    /**
     * 빈 문자열도 의미가 있는 속성을 읽습니다.
     *
     * <p>
     * expectedCurrentAlt=""는 현재 alt="" 상태를
     * 기대한다는 의미입니다.
     * </p>
     */
    private String readStringAllowEmpty(
            Map<?, ?> map,
            String key) {

        if (!map.containsKey(key)) {
            return null;
        }

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        return String.valueOf(
                value
        );
    }

    private Integer readInteger(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(
                    String.valueOf(value)
                            .trim()
            );

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean readBoolean(
            Map<?, ?> map,
            String key) {

        Object value =
                map.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String text =
                String.valueOf(
                        value
                ).trim();

        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }

        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }

        return null;
    }

    private String firstNonBlank(
            String first,
            String second) {

        if (!isBlank(first)) {
            return first;
        }

        return second;
    }

    private Integer firstNonNull(
            Integer first,
            Integer second) {

        return first != null
                ? first
                : second;
    }

    private String trimToNull(
            String value) {

        return isBlank(value)
                ? null
                : value.trim();
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }

    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable
                        .getMessage()
                        .isBlank()) {

            return "알 수 없는 오류";
        }

        return throwable
                .getMessage()
                .trim();
    }

    /**
     * ApplyAltTextTool 입력 DTO.
     *
     * <p>
     * 기존 Tool 계약과 새 접근성 계층의 계약 사이에서
     * Adapter 역할을 합니다.
     * </p>
     */
    public static final class ApplyAltTextRequest {

        private String projectRoot;
        private String xhtmlPath;

        private String imageElementId;
        private String imageSrc;

        private String accessibilityType;

        private String altText;
        private String detailedDescription;

        private String expectedCurrentAlt;
        private boolean expectedCurrentAltSpecified;

        private int maxAltLength =
                DEFAULT_MAX_ALT_LENGTH;

        private boolean decorative;

        private boolean overwriteExisting;

        private boolean removeTitle;
        private boolean removeAriaLabel;

        private boolean applyPresentationRole =
                true;

        private boolean applyAriaHidden;

        private boolean createBackup =
                true;

        private boolean dryRun;

        private boolean restrictToProject =
                true;

        public ApplyAltTextRequest() {
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(
                String projectRoot) {

            this.projectRoot =
                    projectRoot;
        }

        public String getXhtmlPath() {
            return xhtmlPath;
        }

        public void setXhtmlPath(
                String xhtmlPath) {

            this.xhtmlPath =
                    xhtmlPath;
        }

        public String getImageElementId() {
            return imageElementId;
        }

        public void setImageElementId(
                String imageElementId) {

            this.imageElementId =
                    imageElementId;
        }

        public String getImageSrc() {
            return imageSrc;
        }

        public void setImageSrc(
                String imageSrc) {

            this.imageSrc =
                    imageSrc;
        }

        public String getAccessibilityType() {
            return accessibilityType;
        }

        public void setAccessibilityType(
                String accessibilityType) {

            this.accessibilityType =
                    accessibilityType;
        }

        public String getAltText() {
            return altText;
        }

        public void setAltText(
                String altText) {

            this.altText =
                    altText;
        }

        public String getDetailedDescription() {
            return detailedDescription;
        }

        public void setDetailedDescription(
                String detailedDescription) {

            this.detailedDescription =
                    detailedDescription;
        }

        public String getExpectedCurrentAlt() {
            return expectedCurrentAlt;
        }

        public void setExpectedCurrentAlt(
                String expectedCurrentAlt) {

            this.expectedCurrentAlt =
                    expectedCurrentAlt;

            this.expectedCurrentAltSpecified =
                    true;
        }

        public boolean hasExpectedCurrentAlt() {
            return expectedCurrentAltSpecified;
        }

        public int getMaxAltLength() {
            return maxAltLength;
        }

        public void setMaxAltLength(
                int maxAltLength) {

            this.maxAltLength =
                    maxAltLength;
        }

        public boolean isDecorative() {
            return decorative;
        }

        public void setDecorative(
                boolean decorative) {

            this.decorative =
                    decorative;
        }

        public boolean isOverwriteExisting() {
            return overwriteExisting;
        }

        public void setOverwriteExisting(
                boolean overwriteExisting) {

            this.overwriteExisting =
                    overwriteExisting;
        }

        public boolean isRemoveTitle() {
            return removeTitle;
        }

        public void setRemoveTitle(
                boolean removeTitle) {

            this.removeTitle =
                    removeTitle;
        }

        public boolean isRemoveAriaLabel() {
            return removeAriaLabel;
        }

        public void setRemoveAriaLabel(
                boolean removeAriaLabel) {

            this.removeAriaLabel =
                    removeAriaLabel;
        }

        public boolean isApplyPresentationRole() {
            return applyPresentationRole;
        }

        public void setApplyPresentationRole(
                boolean applyPresentationRole) {

            this.applyPresentationRole =
                    applyPresentationRole;
        }

        public boolean isApplyAriaHidden() {
            return applyAriaHidden;
        }

        public void setApplyAriaHidden(
                boolean applyAriaHidden) {

            this.applyAriaHidden =
                    applyAriaHidden;
        }

        public boolean isCreateBackup() {
            return createBackup;
        }

        public void setCreateBackup(
                boolean createBackup) {

            this.createBackup =
                    createBackup;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(
                boolean dryRun) {

            this.dryRun =
                    dryRun;
        }

        public boolean isRestrictToProject() {
            return restrictToProject;
        }

        public void setRestrictToProject(
                boolean restrictToProject) {

            this.restrictToProject =
                    restrictToProject;
        }
    }
}