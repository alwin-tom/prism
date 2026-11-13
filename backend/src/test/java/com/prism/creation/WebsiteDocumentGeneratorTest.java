package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.config.PrismProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteDocumentGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebsiteDocumentGenerator generator = WebsiteDocumentGenerator.legacyForTests(mapper);

    @Test
    void acceptsASchemaBoundDocument() throws Exception {
        Scripted client = new Scripted(mapper.readTree(plan()), mapper.readTree(document("HEADING")));
        WebsiteDocumentGenerator.Generated generated = generator.generate(client, settings(0), "WEDDING", mapper.createObjectNode().put("coupleNames", "Meera and Arun"), Set.of(), status -> { });
        assertEquals(List.of(PrismSchemas.PLAN_SCHEMA, PrismSchemas.DOCUMENT_SCHEMA), client.schemas);
        assertTrue(client.sawSchema);
        assertEquals("Meera and Arun", generated.document().path("root").path("children").get(0).path("props").path("text").asText());
        assertEquals("Manrope", generated.document().path("root").path("styles").path("fontFamily").asText());
        assertEquals("Playfair Display", generated.document().path("root").path("children").get(0).path("styles").path("fontFamily").asText());
    }

    @Test
    void rejectsAnElementOutsideTheSchema() throws Exception {
        Scripted client = new Scripted(mapper.readTree(plan()), mapper.readTree(document("GALLERY")));
        WebsiteDocumentGenerator.GenerationFailure failure = assertThrows(WebsiteDocumentGenerator.GenerationFailure.class,
                () -> generator.generate(client, settings(0), "WEDDING", mapper.createObjectNode(), Set.of(), status -> { }));
        assertEquals(0, failure.repairs());
        assertTrue(failure.getMessage().contains("schema"));
    }

    @Test
    void clearsAnImageThatWasNotUploaded() throws Exception {
        ObjectNode doc = (ObjectNode) mapper.readTree(document("HEADING"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) doc.path("root").path("children")).add(mapper.readTree("""
                {"id":"photo","type":"IMAGE","name":"Photo","props":{"text":"","level":2,"href":"","mediaId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","alt":"Couple","size":24,"preset":"","autoplay":"","interval":"","perView":""},"styles":{"color":"#121417","background":"#fff9f4","fontFamily":"IBM Plex Sans","fontSize":"16px","textAlign":"left","padding":"0","borderRadius":"","boxShadow":""},"children":[]}
                """));
        Scripted client = new Scripted(mapper.readTree(plan()), doc);
        WebsiteDocumentGenerator.Generated generated = generator.generate(client, settings(0), "WEDDING", mapper.createObjectNode(), Set.of(), status -> { });
        assertEquals("", generated.document().path("root").path("children").get(2).path("props").path("mediaId").asText());
    }

    @Test
    void repairsAPageWhoseTextWasLeftBlank() throws Exception {
        ObjectNode blank = (ObjectNode) mapper.readTree(document("HEADING"));
        ((ObjectNode) blank.path("root").path("children").get(0).path("props")).put("text", "");
        ((ObjectNode) blank.path("root").path("children").get(1).path("props")).put("text", "");
        Scripted client = new Scripted(mapper.readTree(plan()), blank, mapper.readTree(document("HEADING")));
        WebsiteDocumentGenerator.Generated generated = generator.generate(client, settings(1), "CORPORATE", mapper.createObjectNode(), Set.of(), status -> { });
        assertEquals("Meera and Arun", generated.document().path("root").path("children").get(0).path("props").path("text").asText());
        assertEquals(1, generated.repairs());
    }

    @Test
    void repairsOnceThenAccepts() throws Exception {
        Scripted client = new Scripted(mapper.readTree(plan()), mapper.createObjectNode(), mapper.readTree(document("HEADING")));
        WebsiteDocumentGenerator.Generated generated = generator.generate(client, settings(1), "BIRTHDAY", mapper.createObjectNode(), Set.of(), status -> { });
        assertEquals(3, client.schemas.size());
        assertEquals("HEADING", generated.document().path("root").path("children").get(0).path("type").asText());
        assertEquals(1, generated.repairs());
    }

    @Test
    void failsWhenRepairStillDoesNotValidate() throws Exception {
        Scripted client = new Scripted(mapper.readTree(plan()), mapper.createObjectNode(), mapper.createObjectNode());
        WebsiteDocumentGenerator.GenerationFailure failure = assertThrows(WebsiteDocumentGenerator.GenerationFailure.class,
                () -> generator.generate(client, settings(1), "CUSTOM", mapper.createObjectNode(), Set.of(), status -> { }));
        assertTrue(failure.repairs() >= 1);
        assertTrue(failure.getMessage().length() > 0);
    }

    @Test
    void structuredRequestCarriesTheDocumentSchema() throws Exception {
        ObjectNode body = OpenRouterMessages.body(mapper, "test-model", "system", "user", PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA, 800, true);
        assertEquals("json_schema", body.path("response_format").path("type").asText());
        assertEquals(true, body.path("response_format").path("json_schema").path("strict").asBoolean());
        assertEquals(PrismSchemas.DOCUMENT_SCHEMA, body.path("response_format").path("json_schema").path("name").asText());
        assertEquals("SECTION", body.path("response_format").path("json_schema").path("schema").path("$defs").path("element").path("properties").path("type").path("enum").get(0).asText());
        assertEquals("string", body.path("response_format").path("json_schema").path("schema").path("$defs").path("element").path("properties").path("props").path("properties").path("text").path("type").asText());
        ObjectNode fallback = OpenRouterMessages.body(mapper, "test-model", "system", "user", null, PrismSchemas.DOCUMENT_SCHEMA, 800, false);
        assertEquals("json_object", fallback.path("response_format").path("type").asText());
    }

    private PrismProperties.OpenRouter settings(int repairs) {
        PrismProperties.OpenRouter settings = new PrismProperties.OpenRouter();
        settings.setPlanningModel("plan");
        settings.setGenerationModel("gen");
        settings.setRepairModel("repair");
        settings.setMaxRepairAttempts(repairs);
        return settings;
    }

    private String plan() {
        return "{\"title\":\"Meera and Arun\",\"description\":\"Wedding\",\"style\":\"elegant\",\"voice\":\"Refined and unhurried.\",\"sections\":[\"Hero\"],\"direction\":\"luxury-romantic\",\"concept\":\"Candlelit editorial\",\"mood\":\"Intimate and warm.\",\"patterns\":[\"full-width-hero\",\"stacked-editorial\"]}";
    }

    private String document(String extraType) {
        return """
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Meera and Arun", "description": "Wedding"},
                  "root": {
                    "id": "page",
                    "type": "SECTION",
                    "name": "Page",
                    "props": {"text": "", "level": 2, "href": "", "mediaId": "", "alt": "", "size": 24, "preset": "", "autoplay": "", "interval": "", "perView": ""},
                    "styles": {"color": "#121417", "background": "#fff9f4", "fontFamily": "Playfair Display", "fontSize": "32px", "textAlign": "left", "padding": "24px", "borderRadius": "", "boxShadow": ""},
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Meera and Arun", "level": 1, "href": "", "mediaId": "", "alt": "", "size": 24, "preset": "", "autoplay": "", "interval": "", "perView": ""}, "styles": {"color": "#121417", "background": "", "fontFamily": "Fraunces", "fontSize": "40px", "textAlign": "left", "padding": "0", "borderRadius": "", "boxShadow": ""}, "children": []},
                      {"id": "extra", "type": "%s", "name": "Extra", "props": {"text": "Hello", "level": 2, "href": "", "mediaId": "", "alt": "", "size": 24, "preset": "", "autoplay": "", "interval": "", "perView": ""}, "styles": {"color": "#121417", "background": "", "fontFamily": "IBM Plex Sans", "fontSize": "18px", "textAlign": "left", "padding": "0", "borderRadius": "", "boxShadow": ""}, "children": []}
                    ]
                  }
                }
                """.formatted(extraType);
    }

    private static final class Scripted implements OpenRouterClient {
        private final List<JsonNode> responses;
        private final AtomicInteger index = new AtomicInteger();
        private final List<String> schemas = new ArrayList<>();
        private boolean sawSchema;

        private Scripted(JsonNode... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public Completion complete(String model, String system, String user, JsonNode schema, String schemaName) {
            schemas.add(schemaName);
            sawSchema = schema != null && schema.isObject();
            JsonNode content = responses.get(index.getAndIncrement());
            return new Completion(content, 4, 6, model, schema != null);
        }
    }
}
