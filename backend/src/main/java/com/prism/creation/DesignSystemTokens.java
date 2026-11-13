package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.typography.FontRegistry;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Allowlisted semantic design tokens stored under document.designTokens.
 * Empty/legacy documents keep empty colour/font/spacing maps for compatibility.
 */
public final class DesignSystemTokens {

    private static final Pattern HEX = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private static final Pattern SAFE_CSS = Pattern.compile("^(?!.*url\\s*\\()(?!.*expression\\s*\\()[\\w\\s%#.,()+*/\\-\"']{0,160}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COLOUR_ROLES = Set.of(
            "background", "surface", "surfaceAlt", "text", "mutedText", "primary", "secondary", "accent", "border", "overlay", "focus", "heading", "lightText");
    private static final Set<String> SPACING_KEYS = Set.of("xs", "sm", "md", "lg", "xl", "section", "gutter", "contentWidth");
    private static final Set<String> SHAPE_KEYS = Set.of("radius", "radiusLg", "radiusFull", "buttonRadius", "imageRadius", "borderStyle", "shadow", "elevation");
    private static final Set<String> IMAGE_KEYS = Set.of("aspectHero", "aspectCard", "objectFit", "overlay", "mask");
    private static final Set<String> MOTION_KEYS = Set.of("duration", "easing", "entrance", "reducedMotion");
    private static final Set<String> META_KEYS = Set.of("archetypeId", "version", "motionStrategy", "visualComplexity");

    private DesignSystemTokens() {
    }

    public static ObjectNode sanitize(ObjectMapper mapper, JsonNode input) {
        ObjectNode out = mapper.createObjectNode();
        ObjectNode colors = out.putObject("colors");
        ObjectNode fonts = out.putObject("fonts");
        ObjectNode spacing = out.putObject("spacing");
        ObjectNode shape = out.putObject("shape");
        ObjectNode image = out.putObject("image");
        ObjectNode motion = out.putObject("motion");
        ObjectNode meta = out.putObject("meta");
        if (input == null || !input.isObject()) {
            return out;
        }
        copyAllowed(colors, input.path("colors"), COLOUR_ROLES, true);
        copyFonts(fonts, input.path("fonts"));
        copyAllowed(spacing, input.path("spacing"), SPACING_KEYS, false);
        copyAllowed(shape, input.path("shape"), SHAPE_KEYS, false);
        copyAllowed(image, input.path("image"), IMAGE_KEYS, false);
        copyAllowed(motion, input.path("motion"), MOTION_KEYS, false);
        copyAllowed(meta, input.path("meta"), META_KEYS, false);
        if (meta.path("archetypeId").asText("").isBlank() && input.path("archetypeId").isTextual()) {
            meta.put("archetypeId", clip(input.path("archetypeId").asText(""), 64));
        }
        return out;
    }

    public static ObjectNode fromDirection(ObjectMapper mapper, DesignDirection direction, String archetypeId, JsonNode creative) {
        ObjectNode tokens = mapper.createObjectNode();
        ObjectNode colors = tokens.putObject("colors");
        colors.put("background", direction.pageBackground());
        colors.put("surface", direction.surface());
        colors.put("surfaceAlt", direction.alternateBackground());
        colors.put("text", direction.text());
        colors.put("mutedText", direction.muted());
        colors.put("primary", direction.accent());
        colors.put("secondary", direction.heading());
        colors.put("accent", direction.accent());
        colors.put("border", "#E5ECEC");
        colors.put("overlay", "rgba(9,30,39,0.45)");
        colors.put("focus", "#25B5AE");
        colors.put("heading", direction.heading());
        colors.put("lightText", direction.lightText());
        ObjectNode fonts = tokens.putObject("fonts");
        fonts.put("display", direction.displayFont());
        fonts.put("heading", direction.displayFont());
        fonts.put("body", direction.bodyFont());
        ObjectNode spacing = tokens.putObject("spacing");
        spacing.put("xs", "0.25rem");
        spacing.put("sm", "0.5rem");
        spacing.put("md", "1rem");
        spacing.put("lg", "1.5rem");
        spacing.put("xl", "2.5rem");
        spacing.put("section", "72px");
        spacing.put("gutter", "1.25rem");
        spacing.put("contentWidth", "1120px");
        ObjectNode shape = tokens.putObject("shape");
        shape.put("radius", direction.radius());
        shape.put("radiusLg", direction.radius());
        shape.put("buttonRadius", "0".equals(direction.radius()) ? "0" : "999px");
        shape.put("imageRadius", direction.imageRadius());
        shape.put("shadow", direction.shadow());
        shape.put("borderStyle", "solid");
        ObjectNode image = tokens.putObject("image");
        image.put("aspectHero", "16 / 9");
        image.put("aspectCard", "4 / 3");
        image.put("objectFit", "cover");
        image.put("overlay", direction.heroDark() ? "rgba(9,30,39,0.35)" : "transparent");
        ObjectNode motion = tokens.putObject("motion");
        String strategy = creative == null ? "subtle" : creative.path("motionStrategy").asText("subtle");
        motion.put("duration", "280ms");
        motion.put("easing", "cubic-bezier(0.22, 1, 0.36, 1)");
        motion.put("entrance", strategy);
        motion.put("reducedMotion", "reduce");
        ObjectNode meta = tokens.putObject("meta");
        meta.put("archetypeId", archetypeId == null ? "" : archetypeId);
        meta.put("version", "webaux-creative-v2");
        meta.put("motionStrategy", strategy);
        if (creative != null && creative.path("visualComplexity").isTextual()) {
            meta.put("visualComplexity", creative.path("visualComplexity").asText("balanced"));
        }
        return sanitize(mapper, tokens);
    }

    public static String resolveColor(JsonNode tokens, String role, String fallback) {
        String value = tokens == null ? "" : tokens.path("colors").path(role).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static void copyAllowed(ObjectNode target, JsonNode source, Set<String> keys, boolean colour) {
        if (source == null || !source.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!keys.contains(entry.getKey())) {
                continue;
            }
            String value = entry.getValue().asText("").trim();
            if (value.isBlank() || value.length() > 160) {
                continue;
            }
            if (colour) {
                if (HEX.matcher(value).matches() || value.startsWith("rgba(") || value.startsWith("rgb(") || value.startsWith("linear-gradient(")) {
                    if (!value.toLowerCase(Locale.ROOT).contains("url(")) {
                        target.put(entry.getKey(), value);
                    }
                }
            } else if (SAFE_CSS.matcher(value).matches()) {
                target.put(entry.getKey(), value);
            }
        }
    }

    private static void copyFonts(ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        for (String key : Set.of("display", "heading", "body", "accent")) {
            String family = source.path(key).asText("").trim();
            if (family.isBlank()) {
                continue;
            }
            if (FontRegistry.knownFamily(family) || "inherit".equals(family)) {
                target.put(key, family);
            }
        }
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
