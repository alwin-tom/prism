package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.creation.OpenRouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);
    private static final int CHUNK_CHARS = 6_000;

    private final WebsiteContentExtractionService extraction;
    private final AIContentModerationProvider provider;
    private final ModerationRiskAssessmentService risk;

    public ContentModerationService(
            WebsiteContentExtractionService extraction,
            AIContentModerationProvider provider,
            ModerationRiskAssessmentService risk
    ) {
        this.extraction = extraction;
        this.provider = provider;
        this.risk = risk;
    }

    public ModerationAnalysisResult analyse(JsonNode editorDocument, JsonNode configuration) {
        ExtractedWebsiteContent content = extraction.extract(editorDocument, configuration);
        if (content.isEmpty()) {
            return risk.clean("No visitor-facing text was found in the submitted website.");
        }
        List<ExtractedWebsiteContent.ContentChunk> chunks = content.chunks(CHUNK_CHARS);
        List<ModerationAnalysisResult> parts = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            try {
                parts.add(provider.moderateChunk(chunks.get(index), index, chunks.size()));
            } catch (OpenRouterException exception) {
                log.warn("Moderation provider failed on chunk {}/{}: {}", index + 1, chunks.size(), exception.getMessage());
                throw exception;
            }
        }
        return risk.aggregate(parts);
    }
}
