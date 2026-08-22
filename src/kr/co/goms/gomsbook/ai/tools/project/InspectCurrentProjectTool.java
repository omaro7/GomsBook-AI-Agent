/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * Agent tool that inspects the EPUB project currently
 * opened in GomsBookEditor.
 *
 * <p>
 * This tool returns basic project information such as:
 * project name, root directory, TEXT directory,
 * navigation document, OPF document and XHTML file count.
 * </p>
 */
public final class InspectCurrentProjectTool
        implements AgentTool {

    public static final String NAME = "inspect_current_project";

    private final CurrentProjectProvider projectProvider;


    public InspectCurrentProjectTool(
            CurrentProjectProvider projectProvider) {

        if (projectProvider == null) {

            throw new IllegalArgumentException(
                    "projectProvider must not be null."
            );
        }

        this.projectProvider =
                projectProvider;
    }


    @Override
    public String getName() {

        return NAME;
    }


    @Override
    public String getDescription() {

        return "Inspect the EPUB project currently opened "
                + "in GomsBookEditor and return project name, "
                + "project root, TEXT directory, nav.xhtml, "
                + "OPF package document and XHTML document count.";
    }


	@Override
	public ToolResult execute(ToolRequest request, ToolContext context) {

	    try {

	        EpubProjectContext project =
	                projectProvider
	                        .getCurrentProject();


	        Map<String, Object> result =
	                createResult(
	                        project
	                );


	        return ToolResult
	                .success(NAME)
	                .requestId(
	                        request != null
	                                ? request.getRequestId()
	                                : null
	                )
	                .message(
	                        "Current EPUB project inspected successfully."
	                )
	                .data(result)
	                .build();

	    } catch (Exception e) {

	        return ToolResult
	                .failure(
	                        NAME,
	                        "Failed to inspect current EPUB project: "
	                                + e.getMessage(),
	                        e
	                )
	                .requestId(
	                        request != null
	                                ? request.getRequestId()
	                                : null
	                )
	                .build();
	    }
	}


    private Map<String, Object> createResult(
            EpubProjectContext project) {

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "projectName",
                project.getProjectName()
        );


        result.put(
                "projectRoot",
                normalizePath(
                        project.getProjectRoot()
                )
        );


        result.put(
                "textDirectory",
                normalizePath(
                        project.getTextDirectory()
                )
        );


        result.put(
                "textDirectoryExists",
                project.hasTextDirectory()
        );


        result.put(
                "navigationFile",
                normalizePath(
                        project.getNavigationFile()
                )
        );


        result.put(
                "navigationFileExists",
                project.hasNavigationFile()
        );


        result.put(
                "packageDocument",
                normalizePath(
                        project.getPackageDocument()
                )
        );


        result.put(
                "packageDocumentExists",
                project.hasPackageDocument()
        );


        result.put(
                "xhtmlCount",
                countXhtmlFiles(
                        project.getTextDirectory()
                )
        );


        return result;
    }


    private long countXhtmlFiles(
            Path textDirectory) {

        if (textDirectory == null
                || !Files.isDirectory(
                        textDirectory
                )) {

            return 0L;
        }


        try (Stream<Path> stream =
                Files.walk(
                        textDirectory
                )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            this::isXhtml
                    )
                    .count();

        } catch (Exception e) {

            return 0L;
        }
    }


    private boolean isXhtml(
            Path path) {

        if (path == null
                || path.getFileName() == null) {

            return false;
        }


        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return fileName.endsWith(
                ".xhtml"
        );
    }


    private String normalizePath(
            Path path) {

        if (path == null) {

            return "";
        }

        return path
                .toAbsolutePath()
                .normalize()
                .toString();
    }

}