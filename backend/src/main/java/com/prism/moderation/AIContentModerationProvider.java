package com.prism.moderation;

public interface AIContentModerationProvider {

    ModerationAnalysisResult moderateChunk(ExtractedWebsiteContent.ContentChunk chunk, int chunkIndex, int chunkCount);
}
