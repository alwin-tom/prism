package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DesignReferenceService {

    private final DesignReferenceRepository references;
    private final DesignReferenceSectionRepository sections;
    private final DesignReferenceMediaRepository mediaRows;
    private final DesignReferenceMediaService media;
    private final DesignReferenceRetrievalLogRepository logs;
    private final ObjectMapper mapper;

    public DesignReferenceService(
            DesignReferenceRepository references,
            DesignReferenceSectionRepository sections,
            DesignReferenceMediaRepository mediaRows,
            DesignReferenceMediaService media,
            DesignReferenceRetrievalLogRepository logs,
            ObjectMapper mapper
    ) {
        this.references = references;
        this.sections = sections;
        this.mediaRows = mediaRows;
        this.media = media;
        this.logs = logs;
        this.mapper = mapper;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", references.count());
        body.put("approved", references.countByStatus("APPROVED"));
        body.put("draft", references.countByStatus("DRAFT"));
        body.put("archived", references.countByStatus("ARCHIVED"));
        body.put("original", references.countBySourceType("ORIGINAL"));
        body.put("inspirationLinks", references.countBySourceType("INSPIRATION_LINK"));
        body.put("byCategory", toCountMap(references.countByCategory()));
        body.put("byArchetype", toCountMap(references.countByArchetype()));
        body.put("mostRetrieved", references.mostRetrieved().stream().limit(10).map(this::summary).toList());
        return body;
    }

    public List<Map<String, Object>> list(String status, String category, String archetype, String sourceType, String q) {
        String st = blank(status, "APPROVED");
        if ("ALL".equalsIgnoreCase(st)) {
            return references.findAll().stream().map(this::summary).toList();
        }
        return references.search(st, blank(category, ""), blank(archetype, ""), blank(sourceType, ""), blank(q, ""))
                .stream().map(this::summary).toList();
    }

    public Map<String, Object> get(UUID id) {
        DesignReference row = require(id);
        Map<String, Object> body = detail(row);
        body.put("sections", sections.findByReferenceIdOrderBySortOrderAsc(row.id).stream().map(this::sectionView).toList());
        return body;
    }

    @Transactional
    public Map<String, Object> create(JsonNode request, UserPrincipal actor) {
        DesignReference row = new DesignReference();
        row.seedKey = "manual-" + UUID.randomUUID().toString().substring(0, 8);
        apply(row, request, true);
        if (row.status == null || row.status.isBlank()) {
            row.status = "DRAFT";
        }
        DesignReference saved = references.save(row);
        replaceSections(saved.id, request.path("sections"));
        return get(saved.id);
    }

    @Transactional
    public Map<String, Object> update(UUID id, JsonNode request) {
        DesignReference row = require(id);
        apply(row, request, false);
        references.save(row);
        if (request.has("sections")) {
            replaceSections(row.id, request.path("sections"));
        }
        return get(row.id);
    }

    @Transactional
    public Map<String, Object> setStatus(UUID id, String status) {
        DesignReference row = require(id);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "APPROVED", "ARCHIVED").contains(next)) {
            throw ApiException.badRequest("Status must be DRAFT, APPROVED, or ARCHIVED.");
        }
        row.status = next;
        references.save(row);
        return get(row.id);
    }

    @Transactional
    public void delete(UUID id) {
        DesignReference row = require(id);
        sections.deleteByReferenceId(row.id);
        references.delete(row);
    }

    @Transactional
    public Map<String, Object> uploadScreenshot(UUID id, String kind, MultipartFile file) throws IOException {
        DesignReference row = require(id);
        DesignReferenceMedia stored = media.store(file, kind);
        if ("DESKTOP".equalsIgnoreCase(kind)) {
            row.desktopMediaId = stored.id;
        } else if ("MOBILE".equalsIgnoreCase(kind)) {
            row.mobileMediaId = stored.id;
        }
        references.save(row);
        return get(row.id);
    }

    public DesignReference require(UUID id) {
        return references.findById(id).orElseThrow(() -> ApiException.notFound("That design reference was not found."));
    }

    public Map<String, Object> summary(DesignReference row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id);
        body.put("seedKey", row.seedKey);
        body.put("name", row.name);
        body.put("description", row.description);
        body.put("status", row.status);
        body.put("sourceType", row.sourceType);
        body.put("eventCategory", row.eventCategory);
        body.put("archetypeId", row.archetypeId);
        body.put("mood", Arrays.asList(row.mood == null ? new String[0] : row.mood));
        body.put("styleTags", Arrays.asList(row.styleTags == null ? new String[0] : row.styleTags));
        body.put("qualityScore", row.qualityScore);
        body.put("retrievalCount", row.retrievalCount);
        body.put("inspirationUrl", row.inspirationUrl);
        body.put("hasDesktopScreenshot", row.desktopMediaId != null);
        body.put("hasMobileScreenshot", row.mobileMediaId != null);
        body.put("updatedAt", row.updatedAt);
        return body;
    }

    public Map<String, Object> detail(DesignReference row) {
        Map<String, Object> body = summary(row);
        body.put("colourPalette", row.colourPalette);
        body.put("typography", row.typography);
        body.put("layout", row.layout);
        body.put("patternJson", row.patternJson);
        body.put("componentMap", row.componentMap);
        body.put("missingComponents", Arrays.asList(row.missingComponents == null ? new String[0] : row.missingComponents));
        body.put("keywords", Arrays.asList(row.keywords == null ? new String[0] : row.keywords));
        body.put("attribution", row.attribution);
        body.put("usagePermission", row.usagePermission);
        body.put("desktopMediaId", row.desktopMediaId);
        body.put("mobileMediaId", row.mobileMediaId);
        body.put("embeddingModel", row.embeddingModel);
        body.put("embeddingUpdatedAt", row.embeddingUpdatedAt);
        body.put("createdAt", row.createdAt);
        if (row.desktopMediaId != null) {
            mediaRows.findById(row.desktopMediaId).ifPresent(m -> body.put("desktopMedia", media.view(m)));
        }
        if (row.mobileMediaId != null) {
            mediaRows.findById(row.mobileMediaId).ifPresent(m -> body.put("mobileMedia", media.view(m)));
        }
        return body;
    }

    public ObjectNode compactForPrompt(DesignReference row) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", row.id.toString());
        node.put("name", row.name);
        node.put("archetypeId", row.archetypeId);
        node.put("eventCategory", row.eventCategory);
        node.put("hero", row.patternJson == null ? "" : row.patternJson.path("hero").asText(""));
        node.put("sectionRhythm", row.patternJson == null ? "" : row.patternJson.path("sectionRhythm").asText(""));
        node.put("density", row.patternJson == null ? "" : row.patternJson.path("density").asText(""));
        node.set("colourPalette", row.colourPalette == null ? mapper.createObjectNode() : row.colourPalette);
        node.set("typography", row.typography == null ? mapper.createObjectNode() : row.typography);
        node.set("layout", row.layout == null ? mapper.createObjectNode() : row.layout);
        node.set("componentMap", row.componentMap == null ? mapper.createObjectNode() : row.componentMap);
        ArrayNode moods = node.putArray("mood");
        for (String mood : row.mood == null ? new String[0] : row.mood) {
            moods.add(mood);
        }
        ArrayNode sectionKeys = node.putArray("sections");
        for (DesignReferenceSection section : sections.findByReferenceIdOrderBySortOrderAsc(row.id)) {
            ObjectNode item = sectionKeys.addObject();
            item.put("key", section.sectionKey);
            item.put("purpose", section.purpose);
            item.put("composition", section.composition);
            item.put("variant", section.patternJson == null ? "" : section.patternJson.path("variant").asText(""));
        }
        return node;
    }

    @Transactional
    public void recordRetrieval(UUID jobId, UUID userId, String category, List<UUID> ids, String mode) {
        DesignReferenceRetrievalLog log = new DesignReferenceRetrievalLog();
        log.jobId = jobId;
        log.userId = userId;
        log.eventCategory = category;
        log.referenceIds = ids.toArray(UUID[]::new);
        log.retrievalMode = mode == null ? "sql" : mode;
        logs.save(log);
        for (UUID id : ids) {
            references.findById(id).ifPresent(row -> {
                row.retrievalCount = row.retrievalCount + 1;
                references.save(row);
            });
        }
    }

    private void apply(DesignReference row, JsonNode request, boolean creating) {
        if (request.path("name").isTextual()) {
            row.name = clip(request.path("name").asText(), 160);
        } else if (creating) {
            throw ApiException.badRequest("Name is required.");
        }
        if (request.path("description").isTextual()) {
            row.description = clip(request.path("description").asText(), 4000);
        }
        if (request.path("status").isTextual()) {
            row.status = request.path("status").asText().trim().toUpperCase(Locale.ROOT);
        }
        if (request.path("sourceType").isTextual()) {
            row.sourceType = request.path("sourceType").asText().trim().toUpperCase(Locale.ROOT);
        } else if (creating) {
            row.sourceType = "ORIGINAL";
        }
        if (request.path("eventCategory").isTextual()) {
            row.eventCategory = request.path("eventCategory").asText().trim().toUpperCase(Locale.ROOT);
        } else if (creating) {
            throw ApiException.badRequest("Event category is required.");
        }
        if (request.path("archetypeId").isTextual()) {
            row.archetypeId = request.path("archetypeId").asText().trim();
        } else if (creating) {
            throw ApiException.badRequest("Archetype is required.");
        }
        if (request.path("mood").isArray()) {
            row.mood = textArray(request.path("mood"));
        }
        if (request.path("styleTags").isArray()) {
            row.styleTags = textArray(request.path("styleTags"));
        }
        if (request.path("colourPalette").isObject()) {
            row.colourPalette = request.path("colourPalette");
        } else if (creating) {
            row.colourPalette = mapper.createObjectNode();
        }
        if (request.path("typography").isObject()) {
            row.typography = request.path("typography");
        } else if (creating) {
            row.typography = mapper.createObjectNode();
        }
        if (request.path("layout").isObject()) {
            row.layout = request.path("layout");
        } else if (creating) {
            row.layout = mapper.createObjectNode();
        }
        if (request.path("patternJson").isObject()) {
            row.patternJson = request.path("patternJson");
        } else if (creating) {
            row.patternJson = mapper.createObjectNode();
        }
        if (request.path("componentMap").isObject()) {
            row.componentMap = request.path("componentMap");
        } else if (creating) {
            row.componentMap = mapper.createObjectNode();
        }
        if (request.path("missingComponents").isArray()) {
            row.missingComponents = textArray(request.path("missingComponents"));
        }
        if (request.path("keywords").isArray()) {
            row.keywords = textArray(request.path("keywords"));
        }
        if (request.path("attribution").isTextual()) {
            row.attribution = clip(request.path("attribution").asText(), 500);
        }
        if (request.path("usagePermission").isTextual()) {
            row.usagePermission = clip(request.path("usagePermission").asText(), 40);
        }
        if (request.path("inspirationUrl").isTextual()) {
            row.inspirationUrl = clip(request.path("inspirationUrl").asText(), 500);
        }
        if (request.path("qualityScore").isNumber()) {
            BigDecimal score = request.path("qualityScore").decimalValue();
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
                throw ApiException.badRequest("Quality score must be between 0 and 10.");
            }
            row.qualityScore = score;
        }
    }

    private void replaceSections(UUID referenceId, JsonNode array) {
        sections.deleteByReferenceId(referenceId);
        if (array == null || !array.isArray()) {
            return;
        }
        int order = 0;
        for (JsonNode item : array) {
            DesignReferenceSection section = new DesignReferenceSection();
            section.referenceId = referenceId;
            section.sectionKey = clip(item.path("sectionKey").asText("section-" + order), 80);
            section.purpose = clip(item.path("purpose").asText(""), 500);
            section.composition = clip(item.path("composition").asText(""), 120);
            section.patternJson = item.path("patternJson").isObject() ? item.path("patternJson") : mapper.createObjectNode();
            section.componentMap = item.path("componentMap").isObject() ? item.path("componentMap") : mapper.createObjectNode();
            section.sortOrder = item.path("sortOrder").asInt(order);
            sections.save(section);
            order++;
        }
    }

    private Map<String, Object> sectionView(DesignReferenceSection section) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", section.id);
        body.put("sectionKey", section.sectionKey);
        body.put("purpose", section.purpose);
        body.put("composition", section.composition);
        body.put("patternJson", section.patternJson);
        body.put("componentMap", section.componentMap);
        body.put("sortOrder", section.sortOrder);
        return body;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static String[] textArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value.length() > 80 ? value.substring(0, 80) : value);
            }
        }
        return values.toArray(String[]::new);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
