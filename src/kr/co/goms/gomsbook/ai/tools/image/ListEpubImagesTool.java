/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tools.image;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import kr.co.goms.gomsbook.ai.epub.model.EpubImage;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;

/**
 * 현재 EPUB 프로젝트의 XHTML을 분석하여 이미지 목록을 조회합니다.
 */
public final class ListEpubImagesTool implements AgentTool {

    public static final String NAME = "list_epub_images";
    public static final String TOOL_NAME = NAME;

    private static final String DESCRIPTION = "Lists images used by XHTML documents in the current EPUB project, including src, image id, alt, role and aria-hidden. Do not translate, summarize or map role values such as doc-cover to labels like 표지.";

    private final CurrentProjectProvider projectProvider;

    public ListEpubImagesTool(CurrentProjectProvider projectProvider) {
        this.projectProvider = Objects.requireNonNull(projectProvider, "projectProvider must not be null");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", false);

        return schema;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            EpubProjectContext project = projectProvider.getCurrentProject();

            if (project == null) {
                return ToolResult.failure(NAME, "No current EPUB project is open.").build();
            }

            Path textDirectory = project.getTextDirectory();

            validateTextDirectory(textDirectory);

            List<EpubImage> images = collectImageFromDirectory(textDirectory.toFile());
            List<Map<String, Object>> imageResults = convertImages(project, images);

            int missingAltCount = 0;
            int ariaHiddenCount = 0;

            for (EpubImage image : images) {

                if (image == null) {
                    continue;
                }

                if (image.getAlt() == null || image.getAlt().isBlank()) {
                    missingAltCount++;
                }

                if ("true".equalsIgnoreCase(image.getAriaHidden())) {
                    ariaHiddenCount++;
                }
            }

            return ToolResult.success(NAME)
                    .message("EPUB image listing completed. " + images.size() + " image reference(s) found. Display src, xhtml, imgId, alt and raw role values exactly as stored in XHTML")
                    .data("projectName", project.getProjectName())
                    .data("textDirectory", textDirectory.toAbsolutePath().normalize().toString())
                    .data("imageCount", images.size())
                    .data("missingAltCount", missingAltCount)
                    .data("ariaHiddenCount", ariaHiddenCount)
                    .data("images", imageResults)
                    .build();

        } catch (Exception exception) {

            return ToolResult.failure(NAME, "Failed to list images from current EPUB project: " + safeMessage(exception), exception).build();
        }
    }

    private List<EpubImage> collectImageFromDirectory(File textDir) throws Exception {

        List<EpubImage> images = new ArrayList<>();

        if (textDir == null || !textDir.isDirectory()) {
            return images;
        }

        File[] files = textDir.listFiles((dir, name) -> {

            if (name == null) {
                return false;
            }

            String lowerName = name.toLowerCase(Locale.ROOT);

            return lowerName.endsWith(".xhtml") || lowerName.endsWith(".html");
        });

        if (files == null) {
            return images;
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : files) {
            images.addAll(collectImageFromFile(file));
        }

        return images;
    }

    private List<EpubImage> collectImageFromFile(File file) throws Exception {

        List<EpubImage> images = new ArrayList<>();

        Document document = Jsoup.parse(file, StandardCharsets.UTF_8.name());
        Elements imgElements = document.select("img");

        for (int index = 0; index < imgElements.size(); index++) {

            Element img = imgElements.get(index);

            String src = img.attr("src");
            String imgId = createImageId(src);
            String alt = img.hasAttr("alt") ? img.attr("alt") : "";
            String role = img.hasAttr("role") ? img.attr("role") : "";
            String ariaHidden = img.hasAttr("aria-hidden") ? img.attr("aria-hidden") : "";

            EpubImage epubImage = new EpubImage(file, index, src, imgId, alt, role, ariaHidden);

            images.add(epubImage);
        }

        return images;
    }

    private String createImageId(String src) {

        if (src == null || src.isBlank()) {
            return "";
        }

        String imageId = src.trim();

        int queryIndex = imageId.indexOf('?');

        if (queryIndex >= 0) {
            imageId = imageId.substring(0, queryIndex);
        }

        int fragmentIndex = imageId.indexOf('#');

        if (fragmentIndex >= 0) {
            imageId = imageId.substring(0, fragmentIndex);
        }

        int lastSlash = Math.max(imageId.lastIndexOf('/'), imageId.lastIndexOf('\\'));

        if (lastSlash >= 0 && lastSlash < imageId.length() - 1) {
            imageId = imageId.substring(lastSlash + 1);
        }

        int dotIndex = imageId.lastIndexOf('.');

        if (dotIndex > 0) {
            imageId = imageId.substring(0, dotIndex);
        }

        return imageId;
    }

    private List<Map<String, Object>> convertImages(EpubProjectContext project, List<EpubImage> images) {

        List<Map<String, Object>> results = new ArrayList<>();
        Path projectRoot = project.getProjectRoot().toAbsolutePath().normalize();

        for (EpubImage image : images) {

            if (image == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();

            File file = image.getFile();
            String xhtmlFile = file == null ? "" : file.getName();
            String xhtmlPath = "";

            if (file != null) {

                Path filePath = file.toPath().toAbsolutePath().normalize();

                if (filePath.startsWith(projectRoot)) {
                    xhtmlPath = projectRoot.relativize(filePath).toString().replace('\\', '/');
                } else {
                    xhtmlPath = filePath.toString().replace('\\', '/');
                }
            }

            item.put("xhtmlFile", xhtmlFile);
            item.put("xhtmlPath", xhtmlPath);
            item.put("index", image.getIndex());
            item.put("src", safeString(image.getSrc()));
            item.put("imgId", safeString(image.getImgId()));
            item.put("alt", safeString(image.getAlt()));
            item.put("role", safeString(image.getRole()));
            item.put("ariaHidden", safeString(image.getAriaHidden()));
            item.put("altMissing", image.getAlt() == null || image.getAlt().isBlank());

            results.add(item);
        }

        return results;
    }

    private void validateTextDirectory(Path textDirectory) {

        if (textDirectory == null) {
            throw new IllegalStateException("EPUB TEXT directory is null.");
        }

        Path normalized = textDirectory.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {
            throw new IllegalStateException("EPUB TEXT directory does not exist: " + normalized);
        }

        if (!Files.isDirectory(normalized)) {
            throw new IllegalStateException("EPUB TEXT path is not a directory: " + normalized);
        }

        if (!Files.isReadable(normalized)) {
            throw new IllegalStateException("EPUB TEXT directory is not readable: " + normalized);
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) {
            return "Unknown error";
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message;
    }
}