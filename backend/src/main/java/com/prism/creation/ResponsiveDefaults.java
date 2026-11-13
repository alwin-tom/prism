package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ResponsiveDefaults {

    private ResponsiveDefaults() {
    }

    public static ObjectNode apply(ObjectNode document) {
        if (document != null && document.path("root").isObject()) {
            visit((ObjectNode) document.get("root"));
        }
        return document;
    }

    private static void visit(ObjectNode node) {
        adjust(node);
        for (JsonNode child : node.path("children")) {
            if (child instanceof ObjectNode object) {
                visit(object);
            }
        }
    }

    private static void adjust(ObjectNode node) {
        String type = node.path("type").asText("");
        ObjectNode styles = object(node, "styles");
        Integer font = pixels(styles.path("fontSize").asText(""));
        if ("HEADING".equals(type) && font != null && font >= 32) {
            int tablet = Math.max(24, (int) Math.round(font * (font >= 48 ? 0.78 : 0.86)));
            int mobile = Math.max(22, (int) Math.round(font * (font >= 48 ? 0.56 : 0.72)));
            style(node, "tablet", "fontSize", tablet + "px");
            style(node, "mobile", "fontSize", mobile + "px");
        }
        Integer height = pixels(styles.path("height").asText(""));
        if (height != null && height >= 400) {
            style(node, "tablet", "height", Math.round(height * 0.82) + "px");
            style(node, "mobile", "height", Math.round(height * 0.68) + "px");
        }
        if ("SECTION".equals(type) || "CONTAINER".equals(type)) {
            Integer pad = widest(styles.path("padding").asText(""));
            if (pad != null && pad >= 32) {
                style(node, "mobile", "padding", "48px 16px");
            }
            if (pad != null && pad >= 48) {
                style(node, "tablet", "padding", "56px 28px");
            }
        }
        String direction = styles.path("flexDirection").asText("");
        if (("FLEX".equals(type) || "SECTION".equals(type)) && (direction.isBlank() || direction.startsWith("row"))) {
            style(node, "mobile", "flexDirection", "column");
        }
        int columns = columns(styles.path("gridTemplateColumns").asText(""));
        if ("GRID".equals(type) && columns >= 2) {
            style(node, "mobile", "gridTemplateColumns", "1fr");
            if (columns >= 3) {
                style(node, "tablet", "gridTemplateColumns", "1fr 1fr");
            }
        }
        Integer gap = pixels(styles.path("gap").asText(""));
        if (gap != null && gap >= 24) {
            style(node, "mobile", "gap", Math.max(8, Math.round(gap * 0.5)) + "px");
        }
        if ("BUTTON".equals(type)) {
            style(node, "mobile", "width", "100%");
        }
        if ("CAROUSEL".equals(type)) {
            int perView = node.path("props").path("perView").asInt(1);
            if (perView >= 3) {
                prop(node, "tablet", "perView", 2);
            }
            if (perView >= 2) {
                prop(node, "mobile", "perView", 1);
            }
            if (gap != null && gap >= 16) {
                style(node, "tablet", "gap", Math.max(12, gap - 8) + "px");
                style(node, "mobile", "gap", "8px");
            }
        }
    }

    private static void style(ObjectNode node, String device, String key, String value) {
        ObjectNode styles = object(object(node, "responsiveStyles"), device);
        if (styles.path(key).asText("").isBlank()) {
            styles.put(key, value);
        }
    }

    private static void prop(ObjectNode node, String device, String key, int value) {
        ObjectNode props = object(object(node, "responsiveProps"), device);
        if (!props.has(key)) {
            props.put(key, value);
        }
    }

    private static ObjectNode object(ObjectNode parent, String name) {
        JsonNode existing = parent.get(name);
        if (existing instanceof ObjectNode object) {
            return object;
        }
        return parent.putObject(name);
    }

    private static Integer pixels(String value) {
        String trimmed = value == null ? "" : value.trim().toLowerCase();
        if (!trimmed.endsWith("px")) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 2).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer widest(String padding) {
        if (padding == null || padding.isBlank()) {
            return null;
        }
        int widest = 0;
        boolean found = false;
        for (String part : padding.trim().split("\\s+")) {
            Integer size = pixels(part);
            if (size != null) {
                found = true;
                widest = Math.max(widest, size);
            }
        }
        return found ? widest : null;
    }

    private static int columns(String template) {
        if (template == null || template.isBlank()) {
            return 0;
        }
        String[] parts = template.trim().split("\\s+");
        int count = 0;
        for (String part : parts) {
            if (!part.isBlank() && !part.equals("/")) {
                count++;
            }
        }
        return count;
    }
}
