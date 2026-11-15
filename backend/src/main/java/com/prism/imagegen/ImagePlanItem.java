package com.prism.imagegen;

public record ImagePlanItem(
        String assetKey,
        String type,
        String sectionId,
        String targetNodeId,
        String purpose,
        String prompt,
        String aspectRatio,
        String priority,
        boolean required
) {
}
