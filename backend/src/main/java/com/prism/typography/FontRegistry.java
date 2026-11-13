package com.prism.typography;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Curated font registry shared with the editor. Stylesheet requests are built only from these entries.
 * A future provider value of "self" can serve licensed WOFF2 files from WebAux. Entries do not accept remote stylesheet URLs.
 */
public final class FontRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode ROOT = load();
    private static final Map<String, JsonNode> BY_ID = index("id");
    private static final Map<String, JsonNode> BY_FAMILY = index("family");
    private static final Pattern VARIATION = Pattern.compile("\"([a-z]{4})\"\\s+(-?\\d{1,4}(?:\\.\\d+)?)");
    private static final Set<String> HEADING_ROLES = Set.of("display", "hero", "h1", "h2", "h3", "h4");
    private static final Set<String> BODY_ROLES = Set.of("bodyLarge", "body", "bodySmall", "caption", "label", "button", "navigation");
    public static final int MAX_FAMILIES = ROOT.path("maxFamilies").asInt(6);

    private FontRegistry() {
    }

    public static JsonNode root() {
        return ROOT;
    }

    public static JsonNode font(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return BY_ID.get(id.trim());
    }

    public static JsonNode fontByFamily(String family) {
        if (family == null || family.isBlank()) {
            return null;
        }
        return BY_FAMILY.get(family.trim());
    }

    public static boolean knownFamily(String family) {
        return family != null && ("inherit".equals(family) || BY_FAMILY.containsKey(family));
    }

    public static boolean knownId(String id) {
        JsonNode font = font(id);
        return font != null && font.path("active").asBoolean(true);
    }

    public static String familyName(String id) {
        JsonNode font = font(id);
        return font == null ? "" : font.path("family").asText("");
    }

    public static List<String> families() {
        List<String> names = new ArrayList<>();
        for (JsonNode font : ROOT.path("fonts")) {
            if (font.path("active").asBoolean(true)) {
                names.add(font.path("family").asText());
            }
        }
        return names;
    }

    public static String catalog() {
        StringBuilder text = new StringBuilder();
        for (JsonNode font : ROOT.path("fonts")) {
            if (!font.path("active").asBoolean(true)) {
                continue;
            }
            text.append(font.path("id").asText()).append(" = ").append(font.path("family").asText())
                    .append(" (").append(font.path("category").asText());
            if (font.path("styles").toString().contains("italic")) {
                text.append(", italic");
            }
            if (font.path("variable").asBoolean(false)) {
                text.append(", variable");
            }
            text.append("). ");
        }
        return text.toString().trim();
    }

    public static String presetCatalog() {
        StringBuilder text = new StringBuilder();
        for (JsonNode preset : ROOT.path("presets")) {
            text.append(preset.path("id").asText()).append(" (").append(preset.path("name").asText())
                    .append(": ").append(preset.path("headingFontId").asText())
                    .append(" / ").append(preset.path("bodyFontId").asText());
            String accent = preset.path("accentFontId").asText("");
            if (!accent.isBlank()) {
                text.append(" / ").append(accent);
            }
            text.append("). ");
        }
        return text.toString().trim();
    }

    public static JsonNode preset(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (JsonNode preset : ROOT.path("presets")) {
            if (id.equals(preset.path("id").asText())) {
                return preset;
            }
        }
        return null;
    }

    public static String presetForDirection(String directionId) {
        return switch (directionId == null ? "" : directionId) {
            case "luxury-romantic" -> "luxury-editorial";
            case "modern-editorial" -> "creative-portfolio";
            case "cinematic-dark", "art-deco" -> "cinematic-display";
            case "elegant-minimal", "scandinavian-minimal" -> "minimal-contemporary";
            case "playful-celebration" -> "playful-celebration";
            case "pastel-dream" -> "romantic-wedding";
            case "modern-corporate" -> "modern-corporate";
            case "premium-business" -> "premium-business";
            case "creative-portfolio" -> "creative-portfolio";
            case "botanical-natural", "vintage-classic" -> "vintage-classic";
            case "bold-contemporary", "futuristic-gradient" -> "bold-modern";
            default -> "modern-corporate";
        };
    }

    public static String normalizeFamily(String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if ("inherit".equals(trimmed)) {
            return "inherit";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("http") || trimmed.indexOf('<') >= 0 || trimmed.indexOf(';') >= 0 || trimmed.indexOf('\\') >= 0) {
            warn(warnings, "Removed an unsafe font reference.");
            return "IBM Plex Sans";
        }
        JsonNode font = BY_FAMILY.get(trimmed);
        if (font != null) {
            return font.path("family").asText();
        }
        warn(warnings, "Replaced an unknown font.");
        if (lower.contains("serif") || lower.contains("display") || lower.contains("script")) {
            return "Fraunces";
        }
        return "IBM Plex Sans";
    }

    public static String weight(JsonNode font, String raw) {
        int wanted = parseWeight(raw);
        if (font == null) {
            return String.valueOf(Math.min(900, Math.max(100, wanted)));
        }
        JsonNode axis = axis(font, "wght");
        if (font.path("variable").asBoolean(false) && axis != null) {
            int min = axis.path("min").asInt(100);
            int max = axis.path("max").asInt(900);
            return String.valueOf(Math.min(max, Math.max(min, wanted)));
        }
        int best = font.path("weights").path(0).asInt(400);
        int distance = Integer.MAX_VALUE;
        for (JsonNode candidate : font.path("weights")) {
            int weight = candidate.asInt();
            int gap = Math.abs(weight - wanted);
            if (gap < distance) {
                distance = gap;
                best = weight;
            }
        }
        return String.valueOf(best);
    }

    public static String style(JsonNode font, String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!"italic".equals(value) && !"oblique".equals(value)) {
            return "normal";
        }
        if (font == null) {
            return "normal";
        }
        for (JsonNode style : font.path("styles")) {
            if (value.equals(style.asText())) {
                return value;
            }
        }
        return "normal";
    }

    public static String variations(JsonNode font, String raw) {
        if (font == null || !font.path("variable").asBoolean(false) || raw == null || raw.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        Matcher matcher = VARIATION.matcher(raw);
        while (matcher.find()) {
            JsonNode axis = axis(font, matcher.group(1));
            if (axis == null) {
                continue;
            }
            double value = Double.parseDouble(matcher.group(2));
            double min = axis.path("min").asDouble(0);
            double max = axis.path("max").asDouble(1000);
            value = Math.min(max, Math.max(min, value));
            String rendered = value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
            kept.add("\"" + matcher.group(1) + "\" " + rendered);
        }
        return String.join(", ", kept);
    }

    public static boolean supportsItalic(JsonNode font) {
        for (JsonNode style : font == null ? List.<JsonNode>of() : font.path("styles")) {
            if ("italic".equals(style.asText())) {
                return true;
            }
        }
        return false;
    }

    public static boolean handwriting(String id) {
        JsonNode font = font(id);
        return font != null && "handwriting".equals(font.path("category").asText());
    }

    public static ObjectNode ensure(ObjectMapper mapper, JsonNode incoming, JsonNode root) {
        String headingFamily = findFamily(root, "HEADING", "Fraunces");
        String bodyFamily = findFamily(root, "PARAGRAPH", headingFamily);
        String headingId = idForFamily(headingFamily, "fraunces");
        String bodyId = idForFamily(bodyFamily, headingId);
        if (incoming == null || !incoming.isObject()) {
            return build(mapper, "custom", headingId, bodyId, "");
        }
        String presetId = incoming.path("presetId").asText("custom");
        if (!"custom".equals(presetId) && preset(presetId) == null) {
            presetId = "custom";
        }
        JsonNode roles = incoming.path("roles");
        String heading = firstKnown(incoming.path("headingFontId").asText(""), roleId(roles, "display"), roleId(roles, "h1"), headingId);
        String body = firstKnown(incoming.path("bodyFontId").asText(""), roleId(roles, "body"), bodyId);
        String accent = firstKnown(incoming.path("accentFontId").asText(""), roleId(roles, "accent"), "");
        ObjectNode built = build(mapper, presetId, heading, body, accent);
        if (roles.isObject()) {
            roles.fieldNames().forEachRemaining(name -> {
                JsonNode target = built.path("roles").path(name);
                if (target instanceof ObjectNode object) {
                    overlay(object, roles.get(name));
                }
            });
        }
        return built;
    }

    public static ObjectNode build(ObjectMapper mapper, String presetId, String headingId, String bodyId, String accentId) {
        JsonNode preset = preset(presetId);
        String resolvedPreset = preset == null ? "custom" : preset.path("id").asText();
        String heading = firstKnown(headingId, preset == null ? "" : preset.path("headingFontId").asText(""), "fraunces");
        String body = firstKnown(bodyId, preset == null ? "" : preset.path("bodyFontId").asText(""), "manrope");
        if (handwriting(body)) {
            String presetBody = preset == null ? "" : preset.path("bodyFontId").asText("");
            body = handwriting(presetBody) ? "manrope" : firstKnown(presetBody, "manrope");
        }
        String accent = knownId(accentId) ? accentId : "";
        ObjectNode node = mapper.createObjectNode();
        node.put("presetId", resolvedPreset);
        node.put("headingFontId", heading);
        node.put("bodyFontId", body);
        node.put("accentFontId", accent);
        ObjectNode roles = node.putObject("roles");
        for (JsonNode role : ROOT.path("roles")) {
            roles.set(role.asText(), roleNode(mapper, role.asText(), heading, body, accent, preset));
        }
        return node;
    }

    public static void reconcile(ObjectNode styles) {
        if (styles == null) {
            return;
        }
        String fontId = styles.path("fontId").asText("");
        JsonNode font = font(fontId);
        if (!fontId.isBlank() && font == null) {
            styles.remove("fontId");
            font = null;
        }
        if (font != null) {
            styles.put("fontFamily", font.path("family").asText());
        } else {
            font = fontByFamily(styles.path("fontFamily").asText(""));
        }
        if (styles.has("fontWeight")) {
            styles.put("fontWeight", weight(font, styles.path("fontWeight").asText("")));
        }
        if (styles.has("fontStyle")) {
            styles.put("fontStyle", style(font, styles.path("fontStyle").asText("")));
        }
        if (styles.has("fontVariationSettings")) {
            String variations = variations(font, styles.path("fontVariationSettings").asText(""));
            if (variations.isBlank()) {
                styles.remove("fontVariationSettings");
            } else {
                styles.put("fontVariationSettings", variations);
            }
        }
        String role = styles.path("fontRole").asText("");
        if (!role.isBlank() && !knownRole(role)) {
            styles.remove("fontRole");
        }
    }

    public static String roleFamily(JsonNode document, String role) {
        JsonNode token = document == null ? null : document.path("typography").path("roles").path(role);
        if (token == null || token.isMissingNode()) {
            return "";
        }
        String family = token.path("fontFamily").asText("");
        if (!family.isBlank()) {
            return family;
        }
        return familyName(token.path("fontId").asText(""));
    }

    public static void attachTypography(ObjectNode properties) {
        properties.set("typography", typographySchema(MAPPER));
    }

    public static ObjectNode typographySchema(ObjectMapper mapper) {
        ArrayNode ids = mapper.createArrayNode();
        ArrayNode accentIds = mapper.createArrayNode();
        accentIds.add("");
        for (JsonNode font : ROOT.path("fonts")) {
            if (font.path("active").asBoolean(true)) {
                ids.add(font.path("id").asText());
                accentIds.add(font.path("id").asText());
            }
        }
        ArrayNode presets = mapper.createArrayNode();
        presets.add("custom");
        for (JsonNode preset : ROOT.path("presets")) {
            presets.add(preset.path("id").asText());
        }
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("presetId").add("headingFontId").add("bodyFontId").add("accentFontId");
        ObjectNode fields = schema.putObject("properties");
        enumField(fields, "presetId", presets);
        enumField(fields, "headingFontId", ids);
        enumField(fields, "bodyFontId", ids);
        enumField(fields, "accentFontId", accentIds);
        return schema;
    }

    private static ObjectNode roleNode(ObjectMapper mapper, String role, String headingId, String bodyId, String accentId, JsonNode preset) {
        boolean heading = HEADING_ROLES.contains(role);
        boolean body = BODY_ROLES.contains(role);
        String fontId = "accent".equals(role) ? (accentId.isBlank() ? headingId : accentId) : body ? bodyId : headingId;
        JsonNode font = thisFont(fontId);
        String preferredWeight = "accent".equals(role)
                ? scale(preset, "accentWeight", "400")
                : heading ? scale(preset, "headingWeight", "500") : scale(preset, "bodyWeight", "400");
        if ("h3".equals(role) || "h4".equals(role)) {
            preferredWeight = hasWeight(font, 500) ? "500" : preferredWeight;
        }
        if ("button".equals(role) || "label".equals(role) || "navigation".equals(role)) {
            preferredWeight = hasWeight(font, 600) ? "600" : preferredWeight;
        }
        String size = switch (role) {
            case "display", "hero" -> scale(preset, "displaySize", "64px");
            case "h1" -> scale(preset, "h1Size", "64px");
            case "h2" -> scale(preset, "h2Size", "36px");
            case "h3", "h4" -> scale(preset, "h3Size", "22px");
            case "bodySmall", "caption" -> "0.875rem";
            case "label", "navigation", "button" -> "0.95rem";
            case "accent" -> scale(preset, "displaySize", "64px");
            default -> scale(preset, "bodySize", "18px");
        };
        String line = "button".equals(role) ? "1.2" : heading || "accent".equals(role)
                ? scale(preset, "headingLineHeight", "1.08")
                : scale(preset, "bodyLineHeight", "1.65");
        String tracking = heading ? scale(preset, "headingTracking", "-0.03em") : "0em";
        String transform = "display".equals(role) || "hero".equals(role) || "h1".equals(role)
                ? scale(preset, "headingTransform", "none")
                : "none";
        ObjectNode roleNode = mapper.createObjectNode();
        roleNode.put("fontId", font.path("id").asText(fontId));
        roleNode.put("fontFamily", font.path("family").asText("Fraunces"));
        roleNode.put("fontWeight", weight(font, preferredWeight));
        roleNode.put("fontStyle", "normal");
        roleNode.put("fontSize", size);
        roleNode.put("lineHeight", line);
        roleNode.put("letterSpacing", tracking);
        roleNode.put("textTransform", transform);
        return roleNode;
    }

    private static void overlay(ObjectNode target, JsonNode incoming) {
        if (target == null || target.isMissingNode() || incoming == null || !incoming.isObject()) {
            return;
        }
        String fontId = incoming.path("fontId").asText("");
        JsonNode font = font(fontId);
        if (font != null) {
            target.put("fontId", font.path("id").asText());
            target.put("fontFamily", font.path("family").asText());
            font = thisFont(font.path("id").asText());
        } else {
            font = fontByFamily(target.path("fontFamily").asText(""));
        }
        copySafe(target, incoming, "fontWeight", 12);
        copySafe(target, incoming, "fontStyle", 12);
        copySafe(target, incoming, "fontSize", 120);
        copySafe(target, incoming, "lineHeight", 20);
        copySafe(target, incoming, "letterSpacing", 20);
        copySafe(target, incoming, "textTransform", 20);
        if (target.has("fontWeight")) {
            target.put("fontWeight", weight(font, target.path("fontWeight").asText("")));
        }
        if (target.has("fontStyle")) {
            target.put("fontStyle", style(font, target.path("fontStyle").asText("")));
        }
    }

    private static void copySafe(ObjectNode target, JsonNode incoming, String key, int limit) {
        String value = incoming.path(key).asText("");
        if (value.isBlank() || value.length() > limit || value.indexOf('<') >= 0 || value.indexOf(';') >= 0) {
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("expression(") || lower.contains("javascript") || lower.contains("http")) {
            return;
        }
        target.put(key, value);
    }

    private static String findFamily(JsonNode node, String type, String fallback) {
        if (node == null) {
            return fallback;
        }
        if (type.equals(node.path("type").asText(""))) {
            String family = node.path("styles").path("fontFamily").asText("");
            if (knownFamily(family) && !"inherit".equals(family)) {
                return family;
            }
        }
        for (JsonNode child : node.path("children")) {
            String found = findFamily(child, type, "");
            if (!found.isBlank()) {
                return found;
            }
        }
        return fallback;
    }

    private static String idForFamily(String family, String fallback) {
        JsonNode font = fontByFamily(family);
        if (font != null && font.path("active").asBoolean(true)) {
            return font.path("id").asText(fallback);
        }
        return fallback;
    }

    private static String firstKnown(String... ids) {
        for (String id : ids) {
            if (knownId(id)) {
                return id;
            }
        }
        return "";
    }

    private static String roleId(JsonNode roles, String role) {
        if (roles == null || !roles.isObject()) {
            return "";
        }
        return roles.path(role).path("fontId").asText("");
    }

    private static boolean knownRole(String role) {
        for (JsonNode name : ROOT.path("roles")) {
            if (role.equals(name.asText())) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode thisFont(String id) {
        JsonNode font = font(id);
        return font == null ? font("fraunces") : font;
    }

    private static boolean hasWeight(JsonNode font, int weight) {
        for (JsonNode candidate : font.path("weights")) {
            if (candidate.asInt() == weight) {
                return true;
            }
        }
        return font.path("variable").asBoolean(false);
    }

    private static String scale(JsonNode preset, String key, String fallback) {
        if (preset == null) {
            return fallback;
        }
        String value = preset.path(key).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static JsonNode axis(JsonNode font, String tag) {
        for (JsonNode axis : font.path("axes")) {
            if (tag.equals(axis.path("tag").asText())) {
                return axis;
            }
        }
        return null;
    }

    private static int parseWeight(String raw) {
        if (raw == null || raw.isBlank() || "normal".equalsIgnoreCase(raw)) {
            return 400;
        }
        if ("bold".equalsIgnoreCase(raw)) {
            return 700;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return 400;
        }
    }

    private static void enumField(ObjectNode fields, String name, ArrayNode values) {
        ObjectNode field = fields.putObject(name);
        field.put("type", "string");
        field.set("enum", values);
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings != null && !warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private static Map<String, JsonNode> index(String field) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode font : ROOT.path("fonts")) {
            map.put(font.path(field).asText(), font);
        }
        return map;
    }

    private static JsonNode load() {
        try (InputStream input = FontRegistry.class.getResourceAsStream("/typography/registry.json")) {
            if (input == null) {
                throw new IllegalStateException("The font registry is missing.");
            }
            return MAPPER.readTree(input);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("The font registry could not be read.", exception);
        }
    }
}
