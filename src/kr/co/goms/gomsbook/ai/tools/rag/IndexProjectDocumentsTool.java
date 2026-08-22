/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.rag;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.rag.index.ProjectIndexException;
import kr.co.goms.gomsbook.ai.rag.index.ProjectIndexResult;
import kr.co.goms.gomsbook.ai.rag.index.ProjectRagIndexer;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * 현재 GomsBookEditor EPUB 프로젝트의 RAG 인덱스를
 * 명시적으로 동기화하는 Tool입니다.
 *
 * <p>실제 NEW / CHANGED / UNCHANGED / DELETED 판정,
 * Embedding 및 VectorStore 갱신은 {@link ProjectRagIndexer}에
 * 위임합니다.</p>
 */
public final class IndexProjectDocumentsTool
        implements AgentTool {

    public static final String NAME =
            "index_project_documents";

    private final CurrentProjectProvider projectProvider;

    private final ProjectRagIndexer projectRagIndexer;

    public IndexProjectDocumentsTool(
            CurrentProjectProvider projectProvider,
            ProjectRagIndexer projectRagIndexer) {

        this.projectProvider =
                Objects.requireNonNull(
                        projectProvider,
                        "projectProvider must not be null"
                );

        this.projectRagIndexer =
                Objects.requireNonNull(
                        projectRagIndexer,
                        "projectRagIndexer must not be null"
                );
    }

    @Override
    public String getName() {

        return NAME;
    }

    @Override
    public String getDescription() {

        return "Synchronizes the RAG index for XHTML documents "
                + "in the TEXT directory of the current EPUB project. "
                + "Only new, changed or deleted documents update "
                + "the vector store.";
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        try {

            EpubProjectContext project =
                    projectProvider
                            .getCurrentProject();

            ProjectIndexResult result =
                    projectRagIndexer
                            .synchronize(
                                    project
                            );

            return ToolResult
                    .success(NAME)
                    .message(
                            "Project RAG index synchronized successfully."
                    )
                    .data(
                            "projectId",
                            result.getProjectId()
                    )
                    .data(
                            "projectName",
                            result.getProjectName()
                    )
                    .data(
                            "textDirectory",
                            result.getTextDirectory()
                    )
                    .data(
                            "embeddingModel",
                            result.getEmbeddingModel()
                    )
                    .data(
                            "discoveredFiles",
                            result.getDiscoveredFiles()
                    )
                    .data(
                            "processedFiles",
                            result.getProcessedFiles()
                    )
                    .data(
                            "newFiles",
                            result.getNewFiles()
                    )
                    .data(
                            "reindexedFiles",
                            result.getReindexedFiles()
                    )
                    .data(
                            "skippedFiles",
                            result.getSkippedFiles()
                    )
                    .data(
                            "deletedFiles",
                            result.getDeletedFiles()
                    )
                    .data(
                            "createdChunks",
                            result.getCreatedChunks()
                    )
                    .data(
                            "createdEmbeddings",
                            result.getCreatedEmbeddings()
                    )
                    .data(
                            "storedVectors",
                            result.getStoredVectors()
                    )
                    .data(
                            "deletedVectors",
                            result.getDeletedVectors()
                    )
                    .data(
                            "vectorStoreSize",
                            result.getVectorStoreSize()
                    )
                    .data(
                            "indexedFiles",
                            result.getIndexedFiles()
                    )
                    .data(
                            "skippedFilePaths",
                            result.getSkippedFilePaths()
                    )
                    .data(
                            "deletedFilePaths",
                            result.getDeletedFilePaths()
                    )
                    .build();

        } catch (ProjectIndexException exception) {

            return ToolResult
                    .failure(
                            NAME,
                            "Failed to synchronize project RAG index: "
                                    + safeMessage(exception),
                            exception
                    )
                    .build();

        } catch (RuntimeException exception) {

            return ToolResult
                    .failure(
                            NAME,
                            "Unexpected project RAG indexing failure: "
                                    + safeMessage(exception),
                            exception
                    )
                    .build();
        }
    }

    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {

            return "Unknown error";
        }

        return throwable.getMessage();
    }
}