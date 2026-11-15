package com.prism.creation;

import com.prism.imagegen.ImageGenerationOrchestrator;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(GenerationPipeline.class);

    private final CreationService creation;
    private final WebsiteDocumentGenerator generator;
    private final OpenRouterClient client;
    private final AiRuntime runtime;
    private final PrismProperties properties;
    private final ImageGenerationOrchestrator images;

    public GenerationPipeline(
            CreationService creation,
            WebsiteDocumentGenerator generator,
            OpenRouterClient client,
            AiRuntime runtime,
            PrismProperties properties,
            ImageGenerationOrchestrator images
    ) {
        this.creation = creation;
        this.generator = generator;
        this.client = client;
        this.runtime = runtime;
        this.properties = properties;
        this.images = images;
    }

    public void run(UUID jobId) {
        try {
            CreationService.Prepared prepared = creation.prepare(jobId);
            if (prepared == null || creation.cancelled(jobId)) {
                return;
            }
            String prompt = PromptBuilder.document(prepared.category(), prepared.answers(), prepared.mediaIds(), prepared.answers());
            if (prompt.length() > 80_000) {
                throw new OpenRouterException(OpenRouterException.Kind.CONTEXT, "The request is too large for the model.");
            }
            creation.rememberPrompt(jobId, prompt);
            PrismProperties.OpenRouter settings = runtime.forThisRun();
            runtime.assertDailyBudget(settings);
            WebsiteDocumentGenerator.Generated generated = generator.generate(
                    client,
                    settings,
                    prepared.category(),
                    prepared.answers(),
                    prepared.mediaIds(),
                    status -> {
                        if (!creation.cancelled(jobId)) {
                            creation.advance(jobId, status);
                        }
                    },
                    prepared.userId()
            );
            if (creation.cancelled(jobId)) {
                return;
            }
            creation.installGenerated(prepared.sessionId(), generated.document(), generated.plan());
            if (!creation.cancelled(jobId) && properties.getImage().isEnabled()) {
                try {
                    creation.advance(jobId, "IMAGES");
                    images.runForWebsiteGeneration(
                            prepared.eventId(),
                            jobId,
                            generated.imagePlan(),
                            prepared.category(),
                            prepared.answers(),
                            prepared.mediaIds()
                    );
                } catch (RuntimeException exception) {
                    log.warn("Image generation stage failed for job {}: {}", jobId, exception.toString());
                }
            }
            if (creation.cancelled(jobId)) {
                return;
            }
            creation.finish(jobId, generated, prepared.sessionId());
        } catch (WebsiteDocumentGenerator.GenerationFailure failure) {
            creation.fail(jobId, failure.getMessage(), failure.plan(), failure.raw(), failure.inputTokens(), failure.outputTokens(), failure.costUsd(), failure.latencyMs(), failure.requests(), failure.usage());
        } catch (OpenRouterException exception) {
            creation.fail(jobId, exception.getMessage(), null, null, 0, 0);
        } catch (ApiException exception) {
            creation.fail(jobId, exception.getMessage(), null, null, 0, 0);
        } catch (RuntimeException exception) {
            creation.fail(jobId, "The website could not be generated.", null, null, 0, 0);
        }
    }
}
