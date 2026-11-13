package com.prism.typography;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TypographyPatch {

    private static final Set<String> GENERIC_SIZES = Set.of("", "64px", "48px", "40px", "36px", "32px", "22px", "18px", "16px");
    private static final Set<String> GENERIC_WEIGHTS = Set.of("", "400", "500", "700");

    private TypographyPatch() {
    }

    public static ObjectNode apply(ObjectMapper mapper, ObjectNode document, JsonNode patch, List<String> warnings) {
        if (patch == null || !patch.isObject()) {
            warnings.add("The typography change was empty.");
            return document;
        }
        String presetId = patch.path("presetId").asText("");
        if (FontRegistry.preset(presetId) != null && document.path("root") instanceof ObjectNode root) {
            applyPreset(mapper, document, root, presetId);
        } else if (!presetId.isBlank() && !"custom".equals(presetId)) {
            warnings.add("Ignored an unknown typography preset.");
        }
        if (patch.path("nodes").isArray() && document.path("root") instanceof ObjectNode root) {
            for (JsonNode change : patch.path("nodes")) {
                applyNode(root, change, warnings);
            }
        }
        document.set("typography", FontRegistry.ensure(mapper, document.get("typography"), document.get("root")));
        return document;
    }

    public static ObjectNode schema(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        node.putArray("required").add("summary").add("presetId").add("nodes");
        ObjectNode properties = node.putObject("properties");
        properties.putObject("summary").put("type", "string");
        ArrayNode presets = properties.putObject("presetId").put("type", "string").putArray("enum");
        presets.add("");
        presets.add("custom");
        for (JsonNode preset : FontRegistry.root().path("presets")) {
            presets.add(preset.path("id").asText());
        }
        ObjectNode nodes = properties.putObject("nodes");
        nodes.put("type", "array");
        ObjectNode item = nodes.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", false);
        item.putArray("required").add("id").add("fontId").add("fontWeight").add("fontStyle").add("letterSpacing").add("lineHeight").add("fontSize").add("tabletFontSize").add("mobileFontSize");
        ObjectNode fields = item.putObject("properties");
        fields.putObject("id").put("type", "string");
        ArrayNode fontIds = fields.putObject("fontId").put("type", "string").putArray("enum");
        fontIds.add("");
        for (JsonNode font : FontRegistry.root().path("fonts")) {
            if (font.path("active").asBoolean(true)) {
                fontIds.add(font.path("id").asText());
            }
        }
        for (String field : List.of("fontWeight", "fontStyle", "letterSpacing", "lineHeight", "fontSize", "tabletFontSize", "mobileFontSize")) {
            fields.putObject(field).put("type", "string");
        }
        return node;
    }

    private static void applyPreset(ObjectMapper mapper, ObjectNode document, ObjectNode root, String presetId) {
        ObjectNode previous = FontRegistry.ensure(mapper, document.get("typography"), root);
        Set<String> families = new LinkedHashSet<>();
        for (JsonNode role : previous.path("roles")) {
            String family = role.path("fontFamily").asText("");
            if (!family.isBlank()) {
                families.add(family);
            }
        }
        JsonNode preset = FontRegistry.preset(presetId);
        ObjectNode next = FontRegistry.build(mapper, presetId, preset.path("headingFontId").asText(), preset.path("bodyFontId").asText(), preset.path("accentFontId").asText());
        document.set("typography", next);
        retarget(root, families, previous, next);
    }

    private static void retarget(JsonNode node, Set<String> families, ObjectNode previous, ObjectNode next) {
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        String type = object.path("type").asText("");
        if ("HEADING".equals(type) || "PARAGRAPH".equals(type) || "BUTTON".equals(type)) {
            String role = roleFor(object);
            JsonNode before = previous.path("roles").path(role);
            JsonNode after = next.path("roles").path(role);
            String family = object.path("styles").path("fontFamily").asText("");
            String fontId = object.path("styles").path("fontId").asText("");
            boolean follows = family.isBlank() || families.contains(family) || fontId.equals(before.path("fontId").asText("x"));
            if (follows && after.isObject()) {
                ObjectNode styles = object.with("styles");
                styles.put("fontId", after.path("fontId").asText(""));
                styles.put("fontFamily", after.path("fontFamily").asText(""));
                styles.put("fontRole", role);
                if (followsValue(styles.path("fontWeight").asText(""), before.path("fontWeight").asText(""), GENERIC_WEIGHTS)) {
                    styles.put("fontWeight", after.path("fontWeight").asText(""));
                }
                if (followsValue(styles.path("fontSize").asText(""), before.path("fontSize").asText(""), GENERIC_SIZES)) {
                    styles.put("fontSize", after.path("fontSize").asText(""));
                }
                if (followsValue(styles.path("lineHeight").asText(""), before.path("lineHeight").asText(""), Set.of("", "1.08", "1.65", "1.7"))) {
                    styles.put("lineHeight", after.path("lineHeight").asText(""));
                }
                if (followsValue(styles.path("letterSpacing").asText(""), before.path("letterSpacing").asText(""), Set.of("", "0", "0em", "-0.03em"))) {
                    styles.put("letterSpacing", after.path("letterSpacing").asText(""));
                }
            }
        }
        for (JsonNode child : object.path("children")) {
            retarget(child, families, previous, next);
        }
    }

    private static void applyNode(ObjectNode root, JsonNode change, List<String> warnings) {
        if (change == null || !change.isObject()) {
            return;
        }
        String id = change.path("id").asText("");
        ObjectNode node = find(root, id);
        if (node == null) {
            if (!id.isBlank()) {
                warnings.add("Skipped a typography change for a missing element.");
            }
            return;
        }
        ObjectNode styles = node.with("styles");
        String fontId = change.path("fontId").asText("");
        if (!fontId.isBlank()) {
            boolean bodyScript = "PARAGRAPH".equals(node.path("type").asText("")) && FontRegistry.handwriting(fontId);
            if (FontRegistry.knownId(fontId) && !unsafe(fontId) && !bodyScript) {
                JsonNode font = FontRegistry.font(fontId);
                styles.put("fontId", fontId);
                styles.put("fontFamily", font.path("family").asText(""));
            } else {
                warnings.add(bodyScript ? "Kept the body font readable." : "Ignored an unsupported font.");
            }
        }
        JsonNode font = FontRegistry.font(styles.path("fontId").asText(""));
        if (font == null) {
            font = FontRegistry.fontByFamily(styles.path("fontFamily").asText(""));
        }
        putStyle(styles, "fontWeight", FontRegistry.weight(font, change.path("fontWeight").asText("")), change.path("fontWeight").asText(""));
        putStyle(styles, "fontStyle", FontRegistry.style(font, change.path("fontStyle").asText("")), change.path("fontStyle").asText(""));
        putSafe(styles, "letterSpacing", change.path("letterSpacing").asText(""), 20);
        putSafe(styles, "lineHeight", change.path("lineHeight").asText(""), 20);
        putSafe(styles, "fontSize", change.path("fontSize").asText(""), 120);
        putResponsive(node, "tablet", change.path("tabletFontSize").asText(""), warnings);
        putResponsive(node, "mobile", change.path("mobileFontSize").asText(""), warnings);
        FontRegistry.reconcile(styles);
    }

    private static void putStyle(ObjectNode styles, String key, String repaired, String raw) {
        if (raw == null || raw.isBlank() || unsafe(raw)) {
            return;
        }
        styles.put(key, repaired);
    }

    private static void putSafe(ObjectNode styles, String key, String value, int limit) {
        if (value == null || value.isBlank() || unsafe(value) || value.length() > limit) {
            return;
        }
        styles.put(key, value);
    }

    private static void putResponsive(ObjectNode node, String device, String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (unsafe(value) || value.length() > 120) {
            warnings.add("Ignored an unsafe font size.");
            return;
        }
        ObjectNode responsive = node.with("responsiveStyles");
        responsive.with(device).put("fontSize", value);
    }

    private static boolean unsafe(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("url(") || lower.contains("http") || lower.contains("expression(") || lower.contains("javascript") || value.indexOf('<') >= 0 || value.indexOf(';') >= 0;
    }

    private static boolean followsValue(String current, String previous, Set<String> generic) {
        return current.isBlank() || current.equals(previous) || generic.contains(current);
    }

    private static String roleFor(ObjectNode node) {
        String explicit = node.path("styles").path("fontRole").asText("");
        if (FontRegistry.root().path("roles").isArray()) {
            for (JsonNode role : FontRegistry.root().path("roles")) {
                if (explicit.equals(role.asText())) {
                    return explicit;
                }
            }
        }
        return switch (node.path("type").asText("")) {
            case "HEADING" -> {
                int level = node.path("props").path("level").asInt(2);
                yield level <= 1 ? "h1" : level == 2 ? "h2" : level == 3 ? "h3" : "h4";
            }
            case "BUTTON" -> "button";
            default -> "body";
        };
    }

    private static ObjectNode find(ObjectNode node, String id) {
        if (id.equals(node.path("id").asText(""))) {
            return node;
        }
        for (JsonNode child : node.path("children")) {
            if (child instanceof ObjectNode object) {
                ObjectNode found = find(object, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static String summary(JsonNode patch) {
        String text = patch == null ? "" : patch.path("summary").asText("").replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            return "Updated the typography.";
        }
        return text.length() > 240 ? text.substring(0, 240) : text;
    }

}
