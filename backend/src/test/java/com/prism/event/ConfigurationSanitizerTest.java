package com.prism.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationSanitizerTest {

    private final ConfigurationSanitizer sanitizer = new ConfigurationSanitizer(new ObjectMapper());

    @Test
    void dropsMarkupAndUnknownSectionTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode input = (ObjectNode) mapper.readTree("""
                {
                  "theme": {"background":"#ffffff","ink":"#111111","accent":"#1b6c80","font":"Comic Sans","displayFont":"Fraunces"},
                  "event": {"name":"Meera <script>","subtitle":"Hello","startAt":"2027-04-18T11:30:00Z"},
                  "rsvp": {"enabled": true},
                  "sections": [
                    {"id":"hero","type":"HERO","properties":{"title":"Meera","subtitle":"Arun","html":"<b>no</b>"}},
                    {"id":"bad","type":"RAW_HTML","properties":{}}
                  ]
                }
                """);
        assertThrows(ApiException.class, () -> sanitizer.sanitize(input, false));
    }

    @Test
    void keepsRegisteredSectionsAndAWhitelistedFont() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode input = (ObjectNode) mapper.readTree("""
                {
                  "theme": {"background":"#f4f1ea","ink":"#1c1915","accent":"#1f4d3a","font":"Public Sans","displayFont":"Fraunces"},
                  "event": {"name":"Meera and Arun","subtitle":"Saturday","description":"Courtyard","startAt":"2027-04-18T11:30:00Z","venueName":"Byculla"},
                  "rsvp": {"enabled": true, "maxGuestsPerResponse": 4},
                  "sections": [
                    {"id":"hero","type":"HERO","properties":{"title":"Meera and Arun","subtitle":"Saturday","backgroundImage":"/placeholders/linen.svg"}}
                  ]
                }
                """);
        ObjectNode clean = sanitizer.sanitize(input, true);
        assertEquals("Public Sans", clean.path("theme").path("font").asText());
        assertEquals("HERO", clean.path("sections").get(0).path("type").asText());
        assertFalse(clean.path("sections").get(0).path("properties").has("html"));
    }

    @Test
    void keepsTextStyleAndDropsAnUnknownFont() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode input = (ObjectNode) mapper.readTree("""
                {
                  "theme": {"background":"#f4f1ea","ink":"#1c1915","accent":"#1f4d3a","font":"Public Sans","displayFont":"Fraunces"},
                  "event": {"name":"Meera and Arun","subtitle":"Saturday","description":"Courtyard","startAt":"2027-04-18T11:30:00Z","venueName":"Byculla"},
                  "rsvp": {"enabled": true, "maxGuestsPerResponse": 4},
                  "sections": [
                    {"id":"hero","type":"HERO","properties":{"title":"Meera and Arun","subtitle":"Saturday","backgroundImage":"/placeholders/linen.svg"}}
                  ],
                  "canvas": {"height":640,"elements":[
                    {"id":"hello","type":"text","text":"Hello","font":"Comic Sans","bold":true,"italic":true,"underline":false,"weight":"display"}
                  ]}
                }
                """);
        ObjectNode clean = sanitizer.sanitize(input, true);
        assertEquals("", clean.path("canvas").path("elements").get(0).path("font").asText());
        assertEquals(true, clean.path("canvas").path("elements").get(0).path("bold").asBoolean());
        assertEquals(true, clean.path("canvas").path("elements").get(0).path("italic").asBoolean());
        assertEquals(false, clean.path("canvas").path("elements").get(0).path("underline").asBoolean());
    }
}
