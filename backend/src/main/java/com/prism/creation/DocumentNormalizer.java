package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.editor.DocumentValidator;
import com.prism.typography.FontRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DocumentNormalizer {

    private static final Set<String> TYPES = Set.of(
            "SECTION", "CONTAINER", "FLEX", "GRID", "GROUP", "CAROUSEL", "MARQUEE",
            "HEADING", "PARAGRAPH", "BUTTON", "IMAGE", "SPACER", "DIVIDER",
            "QUOTE", "COUNTDOWN", "FAQ_ACCORDION", "FAQ_ITEM", "TIMELINE", "TIMELINE_ITEM");
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
    private static final Pattern ID = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final ObjectMapper mapper;
    private int sequence;

    public DocumentNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode normalize(JsonNode input, Set<String> mediaIds, List<String> warnings) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("The model did not return a document object.");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("schemaVersion", DocumentValidator.SCHEMA);
        String documentId = input.path("documentId").asText("");
        out.put("documentId", UUID_TEXT.matcher(documentId).matches() ? documentId.toLowerCase(Locale.ROOT) : UUID.randomUUID().toString());
        ObjectNode page = out.putObject("page");
        page.put("title", clip(input.path("page").path("title").asText("Untitled website"), 160));
        page.put("description", clip(input.path("page").path("description").asText(""), 300));
        out.set("breakpoints", mapper.createObjectNode()
                .put("desktop", com.prism.editor.ResponsiveBreakpoints.DESKTOP_MIN)
                .put("tablet", com.prism.editor.ResponsiveBreakpoints.TABLET_MIN)
                .put("mobile", com.prism.editor.ResponsiveBreakpoints.MOBILE_MAX));
        out.set("designTokens", DesignSystemTokens.sanitize(mapper, input.get("designTokens")));
        if (input.path("creative").isObject()) {
            out.set("creative", input.get("creative").deepCopy());
        }
        JsonNode root = input.get("root");
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("The document has no root section.");
        }
        Set<String> ids = new HashSet<>();
        ObjectNode cleaned = element(root, true, ids, mediaIds, warnings);
        if (cleaned == null || !"SECTION".equals(cleaned.path("type").asText())) {
            throw new IllegalArgumentException("The root must be a section.");
        }
        out.set("root", cleaned);
        return out;
    }

    private ObjectNode element(JsonNode node, boolean root, Set<String> ids, Set<String> mediaIds, List<String> warnings) {
        String type = node.path("type").asText("");
        if (!TYPES.contains(type)) {
            if (!type.isBlank()) {
                warnings.add("Omitted unsupported element " + type + ".");
            }
            return null;
        }
        if (root && !"SECTION".equals(type)) {
            throw new IllegalArgumentException("The root must be a section.");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("id", uniqueId(node.path("id").asText(""), ids));
        out.put("type", type);
        out.put("name", clip(node.path("name").asText(type), 80));
        out.set("props", props(type, node.get("props"), mediaIds, warnings));
        out.set("styles", styles(node.get("styles"), mediaIds, warnings));
        ObjectNode responsive = out.putObject("responsiveStyles");
        responsive.set("tablet", styles(node.path("responsiveStyles").get("tablet"), mediaIds, warnings));
        responsive.set("mobile", styles(node.path("responsiveStyles").get("mobile"), mediaIds, warnings));
        ObjectNode responsiveProps = out.putObject("responsiveProps");
        responsiveProps.set("tablet", partialProps(type, node.path("responsiveProps").get("tablet"), mediaIds, warnings));
        responsiveProps.set("mobile", partialProps(type, node.path("responsiveProps").get("mobile"), mediaIds, warnings));
        ArrayNode children = out.putArray("children");
        if (node.path("children").isArray()) {
            for (JsonNode child : node.path("children")) {
                if (child != null && child.isObject()) {
                    ObjectNode cleaned = element(child, false, ids, mediaIds, warnings);
                    if (cleaned != null) {
                        children.add(cleaned);
                    }
                }
            }
        }
        return out;
    }

    private ObjectNode props(String type, JsonNode node, Set<String> mediaIds, List<String> warnings) {
        ObjectNode props = mapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : mapper.createObjectNode();
        switch (type) {
            case "HEADING" -> {
                props.put("text", copy(source, 400));
                int level = source.path("level").asInt(2);
                props.put("level", Math.min(6, Math.max(1, level)));
                copyLinks(props, source, 400);
            }
            case "PARAGRAPH" -> {
                props.put("text", copy(source, 4000));
                copyLinks(props, source, 4000);
            }
            case "BUTTON" -> {
                props.put("text", copy(source, 160));
                props.put("href", link(source.path("href").asText("")));
                copyLinkMeta(props, source);
            }
            case "MARQUEE" -> {
                props.put("text", clip(source.path("text").asText("Announcement"), 800));
                String direction = source.path("direction").asText("left").trim().toLowerCase(Locale.ROOT);
                props.put("direction", MARQUEE_DIRS.contains(direction) ? direction : "left");
                props.put("speed", bounded(source.get("speed"), 50, 1, 100));
                props.put("duration", bounded(source.get("duration"), 28, 4, 120));
                props.put("gap", bounded(source.get("gap"), 32, 0, 160));
                props.put("delay", bounded(source.get("delay"), 0, 0, 30));
                props.put("loop", source.path("loop").asText("1").startsWith("0") ? 0 : 1);
                props.put("pauseOnHover", source.path("pauseOnHover").asText("1").startsWith("0") ? 0 : 1);
                props.put("pauseOnClick", source.path("pauseOnClick").asText("0").startsWith("1") ? 1 : 0);
                props.put("animationEnabled", source.path("animationEnabled").asText("1").startsWith("0") ? 0 : 1);
                props.set("items", normalizeMarqueeItems(source.get("items"), mediaIds, warnings));
                copyLinks(props, source, 800);
            }
            case "IMAGE" -> {
                String mediaId = source.path("mediaId").asText("").toLowerCase(Locale.ROOT);
                if (!mediaId.isBlank() && !mediaIds.contains(mediaId)) {
                    warnings.add("Cleared an image that is not one of the uploaded files.");
                    mediaId = "";
                }
                props.put("mediaId", mediaId);
                props.put("alt", clip(source.path("alt").asText(""), 180));
                String assetKey = source.path("assetKey").asText("").trim().toLowerCase(Locale.ROOT);
                if (!assetKey.isBlank()) {
                    props.put("assetKey", assetKey.length() > 80 ? assetKey.substring(0, 80) : assetKey);
                }
            }
            case "SPACER" -> props.put("size", Math.min(240, Math.max(4, source.path("size").asInt(24))));
            case "CAROUSEL" -> {
                String preset = source.path("preset").asText("minimal").trim().toLowerCase(Locale.ROOT);
                props.put("preset", CAROUSEL_PRESETS.contains(preset) ? preset : "minimal");
                props.put("autoplay", source.path("autoplay").asText("1").startsWith("0") ? 0 : 1);
                props.put("interval", bounded(source.get("interval"), 5000, 2000, 20000));
                props.put("perView", bounded(source.get("perView"), 1, 1, 3));
            }
            case "QUOTE" -> {
                props.put("text", clip(source.path("text").asText(""), 800));
                props.put("attribution", clip(source.path("attribution").asText(""), 160));
                copyLinks(props, source, 800);
            }
            case "COUNTDOWN" -> {
                props.put("target", clip(source.path("target").asText(""), 40));
                props.put("label", clip(source.path("label").asText(""), 120));
            }
            case "FAQ_ACCORDION", "TIMELINE" -> props.put("title", clip(source.path("title").asText(""), 160));
            case "FAQ_ITEM", "TIMELINE_ITEM" -> {
                props.put("title", clip(source.path("title").asText(""), 200));
                props.put("text", clip(source.path("text").asText(""), 2000));
                copyLinks(props, source, 2000);
            }
            default -> {
            }
        }
        return props;
    }

    private ObjectNode partialProps(String type, JsonNode node, Set<String> mediaIds, List<String> warnings) {
        ObjectNode props = mapper.createObjectNode();
        if (node == null || !node.isObject()) {
            return props;
        }
        switch (type) {
            case "IMAGE" -> {
                if (node.has("mediaId")) {
                    String mediaId = node.path("mediaId").asText("").toLowerCase(Locale.ROOT);
                    if (!mediaId.isBlank() && !mediaIds.contains(mediaId)) {
                        warnings.add("Cleared an image that is not one of the uploaded files.");
                        mediaId = "";
                    }
                    props.put("mediaId", mediaId);
                }
                if (node.has("alt")) {
                    props.put("alt", clip(node.path("alt").asText(""), 180));
                }
                if (node.has("assetKey")) {
                    String assetKey = node.path("assetKey").asText("").trim().toLowerCase(Locale.ROOT);
                    if (!assetKey.isBlank()) {
                        props.put("assetKey", assetKey.length() > 80 ? assetKey.substring(0, 80) : assetKey);
                    }
                }
            }
            case "SPACER" -> {
                if (node.has("size")) {
                    props.put("size", Math.min(240, Math.max(4, node.path("size").asInt(24))));
                }
            }
            case "CAROUSEL" -> {
                if (node.has("preset")) {
                    String preset = node.path("preset").asText("minimal").trim().toLowerCase(Locale.ROOT);
                    props.put("preset", CAROUSEL_PRESETS.contains(preset) ? preset : "minimal");
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
                if (node.has("text")) {
                    props.put("text", clip(node.path("text").asText(""), 800));
                }
                if (node.has("attribution")) {
                    props.put("attribution", clip(node.path("attribution").asText(""), 160));
                }
            }
            case "COUNTDOWN" -> {
                if (node.has("target")) {
                    props.put("target", clip(node.path("target").asText(""), 40));
                }
                if (node.has("label")) {
                    props.put("label", clip(node.path("label").asText(""), 120));
                }
            }
            case "FAQ_ACCORDION", "TIMELINE" -> {
                if (node.has("title")) {
                    props.put("title", clip(node.path("title").asText(""), 160));
                }
            }
            case "FAQ_ITEM", "TIMELINE_ITEM" -> {
                if (node.has("title")) {
                    props.put("title", clip(node.path("title").asText(""), 200));
                }
                if (node.has("text")) {
                    props.put("text", clip(node.path("text").asText(""), 2000));
                }
            }
            default -> {
            }
        }
        return props;
    }

    private ObjectNode styles(JsonNode node, Set<String> mediaIds, List<String> warnings) {
        ObjectNode styles = mapper.createObjectNode();
        if (node == null || !node.isObject()) {
            return styles;
        }
        node.fieldNames().forEachRemaining(key -> {
            if (!STYLES.contains(key)) {
                return;
            }
            String value = node.get(key).asText("");
            int limit = "background".equals(key) || "boxShadow".equals(key) || "filter".equals(key) || "border".equals(key) || "textShadow".equals(key) || "textGradient".equals(key) || "fontVariationSettings".equals(key) ? 180 : "fontSize".equals(key) ? 120 : 80;
            if (value.length() > limit || value.indexOf('<') >= 0 || value.indexOf(';') >= 0) {
                return;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.contains("url(") || lower.contains("expression(") || lower.contains("javascript")) {
                return;
            }
            if ("fontFamily".equals(key)) {
                value = FontRegistry.normalizeFamily(value, warnings);
            }
            if ("transform".equals(key) && !value.isBlank() && !value.matches("^(rotate\\(-?\\d{1,3}deg\\)|translate\\(-?\\d{1,4}px, -?\\d{1,4}px\\)|scale\\([0-9.]+(?:, [0-9.]+)?\\))( (rotate\\(-?\\d{1,3}deg\\)|translate\\(-?\\d{1,4}px, -?\\d{1,4}px\\)|scale\\([0-9.]+(?:, [0-9.]+)?\\)))*$")) {
                return;
            }
            if ("backgroundMediaId".equals(key)) {
                value = value.toLowerCase(Locale.ROOT);
                if (!value.isBlank() && (!UUID_TEXT.matcher(value).matches() || !mediaIds.contains(value))) {
                    warnings.add("Omitted a background picture that is not on this event.");
                    return;
                }
            }
            styles.put(key, value);
        });
        FontRegistry.reconcile(styles);
        return styles;
    }

    private void copyLinks(ObjectNode props, JsonNode source, int maxText) {
        if (source.has("href")) {
            props.put("href", link(source.path("href").asText("")));
        }
        copyLinkMeta(props, source);
        if (source.has("linkColor")) {
            String color = source.path("linkColor").asText("").trim();
            if (color.matches("^#[0-9a-fA-F]{3,8}$")) {
                props.put("linkColor", color);
            }
        }
        if (source.has("linkHoverColor")) {
            String color = source.path("linkHoverColor").asText("").trim();
            if (color.matches("^#[0-9a-fA-F]{3,8}$")) {
                props.put("linkHoverColor", color);
            }
        }
        if (source.has("linkUnderline")) {
            props.put("linkUnderline", source.path("linkUnderline").asText("1").startsWith("0") ? 0 : 1);
        }
        if (source.has("richText") && source.get("richText").isArray()) {
            props.set("richText", normalizeRichText(source.get("richText"), maxText));
        }
    }

    private void copyLinkMeta(ObjectNode props, JsonNode source) {
        if (source.has("linkTarget")) {
            String target = source.path("linkTarget").asText("_self").trim().toLowerCase(Locale.ROOT);
            props.put("linkTarget", "_blank".equals(target) ? "_blank" : "_self");
        }
        if (source.has("linkTitle")) {
            props.put("linkTitle", clip(source.path("linkTitle").asText(""), 120));
        }
    }

    private ArrayNode normalizeRichText(JsonNode node, int maxText) {
        ArrayNode out = mapper.createArrayNode();
        int total = 0;
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String runText = clip(item.path("text").asText(""), Math.min(400, maxText));
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
            String href = link(item.path("href").asText(""));
            if (!href.isBlank()) {
                run.put("href", href);
                String target = item.path("target").asText("_self").trim().toLowerCase(Locale.ROOT);
                run.put("target", "_blank".equals(target) ? "_blank" : "_self");
                if (item.has("title")) {
                    run.put("title", clip(item.path("title").asText(""), 120));
                }
            }
            out.add(run);
            if (out.size() >= 40) {
                break;
            }
        }
        return out;
    }

    private ArrayNode normalizeMarqueeItems(JsonNode node, Set<String> mediaIds, List<String> warnings) {
        ArrayNode out = mapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return out;
        }
        int index = 0;
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode row = mapper.createObjectNode();
            String id = item.path("id").asText("").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            if (id.isBlank() || id.length() > 40) {
                id = "mq-" + (++index);
            }
            row.put("id", id);
            String kind = "image".equalsIgnoreCase(item.path("kind").asText("text")) ? "image" : "text";
            row.put("kind", kind);
            if ("image".equals(kind)) {
                String mediaId = item.path("mediaId").asText("").toLowerCase(Locale.ROOT);
                if (!mediaId.isBlank() && !mediaIds.contains(mediaId)) {
                    warnings.add("Cleared a marquee image that is not one of the uploaded files.");
                    mediaId = "";
                }
                row.put("mediaId", mediaId);
                row.put("alt", clip(item.path("alt").asText(""), 180));
            } else {
                ArrayNode runs = item.has("richText") ? normalizeRichText(item.get("richText"), 400) : mapper.createArrayNode();
                String plain = clip(item.path("text").asText(""), 400);
                if (runs.isEmpty() && !plain.isBlank()) {
                    ObjectNode run = mapper.createObjectNode();
                    run.put("text", plain);
                    runs.add(run);
                }
                row.set("richText", runs);
                StringBuilder builder = new StringBuilder();
                for (JsonNode run : runs) {
                    builder.append(run.path("text").asText(""));
                }
                row.put("text", builder.length() > 0 ? clip(builder.toString(), 400) : plain);
            }
            String href = link(item.path("href").asText(""));
            if (!href.isBlank()) {
                row.put("href", href);
                String target = item.path("target").asText("_self").trim().toLowerCase(Locale.ROOT);
                row.put("target", "_blank".equals(target) ? "_blank" : "_self");
                if (item.has("title")) {
                    row.put("title", clip(item.path("title").asText(""), 120));
                }
            }
            out.add(row);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private String link(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank() || trimmed.length() > 400) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return "";
        }
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:") || lower.startsWith("tel:") || trimmed.startsWith("#") || trimmed.startsWith("/")) {
            return trimmed;
        }
        return "";
    }

    private String uniqueId(String raw, Set<String> ids) {
        String id = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        if (!ID.matcher(id).matches()) {
            id = "n" + (++sequence);
        }
        while (!ids.add(id)) {
            id = "n" + (++sequence);
        }
        return id;
    }

    private String copy(JsonNode source, int max) {
        for (String key : java.util.List.of("text", "content", "value", "copy", "body")) {
            String value = source.path(key).asText("");
            if (!value.isBlank()) {
                return clip(value, max);
            }
        }
        return "";
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

    private String clip(String value, int max) {
        String text = value == null ? "" : value;
        if (text.toLowerCase(Locale.ROOT).contains("<script") || text.toLowerCase(Locale.ROOT).contains("javascript:")) {
            text = "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
