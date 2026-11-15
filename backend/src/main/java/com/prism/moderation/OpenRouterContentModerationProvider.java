package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.creation.AttemptBudget;
import com.prism.creation.OpenRouterClient;
import com.prism.creation.OpenRouterException;
import com.prism.shared.config.PrismProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenRouterContentModerationProvider implements AIContentModerationProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterContentModerationProvider.class);

    private final OpenRouterClient openRouter;
    private final PrismProperties properties;
    private final ObjectMapper mapper;
    private final ModerationResultValidator validator;

    public OpenRouterContentModerationProvider(
            OpenRouterClient openRouter,
            PrismProperties properties,
            ObjectMapper mapper,
            ModerationResultValidator validator
    ) {
        this.openRouter = openRouter;
        this.properties = properties;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public ModerationAnalysisResult moderateChunk(ExtractedWebsiteContent.ContentChunk chunk, int chunkIndex, int chunkCount) {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        String model = resolveModel(settings);
        if (model.isBlank()) {
            throw new OpenRouterException(OpenRouterException.Kind.AUTH, "Content moderation model is not configured.");
        }
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new OpenRouterException(OpenRouterException.Kind.AUTH, "OpenRouter is not configured for content moderation.");
        }
        List<String> fallbacks = split(settings.getModerationFallbacks());
        if (fallbacks.isEmpty()) {
            fallbacks = split(settings.getRepairFallbacks());
        }
        OpenRouterClient.Route route = new OpenRouterClient.Route(
                model,
                fallbacks,
                settings.isAllowPaidFallback(),
                Math.max(1, settings.getMaxRetries()),
                Math.max(0, settings.getMaxFallbacks()),
                AttemptBudget.unlimited()
        );
        JsonNode schema = ModerationSchemas.result(mapper);
        String system = ModerationPromptBuilder.system();
        String user = ModerationPromptBuilder.user(chunk, chunkIndex, chunkCount);
        try {
            OpenRouterClient.Completion completion = openRouter.complete(route, system, user, schema, "moderation_result");
            return validator.validate(completion.content(), completion.model(), "openrouter");
        } catch (OpenRouterException exception) {
            if (exception.kind() == OpenRouterException.Kind.STRUCTURED) {
                log.warn("Structured moderation failed for chunk {}; retrying without schema lock", chunkIndex);
                OpenRouterClient.Completion loose = openRouter.complete(route, system, user + "\nReturn JSON only.", null, "moderation_result");
                return validator.validate(loose.content(), loose.model(), "openrouter");
            }
            throw exception;
        }
    }

    private String resolveModel(PrismProperties.OpenRouter settings) {
        if (settings.getModerationModel() != null && !settings.getModerationModel().isBlank()) {
            return settings.getModerationModel().trim();
        }
        if (settings.getRepairModel() != null && !settings.getRepairModel().isBlank()) {
            return settings.getRepairModel().trim();
        }
        if (settings.getGenerationModel() != null && !settings.getGenerationModel().isBlank()) {
            return settings.getGenerationModel().trim();
        }
        return settings.getPlanningModel() == null ? "" : settings.getPlanningModel().trim();
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
        return values;
    }
}
