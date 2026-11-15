package com.prism.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteContentExtractionServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebsiteContentExtractionService service = new WebsiteContentExtractionService();

    @Test
    void extractsHeadingsParagraphsButtonsRichTextMarqueeAndMeta() {
        ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", 1);
        document.put("documentId", "page-home");
        document.putObject("page").put("title", "Meera & Arjun").put("description", "Wedding weekend");
        ObjectNode root = document.putObject("root");
        root.put("id", "section-hero");
        root.put("type", "SECTION");
        root.put("name", "Hero");
        root.putObject("props");
        root.putObject("styles");
        ArrayNode children = root.putArray("children");

        ObjectNode heading = children.addObject();
        heading.put("id", "text-heading");
        heading.put("type", "HEADING");
        heading.put("name", "Title");
        heading.putObject("props").put("text", "Celebrate with us");
        heading.putObject("styles");
        heading.putArray("children");

        ObjectNode paragraph = children.addObject();
        paragraph.put("id", "text-body");
        paragraph.put("type", "PARAGRAPH");
        paragraph.put("name", "Body");
        ObjectNode paragraphProps = paragraph.putObject("props");
        paragraphProps.put("text", "Join our evening reception.");
        ArrayNode rich = paragraphProps.putArray("richText");
        rich.addObject().put("text", "Join our ");
        rich.addObject().put("text", "evening reception.");
        paragraph.putObject("styles");
        paragraph.putArray("children");

        ObjectNode button = children.addObject();
        button.put("id", "btn-rsvp");
        button.put("type", "BUTTON");
        button.put("name", "RSVP");
        button.putObject("props").put("text", "Reply");
        button.putObject("styles");
        button.putArray("children");

        ObjectNode image = children.addObject();
        image.put("id", "img-1");
        image.put("type", "IMAGE");
        image.put("name", "Photo");
        image.putObject("props").put("alt", "Couple portrait");
        image.putObject("styles");
        image.putArray("children");

        ObjectNode marquee = children.addObject();
        marquee.put("id", "marquee-1");
        marquee.put("type", "MARQUEE");
        marquee.put("name", "Ticker");
        ObjectNode marqueeProps = marquee.putObject("props");
        ArrayNode items = marqueeProps.putArray("items");
        items.addObject().put("id", "item-1").put("text", "Welcome guests");
        marquee.putObject("styles");
        marquee.putArray("children");

        ObjectNode nested = children.addObject();
        nested.put("id", "group-1");
        nested.put("type", "GROUP");
        nested.put("name", "Group");
        nested.putObject("props");
        nested.putObject("styles");
        ObjectNode nestedChild = nested.putArray("children").addObject();
        nestedChild.put("id", "quote-1");
        nestedChild.put("type", "QUOTE");
        nestedChild.put("name", "Quote");
        nestedChild.putObject("props").put("text", "Love is patient");
        nestedChild.putObject("styles");
        nestedChild.putArray("children");

        ExtractedWebsiteContent content = service.extract(document, null);
        assertTrue(content.spans().stream().anyMatch(span -> "Celebrate with us".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Reply".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Couple portrait".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Welcome guests".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Love is patient".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Meera & Arjun".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "text-body".equals(span.elementId()) && "richText".equals(span.field())));
        assertFalse(content.isEmpty());
    }

    @Test
    void extractsLegacyConfigurationAndPreservesLocations() {
        ObjectNode configuration = mapper.createObjectNode();
        configuration.putObject("event").put("name", "Office launch").put("description", "Join the team");
        configuration.putObject("seo").put("title", "Launch night").put("description", "Product launch");
        ObjectNode section = configuration.putArray("sections").addObject();
        section.put("id", "section-about").put("title", "About").put("body", "We are opening a new studio.");
        ObjectNode canvasElement = configuration.putObject("canvas").putArray("elements").addObject();
        canvasElement.put("id", "canvas-1").put("type", "text").put("text", "Doors open at 6");

        ExtractedWebsiteContent content = service.extract(null, configuration);
        assertTrue(content.spans().stream().anyMatch(span -> "About".equals(span.text()) && "section-about".equals(span.elementId())));
        assertTrue(content.spans().stream().anyMatch(span -> "We are opening a new studio.".equals(span.text())));
        assertTrue(content.spans().stream().anyMatch(span -> "Doors open at 6".equals(span.text()) && "canvas-1".equals(span.elementId())));
        assertTrue(content.spans().stream().anyMatch(span -> "Launch night".equals(span.text())));
    }

    @Test
    void handlesMalformedDocumentsSafely() {
        ExtractedWebsiteContent empty = service.extract(null, null);
        assertTrue(empty.isEmpty());
        ObjectNode broken = mapper.createObjectNode();
        broken.put("root", "not-an-object");
        ExtractedWebsiteContent content = service.extract(broken, mapper.createObjectNode());
        assertTrue(content.characterCount() >= 0);
    }
}
