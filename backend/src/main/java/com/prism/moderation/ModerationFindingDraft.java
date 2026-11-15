package com.prism.moderation;

import java.math.BigDecimal;

public record ModerationFindingDraft(
        String category,
        String severity,
        BigDecimal confidence,
        String pageId,
        String sectionId,
        String elementId,
        String elementType,
        String matchedText,
        Integer startOffset,
        Integer endOffset,
        String context,
        String reason,
        String suggestion
) {
}
