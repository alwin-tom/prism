package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.typography.FontRegistry;

public final class PrismSchemas {

    public static final String PROMPT_VERSION = "prism-website-6";
    public static final String PROMPT_VERSION_V2 = CreativeSchemas.PROMPT_VERSION;
    public static final String PLAN_SCHEMA = "prism_website_plan";
    public static final String DOCUMENT_SCHEMA = "prism_website_document";

    private PrismSchemas() {
    }

    public static JsonNode plan(ObjectMapper mapper) {
        ObjectNode schema = (ObjectNode) read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["title", "description", "style", "voice", "sections", "direction", "concept", "mood", "patterns"],
                  "properties": {
                    "title": {"type": "string"},
                    "description": {"type": "string", "description": "One or two sentences in the selected tone, using only supplied facts."},
                    "style": {"type": "string", "description": "The selected style or theme value, such as formal or romantic."},
                    "voice": {"type": "string", "description": "Two sentences on how every heading and paragraph should sound. Match the selected tone."},
                    "sections": {"type": "array", "items": {"type": "string"}},
                    "direction": {"type": "string", "enum": ["luxury-romantic", "modern-editorial", "cinematic-dark", "elegant-minimal", "playful-celebration", "pastel-dream", "modern-corporate", "premium-business", "creative-portfolio", "botanical-natural", "vintage-classic", "bold-contemporary", "scandinavian-minimal", "art-deco", "futuristic-gradient"]},
                    "concept": {"type": "string", "description": "A short art-direction name."},
                    "mood": {"type": "string", "description": "One sentence on the atmosphere."},
                    "patterns": {"type": "array", "items": {"type": "string", "enum": ["full-width-hero", "split-hero", "centered-editorial", "image-led", "asymmetrical-columns", "floating-cards", "gradient-band", "alternating-image-text", "carousel", "timeline", "stacked-editorial"]}}
                  }
                }
                """);
        FontRegistry.attachTypography((ObjectNode) schema.path("properties"));
        return schema;
    }

    public static JsonNode document(ObjectMapper mapper) {
        ObjectNode schema = (ObjectNode) read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["schemaVersion", "documentId", "page", "root"],
                  "properties": {
                    "schemaVersion": {"type": "integer", "const": 1},
                    "documentId": {"type": "string"},
                    "page": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["title", "description"],
                      "properties": {
                        "title": {"type": "string"},
                        "description": {"type": "string"}
                      }
                    },
                    "root": {"$ref": "#/$defs/element"}
                  },
                  "$defs": {
                    "element": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["id", "type", "name", "props", "styles", "children"],
                      "properties": {
                        "id": {"type": "string"},
                        "type": {"type": "string", "enum": ["SECTION", "CONTAINER", "FLEX", "GRID", "GROUP", "CAROUSEL", "HEADING", "PARAGRAPH", "BUTTON", "IMAGE", "SPACER", "DIVIDER", "QUOTE", "COUNTDOWN", "FAQ_ACCORDION", "FAQ_ITEM", "TIMELINE", "TIMELINE_ITEM"]},
                        "name": {"type": "string"},
                        "props": {
                          "type": "object",
                          "additionalProperties": false,
                          "required": ["text", "level", "href", "mediaId", "alt", "size", "preset", "autoplay", "interval", "perView"],
                          "properties": {
                            "text": {"type": "string", "description": "Visitor-facing copy in the selected tone. A heading is a short title. A paragraph is two to four complete sentences that weave in the supplied facts, never a labelled list. Empty only for layout elements."},
                            "level": {"type": "integer"},
                            "href": {"type": "string"},
                            "mediaId": {"type": "string"},
                            "alt": {"type": "string"},
                            "size": {"type": "integer"},
                            "preset": {"type": "string", "description": "Carousel preset: minimal, cards, hero, coverflow, fade, or editorial. Empty on every other element."},
                            "autoplay": {"type": "string", "description": "1 or 0. Empty except on a carousel."},
                            "interval": {"type": "string", "description": "Carousel interval in milliseconds, such as 5000. Empty except on a carousel."},
                            "perView": {"type": "string", "description": "1, 2, or 3 visible slides. Empty except on a carousel."}
                          }
                        },
                        "styles": {
                          "type": "object",
                          "additionalProperties": false,
                          "required": ["color", "background", "fontFamily", "fontSize", "textAlign", "padding", "borderRadius", "boxShadow"],
                          "properties": {
                            "color": {"type": "string"},
                            "background": {"type": "string", "description": "A hex colour or a linear-gradient without url()."},
                            "fontFamily": {"type": "string"},
                            "fontSize": {"type": "string"},
                            "textAlign": {"type": "string"},
                            "padding": {"type": "string"},
                            "borderRadius": {"type": "string"},
                            "boxShadow": {"type": "string"}
                          }
                        },
                        "children": {"type": "array", "items": {"$ref": "#/$defs/element"}}
                      }
                    }
                  }
                }
                """);
        FontRegistry.attachTypography((ObjectNode) schema.path("properties"));
        return schema;
    }

    private static JsonNode read(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("The generation schema could not be read.");
        }
    }
}
