package com.prism.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ConfigurationSanitizer {

    private static final Set<String> TYPES = Set.of(
            "HERO", "COUNTDOWN", "TEXT_BLOCK", "IMAGE", "IMAGE_GALLERY", "VIDEO",
            "EVENT_DETAILS", "EVENT_SCHEDULE", "LOCATION_MAP", "RSVP_FORM", "FOOTER");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern PLACEHOLDER = Pattern.compile("^/placeholders/[a-z0-9-]+\\.svg$");
    private static final Pattern SECTION_ID = Pattern.compile("^[a-z0-9-]{1,40}$");

    private final ObjectMapper mapper;

    public ConfigurationSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode sanitize(JsonNode input, boolean strict) {
        if (input == null || !input.isObject()) {
            throw ApiException.badRequest("The site configuration is missing.");
        }
        ObjectNode out = mapper.createObjectNode();
        out.set("theme", theme(input.get("theme")));
        out.set("event", event(input.get("event"), strict));
        out.set("rsvp", rsvp(input.get("rsvp")));
        out.set("sections", sections(input.get("sections")));
        if (input.has("canvas") && input.get("canvas").isObject()) {
            out.set("canvas", canvas(input.get("canvas")));
        }
        if (strict) {
            if (out.path("event").path("name").asText("").isBlank()) {
                throw ApiException.badRequest("Enter the event name.");
            }
            parseInstant(out.path("event").path("startAt").asText(""), "Enter the event date.");
            if (out.path("sections").isEmpty()) {
                throw ApiException.badRequest("Add at least one section before submitting.");
            }
            double ratio = contrast(out.path("theme").path("ink").asText(), out.path("theme").path("background").asText());
            if (ratio < 4.5) {
                throw ApiException.badRequest("Choose an ink colour that stays readable on the background.");
            }
        }
        return out;
    }

    public Set<String> mediaIds(JsonNode config) {
        Set<String> ids = new HashSet<>();
        for (JsonNode section : config.path("sections")) {
            JsonNode properties = section.path("properties");
            addMedia(ids, properties.path("backgroundImage").asText(""));
            addMedia(ids, properties.path("mediaId").asText(""));
            if (properties.path("mediaIds").isArray()) {
                properties.path("mediaIds").forEach(node -> addMedia(ids, node.asText("")));
            }
        }
        for (JsonNode element : config.path("canvas").path("elements")) {
            addMedia(ids, element.path("mediaId").asText(""));
        }
        return ids;
    }

    private void addMedia(Set<String> ids, String value) {
        if (value == null || value.isBlank() || value.startsWith("/placeholders/")) {
            return;
        }
        ids.add(value);
    }

    private ObjectNode theme(JsonNode node) {
        ObjectNode theme = mapper.createObjectNode();
        theme.put("background", colour(node, "background", "#f7f6f3"));
        theme.put("ink", colour(node, "ink", "#171717"));
        theme.put("accent", colour(node, "accent", "#1b6c80"));
        theme.put("font", font(node, "font", "Public Sans"));
        theme.put("displayFont", font(node, "displayFont", "Fraunces"));
        return theme;
    }

    private ObjectNode event(JsonNode node, boolean strict) {
        ObjectNode event = mapper.createObjectNode();
        event.put("name", text(node == null ? null : node.get("name"), 160, false));
        event.put("subtitle", text(node == null ? null : node.get("subtitle"), 180, false));
        event.put("description", text(node == null ? null : node.get("description"), 2000, true));
        event.put("startAt", instantText(node == null ? null : node.get("startAt"), strict));
        event.put("endAt", instantText(node == null ? null : node.get("endAt"), false));
        event.put("timezone", zone(node == null ? null : node.path("timezone").asText("")));
        event.put("venueName", text(node == null ? null : node.get("venueName"), 160, false));
        event.put("venueAddress", text(node == null ? null : node.get("venueAddress"), 300, true));
        event.put("mapUrl", mapUrl(node == null ? null : node.path("mapUrl").asText("")));
        return event;
    }

    private ObjectNode rsvp(JsonNode node) {
        ObjectNode rsvp = mapper.createObjectNode();
        rsvp.put("enabled", node != null && node.path("enabled").asBoolean(true));
        rsvp.put("deadline", instantText(node == null ? null : node.get("deadline"), false));
        int maxGuests = node == null ? 4 : node.path("maxGuestsPerResponse").asInt(4);
        rsvp.put("maxGuestsPerResponse", Math.min(10, Math.max(1, maxGuests)));
        rsvp.put("allowPlusOne", node == null || node.path("allowPlusOne").asBoolean(true));
        rsvp.put("collectPhone", node != null && node.path("collectPhone").asBoolean(false));
        rsvp.put("collectEmail", node != null && node.path("collectEmail").asBoolean(false));
        rsvp.put("confirmationMessage", text(node == null ? null : node.get("confirmationMessage"), 300, true));
        return rsvp;
    }

    private ArrayNode sections(JsonNode node) {
        ArrayNode sections = mapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return sections;
        }
        Set<String> ids = new HashSet<>();
        int count = 0;
        for (JsonNode section : node) {
            if (count >= 30) {
                break;
            }
            String type = section.path("type").asText("");
            if (!TYPES.contains(type)) {
                throw ApiException.badRequest("A section type is not supported.");
            }
            String id = section.path("id").asText("").toLowerCase(Locale.ROOT);
            if (!SECTION_ID.matcher(id).matches() || !ids.add(id)) {
                throw ApiException.badRequest("Each section needs its own short id.");
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("id", id);
            out.put("type", type);
            out.set("properties", properties(type, section.get("properties")));
            sections.add(out);
            count++;
        }
        return sections;
    }

    private ObjectNode canvas(JsonNode node) {
        ObjectNode canvas = mapper.createObjectNode();
        canvas.put("height", clampInt(node, "height", 1200, 66, 12000));
        ArrayNode elements = mapper.createArrayNode();
        Set<String> ids = new HashSet<>();
        int count = 0;
        if (node.path("elements").isArray()) {
            for (JsonNode item : node.path("elements")) {
                if (count >= 80) {
                    break;
                }
                String type = item.path("type").asText("");
                if (!type.equals("text") && !type.equals("image") && !type.equals("button") && !type.equals("box") && !type.equals("rsvp")) {
                    continue;
                }
                String id = item.path("id").asText("").toLowerCase(Locale.ROOT);
                if (!SECTION_ID.matcher(id).matches() || !ids.add(id)) {
                    continue;
                }
                ObjectNode element = mapper.createObjectNode();
                element.put("id", id);
                element.put("type", type);
                element.put("x", clampInt(item, "x", 8, 0, 100));
                element.put("y", clampInt(item, "y", 32, 0, 12000));
                element.put("w", clampInt(item, "w", 40, 8, 100));
                element.put("h", clampInt(item, "h", 80, 16, 4000));
                element.put("z", clampInt(item, "z", count + 1, 0, 400));
                element.put("text", text(item.get("text"), 2000, true));
                element.put("fontSize", clampInt(item, "fontSize", 18, 10, 120));
                element.put("color", colour(item, "color", "#171717"));
                element.put("align", side(item, "align", "left"));
                String fill = item.path("fill").asText("");
                element.put("fill", HEX.matcher(fill).matches() ? fill.toLowerCase(Locale.ROOT) : "");
                element.put("weight", "display".equals(item.path("weight").asText("")) ? "display" : "regular");
                String font = item.path("font").asText("");
                element.put("font", com.prism.typography.FontRegistry.knownFamily(font) ? font : "");
                element.put("bold", item.path("bold").asBoolean(false));
                element.put("italic", item.path("italic").asBoolean(false));
                element.put("underline", item.path("underline").asBoolean(false));
                element.put("mediaId", mediaRef(item.get("mediaId")));
                element.put("borderWidth", clampInt(item, "borderWidth", 0, 0, 24));
                String borderColor = item.path("borderColor").asText("");
                element.put("borderColor", HEX.matcher(borderColor).matches() ? borderColor.toLowerCase(Locale.ROOT) : "");
                element.put("radius", clampInt(item, "radius", 0, 0, 80));
                element.put("shadow", clampInt(item, "shadow", 0, 0, 48));
                element.put("href", link(item.get("href")));
                elements.add(element);
                count++;
            }
        }
        canvas.set("elements", elements);
        return canvas;
    }

    private ObjectNode properties(String type, JsonNode node) {
        ObjectNode properties = mapper.createObjectNode();
        switch (type) {
            case "HERO" -> {
                properties.put("title", text(node == null ? null : node.get("title"), 80, false));
                properties.put("subtitle", text(node == null ? null : node.get("subtitle"), 160, false));
                properties.put("backgroundImage", mediaRef(node == null ? null : node.get("backgroundImage")));
            }
            case "COUNTDOWN" -> properties.put("label", text(node == null ? null : node.get("label"), 80, false));
            case "TEXT_BLOCK" -> {
                properties.put("heading", text(node == null ? null : node.get("heading"), 120, false));
                properties.put("body", text(node == null ? null : node.get("body"), 2000, true));
            }
            case "IMAGE", "VIDEO" -> {
                properties.put("mediaId", mediaRef(node == null ? null : node.get("mediaId")));
                properties.put("caption", text(node == null ? null : node.get("caption"), 180, false));
            }
            case "IMAGE_GALLERY" -> {
                ArrayNode ids = mapper.createArrayNode();
                if (node != null && node.path("mediaIds").isArray()) {
                    int added = 0;
                    for (JsonNode id : node.path("mediaIds")) {
                        if (added >= 24) {
                            break;
                        }
                        String ref = mediaRef(id);
                        if (!ref.isBlank()) {
                            ids.add(ref);
                            added++;
                        }
                    }
                }
                properties.set("mediaIds", ids);
                properties.put("caption", text(node == null ? null : node.get("caption"), 180, false));
            }
            case "EVENT_DETAILS", "RSVP_FORM" ->
                    properties.put("heading", text(node == null ? null : node.get("heading"), 120, false));
            case "EVENT_SCHEDULE" -> {
                properties.put("heading", text(node == null ? null : node.get("heading"), 120, false));
                ArrayNode items = mapper.createArrayNode();
                if (node != null && node.path("items").isArray()) {
                    int added = 0;
                    for (JsonNode item : node.path("items")) {
                        if (added >= 24) {
                            break;
                        }
                        String title = text(item.get("title"), 120, false);
                        if (title.isBlank()) {
                            continue;
                        }
                        ObjectNode row = mapper.createObjectNode();
                        row.put("time", text(item.get("time"), 20, false));
                        row.put("title", title);
                        row.put("detail", text(item.get("detail"), 300, false));
                        items.add(row);
                        added++;
                    }
                }
                properties.set("items", items);
            }
            case "LOCATION_MAP" -> {
                properties.put("heading", text(node == null ? null : node.get("heading"), 120, false));
                properties.put("note", text(node == null ? null : node.get("note"), 300, true));
            }
            case "FOOTER" -> properties.put("note", text(node == null ? null : node.get("note"), 300, true));
            default -> throw ApiException.badRequest("A section type is not supported.");
        }
        layout(properties, node);
        return properties;
    }

    private void layout(ObjectNode properties, JsonNode node) {
        properties.put("align", side(node, "align", "left"));
        properties.put("imageAlign", side(node, "imageAlign", "center"));
        properties.put("focusX", percent(node, "focusX"));
        properties.put("focusY", percent(node, "focusY"));
        properties.put("imageX", placed(node, "imageX", 52));
        properties.put("imageY", placed(node, "imageY", 4));
        properties.put("imageW", placed(node, "imageW", 40));
        properties.put("imageH", placed(node, "imageH", 24));
        properties.put("frameX", placed(node, "frameX", 0));
        properties.put("frameW", placed(node, "frameW", 100));
        properties.put("frameH", placed(node, "frameH", 0));
        properties.put("fontSize", placed(node, "fontSize", 0));
        properties.set("alignments", alignments(node));
    }

    private ObjectNode alignments(JsonNode node) {
        ObjectNode out = mapper.createObjectNode();
        JsonNode source = node == null ? null : node.get("alignments");
        if (source == null || !source.isObject()) {
            return out;
        }
        int count = 0;
        var fields = source.fields();
        while (fields.hasNext() && count < 40) {
            var entry = fields.next();
            String key = entry.getKey();
            if (!key.matches("^[a-z0-9-]{1,40}$")) {
                continue;
            }
            String value = entry.getValue().asText("");
            if ("left".equals(value) || "center".equals(value) || "right".equals(value)) {
                out.put(key, value);
                count++;
            }
        }
        return out;
    }

    private String side(JsonNode node, String field, String fallback) {
        String value = node == null ? "" : node.path(field).asText("");
        return "left".equals(value) || "center".equals(value) || "right".equals(value) ? value : fallback;
    }

    private int placed(JsonNode node, String field, int fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (!value.isNumber() && value.asText("").isBlank()) {
            return fallback;
        }
        return Math.min(100, Math.max(0, percent(node, field)));
    }

    private int percent(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return 50;
        }
        JsonNode value = node.get(field);
        int parsed;
        if (value.isNumber()) {
            parsed = value.asInt();
        } else {
            try {
                parsed = Integer.parseInt(value.asText("").trim());
            } catch (NumberFormatException exception) {
                return 50;
            }
        }
        return Math.min(100, Math.max(0, parsed));
    }

    private int clampInt(JsonNode node, String field, int fallback, int min, int max) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (!value.isNumber() && value.asText("").isBlank()) {
            return fallback;
        }
        int parsed;
        if (value.isNumber()) {
            parsed = value.asInt();
        } else {
            try {
                parsed = Integer.parseInt(value.asText("").trim());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
        return Math.min(max, Math.max(min, parsed));
    }

    private String colour(JsonNode node, String field, String fallback) {
        String value = node == null ? "" : node.path(field).asText("");
        if (HEX.matcher(value).matches()) {
            return value.toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    private String font(JsonNode node, String field, String fallback) {
        String value = node == null ? "" : node.path(field).asText("");
        return com.prism.typography.FontRegistry.knownFamily(value) ? value : fallback;
    }

    private String instantText(JsonNode node, boolean required) {
        String value = node == null || node.isNull() ? "" : node.asText("").trim();
        if (value.isBlank()) {
            if (required) {
                throw ApiException.badRequest("Enter the event date.");
            }
            return "";
        }
        parseInstant(value, "Enter a valid date and time.");
        return value;
    }

    private Instant parseInstant(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw ApiException.badRequest(message);
        }
    }

    private String zone(String value) {
        if (value == null || value.isBlank()) {
            return "Asia/Kolkata";
        }
        try {
            ZoneId.of(value);
            return value;
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("Choose a valid timezone.");
        }
    }

    private String mapUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        boolean allowed = trimmed.startsWith("https://maps.google.")
                || trimmed.startsWith("https://www.google.com/maps")
                || trimmed.startsWith("https://maps.app.goo.gl/")
                || trimmed.startsWith("https://goo.gl/maps");
        if (!allowed || trimmed.contains("<") || trimmed.contains(" ")) {
            throw ApiException.badRequest("Use a Google Maps link for the venue.");
        }
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String mediaRef(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText("").trim();
        if (value.isBlank()) {
            return "";
        }
        if (PLACEHOLDER.matcher(value).matches()) {
            return value;
        }
        try {
            return java.util.UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("An image or video reference is not valid.");
        }
    }

    private String text(JsonNode node, int max, boolean multiline) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        value = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        if (!multiline) {
            value = value.replace('\n', ' ').replace('\t', ' ').trim();
        } else {
            value = value.trim();
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || lowered.contains("javascript:")) {
            throw ApiException.badRequest("Text cannot include markup.");
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String link(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText("").trim();
        if (value.length() > 500) {
            value = value.substring(0, 500);
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || value.indexOf('"') >= 0 || value.indexOf(' ') >= 0
                || lowered.startsWith("javascript:") || lowered.startsWith("data:") || lowered.startsWith("vbscript:")) {
            return "";
        }
        if (lowered.startsWith("https://") || lowered.startsWith("http://") || lowered.startsWith("mailto:") || lowered.startsWith("tel:")
                || value.startsWith("#") || (value.startsWith("/") && !value.startsWith("//"))) {
            return value;
        }
        return "";
    }

    static double contrast(String ink, String background) {
        double lighter = Math.max(luminance(ink), luminance(background));
        double darker = Math.min(luminance(ink), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel(((rgb >> 16) & 0xff) / 255d);
        double g = channel(((rgb >> 8) & 0xff) / 255d);
        double b = channel((rgb & 0xff) / 255d);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
