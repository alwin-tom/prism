package com.prism.imagegen;

import com.prism.shared.config.PrismProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageGenerationJobService {

    private final ImageGenerationJobRepository jobs;
    private final GeneratedAssetRepository assets;
    private final PrismProperties properties;

    public ImageGenerationJobService(
            ImageGenerationJobRepository jobs,
            GeneratedAssetRepository assets,
            PrismProperties properties
    ) {
        this.jobs = jobs;
        this.assets = assets;
        this.properties = properties;
    }

    @Transactional
    public GeneratedAsset createAsset(UUID eventId, UUID websiteJobId, ImagePlanItem plan) {
        int version = assets.findFirstByEventIdAndAssetKeyOrderByVersionDesc(eventId, plan.assetKey())
                .map(existing -> existing.version + 1)
                .orElse(1);
        GeneratedAsset asset = new GeneratedAsset();
        asset.eventId = eventId;
        asset.websiteGenerationJobId = websiteJobId;
        asset.assetKey = plan.assetKey();
        asset.assetType = plan.type();
        asset.sectionId = blankToNull(plan.sectionId());
        asset.targetNodeId = blankToNull(plan.targetNodeId());
        asset.version = version;
        asset.prompt = plan.prompt();
        asset.purpose = blankToNull(plan.purpose());
        asset.aspectRatio = plan.aspectRatio();
        asset.provider = properties.getImage().getProvider();
        asset.status = "PENDING";
        return assets.save(asset);
    }

    @Transactional
    public ImageGenerationJob enqueue(GeneratedAsset asset, String idempotencyKey) {
        return jobs.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            ImageGenerationJob job = new ImageGenerationJob();
            job.eventId = asset.eventId;
            job.generatedAssetId = asset.id;
            job.websiteGenerationJobId = asset.websiteGenerationJobId;
            job.assetKey = asset.assetKey;
            job.prompt = asset.prompt;
            job.assetType = asset.assetType;
            job.provider = asset.provider;
            job.status = "QUEUED";
            job.attemptCount = 0;
            job.maxAttempts = Math.max(1, properties.getImage().getMaxRetries() + 1);
            job.idempotencyKey = idempotencyKey;
            asset.status = "QUEUED";
            assets.save(asset);
            return jobs.save(job);
        });
    }

    @Transactional
    public void markGenerating(ImageGenerationJob job, String modelId, BigDecimal estimated) {
        job.status = "GENERATING";
        job.modelId = modelId;
        job.estimatedCostUsd = estimated;
        job.attemptCount = job.attemptCount + 1;
        if (job.startedAt == null) {
            job.startedAt = Instant.now();
        }
        jobs.save(job);
        assets.findById(job.generatedAssetId).ifPresent(asset -> {
            asset.status = "GENERATING";
            asset.modelId = modelId;
            asset.estimatedCostUsd = estimated;
            assets.save(asset);
        });
    }

    @Transactional
    public void markProcessing(ImageGenerationJob job) {
        job.status = "PROCESSING";
        jobs.save(job);
        assets.findById(job.generatedAssetId).ifPresent(asset -> {
            asset.status = "PROCESSING";
            assets.save(asset);
        });
    }

    @Transactional
    public void markCompleted(ImageGenerationJob job, GeneratedAsset asset, BigDecimal actualCost) {
        job.status = "COMPLETED";
        job.actualCostUsd = actualCost;
        job.completedAt = Instant.now();
        job.errorDetails = null;
        jobs.save(job);
        asset.status = "COMPLETED";
        asset.actualCostUsd = actualCost;
        asset.errorMessage = null;
        assets.save(asset);
    }

    @Transactional
    public void markSkipped(ImageGenerationJob job, GeneratedAsset asset, String reason) {
        job.status = "SKIPPED";
        job.errorDetails = reason;
        job.completedAt = Instant.now();
        jobs.save(job);
        asset.status = "SKIPPED";
        asset.errorMessage = reason;
        assets.save(asset);
    }

    @Transactional
    public void markFailed(ImageGenerationJob job, GeneratedAsset asset, String reason) {
        boolean retryable = job.attemptCount < job.maxAttempts;
        job.status = retryable ? "QUEUED" : "FAILED";
        job.errorDetails = reason;
        if (!retryable) {
            job.completedAt = Instant.now();
        }
        jobs.save(job);
        asset.status = retryable ? "QUEUED" : "FAILED";
        asset.errorMessage = reason;
        assets.save(asset);
    }

    public Map<String, Object> progressForWebsiteJob(UUID websiteJobId) {
        List<ImageGenerationJob> rows = websiteJobId == null
                ? List.of()
                : jobs.findByWebsiteGenerationJobIdOrderByCreatedAtAsc(websiteJobId);
        int total = rows.size();
        int completed = 0;
        int failed = 0;
        int skipped = 0;
        int pending = 0;
        String current = "";
        for (ImageGenerationJob row : rows) {
            switch (row.status) {
                case "COMPLETED" -> completed++;
                case "FAILED", "CANCELLED" -> failed++;
                case "SKIPPED" -> skipped++;
                default -> {
                    pending++;
                    if (current.isBlank()) {
                        current = label(row);
                    }
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("completed", completed);
        body.put("failed", failed);
        body.put("skipped", skipped);
        body.put("pending", pending);
        body.put("currentLabel", current);
        return body;
    }

    public Map<String, Object> viewJob(ImageGenerationJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", job.id);
        body.put("eventId", job.eventId);
        body.put("generatedAssetId", job.generatedAssetId);
        body.put("websiteGenerationJobId", job.websiteGenerationJobId);
        body.put("assetKey", job.assetKey);
        body.put("assetType", job.assetType);
        body.put("status", job.status);
        body.put("modelId", job.modelId);
        body.put("attemptCount", job.attemptCount);
        body.put("maxAttempts", job.maxAttempts);
        body.put("estimatedCostUsd", job.estimatedCostUsd);
        body.put("actualCostUsd", job.actualCostUsd);
        body.put("errorDetails", job.errorDetails);
        body.put("startedAt", job.startedAt);
        body.put("completedAt", job.completedAt);
        body.put("createdAt", job.createdAt);
        return body;
    }

    public Map<String, Object> viewAsset(GeneratedAsset asset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", asset.id);
        body.put("eventId", asset.eventId);
        body.put("assetKey", asset.assetKey);
        body.put("assetType", asset.assetType);
        body.put("sectionId", asset.sectionId);
        body.put("targetNodeId", asset.targetNodeId);
        body.put("mediaId", asset.mediaId);
        body.put("version", asset.version);
        body.put("mimeType", asset.mimeType);
        body.put("fileSize", asset.fileSize);
        body.put("width", asset.width);
        body.put("height", asset.height);
        body.put("aspectRatio", asset.aspectRatio);
        body.put("purpose", asset.purpose);
        body.put("prompt", asset.prompt);
        body.put("provider", asset.provider);
        body.put("modelId", asset.modelId);
        body.put("status", asset.status);
        body.put("estimatedCostUsd", asset.estimatedCostUsd);
        body.put("actualCostUsd", asset.actualCostUsd);
        body.put("errorMessage", asset.errorMessage);
        body.put("url", asset.mediaId == null ? null : "/api/v1/events/" + asset.eventId + "/media/" + asset.mediaId);
        body.put("createdAt", asset.createdAt);
        body.put("updatedAt", asset.updatedAt);
        return body;
    }

    private static String label(ImageGenerationJob job) {
        return switch (job.assetType) {
            case "HERO" -> "Generating hero image";
            case "BACKGROUND" -> "Generating background image";
            case "GALLERY" -> "Generating gallery image";
            case "ILLUSTRATION" -> "Generating illustration";
            default -> "Generating image";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
