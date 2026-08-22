/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Default implementation of {@link CurrentProjectProvider}.
 *
 * <p>
 * The provider receives the root directory of the currently
 * opened GomsBookEditor project and resolves the EPUB project
 * structure required by Agent tools and RAG services.
 * </p>
 */
public final class DefaultCurrentProjectProvider
        implements CurrentProjectProvider {

    private static final String DEFAULT_OEBPS_DIRECTORY =
            "OEBPS";

    private static final String DEFAULT_TEXT_DIRECTORY =
            "Text";

    private static final String DEFAULT_NAVIGATION_FILE =
            "nav.xhtml";

    private static final String DEFAULT_PACKAGE_FILE =
            "content.opf";


    private final Supplier<Path> projectRootSupplier;


    /**
     * Creates a provider using a supplier that returns
     * the currently opened project root.
     *
     * @param projectRootSupplier
     *        supplier for current project root
     */
    public DefaultCurrentProjectProvider(
            Supplier<Path> projectRootSupplier) {

        this.projectRootSupplier =
                Objects.requireNonNull(
                        projectRootSupplier,
                        "projectRootSupplier"
                );
    }


    @Override
    public EpubProjectContext getCurrentProject() {

        Path projectRoot =
                projectRootSupplier.get();

        if (projectRoot == null) {

            throw new IllegalStateException(
                    "No current EPUB project is available."
            );
        }

        projectRoot =
                projectRoot
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isDirectory(projectRoot)) {

            throw new IllegalStateException(
                    "Current project root does not exist: "
                            + projectRoot
            );
        }


        String projectName =
                resolveProjectName(
                        projectRoot
                );


        Path packageDocument =
                resolvePackageDocument(
                        projectRoot
                );


        Path contentRoot =
                packageDocument
                        .getParent();


        if (contentRoot == null) {

            throw new IllegalStateException(
                    "Unable to resolve EPUB content root: "
                            + packageDocument
            );
        }


        Path textDirectory =
                resolveTextDirectory(
                        contentRoot
                );


        Path navigationFile =
                resolveNavigationFile(
                        contentRoot
                );


        return new EpubProjectContext(
                projectName,
                projectRoot,
                textDirectory,
                navigationFile,
                packageDocument
        );
    }


    /**
     * Resolves the project name from the project root.
     */
    private String resolveProjectName(
            Path projectRoot) {

        Path fileName =
                projectRoot.getFileName();

        if (fileName == null) {

            return projectRoot.toString();
        }

        return fileName.toString();
    }


    /**
     * Resolves the OPF package document.
     *
     * <p>
     * First checks the conventional
     * OEBPS/content.opf location.
     * If it is not available, searches the project
     * recursively for an OPF document.
     * </p>
     */
    private Path resolvePackageDocument(
            Path projectRoot) {

        Path defaultPackage =
                projectRoot
                        .resolve(
                                DEFAULT_OEBPS_DIRECTORY
                        )
                        .resolve(
                                DEFAULT_PACKAGE_FILE
                        );

        if (Files.isRegularFile(defaultPackage)) {

            return defaultPackage;
        }


        try (var stream =
                Files.walk(
                        projectRoot
                )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            this::isOpfFile
                    )
                    .sorted()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "EPUB package document "
                                            + "(*.opf) was not found: "
                                            + projectRoot
                                    )
                    );

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to locate EPUB package document.",
                    e
            );
        }
    }


    /**
     * Resolves the EPUB TEXT directory.
     */
    private Path resolveTextDirectory(
            Path contentRoot) {

        Path defaultText =
                contentRoot.resolve(
                        DEFAULT_TEXT_DIRECTORY
                );

        if (Files.isDirectory(defaultText)) {

            return defaultText;
        }


        /*
         * Some EPUB projects use lowercase directory names.
         */
        Path lowercaseText =
                contentRoot.resolve(
                        "text"
                );

        if (Files.isDirectory(lowercaseText)) {

            return lowercaseText;
        }


        /*
         * Do not fail immediately.
         *
         * Returning the conventional location allows
         * EpubProjectContext.hasTextDirectory() and
         * Agent tools to report the missing directory.
         */
        return defaultText;
    }


    /**
     * Resolves nav.xhtml.
     */
    private Path resolveNavigationFile(
            Path contentRoot) {

        Path defaultNavigation =
                contentRoot.resolve(
                        DEFAULT_NAVIGATION_FILE
                );

        if (Files.isRegularFile(defaultNavigation)) {

            return defaultNavigation;
        }


        try (var stream =
                Files.walk(
                        contentRoot
                )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            this::isNavigationFile
                    )
                    .sorted()
                    .findFirst()
                    .orElse(
                            defaultNavigation
                    );

        } catch (Exception e) {

            return defaultNavigation;
        }
    }


    private boolean isOpfFile(
            Path path) {

        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return fileName.endsWith(
                ".opf"
        );
    }


    private boolean isNavigationFile(
            Path path) {

        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return DEFAULT_NAVIGATION_FILE
                .equals(
                        fileName
                );
    }
}