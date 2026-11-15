package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DesignApplicator {

    private DesignApplicator() {
    }

    public static ObjectNode apply(ObjectNode document, DesignDirection direction) {
        if (document == null || direction == null) {
            return document;
        }
        ObjectNode copy = document.deepCopy();
        if (copy.path("root") instanceof ObjectNode root) {
            paint(root, direction, new int[]{0}, false, false);
            repairTextEllipses(root, direction);
        }
        return copy;
    }

    private static void paint(ObjectNode node, DesignDirection direction, int[] sectionCount, boolean dark, boolean split) {
        String type = node.path("type").asText("");
        ObjectNode styles = node.with("styles");
        releaseOffsets(styles);
        if ("SECTION".equals(type)) {
            int index = sectionCount[0]++;
            boolean hero = index == 0;
            dark = hero ? direction.heroDark() : direction.pageDark();
            styles.put("background", hero ? direction.heroBackground() : (index % 2 == 0 ? direction.alternateBackground() : direction.pageBackground()));
            styles.put("color", dark ? direction.lightText() : direction.text());
            styles.put("padding", hero ? "88px 24px 72px" : "72px 24px");
            styles.put("textAlign", "center");
            if (styles.path("fontFamily").asText("").isBlank()) {
                styles.put("fontFamily", direction.bodyFont());
            }
        } else if ("HEADING".equals(type)) {
            styles.put("fontFamily", direction.displayFont());
            styles.put("color", dark ? direction.lightText() : direction.heading());
            styles.put("fontWeight", "500");
            styles.put("lineHeight", "1.08");
            int level = node.path("props").path("level").asInt(2);
            styles.put("fontSize", level <= 1 ? "64px" : level == 2 ? "36px" : "22px");
            if (level <= 1) {
                styles.put("letterSpacing", "-0.03em");
            }
            styles.put("textAlign", split ? "left" : "center");
        } else if ("PARAGRAPH".equals(type)) {
            styles.put("fontFamily", direction.bodyFont());
            styles.put("color", dark ? direction.lightText() : direction.text());
            styles.put("fontSize", "18px");
            styles.put("lineHeight", "1.65");
            styles.put("fontWeight", "400");
            styles.put("textAlign", split ? "left" : "center");
        } else if ("BUTTON".equals(type)) {
            styles.put("fontFamily", direction.bodyFont());
            styles.put("background", direction.accent());
            styles.put("color", contrastText(direction.accent(), direction));
            styles.put("borderRadius", "0".equals(direction.radius()) ? "0" : "999px");
            styles.put("padding", "14px 28px");
            if (!"none".equals(direction.shadow())) {
                styles.put("boxShadow", direction.shadow());
            }
        } else if ("IMAGE".equals(type)) {
            styles.put("borderRadius", direction.imageRadius());
            styles.put("objectFit", "cover");
        } else if ("GROUP".equals(type) || "CONTAINER".equals(type)) {
            styles.put("background", direction.surface());
            styles.put("color", direction.pageDark() ? direction.lightText() : direction.text());
            styles.put("borderRadius", DesignDirection.cardRadius(direction.radius()));
            styles.put("boxShadow", direction.shadow());
            styles.put("padding", "32px 28px");
            styles.put("textAlign", "center");
            styles.put("overflow", "hidden");
            if (styles.path("display").asText("").isBlank()) {
                styles.put("display", "flex");
                styles.put("flexDirection", "column");
                styles.put("gap", "12px");
                styles.put("justifyContent", "center");
                styles.put("alignItems", "center");
            }
        } else if ("FLEX".equals(type) || "GRID".equals(type)) {
            styles.put("display", "FLEX".equals(type) ? "flex" : "grid");
            if (styles.path("alignItems").asText("").isBlank()) {
                styles.put("alignItems", "center");
            }
            if (styles.path("gap").asText("").isBlank()) {
                styles.put("gap", "28px");
            }
            if ("FLEX".equals(type) && styles.path("flexWrap").asText("").isBlank()) {
                styles.put("flexWrap", "wrap");
            }
            if ("GRID".equals(type) && styles.path("gridTemplateColumns").asText("").isBlank()) {
                styles.put("gridTemplateColumns", "repeat(auto-fit, minmax(240px, 1fr))");
            }
        } else if ("DIVIDER".equals(type)) {
            styles.put("background", direction.accent());
            styles.put("height", "1px");
            styles.put("opacity", "0.7");
        } else if ("COUNTDOWN".equals(type)) {
            styles.put("fontFamily", direction.displayFont());
            styles.put("color", dark ? direction.lightText() : direction.text());
            styles.put("textAlign", split ? "left" : "center");
            styles.put("display", "flex");
            styles.put("flexDirection", "column");
            styles.put("gap", "12px");
            styles.put("padding", "16px 0");
            if (styles.path("fontSize").asText("").isBlank()) {
                styles.put("fontSize", "18px");
            }
        } else if ("QUOTE".equals(type)) {
            styles.put("fontFamily", direction.displayFont());
            styles.put("color", dark ? direction.lightText() : direction.heading());
            styles.put("textAlign", split ? "left" : "center");
        }
        centerFixedBox(styles);
        boolean childDark = "GROUP".equals(type) || "CONTAINER".equals(type) ? direction.pageDark() : dark;
        // Card bodies stay centred so copy sits in the widest part of a rounded panel.
        boolean childSplit = ("GROUP".equals(type) || "CONTAINER".equals(type))
                ? false
                : split || "GRID".equals(type) || ("FLEX".equals(type) && row(styles));
        for (int i = 0; i < node.path("children").size(); i++) {
            if (node.path("children").get(i) instanceof ObjectNode child) {
                paint(child, direction, sectionCount, childDark, childSplit);
            }
        }
    }

    /**
     * Convert circular/elliptical text panels into readable rounded cards.
     * Circles remain valid for IMAGE-only decorative nodes.
     */
    static void repairTextEllipses(ObjectNode node, DesignDirection direction) {
        if (node == null) {
            return;
        }
        String type = node.path("type").asText("");
        ObjectNode styles = node.with("styles");
        String radius = styles.path("borderRadius").asText("");
        boolean textPanel = holdsReadableText(node) && !"IMAGE".equals(type) && !"BUTTON".equals(type);
        if (textPanel && DesignDirection.isEllipseRadius(radius)) {
            styles.put("borderRadius", DesignDirection.cardRadius(direction == null ? "24px" : direction.radius()));
            String padding = styles.path("padding").asText("");
            if (padding.isBlank() || thinPadding(padding)) {
                styles.put("padding", "36px 32px");
            }
            styles.put("textAlign", "center");
            styles.put("overflow", "hidden");
            if (styles.path("display").asText("").isBlank()) {
                styles.put("display", "flex");
                styles.put("flexDirection", "column");
                styles.put("justifyContent", "center");
                styles.put("alignItems", "center");
                styles.put("gap", "12px");
            }
            centerTextDescendants(node);
        }
        for (JsonNode child : node.path("children")) {
            if (child instanceof ObjectNode object) {
                repairTextEllipses(object, direction);
            }
        }
    }

    private static void centerTextDescendants(ObjectNode node) {
        for (JsonNode child : node.path("children")) {
            if (!(child instanceof ObjectNode object)) {
                continue;
            }
            String type = object.path("type").asText("");
            if ("HEADING".equals(type) || "PARAGRAPH".equals(type) || "QUOTE".equals(type)) {
                object.with("styles").put("textAlign", "center");
            }
            centerTextDescendants(object);
        }
    }

    private static boolean holdsReadableText(JsonNode node) {
        String type = node.path("type").asText("");
        if ("HEADING".equals(type) || "PARAGRAPH".equals(type) || "QUOTE".equals(type) || "FAQ_ITEM".equals(type) || "TIMELINE_ITEM".equals(type)) {
            return !node.path("props").path("text").asText("").isBlank() || !node.path("props").path("title").asText("").isBlank();
        }
        for (JsonNode child : node.path("children")) {
            if (holdsReadableText(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean thinPadding(String padding) {
        String[] parts = padding.trim().split("\\s+");
        for (String part : parts) {
            try {
                double value = Double.parseDouble(part.replace("px", "").replace("%", ""));
                if (part.contains("%")) {
                    if (value < 12) {
                        return true;
                    }
                } else if (value < 24) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static void releaseOffsets(ObjectNode styles) {
        styles.remove("position");
        styles.remove("left");
        styles.remove("top");
        styles.remove("right");
        styles.remove("bottom");
        styles.remove("alignSelf");
        if (pinned(styles.path("marginLeft").asText(""))) {
            styles.remove("marginLeft");
        }
        if (pinned(styles.path("marginRight").asText(""))) {
            styles.remove("marginRight");
        }
    }

    private static boolean pinned(String value) {
        return "0".equals(value) || "0px".equals(value) || "0%".equals(value);
    }

    private static boolean row(ObjectNode styles) {
        String direction = styles.path("flexDirection").asText("row");
        return !"column".equals(direction) && !"column-reverse".equals(direction);
    }

    private static void centerFixedBox(ObjectNode styles) {
        if (!"center".equals(styles.path("textAlign").asText(""))) {
            return;
        }
        String width = styles.path("width").asText("");
        if (width.isBlank() || "100%".equals(width) || "auto".equals(width)) {
            return;
        }
        styles.put("marginLeft", "auto");
        styles.put("marginRight", "auto");
    }

    private static String contrastText(String background, DesignDirection direction) {
        return luminance(background) > 0.45 ? direction.text() : direction.lightText();
    }

    static double luminance(String colour) {
        String hex = colour == null ? "" : colour.trim();
        if (hex.startsWith("linear-gradient")) {
            int hash = hex.indexOf('#');
            hex = hash >= 0 ? hex.substring(hash) : "";
        }
        if (!hex.startsWith("#")) {
            return 0.5;
        }
        String body = hex.substring(1);
        if (body.length() == 3) {
            body = "" + body.charAt(0) + body.charAt(0) + body.charAt(1) + body.charAt(1) + body.charAt(2) + body.charAt(2);
        }
        if (body.length() < 6) {
            return 0.5;
        }
        try {
            int red = Integer.parseInt(body.substring(0, 2), 16);
            int green = Integer.parseInt(body.substring(2, 4), 16);
            int blue = Integer.parseInt(body.substring(4, 6), 16);
            return (0.2126 * channel(red)) + (0.7152 * channel(green)) + (0.0722 * channel(blue));
        } catch (NumberFormatException exception) {
            return 0.5;
        }
    }

    private static double channel(int value) {
        double scaled = value / 255.0;
        return scaled <= 0.03928 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    }
}
