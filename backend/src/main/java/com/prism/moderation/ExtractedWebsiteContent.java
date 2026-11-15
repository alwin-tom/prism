package com.prism.moderation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExtractedWebsiteContent(List<ExtractedTextSpan> spans) {

    public ExtractedWebsiteContent {
        spans = List.copyOf(spans == null ? List.of() : spans);
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }

    public int characterCount() {
        return spans.stream().mapToInt(span -> span.text() == null ? 0 : span.text().length()).sum();
    }

    public List<ContentChunk> chunks(int maxChars) {
        int limit = Math.max(500, maxChars);
        List<ContentChunk> chunks = new ArrayList<>();
        List<ExtractedTextSpan> current = new ArrayList<>();
        int size = 0;
        int index = 0;
        for (ExtractedTextSpan span : spans) {
            String text = span.text() == null ? "" : span.text().trim();
            if (text.isBlank()) {
                continue;
            }
            int cost = text.length() + 48;
            if (!current.isEmpty() && size + cost > limit) {
                chunks.add(new ContentChunk(index++, List.copyOf(current)));
                current = new ArrayList<>();
                size = 0;
            }
            current.add(span);
            size += cost;
        }
        if (!current.isEmpty()) {
            chunks.add(new ContentChunk(index, List.copyOf(current)));
        }
        return chunks;
    }

    public Map<String, List<ExtractedTextSpan>> byElement() {
        Map<String, List<ExtractedTextSpan>> grouped = new LinkedHashMap<>();
        for (ExtractedTextSpan span : spans) {
            String key = (span.pageId() == null ? "" : span.pageId()) + "|"
                    + (span.elementId() == null ? "" : span.elementId()) + "|"
                    + (span.field() == null ? "" : span.field());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(span);
        }
        return grouped;
    }

    public record ContentChunk(int index, List<ExtractedTextSpan> spans) {
        public String promptBody() {
            StringBuilder body = new StringBuilder();
            for (ExtractedTextSpan span : spans) {
                body.append("[span page=")
                        .append(nullToEmpty(span.pageId()))
                        .append(" section=")
                        .append(nullToEmpty(span.sectionId()))
                        .append(" element=")
                        .append(nullToEmpty(span.elementId()))
                        .append(" type=")
                        .append(nullToEmpty(span.elementType()))
                        .append(" field=")
                        .append(nullToEmpty(span.field()))
                        .append(" offset=")
                        .append(span.startOffset())
                        .append('-')
                        .append(span.endOffset())
                        .append("]\n")
                        .append(span.text())
                        .append("\n\n");
            }
            return body.toString().trim();
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
