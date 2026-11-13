package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.typography.FontRegistry;

public final class TypographyApplicator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TypographyApplicator() {
    }

    public static ObjectNode apply(ObjectNode document, JsonNode plan, String directionId) {
        if (document == null) {
            return null;
        }
        JsonNode requested = object(document.get("typography"));
        if (requested == null) {
            requested = plan == null ? null : object(plan.get("typography"));
        }
        String requestedPreset = text(requested, "presetId");
        boolean chosen = FontRegistry.preset(requestedPreset) != null;
        String presetId = chosen ? requestedPreset : FontRegistry.presetForDirection(directionId);
        JsonNode preset = FontRegistry.preset(presetId);
        String heading = chosen && FontRegistry.knownId(text(requested, "headingFontId")) ? text(requested, "headingFontId") : preset == null ? "fraunces" : preset.path("headingFontId").asText("fraunces");
        String body = chosen && FontRegistry.knownId(text(requested, "bodyFontId")) ? text(requested, "bodyFontId") : preset == null ? "manrope" : preset.path("bodyFontId").asText("manrope");
        String accent = chosen ? text(requested, "accentFontId") : preset == null ? "" : preset.path("accentFontId").asText("");
        if (!FontRegistry.knownId(accent)) {
            accent = "";
        }
        ObjectNode typography = FontRegistry.build(MAPPER, presetId, heading, body, accent);
        document.set("typography", typography);
        if (document.path("root") instanceof ObjectNode root) {
            paint(root, typography);
        }
        return document;
    }

    private static void paint(ObjectNode node, ObjectNode typography) {
        String type = node.path("type").asText("");
        String role = switch (type) {
            case "HEADING" -> headingRole(node.path("props").path("level").asInt(2));
            case "PARAGRAPH" -> "body";
            case "BUTTON" -> "button";
            case "SECTION" -> "body";
            default -> "";
        };
        if (!role.isBlank()) {
            JsonNode token = typography.path("roles").path(role);
            ObjectNode styles = node.with("styles");
            styles.put("fontFamily", token.path("fontFamily").asText(""));
            styles.put("fontId", token.path("fontId").asText(""));
            if (!"SECTION".equals(type)) {
                styles.put("fontWeight", token.path("fontWeight").asText("400"));
                styles.put("fontStyle", token.path("fontStyle").asText("normal"));
                styles.put("fontSize", token.path("fontSize").asText(""));
                styles.put("lineHeight", token.path("lineHeight").asText(""));
                styles.put("letterSpacing", token.path("letterSpacing").asText(""));
                styles.put("textTransform", token.path("textTransform").asText("none"));
                styles.put("fontRole", role);
            }
        }
        for (JsonNode child : node.path("children")) {
            if (child instanceof ObjectNode object) {
                paint(object, typography);
            }
        }
    }

    private static String headingRole(int level) {
        if (level <= 1) {
            return "display";
        }
        if (level == 2) {
            return "h2";
        }
        if (level == 3) {
            return "h3";
        }
        return "h4";
    }

    private static JsonNode object(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    private static String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }
}
