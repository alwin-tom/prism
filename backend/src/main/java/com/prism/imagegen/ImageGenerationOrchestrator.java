package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class ImageGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationOrchestrator.class);

    private final PrismProperties properties;
    private final ImagePlanningService planning;
    private final ImageGenerationJobService jobs;
    private final GeneratedAssetRepository assets;
    private final ImageGenerationJobRepository jobRepository;
    private final ImageModelSelector selector;
    private final ImageProvider provider;
    private final ImageStorageService storage;
    private final ImageDocumentBinder binder;
    private final EventRepository events;

    public ImageGenerationOrchestrator(
            PrismProperties properties,
            ImagePlanningService planning,
            ImageGenerationJobService jobs,
            GeneratedAssetRepository assets,
            ImageGenerationJobRepository jobRepository,
            ImageModelSelector selector,
            ImageProvider provider,
            ImageStorageService storage,
            ImageDocumentBinder binder,
            EventRepository events
    ) {
        this.properties = properties;
        this.planning = planning;
        this.jobs = jobs;
        this.assets = assets;
        this.jobRepository = jobRepository;
        this.selector = selector;
        this.provider = provider;
        this.storage = storage;
        this.binder = binder;
        this.events = events;
    }

    public void runForWebsiteGeneration(
            UUID eventId,
            UUID websiteJobId,
            JsonNode imagePlan,
            String category,
            JsonNode answers,
            Set<String> existingMediaIds
    ) {
        if (!properties.getImage().isEnabled()) {
            return;
        }
        if (properties.getOpenrouter().getApiKey() == null || properties.getOpenrouter().getApiKey().isBlank()) {
            return;
        }
        List<ImagePlanItem> plan = planning.plan(imagePlan, category, answers, existingMediaIds);
        if (plan.isEmpty()) {
            return;
        }
        List<ImageGenerationJob> queued = new ArrayList<>();
        for (ImagePlanItem item : plan) {
            GeneratedAsset asset = jobs.createAsset(eventId, websiteJobId, item);
            String idempotency = websiteJobId + ":" + item.assetKey() + ":v" + asset.version;
            queued.add(jobs.enqueue(asset, idempotency));
        }
        runJobs(queued, true);
    }

    public ImageGenerationJob regenerate(UUID eventId, GeneratedAsset previous, ImagePlanItem plan) {
        GeneratedAsset asset = jobs.createAsset(eventId, null, plan);
        String idempotency = "regen:" + eventId + ":" + asset.assetKey + ":v" + asset.version + ":" + UUID.randomUUID();
        ImageGenerationJob job = jobs.enqueue(asset, idempotency);
        runJobs(List.of(job), false);
        return jobRepository.findById(job.id).orElse(job);
    }

    public ImageGenerationJob generateOne(UUID eventId, ImagePlanItem plan) {
        GeneratedAsset asset = jobs.createAsset(eventId, null, plan);
        String idempotency = "manual:" + eventId + ":" + asset.assetKey + ":v" + asset.version;
        ImageGenerationJob job = jobs.enqueue(asset, idempotency);
        runJobs(List.of(job), false);
        return jobRepository.findById(job.id).orElse(job);
    }

    private void runJobs(List<ImageGenerationJob> queued, boolean respectWaitBudget) {
        if (queued.isEmpty()) {
            return;
        }
        int waitSeconds = Math.max(5, properties.getImage().getWaitSeconds());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, queued.size()));
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (ImageGenerationJob job : queued) {
                tasks.add(() -> {
                    process(job.id);
                    return null;
                });
            }
            if (respectWaitBudget) {
                List<Future<Void>> futures = pool.invokeAll(tasks, waitSeconds, TimeUnit.SECONDS);
                for (Future<Void> future : futures) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
            } else {
                pool.invokeAll(tasks);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }

    public void process(UUID jobId) {
        ImageGenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        if ("COMPLETED".equals(job.status) || "SKIPPED".equals(job.status) || "CANCELLED".equals(job.status)) {
            return;
        }
        GeneratedAsset asset = assets.findById(job.generatedAssetId)
                .orElseThrow(() -> ApiException.notFound("That generated asset was not found."));
        EventSite event = events.findById(job.eventId)
                .orElseThrow(() -> ApiException.notFound("That event site was not found."));
        BigDecimal spent = assets.sumActualCost(job.eventId);
        ImageModelSelector.Selection selection = selector.select(asset.aspectRatio, spent);
        if (selection.skip()) {
            jobs.markSkipped(job, asset, selection.reason());
            return;
        }
        jobs.markGenerating(job, selection.model().id(), selection.estimatedCost());
        try {
            ImageModelCard model = selection.model();
            boolean supportsAspect = model.supportedParameters() != null && model.supportedParameters().contains("aspect_ratio");
            boolean supportsFormat = model.supportedParameters() != null && model.supportedParameters().contains("output_format");
            boolean supportsN = model.supportedParameters() == null || model.supportedParameters().isEmpty()
                    || model.supportedParameters().contains("n");
            String aspect = asset.aspectRatio;
            if (supportsAspect && aspect != null && !aspect.isBlank() && !model.supportsAspect(aspect)) {
                aspect = model.supportedAspectRatios() == null || model.supportedAspectRatios().isEmpty()
                        ? null
                        : model.supportedAspectRatios().iterator().next();
            }
            ImageProvider.GeneratedImage generated = provider.generate(new ImageProvider.Request(
                    model.id(),
                    job.prompt,
                    aspect,
                    "png",
                    supportsAspect,
                    supportsFormat,
                    supportsN
            ));
            BigDecimal actual = generated.costUsd() == null ? selection.estimatedCost() : generated.costUsd();
            if (!withinBudget(actual, spent)) {
                jobs.markFailed(job, asset, "The generated image exceeded the configured budget.");
                return;
            }
            jobs.markProcessing(job);
            storage.store(event, asset, generated.bytes());
            ImagePlanItem plan = new ImagePlanItem(
                    asset.assetKey,
                    asset.assetType,
                    asset.sectionId == null ? "" : asset.sectionId,
                    asset.targetNodeId == null ? "" : asset.targetNodeId,
                    asset.purpose == null ? "" : asset.purpose,
                    asset.prompt,
                    asset.aspectRatio == null ? "16:9" : asset.aspectRatio,
                    "MEDIUM",
                    false
            );
            binder.bind(event.id, plan, asset.mediaId, asset.purpose);
            jobs.markCompleted(job, asset, actual);
        } catch (ImageProviderException exception) {
            log.warn("Image job {} failed: {}", job.id, exception.getMessage());
            jobs.markFailed(job, asset, exception.getMessage());
        } catch (Exception exception) {
            log.warn("Image job {} failed unexpectedly: {}", job.id, exception.toString());
            jobs.markFailed(job, asset, "The image could not be generated.");
        }
    }

    private boolean withinBudget(BigDecimal actual, BigDecimal spent) {
        PrismProperties.Image config = properties.getImage();
        if (actual == null) {
            return !config.isPaidFallbackEnabled();
        }
        if (config.getMaxCostPerImageUsd().compareTo(BigDecimal.ZERO) > 0
                && actual.compareTo(config.getMaxCostPerImageUsd()) > 0) {
            return false;
        }
        BigDecimal next = (spent == null ? BigDecimal.ZERO : spent).add(actual);
        return config.getMaxCostPerWebsiteUsd().compareTo(BigDecimal.ZERO) <= 0
                || next.compareTo(config.getMaxCostPerWebsiteUsd()) <= 0;
    }
}
