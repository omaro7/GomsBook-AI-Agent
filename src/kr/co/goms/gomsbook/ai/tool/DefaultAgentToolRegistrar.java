/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool;

import java.util.Objects;

import kr.co.goms.gomsbook.ai.tools.epub.InspectEpubTool;
import kr.co.goms.gomsbook.ai.tools.project.InspectCurrentProjectTool;
import kr.co.goms.gomsbook.ai.tools.rag.IndexProjectDocumentsTool;
import kr.co.goms.gomsbook.ai.tools.rag.SearchProjectDocumentsTool;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.rag.RagService;
import kr.co.goms.gomsbook.ai.rag.index.ProjectRagIndexer;

/**
 * Default implementation of {@link AgentToolRegistrar}.
 *
 * <p>
 * Registers the Agent Tools exposed to the default
 * GomsBook AI Agent.
 * </p>
 *
 * <p>
 * This registrar owns only Tool registration policy.
 * Tool execution and Tool definition conversion are handled
 * by {@link ToolExecutor} and {@link ToolDefinitionProvider}.
 * </p>
 */
public final class DefaultAgentToolRegistrar implements AgentToolRegistrar {

	private final CurrentProjectProvider currentProjectProvider;
	
	private final RagService ragService;
	
	private final ProjectRagIndexer projectRagIndexer;
	
	
	public DefaultAgentToolRegistrar(
	        CurrentProjectProvider currentProjectProvider,
	        RagService ragService,
	        ProjectRagIndexer projectRagIndexer) {

	    this.currentProjectProvider =
	            Objects.requireNonNull(
	                    currentProjectProvider,
	                    "currentProjectProvider must not be null"
	            );

	    this.ragService =
	            Objects.requireNonNull(
	                    ragService,
	                    "ragService must not be null"
	            );

	    this.projectRagIndexer =
	            Objects.requireNonNull(
	                    projectRagIndexer,
	                    "projectRagIndexer must not be null"
	            );
	}

    /**
     * Registers the default Agent Tools.
     *
     * @param registry
     *        target Tool registry
     */
    @Override
    public void registerTools(
            ToolRegistry registry) {

        Objects.requireNonNull(
                registry,
                "registry must not be null"
        );


        /*
         * Tool Calling pipeline test.
         */
        registerIfAbsent(
                registry,
                new EchoTool()
        );

        /*
         * Current EPUB project inspection.
         */
        registerIfAbsent(
                registry,
                new InspectCurrentProjectTool(
                        currentProjectProvider
                )
        );
        

        /*
         * EPUB inspection.
         */
        registerIfAbsent(
                registry,
                new InspectEpubTool()
        );
        

        /*
         * Current EPUB project RAG indexing.
         */
        registerIfAbsent(
                registry,
                new IndexProjectDocumentsTool(
                        currentProjectProvider,
                        projectRagIndexer
                )
        );
        
        registerIfAbsent(
                registry,
                new SearchProjectDocumentsTool(
                        ragService,
                        currentProjectProvider,
                        projectRagIndexer
                )
        );
        
    }

    /**
     * Registers a Tool only when the same Tool name
     * has not already been registered.
     *
     * @param registry
     *        target registry
     *
     * @param tool
     *        Tool to register
     */
    private void registerIfAbsent(
            ToolRegistry registry,
            AgentTool tool) {

        Objects.requireNonNull(
                tool,
                "tool must not be null"
        );


        if (registry.contains(
                tool.getName()
        )) {

            return;
        }


        registry.register(
                tool
        );
    }
}