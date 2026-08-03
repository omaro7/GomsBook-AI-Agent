/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)1
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool;

/**
 * 요청한 Tool이 Registry에 등록되어 있지 않을 때 발생합니다.
 * tool 이름 규칙입니다.
 * xhtml.generate
xhtml.validate
epub.validate
accessibility.check
metadata.generate
file.apply-change
 */
public final class ToolNotFoundException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String toolName;

    public ToolNotFoundException(
            String toolName
    ) {
        super(
                "Tool is not registered: "
                        + String.valueOf(toolName)
        );

        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}