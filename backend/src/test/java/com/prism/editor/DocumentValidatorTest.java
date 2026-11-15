package com.prism.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentValidator validator = new DocumentValidator(mapper);

    @Test
    void keepsANestedPageAndRejectsAScript() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": "Wedding"},
                  "root": {
                    "id": "page",
                    "type": "SECTION",
                    "name": "Page",
                    "props": {},
                    "styles": {"display": "flex", "flex": "1 1 0%"},
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Anna", "level": 1}, "styles": {}, "children": []}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("Anna", clean.path("root").path("children").get(0).path("props").path("text").asText());
        assertEquals("1 1 0%", clean.path("root").path("styles").path("flex").asText());
        ((ObjectNode) document.path("root").path("children").get(0).path("props")).put("text", "<script>alert(1)</script>");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void rejectsAnUnknownElementAndAJavascriptLink() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {},
                    "children": [
                      {"id": "go", "type": "BUTTON", "name": "Button", "props": {"text": "Go", "href": "javascript:alert(1)"}, "styles": {}, "children": []}
                    ]
                  }
                }
                """);
        assertThrows(ApiException.class, () -> validator.sanitize(document));
        ((ObjectNode) document.path("root").path("children").get(0)).put("type", "RAW_HTML");
        ((ObjectNode) document.path("root").path("children").get(0).path("props")).put("href", "https://example.com");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void keepsRotationAndRejectsAScriptTransform() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {},
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "locked": true, "props": {"text": "Anna", "level": 1}, "styles": {"transform": "rotate(12deg)", "left": "8px", "top": "4px", "position": "relative"}, "children": []}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("rotate(12deg)", clean.path("root").path("children").get(0).path("styles").path("transform").asText());
        assertEquals(true, clean.path("root").path("children").get(0).path("locked").asBoolean());
        ((ObjectNode) document.path("root").path("children").get(0).path("styles")).put("transform", "rotate(12deg) url(javascript:alert(1))");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void keepsAGradientAndACarouselPreset() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {},
                    "styles": {"background": "linear-gradient(160deg, #1b6c80, #f4efe8)", "borderRadius": "16px", "boxShadow": "0 8px 24px rgba(18,20,23,0.12)"},
                    "children": [
                      {"id": "photos", "type": "CAROUSEL", "name": "Photos", "props": {"preset": "coverflow", "autoplay": 1, "interval": 5000, "perView": 2}, "styles": {}, "children": [
                        {"id": "one", "type": "IMAGE", "name": "One", "props": {"mediaId": "", "alt": "First"}, "styles": {}, "children": []}
                      ]}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("linear-gradient(160deg, #1b6c80, #f4efe8)", clean.path("root").path("styles").path("background").asText());
        assertEquals("coverflow", clean.path("root").path("children").get(0).path("props").path("preset").asText());
        assertEquals(2, clean.path("root").path("children").get(0).path("props").path("perView").asInt());
        ((ObjectNode) document.path("root").path("styles")).put("background", "url(https://example.com/a.png)");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void keepsAMobileOverrideAndLeavesTheDesktopValue() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {"fontSize": "48px"},
                    "children": [
                      {"id": "photos", "type": "CAROUSEL", "name": "Photos", "props": {"preset": "minimal", "autoplay": 1, "interval": 5000, "perView": 3},
                       "styles": {}, "responsiveProps": {"mobile": {"perView": 1}}, "children": [
                        {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Anna", "level": 1},
                         "styles": {"fontSize": "48px"}, "responsiveStyles": {"mobile": {"fontSize": "30px"}}, "children": []}
                      ]}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("48px", clean.path("root").path("children").get(0).path("children").get(0).path("styles").path("fontSize").asText());
        assertEquals("30px", clean.path("root").path("children").get(0).path("children").get(0).path("responsiveStyles").path("mobile").path("fontSize").asText());
        assertEquals(3, clean.path("root").path("children").get(0).path("props").path("perView").asInt());
        assertEquals(1, clean.path("root").path("children").get(0).path("responsiveProps").path("mobile").path("perView").asInt());
        assertEquals("", clean.path("root").path("children").get(0).path("responsiveProps").path("tablet").path("perView").asText(""));
    }

    @Test
    void keepsDesignTokensAndCreativeMetadataOnSchemaV1() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "designTokens": {
                    "colors": {"background":"#f8f4ef","text":"#1a1a1a","primary":"#25B5AE"},
                    "fonts": {"display":"Fraunces","body":"Manrope"},
                    "spacing": {"section":"72px"},
                    "meta": {"archetypeId":"luxury-editorial","version":"webaux-creative-v2"}
                  },
                  "creative": {"archetypeId":"luxury-editorial","rationale":"Editorial wedding tone","promptVersion":"webaux-creative-v2"},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {},
                    "children": [
                      {"id": "quote", "type": "QUOTE", "name": "Quote", "props": {"text": "Together", "attribution":"Anna"}, "styles": {}, "children": []},
                      {"id": "count", "type": "COUNTDOWN", "name": "Countdown", "props": {"target":"2026-12-31","label":"Until the day"}, "styles": {}, "children": []},
                      {"id": "faq", "type": "FAQ_ACCORDION", "name": "FAQ", "props": {"title":"Questions"}, "styles": {}, "children": [
                        {"id": "q1", "type": "FAQ_ITEM", "name": "Item", "props": {"title":"Where?","text":"At the venue."}, "styles": {}, "children": []}
                      ]},
                      {"id": "time", "type": "TIMELINE", "name": "Timeline", "props": {"title":"Day"}, "styles": {}, "children": [
                        {"id": "t1", "type": "TIMELINE_ITEM", "name": "Item", "props": {"title":"Ceremony","text":"4pm"}, "styles": {}, "children": []}
                      ]}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("#f8f4ef", clean.path("designTokens").path("colors").path("background").asText());
        assertEquals("luxury-editorial", clean.path("creative").path("archetypeId").asText());
        assertEquals("Together", clean.path("root").path("children").get(0).path("props").path("text").asText());
        assertEquals("2026-12-31", clean.path("root").path("children").get(1).path("props").path("target").asText());
        assertEquals("Where?", clean.path("root").path("children").get(2).path("children").get(0).path("props").path("title").asText());
        assertEquals("Ceremony", clean.path("root").path("children").get(3).path("children").get(0).path("props").path("title").asText());
    }

    @Test
    void keepsBackgroundMediaAndCollectsItWithoutAllowingCssUrls() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {},
                    "styles": {
                      "backgroundMediaId": "22222222-2222-2222-2222-222222222222",
                      "backgroundSize": "cover",
                      "backgroundPosition": "center",
                      "backgroundRepeat": "no-repeat",
                      "backgroundAttachment": "scroll",
                      "backgroundOverlayType": "solid",
                      "backgroundOverlayColor": "#000000",
                      "backgroundOverlayOpacity": "40",
                      "zIndex": "2"
                    },
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Anna", "level": 1}, "styles": {"zIndex": "1"}, "children": []}
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("22222222-2222-2222-2222-222222222222", clean.path("root").path("styles").path("backgroundMediaId").asText());
        assertEquals("cover", clean.path("root").path("styles").path("backgroundSize").asText());
        assertEquals("solid", clean.path("root").path("styles").path("backgroundOverlayType").asText());
        assertEquals(java.util.Set.of("22222222-2222-2222-2222-222222222222"), validator.mediaIds(clean));
        ((ObjectNode) document.path("root").path("styles")).put("backgroundMediaId", "not-a-uuid");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void keepsInlineRichTextLinksAndRejectsDangerousOnes() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {},
                    "children": [
                      {
                        "id": "copy",
                        "type": "PARAGRAPH",
                        "name": "Text",
                        "props": {
                          "text": "Discover our events and register today.",
                          "richText": [
                            {"text": "Discover our events and "},
                            {"text": "register today", "href": "https://example.com/register", "target": "_blank"},
                            {"text": "."}
                          ],
                          "href": "",
                          "linkUnderline": 1
                        },
                        "styles": {},
                        "children": []
                      }
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals(3, clean.path("root").path("children").get(0).path("props").path("richText").size());
        assertEquals("https://example.com/register", clean.path("root").path("children").get(0).path("props").path("richText").get(1).path("href").asText());
        ((ObjectNode) document.path("root").path("children").get(0).path("props").path("richText").get(1)).put("href", "javascript:alert(1)");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void keepsAMarqueeWithItemsAndCollectsImageMedia() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {},
                    "children": [
                      {
                        "id": "ticker",
                        "type": "MARQUEE",
                        "name": "Marquee",
                        "props": {
                          "text": "Hello",
                          "direction": "left",
                          "speed": 40,
                          "duration": 28,
                          "gap": 24,
                          "delay": 0,
                          "loop": 1,
                          "pauseOnHover": 1,
                          "pauseOnClick": 0,
                          "animationEnabled": 1,
                          "items": [
                            {"id": "i1", "kind": "text", "text": "Hello", "href": "https://example.com"},
                            {"id": "i2", "kind": "image", "mediaId": "22222222-2222-2222-2222-222222222222", "alt": "Logo"}
                          ]
                        },
                        "styles": {"overflow": "hidden"},
                        "children": []
                      }
                    ]
                  }
                }
                """);
        ObjectNode clean = validator.sanitize(document);
        assertEquals("MARQUEE", clean.path("root").path("children").get(0).path("type").asText());
        assertEquals(2, clean.path("root").path("children").get(0).path("props").path("items").size());
        assertEquals(java.util.Set.of("22222222-2222-2222-2222-222222222222"), validator.mediaIds(clean));
    }
}
