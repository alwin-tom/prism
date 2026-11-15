package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WebsiteContentExtractionService {

    public ExtractedWebsiteContent extract(JsonNode editorDocument, JsonNode configuration) {
        List<ExtractedTextSpan> spans = new ArrayList<>();
        if (editorDocument != null && !editorDocument.isNull() && !editorDocument.isMissingNode()) {
            extractDocument(editorDocument, spans);
        }
        if (configuration != null && !configuration.isNull() && !configuration.isMissingNode()) {
            extractConfiguration(configuration, spans);
        }
        return new ExtractedWebsiteContent(dedupePreserveLocations(spans));
    }

    private void extractDocument(JsonNode document, List<ExtractedTextSpan> spans) {
        String pageId = text(document.path("documentId"));
        if (pageId.isBlank()) {
            pageId = "page-home";
        }
        JsonNode page = document.path("page");
        add(spans, pageId, null, pageId + ":meta", "PAGE_META", "title", text(page.path("title")));
        add(spans, pageId, null, pageId + ":meta", "PAGE_META", "description", text(page.path("description")));
        JsonNode root = document.path("root");
        if (root.isObject()) {
            walkElement(root, pageId, null, spans);
        }
    }

    private void walkElement(JsonNode node, String pageId, String sectionId, List<ExtractedTextSpan> spans) {
        if (node == null || !node.isObject()) {
            return;
        }
        String id = text(node.path("id"));
        String type = text(node.path("type")).toUpperCase(Locale.ROOT);
        String nextSection = sectionId;
        if ("SECTION".equals(type) && !id.isBlank()) {
            nextSection = id;
        }
        JsonNode props = node.path("props");
        if (props.isObject()) {
            extractProps(pageId, nextSection, id, type, props, spans);
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                walkElement(child, pageId, nextSection, spans);
            }
        }
    }

    private void extractProps(String pageId, String sectionId, String elementId, String type, JsonNode props, List<ExtractedTextSpan> spans) {
        add(spans, pageId, sectionId, elementId, type, "text", text(props.path("text")));
        add(spans, pageId, sectionId, elementId, type, "title", text(props.path("title")));
        add(spans, pageId, sectionId, elementId, type, "label", text(props.path("label")));
        add(spans, pageId, sectionId, elementId, type, "caption", text(props.path("caption")));
        add(spans, pageId, sectionId, elementId, type, "alt", text(props.path("alt")));
        add(spans, pageId, sectionId, elementId, type, "href", visibleHref(text(props.path("href"))));
        extractRichText(pageId, sectionId, elementId, type, "richText", props.path("richText"), spans);

        JsonNode items = props.path("items");
        if (items.isArray()) {
            int index = 0;
            for (JsonNode item : items) {
                String itemId = text(item.path("id"));
                if (itemId.isBlank()) {
                    itemId = elementId + ":item:" + index;
                }
                String itemType = type + "_ITEM";
                add(spans, pageId, sectionId, itemId, itemType, "text", text(item.path("text")));
                add(spans, pageId, sectionId, itemId, itemType, "alt", text(item.path("alt")));
                add(spans, pageId, sectionId, itemId, itemType, "title", text(item.path("title")));
                extractRichText(pageId, sectionId, itemId, itemType, "richText", item.path("richText"), spans);
                index++;
            }
        }

        JsonNode faq = props.path("items");
        if ("FAQ_ACCORDION".equals(type) && faq.isArray()) {
            // already handled via items
        }
        if ("COUNTDOWN".equals(type)) {
            add(spans, pageId, sectionId, elementId, type, "label", text(props.path("label")));
        }
    }

    private void extractRichText(
            String pageId,
            String sectionId,
            String elementId,
            String type,
            String field,
            JsonNode runs,
            List<ExtractedTextSpan> spans
    ) {
        if (runs == null || !runs.isArray() || runs.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (JsonNode run : runs) {
            String value = text(run.path("text"));
            if (value.isBlank()) {
                continue;
            }
            int start = joined.length();
            if (!joined.isEmpty()) {
                joined.append(' ');
                start = joined.length();
            }
            joined.append(value);
            add(spans, pageId, sectionId, elementId, type, field + ":run", value, start, start + value.length());
        }
        if (!joined.isEmpty()) {
            add(spans, pageId, sectionId, elementId, type, field, joined.toString(), 0, joined.length());
        }
    }

    private void extractConfiguration(JsonNode configuration, List<ExtractedTextSpan> spans) {
        JsonNode event = configuration.path("event");
        add(spans, "config", null, "event", "EVENT", "name", text(event.path("name")));
        add(spans, "config", null, "event", "EVENT", "subtitle", text(event.path("subtitle")));
        add(spans, "config", null, "event", "EVENT", "description", text(event.path("description")));
        add(spans, "config", null, "event", "EVENT", "venue", text(event.path("venue")));
        add(spans, "config", null, "event", "EVENT", "address", text(event.path("address")));
        add(spans, "config", null, "seo", "SEO", "title", text(configuration.path("seo").path("title")));
        add(spans, "config", null, "seo", "SEO", "description", text(configuration.path("seo").path("description")));

        JsonNode sections = configuration.path("sections");
        if (sections.isArray()) {
            for (JsonNode section : sections) {
                String sectionId = text(section.path("id"));
                if (sectionId.isBlank()) {
                    sectionId = text(section.path("key"));
                }
                add(spans, "config", sectionId, sectionId, "SECTION", "title", text(section.path("title")));
                add(spans, "config", sectionId, sectionId, "SECTION", "body", text(section.path("body")));
                add(spans, "config", sectionId, sectionId, "SECTION", "text", text(section.path("text")));
                JsonNode blocks = section.path("blocks");
                if (blocks.isArray()) {
                    int index = 0;
                    for (JsonNode block : blocks) {
                        String blockId = text(block.path("id"));
                        if (blockId.isBlank()) {
                            blockId = sectionId + ":block:" + index;
                        }
                        add(spans, "config", sectionId, blockId, "BLOCK", "title", text(block.path("title")));
                        add(spans, "config", sectionId, blockId, "BLOCK", "text", text(block.path("text")));
                        add(spans, "config", sectionId, blockId, "BLOCK", "caption", text(block.path("caption")));
                        index++;
                    }
                }
            }
        }

        JsonNode canvas = configuration.path("canvas").path("elements");
        if (canvas.isArray()) {
            for (JsonNode element : canvas) {
                String id = text(element.path("id"));
                String type = text(element.path("type")).toUpperCase(Locale.ROOT);
                if (type.isBlank()) {
                    type = "CANVAS";
                }
                add(spans, "config", null, id, type, "text", text(element.path("text")));
                add(spans, "config", null, id, type, "label", text(element.path("label")));
                add(spans, "config", null, id, type, "alt", text(element.path("alt")));
            }
        }
    }

    private static void add(
            List<ExtractedTextSpan> spans,
            String pageId,
            String sectionId,
            String elementId,
            String elementType,
            String field,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        String cleaned = value.trim();
        add(spans, pageId, sectionId, elementId, elementType, field, cleaned, 0, cleaned.length());
    }

    private static void add(
            List<ExtractedTextSpan> spans,
            String pageId,
            String sectionId,
            String elementId,
            String elementType,
            String field,
            String value,
            int start,
            int end
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        spans.add(new ExtractedTextSpan(
                blankToNull(pageId),
                blankToNull(sectionId),
                blankToNull(elementId),
                blankToNull(elementType),
                blankToNull(field),
                value.trim(),
                Math.max(0, start),
                Math.max(Math.max(0, start), end)
        ));
    }

    private static List<ExtractedTextSpan> dedupePreserveLocations(List<ExtractedTextSpan> spans) {
        Map<String, ExtractedTextSpan> unique = new LinkedHashMap<>();
        for (ExtractedTextSpan span : spans) {
            String key = String.join("|",
                    nullToEmpty(span.pageId()),
                    nullToEmpty(span.sectionId()),
                    nullToEmpty(span.elementId()),
                    nullToEmpty(span.field()),
                    nullToEmpty(span.text()),
                    String.valueOf(span.startOffset()),
                    String.valueOf(span.endOffset()));
            unique.putIfAbsent(key, span);
        }
        return new ArrayList<>(unique.values());
    }

    private static String visibleHref(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String value = href.trim();
        if (value.startsWith("javascript:") || value.startsWith("data:")) {
            return "";
        }
        return value;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText("").trim();
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
