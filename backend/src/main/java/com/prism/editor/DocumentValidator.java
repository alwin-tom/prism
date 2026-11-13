package com.prism.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.creation.DesignSystemTokens;
import com.prism.shared.exception.ApiException;
import com.prism.typography.FontRegistry;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class DocumentValidator {

    public static final int SCHEMA = 1;
    private static final int MAX_NODES = 300;
    private static final int MAX_DEPTH = 8;
    private static final Pattern ID = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "documentId", "page", "breakpoints", "designTokens", "typography", "creative", "root");
    private static final Set<String> PAGE_KEYS = Set.of("title", "description");
    private static final Set<String> NODE_KEYS = Set.of("id", "type", "name", "locked", "props", "styles", "responsiveStyles", "responsiveProps", "children");
    private static final Set<String> TYPES = Set.of(
            "SECTION", "CONTAINER", "FLEX", "GRID", "GROUP", "CAROUSEL",
            "HEADING", "PARAGRAPH", "BUTTON", "IMAGE", "SPACER", "DIVIDER",
            "QUOTE", "COUNTDOWN", "FAQ_ACCORDION", "FAQ_ITEM", "TIMELINE", "TIMELINE_ITEM");
    private static final Set<String> STYLES = Set.of(
            "color", "background", "fontSize", "fontWeight", "fontStyle", "fontFamily", "lineHeight", "letterSpacing",
            "textAlign", "textDecoration", "padding", "margin", "gap", "display", "flexDirection", "flexWrap",
            "justifyContent", "alignItems", "alignContent", "alignSelf", "flex", "flexGrow", "flexShrink", "flexBasis",
            "marginLeft", "marginRight", "aspectRatio",
            "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight",
            "border", "borderWidth", "borderStyle", "borderColor", "borderRadius", "boxShadow", "opacity", "filter",
            "objectFit", "objectPosition", "gridTemplateColumns", "columnGap", "rowGap",
            "position", "top", "right", "bottom", "left", "zIndex", "overflow", "transform", "textTransform", "order",
            "fontId", "fontRole", "fontStretch", "fontVariant", "fontVariationSettings", "fontFeatureSettings",
            "wordSpacing", "textIndent", "verticalAlign", "textJustify",
            "textDecorationColor", "textDecorationStyle", "textDecorationThickness", "textUnderlineOffset",
            "textShadow", "textOverflow", "whiteSpace", "wordBreak", "overflowWrap", "textStroke", "textGradient");
    private static final Set<String> CAROUSEL_PRESETS = Set.of("minimal", "cards", "hero", "coverflow", "fade", "editorial");

    private final ObjectMapper mapper;
    private int nodes;

    public DocumentValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode sanitize(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw ApiException.badRequest("The page document is missing.");
        }
        rejectUnknown(input, ROOT_KEYS, "document");
        if (input.path("schemaVersion").asInt(0) != SCHEMA) {
            throw ApiException.badRequest("This page document version is not supported.");
        }
        nodes = 0;
        ObjectNode out = mapper.createObjectNode();
        out.put("schemaVersion", SCHEMA);
        String documentId = input.path("documentId").asText("");
        if (!UUID_TEXT.matcher(documentId).matches()) {
            throw ApiException.badRequest("The page document needs an id.");
        }
        out.put("documentId", documentId.toLowerCase(Locale.ROOT));
        out.set("page", page(input.get("page")));
        out.set("breakpoints", mapper.createObjectNode()
                .put("desktop", ResponsiveBreakpoints.DESKTOP_MIN)
                .put("tablet", ResponsiveBreakpoints.TABLET_MIN)
                .put("mobile", ResponsiveBreakpoints.MOBILE_MAX));
        out.set("designTokens", DesignSystemTokens.sanitize(mapper, input.get("designTokens")));
        if (input.path("creative").isObject()) {
            out.set("creative", creative(input.get("creative")));
        }
        out.set("root", element(input.get("root"), new HashSet<>(), 0, true));
        out.set("typography", FontRegistry.ensure(mapper, input.get("typography"), out.get("root")));
        return out;
    }

    private ObjectNode creative(JsonNode node) {
        ObjectNode out = mapper.createObjectNode();
        if (node.path("archetypeId").isTextual()) {
            out.put("archetypeId", text(node.get("archetypeId"), 64));
        }
        if (node.path("rationale").isTextual()) {
            out.put("rationale", text(node.get("rationale"), 500));
        }
        if (node.path("promptVersion").isTextual()) {
            out.put("promptVersion", text(node.get("promptVersion"), 64));
        }
        if (node.path("motionStrategy").isTextual()) {
            out.put("motionStrategy", text(node.get("motionStrategy"), 32));
        }
        if (node.path("visualComplexity").isTextual()) {
            out.put("visualComplexity", text(node.get("visualComplexity"), 32));
        }
        return out;
    }

    public Set<String> mediaIds(JsonNode document) {
        Set<String> ids = new HashSet<>();
        collectMedia(document.path("root"), ids);
        return ids;
    }

    private void collectMedia(JsonNode node, Set<String> ids) {
        if ("IMAGE".equals(node.path("type").asText()) ) {
            collectMediaId(node.path("props").path("mediaId"), ids);
            collectMediaId(node.path("responsiveProps").path("tablet").path("mediaId"), ids);
            collectMediaId(node.path("responsiveProps").path("mobile").path("mediaId"), ids);
        }
        for (JsonNode child : node.path("children")) {
            collectMedia(child, ids);
        }
    }

    private ObjectNode page(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw ApiException.badRequest("The page settings are missing.");
        }
        rejectUnknown(node, PAGE_KEYS, "page");
        ObjectNode page = mapper.createObjectNode();
        page.put("title", text(node.get("title"), 160));
        page.put("description", text(node.get("description"), 300));
        return page;
    }

    private ObjectNode element(JsonNode node, Set<String> ids, int depth, boolean root) {
        if (node == null || !node.isObject()) {
            throw ApiException.badRequest("A page element is missing.");
        }
        if (depth > MAX_DEPTH || ++nodes > MAX_NODES) {
            throw ApiException.badRequest("This page has too many nested elements.");
        }
        rejectUnknown(node, NODE_KEYS, "element");
        String type = node.path("type").asText("");
        if (!TYPES.contains(type) || (root && !"SECTION".equals(type))) {
            throw ApiException.badRequest("This page uses an element WebAux cannot place.");
        }
        String id = node.path("id").asText("").toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches() || !ids.add(id)) {
            throw ApiException.badRequest("Each element needs its own short id.");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("id", id);
        out.put("type", type);
        out.put("name", text(node.get("name"), 80));
        if (node.path("locked").asBoolean(false)) {
            out.put("locked", true);
        }
        out.set("props", props(type, node.get("props")));
        out.set("styles", styles(node.get("styles")));
        out.set("responsiveStyles", responsive(node.get("responsiveStyles")));
        out.set("responsiveProps", responsiveProps(type, node.get("responsiveProps")));
        ArrayNode children = mapper.createArrayNode();
        if (node.path("children").isArray()) {
            for (JsonNode child : node.path("children")) {
                children.add(element(child, ids, depth + 1, false));
            }
        } else if (!node.path("children").isMissingNode() && !node.path("children").isNull()) {
            throw ApiException.badRequest("Element children must be a list.");
        }
        out.set("children", children);
        return out;
    }

    private ObjectNode props(String type, JsonNode node) {
        ObjectNode props = mapper.createObjectNode();
        if (node == null || node.isNull()) {
            node = mapper.createObjectNode();
        }
        if (!node.isObject()) {
            throw ApiException.badRequest("Element properties are unreadable.");
        }
        switch (type) {
            case "HEADING" -> {
                rejectUnknown(node, Set.of("text", "level"), type);
                props.put("text", text(node.get("text"), 400));
                int level = node.path("level").asInt(2);
                if (level < 1 || level > 6) {
                    throw ApiException.badRequest("Choose a heading level from 1 to 6.");
                }
                props.put("level", level);
            }
            case "PARAGRAPH" -> {
                rejectUnknown(node, Set.of("text"), type);
                props.put("text", text(node.get("text"), 4000));
            }
            case "BUTTON" -> {
                rejectUnknown(node, Set.of("text", "href"), type);
                props.put("text", text(node.get("text"), 160));
                props.put("href", link(node.get("href")));
            }
            case "IMAGE" -> {
                rejectUnknown(node, Set.of("mediaId", "alt"), type);
                String mediaId = node.path("mediaId").asText("");
                if (!mediaId.isBlank() && !UUID_TEXT.matcher(mediaId).matches()) {
                    throw ApiException.badRequest("Choose a picture from this event.");
                }
                props.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
                props.put("alt", text(node.get("alt"), 180));
            }
            case "SPACER" -> {
                rejectUnknown(node, Set.of("size"), type);
                int size = node.path("size").asInt(24);
                props.put("size", Math.min(240, Math.max(4, size)));
            }
            case "CAROUSEL" -> {
                rejectUnknown(node, Set.of("preset", "autoplay", "interval", "perView"), type);
                props.put("preset", carouselPreset(node.get("preset")));
                props.put("autoplay", node.path("autoplay").asText("1").startsWith("0") ? 0 : 1);
                props.put("interval", bounded(node.get("interval"), 5000, 2000, 20000));
                props.put("perView", bounded(node.get("perView"), 1, 1, 3));
            }
            case "QUOTE" -> {
                rejectUnknown(node, Set.of("text", "attribution"), type);
                props.put("text", text(node.get("text"), 800));
                props.put("attribution", text(node.get("attribution"), 160));
            }
            case "COUNTDOWN" -> {
                rejectUnknown(node, Set.of("target", "label"), type);
                props.put("target", text(node.get("target"), 40));
                props.put("label", text(node.get("label"), 120));
            }
            case "FAQ_ACCORDION", "TIMELINE" -> {
                rejectUnknown(node, Set.of("title"), type);
                props.put("title", text(node.get("title"), 160));
            }
            case "FAQ_ITEM", "TIMELINE_ITEM" -> {
                rejectUnknown(node, Set.of("text", "title"), type);
                props.put("title", text(node.get("title"), 200));
                props.put("text", text(node.get("text"), 2000));
            }
            default -> {
                if (node.fieldNames().hasNext()) {
                    rejectUnknown(node, Set.of(), type);
                }
            }
        }
        return props;
    }

    private ObjectNode responsiveProps(String type, JsonNode node) {
        ObjectNode out = mapper.createObjectNode();
        if (node != null && !node.isNull()) {
            if (!node.isObject()) {
                throw ApiException.badRequest("Responsive properties are unreadable.");
            }
            rejectUnknown(node, Set.of("tablet", "mobile"), "responsive properties");
        }
        out.set("tablet", partialProps(type, node == null ? null : node.get("tablet")));
        out.set("mobile", partialProps(type, node == null ? null : node.get("mobile")));
        return out;
    }

    private ObjectNode partialProps(String type, JsonNode node) {
        ObjectNode props = mapper.createObjectNode();
        if (node == null || node.isNull()) {
            return props;
        }
        if (!node.isObject()) {
            throw ApiException.badRequest("Responsive properties are unreadable.");
        }
        switch (type) {
            case "IMAGE" -> {
                rejectUnknown(node, Set.of("mediaId", "alt"), type);
                if (node.has("mediaId")) {
                    String mediaId = node.path("mediaId").asText("");
                    if (!mediaId.isBlank() && !UUID_TEXT.matcher(mediaId).matches()) {
                        throw ApiException.badRequest("Choose a picture from this event.");
                    }
                    props.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
                }
                if (node.has("alt")) {
                    props.put("alt", text(node.get("alt"), 180));
                }
            }
            case "SPACER" -> {
                rejectUnknown(node, Set.of("size"), type);
                if (node.has("size")) {
                    props.put("size", bounded(node.get("size"), 24, 4, 240));
                }
            }
            case "CAROUSEL" -> {
                rejectUnknown(node, Set.of("preset", "autoplay", "interval", "perView"), type);
                if (node.has("preset")) {
                    props.put("preset", carouselPreset(node.get("preset")));
                }
                if (node.has("autoplay")) {
                    props.put("autoplay", node.path("autoplay").asText("1").startsWith("0") ? 0 : 1);
                }
                if (node.has("interval")) {
                    props.put("interval", bounded(node.get("interval"), 5000, 2000, 20000));
                }
                if (node.has("perView")) {
                    props.put("perView", bounded(node.get("perView"), 1, 1, 3));
                }
            }
            case "QUOTE" -> {
                rejectUnknown(node, Set.of("text", "attribution"), type);
                if (node.has("text")) {
                    props.put("text", text(node.get("text"), 800));
                }
                if (node.has("attribution")) {
                    props.put("attribution", text(node.get("attribution"), 160));
                }
            }
            case "COUNTDOWN" -> {
                rejectUnknown(node, Set.of("target", "label"), type);
                if (node.has("target")) {
                    props.put("target", text(node.get("target"), 40));
                }
                if (node.has("label")) {
                    props.put("label", text(node.get("label"), 120));
                }
            }
            case "FAQ_ACCORDION", "TIMELINE" -> {
                rejectUnknown(node, Set.of("title"), type);
                if (node.has("title")) {
                    props.put("title", text(node.get("title"), 160));
                }
            }
            case "FAQ_ITEM", "TIMELINE_ITEM" -> {
                rejectUnknown(node, Set.of("text", "title"), type);
                if (node.has("title")) {
                    props.put("title", text(node.get("title"), 200));
                }
                if (node.has("text")) {
                    props.put("text", text(node.get("text"), 2000));
                }
            }
            default -> {
                if (node.fieldNames().hasNext()) {
                    rejectUnknown(node, Set.of(), type);
                }
            }
        }
        return props;
    }

    private void collectMediaId(JsonNode node, Set<String> ids) {
        String mediaId = node == null ? "" : node.asText("");
        if (!mediaId.isBlank()) {
            ids.add(mediaId);
        }
    }

    private ObjectNode styles(JsonNode node) {
        ObjectNode styles = mapper.createObjectNode();
        if (node == null || node.isNull()) {
            return styles;
        }
        if (!node.isObject()) {
            throw ApiException.badRequest("Element styles are unreadable.");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            if (!STYLES.contains(key)) {
                throw ApiException.badRequest("That style is not available.");
            }
            styles.put(key, styleValue(key, node.get(key)));
        }
        FontRegistry.reconcile(styles);
        return styles;
    }

    private ObjectNode responsive(JsonNode node) {
        ObjectNode responsive = mapper.createObjectNode();
        responsive.set("tablet", mapper.createObjectNode());
        responsive.set("mobile", mapper.createObjectNode());
        if (node == null || node.isNull()) {
            return responsive;
        }
        if (!node.isObject()) {
            throw ApiException.badRequest("Responsive styles are unreadable.");
        }
        rejectUnknown(node, Set.of("tablet", "mobile"), "responsive styles");
        responsive.set("tablet", styles(node.get("tablet")));
        responsive.set("mobile", styles(node.get("mobile")));
        return responsive;
    }

    private String styleValue(String key, JsonNode node) {
        String value = node == null ? "" : node.asText("");
        int limit = switch (key) {
            case "background", "boxShadow", "filter", "border", "textShadow", "textGradient", "fontVariationSettings", "fontFeatureSettings" -> 180;
            case "fontSize" -> 120;
            default -> 80;
        };
        if (value.length() > limit || value.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("expression(") || lower.contains("javascript") || value.indexOf('<') >= 0 || value.indexOf(';') >= 0) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("fontFamily".equals(key) && !value.isBlank() && !FontRegistry.knownFamily(value)) {
            throw ApiException.badRequest("Choose a font from the list.");
        }
        if ("fontId".equals(key) && !value.isBlank() && FontRegistry.font(value) == null) {
            throw ApiException.badRequest("Choose a font from the list.");
        }
        if ("fontVariationSettings".equals(key) && !value.isBlank() && !value.matches("^\"[a-z]{4}\" -?\\d{1,4}(?:\\.\\d+)?(?:, \"[a-z]{4}\" -?\\d{1,4}(?:\\.\\d+)?)*$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("fontFeatureSettings".equals(key) && !value.isBlank() && !value.matches("^\"[a-z0-9]{4}\" [01](?:, \"[a-z0-9]{4}\" [01])*$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("textGradient".equals(key) && !value.isBlank() && !value.matches("^(linear|radial)-gradient\\([^)]+\\)$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("textStroke".equals(key) && !value.isBlank() && !value.matches("^\\d(?:\\.\\d)?px (#[0-9a-fA-F]{3,8}|[a-z]{3,20})$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("transform".equals(key) && !value.isBlank() && !value.matches("^(rotate\\(-?\\d{1,3}deg\\)|translate\\(-?\\d{1,4}px, -?\\d{1,4}px\\)|scale\\([0-9.]+(?:, [0-9.]+)?\\))( (rotate\\(-?\\d{1,3}deg\\)|translate\\(-?\\d{1,4}px, -?\\d{1,4}px\\)|scale\\([0-9.]+(?:, [0-9.]+)?\\)))*$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        return value;
    }

    private String carouselPreset(JsonNode node) {
        String value = node == null ? "" : node.asText("minimal").trim().toLowerCase(Locale.ROOT);
        return CAROUSEL_PRESETS.contains(value) ? value : "minimal";
    }

    private int bounded(JsonNode node, int fallback, int min, int max) {
        int value = fallback;
        if (node != null && node.isNumber()) {
            value = node.asInt(fallback);
        } else if (node != null && node.isTextual() && !node.asText("").isBlank()) {
            try {
                value = Integer.parseInt(node.asText("").trim());
            } catch (NumberFormatException exception) {
                value = fallback;
            }
        }
        return Math.min(max, Math.max(min, value));
    }

    private String link(JsonNode node) {
        String value = node == null || node.isNull() ? "" : node.asText("").trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.length() > 400 || value.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("That link is not allowed.");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
            throw ApiException.badRequest("That link is not allowed.");
        }
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:") || lower.startsWith("tel:") || value.startsWith("#") || value.startsWith("/")) {
            return value;
        }
        throw ApiException.badRequest("That link is not allowed.");
    }

    private String text(JsonNode node, int max) {
        String value = node == null || node.isNull() ? "" : node.asText("");
        if (value.length() > max) {
            throw ApiException.badRequest("That text is too long.");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.contains("javascript:")) {
            throw ApiException.badRequest("That text is not allowed.");
        }
        return value;
    }

    private void rejectUnknown(JsonNode node, Set<String> allowed, String where) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw ApiException.badRequest("The " + where + " contains a field WebAux does not keep.");
            }
        }
    }
}
