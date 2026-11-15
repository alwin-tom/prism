package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DesignQuality {

    private DesignQuality() {
    }

    public static Result assess(JsonNode document, DesignDirection direction) {
        List<String> issues = new ArrayList<>();
        List<JsonNode> sections = new ArrayList<>();
        collect(document == null ? null : document.path("root"), sections);
        Set<String> backgrounds = new LinkedHashSet<>();
        boolean gradientHero = false;
        boolean display = false;
        boolean body = false;
        boolean shadow = false;
        int readable = 0;
        int textNodes = 0;
        for (int i = 0; i < sections.size(); i++) {
            JsonNode section = sections.get(i);
            String background = section.path("styles").path("background").asText("");
            if (!background.isBlank()) {
                backgrounds.add(background);
            }
            if (i == 0 && (background.contains("gradient") || (direction != null && background.equals(direction.heroBackground())))) {
                gradientHero = true;
            }
            readable += readableText(section, background, textCount(new int[]{0}));
            textNodes += textCount(new int[]{0}, section);
        }
        JsonNode page = document != null && document.has("root") ? document.path("root") : document;
        String displayFont = direction == null ? "" : direction.displayFont();
        String bodyFont = direction == null ? "" : direction.bodyFont();
        display = usesFont(page, displayFont) || usesFont(page, com.prism.typography.FontRegistry.roleFamily(document, "display")) || usesFont(page, com.prism.typography.FontRegistry.roleFamily(document, "h1")) || usesRegistryFont(page, "HEADING");
        body = usesFont(page, bodyFont) || usesFont(page, com.prism.typography.FontRegistry.roleFamily(document, "body")) || usesRegistryFont(page, "PARAGRAPH");
        shadow = usesValue(document, "boxShadow");
        if (sections.isEmpty()) {
            issues.add("The page has no section.");
        }
        if (!gradientHero && direction != null && direction.heroBackground().contains("gradient")) {
            issues.add("The hero has no strong background.");
        }
        if (sections.size() > 2 && backgrounds.size() < 2) {
            issues.add("Consecutive sections share one background.");
        }
        if (direction != null && !display) {
            issues.add("Headings are missing the display font.");
        }
        if (direction != null && !body) {
            issues.add("Paragraphs are missing the body font.");
        }
        int colour = issues.isEmpty() ? 90 : 70;
        int type = display && body ? 88 : 64;
        int layout = sections.size() >= 2 && backgrounds.size() >= 2 ? 86 : sections.isEmpty() ? 40 : 74;
        int consistency = direction == null ? 60 : 90;
        int image = 80;
        int motion = 70;
        int responsive = 84;
        int access = textNodes == 0 || readable * 2 >= textNodes ? 88 : 62;
        if (access < 80) {
            issues.add("Some text may be hard to read against its background.");
        }
        if (hasTextEllipse(page)) {
            issues.add("Replace circular text panels with rounded rectangular cards so the copy fits.");
            layout = Math.min(layout, 62);
        }
        int overall = (colour + type + layout + consistency + image + motion + responsive + access) / 8;
        if (!shadow && direction != null && !"none".equals(direction.shadow())) {
            overall = Math.max(0, overall - 4);
        }
        return new Result(overall, colour, type, layout, consistency, image, motion, responsive, access, List.copyOf(issues));
    }

    public record Result(int overall, int colorHarmony, int typography, int layoutComposition, int visualConsistency, int imageUsage, int motionDesign, int responsiveDesign, int accessibility, List<String> issues) {
        public ObjectNode json(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("overallScore", overall);
            ObjectNode dimensions = node.putObject("dimensions");
            dimensions.put("colorHarmony", colorHarmony);
            dimensions.put("typography", typography);
            dimensions.put("layoutComposition", layoutComposition);
            dimensions.put("visualConsistency", visualConsistency);
            dimensions.put("imageUsage", imageUsage);
            dimensions.put("motionDesign", motionDesign);
            dimensions.put("responsiveDesign", responsiveDesign);
            dimensions.put("accessibility", accessibility);
            ArrayNode found = node.putArray("issues");
            issues.forEach(found::add);
            node.putArray("recommendations");
            return node;
        }
    }

    private static void collect(JsonNode node, List<JsonNode> sections) {
        if (node == null || !node.isObject()) {
            return;
        }
        if ("SECTION".equals(node.path("type").asText(""))) {
            sections.add(node);
        }
        for (JsonNode child : node.path("children")) {
            collect(child, sections);
        }
    }

    private static boolean usesRegistryFont(JsonNode node, String type) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        if (type.equals(node.path("type").asText("")) && com.prism.typography.FontRegistry.knownFamily(node.path("styles").path("fontFamily").asText(""))) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (usesRegistryFont(child, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesFont(JsonNode node, String font) {
        if (font.isBlank()) {
            return false;
        }
        if (font.equals(node.path("styles").path("fontFamily").asText(""))) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (usesFont(child, font)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesValue(JsonNode node, String key) {
        String value = node.path("styles").path(key).asText("");
        if (!value.isBlank() && !"none".equals(value)) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (usesValue(child, key)) {
                return true;
            }
        }
        return false;
    }

    private static int textCount(int[] ignored, JsonNode node) {
        int count = "HEADING".equals(node.path("type").asText("")) || "PARAGRAPH".equals(node.path("type").asText("")) ? 1 : 0;
        for (JsonNode child : node.path("children")) {
            count += textCount(ignored, child);
        }
        return count;
    }

    private static int textCount(int[] ignored) {
        return 0;
    }

    private static int readableText(JsonNode node, String background, int ignored) {
        int count = 0;
        String type = node.path("type").asText("");
        if ("HEADING".equals(type) || "PARAGRAPH".equals(type)) {
            String colour = node.path("styles").path("color").asText("");
            if (!colour.isBlank() && !background.isBlank() && !background.contains("gradient") && DesignApplicator.luminance(colour) != DesignApplicator.luminance(background)) {
                double contrast = contrast(colour, background);
                if (contrast >= 3) {
                    count = 1;
                }
            } else if (background.contains("gradient") || background.isBlank()) {
                count = 1;
            }
        }
        for (JsonNode child : node.path("children")) {
            count += readableText(child, background, ignored);
        }
        return count;
    }

    private static double contrast(String first, String second) {
        double left = DesignApplicator.luminance(first);
        double right = DesignApplicator.luminance(second);
        double lighter = Math.max(left, right);
        double darker = Math.min(left, right);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static boolean hasTextEllipse(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        String type = node.path("type").asText("");
        String radius = node.path("styles").path("borderRadius").asText("");
        if (!"IMAGE".equals(type) && !"BUTTON".equals(type) && DesignDirection.isEllipseRadius(radius) && holdsText(node)) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (hasTextEllipse(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsText(JsonNode node) {
        String type = node.path("type").asText("");
        if ("HEADING".equals(type) || "PARAGRAPH".equals(type) || "QUOTE".equals(type)) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (holdsText(child)) {
                return true;
            }
        }
        return false;
    }
}
