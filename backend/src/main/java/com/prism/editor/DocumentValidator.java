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
            "SECTION", "CONTAINER", "FLEX", "GRID", "GROUP", "CAROUSEL", "MARQUEE",
            "HEADING", "PARAGRAPH", "BUTTON", "IMAGE", "SPACER", "DIVIDER",
            "QUOTE", "COUNTDOWN", "FAQ_ACCORDION", "FAQ_ITEM", "TIMELINE", "TIMELINE_ITEM");
    private static final Set<String> LINK_STYLE_KEYS = Set.of("href", "linkTarget", "linkTitle", "linkColor", "linkHoverColor", "linkUnderline", "richText");
    private static final Set<String> MARQUEE_DIRS = Set.of("left", "right", "up", "down");
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
            "textShadow", "textOverflow", "whiteSpace", "wordBreak", "overflowWrap", "textStroke", "textGradient",
            "backgroundMediaId", "backgroundSize", "backgroundPosition", "backgroundRepeat", "backgroundAttachment",
            "backgroundOverlayType", "backgroundOverlayColor", "backgroundOverlayOpacity", "backgroundOverlayAngle", "backgroundOverlayEnd");
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
        if ("IMAGE".equals(node.path("type").asText())) {
            collectMediaId(node.path("props").path("mediaId"), ids);
            collectMediaId(node.path("responsiveProps").path("tablet").path("mediaId"), ids);
            collectMediaId(node.path("responsiveProps").path("mobile").path("mediaId"), ids);
        }
        if ("MARQUEE".equals(node.path("type").asText())) {
            for (JsonNode item : node.path("props").path("items")) {
                collectMediaId(item.path("mediaId"), ids);
            }
        }
        collectMediaId(node.path("styles").path("backgroundMediaId"), ids);
        collectMediaId(node.path("responsiveStyles").path("tablet").path("backgroundMediaId"), ids);
        collectMediaId(node.path("responsiveStyles").path("mobile").path("backgroundMediaId"), ids);
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
                rejectUnknown(node, union(Set.of("text", "level"), LINK_STYLE_KEYS), type);
                props.put("text", text(node.get("text"), 400));
                int level = node.path("level").asInt(2);
                if (level < 1 || level > 6) {
                    throw ApiException.badRequest("Choose a heading level from 1 to 6.");
                }
                props.put("level", level);
                putLinkFields(props, node, 400);
            }
            case "PARAGRAPH" -> {
                rejectUnknown(node, union(Set.of("text"), LINK_STYLE_KEYS), type);
                props.put("text", text(node.get("text"), 4000));
                putLinkFields(props, node, 4000);
            }
            case "BUTTON" -> {
                rejectUnknown(node, Set.of("text", "href", "linkTarget", "linkTitle"), type);
                props.put("text", text(node.get("text"), 160));
                props.put("href", link(node.get("href")));
                putOptionalLinkMeta(props, node);
            }
            case "MARQUEE" -> {
                rejectUnknown(node, union(Set.of(
                        "text", "items", "direction", "speed", "duration", "gap", "delay",
                        "loop", "pauseOnHover", "pauseOnClick", "animationEnabled"
                ), LINK_STYLE_KEYS), type);
                props.put("text", text(node.get("text"), 800));
                String direction = node.path("direction").asText("left").trim().toLowerCase(Locale.ROOT);
                props.put("direction", MARQUEE_DIRS.contains(direction) ? direction : "left");
                props.put("speed", bounded(node.get("speed"), 50, 1, 100));
                props.put("duration", bounded(node.get("duration"), 28, 4, 120));
                props.put("gap", bounded(node.get("gap"), 32, 0, 160));
                props.put("delay", bounded(node.get("delay"), 0, 0, 30));
                props.put("loop", flag(node.get("loop"), true));
                props.put("pauseOnHover", flag(node.get("pauseOnHover"), true));
                props.put("pauseOnClick", flag(node.get("pauseOnClick"), false));
                props.put("animationEnabled", flag(node.get("animationEnabled"), true));
                props.set("items", marqueeItems(node.get("items")));
                putLinkFields(props, node, 800);
            }
            case "IMAGE" -> {
                rejectUnknown(node, Set.of("mediaId", "alt", "assetKey"), type);
                String mediaId = node.path("mediaId").asText("");
                if (!mediaId.isBlank() && !UUID_TEXT.matcher(mediaId).matches()) {
                    throw ApiException.badRequest("Choose a picture from this event.");
                }
                props.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
                props.put("alt", text(node.get("alt"), 180));
                String assetKey = node.path("assetKey").asText("").trim().toLowerCase(Locale.ROOT);
                if (!assetKey.isBlank()) {
                    props.put("assetKey", assetKey.length() > 80 ? assetKey.substring(0, 80) : assetKey);
                }
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
                rejectUnknown(node, union(Set.of("text", "attribution"), LINK_STYLE_KEYS), type);
                props.put("text", text(node.get("text"), 800));
                props.put("attribution", text(node.get("attribution"), 160));
                putLinkFields(props, node, 800);
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
                rejectUnknown(node, union(Set.of("text", "title"), Set.of("richText", "linkColor", "linkHoverColor", "linkUnderline")), type);
                props.put("title", text(node.get("title"), 200));
                props.put("text", text(node.get("text"), 2000));
                if (node.has("richText")) {
                    props.set("richText", richText(node.get("richText"), 2000));
                }
                putLinkColors(props, node);
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
                rejectUnknown(node, Set.of("mediaId", "alt", "assetKey"), type);
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
                if (node.has("assetKey")) {
                    String assetKey = node.path("assetKey").asText("").trim().toLowerCase(Locale.ROOT);
                    if (!assetKey.isBlank()) {
                        props.put("assetKey", assetKey.length() > 80 ? assetKey.substring(0, 80) : assetKey);
                    }
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
        if ("backgroundMediaId".equals(key) && !value.isBlank() && !UUID_TEXT.matcher(value).matches()) {
            throw ApiException.badRequest("Choose a picture from this event.");
        }
        if ("backgroundOverlayType".equals(key) && !value.isBlank() && !Set.of("none", "solid", "gradient").contains(value.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("backgroundRepeat".equals(key) && !value.isBlank() && !Set.of("no-repeat", "repeat", "repeat-x", "repeat-y").contains(value.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if ("backgroundAttachment".equals(key) && !value.isBlank() && !Set.of("scroll", "fixed", "local").contains(value.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        if (("backgroundOverlayOpacity".equals(key) || "backgroundOverlayAngle".equals(key) || "zIndex".equals(key)) && !value.isBlank() && !value.matches("^-?\\d{1,4}$")) {
            throw ApiException.badRequest("That style value is not allowed.");
        }
        return "backgroundMediaId".equals(key) && !value.isBlank() ? value.toLowerCase(Locale.ROOT) : value;
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
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            throw ApiException.badRequest("That link is not allowed.");
        }
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:") || lower.startsWith("tel:") || value.startsWith("#") || value.startsWith("/")) {
            return value;
        }
        throw ApiException.badRequest("That link is not allowed.");
    }

    private void putLinkFields(ObjectNode props, JsonNode node, int maxText) {
        if (node.has("href")) {
            props.put("href", link(node.get("href")));
        }
        putOptionalLinkMeta(props, node);
        putLinkColors(props, node);
        if (node.has("richText")) {
            ArrayNode runs = richText(node.get("richText"), maxText);
            props.set("richText", runs);
            if (!node.has("text") || node.path("text").asText("").isBlank()) {
                StringBuilder plain = new StringBuilder();
                for (JsonNode run : runs) {
                    plain.append(run.path("text").asText(""));
                }
                props.put("text", plain.length() > maxText ? plain.substring(0, maxText) : plain.toString());
            }
        }
    }

    private void putOptionalLinkMeta(ObjectNode props, JsonNode node) {
        if (node.has("linkTarget")) {
            String target = node.path("linkTarget").asText("_self").trim().toLowerCase(Locale.ROOT);
            props.put("linkTarget", "_blank".equals(target) ? "_blank" : "_self");
        }
        if (node.has("linkTitle")) {
            props.put("linkTitle", text(node.get("linkTitle"), 120));
        }
    }

    private void putLinkColors(ObjectNode props, JsonNode node) {
        if (node.has("linkColor")) {
            props.put("linkColor", colour(node.get("linkColor")));
        }
        if (node.has("linkHoverColor")) {
            props.put("linkHoverColor", colour(node.get("linkHoverColor")));
        }
        if (node.has("linkUnderline")) {
            props.put("linkUnderline", flag(node.get("linkUnderline"), true));
        }
    }

    private String colour(JsonNode node) {
        String value = node == null ? "" : node.asText("").trim();
        if (value.isBlank()) {
            return "";
        }
        if (!value.matches("^#[0-9a-fA-F]{3,8}$")) {
            throw ApiException.badRequest("That colour is not allowed.");
        }
        return value;
    }

    private int flag(JsonNode node, boolean fallback) {
        if (node == null || node.isNull() || (node.isTextual() && node.asText("").isBlank())) {
            return fallback ? 1 : 0;
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? 1 : 0;
        }
        String value = node.asText("").trim().toLowerCase(Locale.ROOT);
        if ("0".equals(value) || "false".equals(value) || "no".equals(value)) {
            return 0;
        }
        if ("1".equals(value) || "true".equals(value) || "yes".equals(value)) {
            return 1;
        }
        return node.asInt(fallback ? 1 : 0) == 0 ? 0 : 1;
    }

    private ArrayNode richText(JsonNode node, int maxText) {
        ArrayNode out = mapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return out;
        }
        int total = 0;
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            rejectUnknown(item, Set.of("text", "href", "target", "title"), "rich text");
            String runText = text(item.get("text"), Math.min(400, maxText));
            if (runText.isBlank()) {
                continue;
            }
            if (total + runText.length() > maxText) {
                runText = runText.substring(0, Math.max(0, maxText - total));
                if (runText.isBlank()) {
                    break;
                }
            }
            total += runText.length();
            ObjectNode run = mapper.createObjectNode();
            run.put("text", runText);
            String href = link(item.get("href"));
            if (!href.isBlank()) {
                run.put("href", href);
                String target = item.path("target").asText("_self").trim().toLowerCase(Locale.ROOT);
                run.put("target", "_blank".equals(target) ? "_blank" : "_self");
                if (item.has("title")) {
                    run.put("title", text(item.get("title"), 120));
                }
            }
            out.add(run);
            if (out.size() >= 40) {
                break;
            }
        }
        return out;
    }

    private ArrayNode marqueeItems(JsonNode node) {
        ArrayNode out = mapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return out;
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            rejectUnknown(item, Set.of("id", "kind", "text", "richText", "mediaId", "alt", "href", "target", "title"), "marquee item");
            ObjectNode row = mapper.createObjectNode();
            String id = item.path("id").asText("").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            if (id.isBlank() || id.length() > 40 || !ids.add(id)) {
                id = "mq-" + Integer.toString(ids.size() + 1, 36);
                ids.add(id);
            }
            row.put("id", id);
            String kind = "image".equalsIgnoreCase(item.path("kind").asText("text")) ? "image" : "text";
            row.put("kind", kind);
            if ("image".equals(kind)) {
                String mediaId = item.path("mediaId").asText("");
                if (!mediaId.isBlank() && !UUID_TEXT.matcher(mediaId).matches()) {
                    throw ApiException.badRequest("Choose a picture from this event.");
                }
                row.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
                row.put("alt", text(item.get("alt"), 180));
            } else {
                ArrayNode runs = richText(item.get("richText"), 400);
                String plain = text(item.get("text"), 400);
                if (runs.isEmpty() && !plain.isBlank()) {
                    ObjectNode run = mapper.createObjectNode();
                    run.put("text", plain);
                    runs.add(run);
                }
                if (!runs.isEmpty()) {
                    row.set("richText", runs);
                    StringBuilder builder = new StringBuilder();
                    for (JsonNode run : runs) {
                        builder.append(run.path("text").asText(""));
                    }
                    row.put("text", builder.length() > 400 ? builder.substring(0, 400) : builder.toString());
                } else {
                    row.put("text", plain);
                }
            }
            String href = link(item.get("href"));
            if (!href.isBlank()) {
                row.put("href", href);
                String target = item.path("target").asText("_self").trim().toLowerCase(Locale.ROOT);
                row.put("target", "_blank".equals(target) ? "_blank" : "_self");
                if (item.has("title")) {
                    row.put("title", text(item.get("title"), 120));
                }
            }
            out.add(row);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> out = new HashSet<>(left);
        out.addAll(right);
        return out;
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
