package com.prism.moderation;

public record ExtractedTextSpan(
        String pageId,
        String sectionId,
        String elementId,
        String elementType,
        String field,
        String text,
        int startOffset,
        int endOffset
) {
    public ExtractedTextSpan withOffsets(int start, int end) {
        return new ExtractedTextSpan(pageId, sectionId, elementId, elementType, field, text, start, end);
    }
}
