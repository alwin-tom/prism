package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.media.MediaService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GeneratedAssetService {

    private final PrismProperties properties;
    private final EventService events;
    private final GeneratedAssetRepository assets;
    private final ImageGenerationJobRepository jobs;
    private final ImageGenerationJobService jobService;
    private final ImageGenerationOrchestrator orchestrator;
    private final ImagePlanningService planning;
    private final ImageModelDiscoveryService discovery;
    private final ImageDocumentBinder binder;
    private final MediaService media;
    private final ObjectMapper mapper;

    public GeneratedAssetService(
            PrismProperties properties,
            EventService events,
            GeneratedAssetRepository assets,
            ImageGenerationJobRepository jobs,
            ImageGenerationJobService jobService,
            ImageGenerationOrchestrator orchestrator,
            ImagePlanningService planning,
            ImageModelDiscoveryService discovery,
            ImageDocumentBinder binder,
            MediaService media,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.events = events;
        this.assets = assets;
        this.jobs = jobs;
        this.jobService = jobService;
        this.orchestrator = orchestrator;
        this.planning = planning;
        this.discovery = discovery;
        this.binder = binder;
        this.media = media;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID eventId, UserPrincipal principal) {
        events.requireOwner(eventId, principal);
        return assets.findByEventIdOrderByCreatedAtDesc(eventId).stream().map(jobService::viewAsset).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID eventId, UUID assetId, UserPrincipal principal) {
        events.requireOwner(eventId, principal);
        GeneratedAsset asset = assets.findByIdAndEventId(assetId, eventId)
                .orElseThrow(() -> ApiException.notFound("That generated asset was not found."));
        return jobService.viewAsset(asset);
    }

    @Transactional
    public Map<String, Object> generate(UUID eventId, JsonNode body, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        ImagePlanItem plan = singlePlan(body);
        enforceWebsiteLimit(event.id);
        ImageGenerationJob job = orchestrator.generateOne(event.id, plan);
        return jobService.viewJob(job);
    }

    @Transactional
    public Map<String, Object> regenerate(UUID eventId, UUID assetId, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        GeneratedAsset previous = assets.findByIdAndEventId(assetId, eventId)
                .orElseThrow(() -> ApiException.notFound("That generated asset was not found."));
        ImagePlanItem plan = new ImagePlanItem(
                previous.assetKey,
                previous.assetType,
                previous.sectionId == null ? "" : previous.sectionId,
                previous.targetNodeId == null ? "" : previous.targetNodeId,
                previous.purpose == null ? "" : previous.purpose,
                previous.prompt,
                previous.aspectRatio == null ? "16:9" : previous.aspectRatio,
                "HIGH",
                true
        );
        ImageGenerationJob job = orchestrator.regenerate(event.id, previous, plan);
        return jobService.viewJob(job);
    }

    @Transactional
    public void delete(UUID eventId, UUID assetId, UserPrincipal principal) throws IOException {
        EventSite event = events.requireOwner(eventId, principal);
        GeneratedAsset asset = assets.findByIdAndEventId(assetId, eventId)
                .orElseThrow(() -> ApiException.notFound("That generated asset was not found."));
        UUID mediaId = asset.mediaId;
        if (mediaId != null) {
            binder.unbindMedia(event.id, mediaId);
            media.delete(event, mediaId);
        }
        asset.mediaId = null;
        asset.status = "CANCELLED";
        asset.errorMessage = "Deleted.";
        assets.save(asset);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(UUID eventId, UserPrincipal principal) {
        events.requireOwner(eventId, principal);
        return jobs.findByEventIdOrderByCreatedAtDesc(eventId).stream().map(jobService::viewJob).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID eventId, UUID jobId, UserPrincipal principal) {
        events.requireOwner(eventId, principal);
        ImageGenerationJob job = jobs.findByIdAndEventId(jobId, eventId)
                .orElseThrow(() -> ApiException.notFound("That image job was not found."));
        return jobService.viewJob(job);
    }

    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        PrismProperties.Image config = properties.getImage();
        boolean configured = properties.getOpenrouter().getApiKey() != null && !properties.getOpenrouter().getApiKey().isBlank();
        body.put("enabled", config.isEnabled() && configured);
        body.put("configured", configured);
        body.put("provider", config.getProvider());
        body.put("freeFirst", config.isFreeFirst());
        body.put("paidFallbackEnabled", config.isPaidFallbackEnabled());
        body.put("maxImagesPerWebsite", config.getMaxImagesPerWebsite());
        body.put("maxCostPerImageUsd", config.getMaxCostPerImageUsd());
        body.put("maxCostPerWebsiteUsd", config.getMaxCostPerWebsiteUsd());
        body.put("modelCount", discovery.list().size());
        return body;
    }

    public List<Map<String, Object>> models() {
        return discovery.list().stream().map(card -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", card.id());
            row.put("name", card.name());
            row.put("description", card.description());
            row.put("free", card.free());
            row.put("priceKnown", card.priceKnown());
            row.put("pricePerImageUsd", card.pricePerImageUsd());
            row.put("supportedAspectRatios", card.supportedAspectRatios());
            return row;
        }).toList();
    }

    private ImagePlanItem singlePlan(JsonNode body) {
        if (body == null || body.isNull()) {
            throw ApiException.badRequest("Provide an image plan.");
        }
        JsonNode source = body;
        if (body.isObject() && !body.has("imagePlan") && body.has("prompt")) {
            ObjectNode wrapper = mapper.createObjectNode();
            ArrayNode array = wrapper.putArray("imagePlan");
            array.add(body);
            source = wrapper;
        }
        List<ImagePlanItem> items = planning.parse(source, 1);
        if (items.isEmpty()) {
            throw ApiException.badRequest("The image plan is not valid.");
        }
        return items.getFirst();
    }

    private void enforceWebsiteLimit(UUID eventId) {
        long active = assets.countByEventIdAndStatusIn(eventId, List.of("PENDING", "QUEUED", "GENERATING", "PROCESSING", "COMPLETED"));
        if (active >= properties.getImage().getMaxImagesPerWebsite()) {
            throw ApiException.badRequest("This website already has the maximum number of generated images.");
        }
        BigDecimal spent = assets.sumActualCost(eventId);
        if (spent != null
                && properties.getImage().getMaxCostPerWebsiteUsd().compareTo(BigDecimal.ZERO) > 0
                && spent.compareTo(properties.getImage().getMaxCostPerWebsiteUsd()) >= 0) {
            throw ApiException.badRequest("This website has reached its image-generation budget.");
        }
    }

    private void requireEnabled() {
        if (!properties.getImage().isEnabled()) {
            throw ApiException.forbidden("AI image generation is turned off.");
        }
        if (properties.getOpenrouter().getApiKey() == null || properties.getOpenrouter().getApiKey().isBlank()) {
            throw ApiException.forbidden("Image generation is not configured.");
        }
    }
}
