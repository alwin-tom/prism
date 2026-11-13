package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class DesignReferenceRetriever {

    private final DesignReferenceRepository references;
    private final DesignReferenceService service;
    private final DesignReferenceRetrievalLogRepository logs;
    private final DesignReferenceEmbeddingService embeddings;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper mapper;

    public DesignReferenceRetriever(
            DesignReferenceRepository references,
            DesignReferenceService service,
            DesignReferenceRetrievalLogRepository logs,
            DesignReferenceEmbeddingService embeddings,
            EmbeddingClient embeddingClient,
            ObjectMapper mapper
    ) {
        this.references = references;
        this.service = service;
        this.logs = logs;
        this.embeddings = embeddings;
        this.embeddingClient = embeddingClient;
        this.mapper = mapper;
    }

    public Result retrieve(String category, String preferredArchetype, JsonNode answers, UUID userId) {
        String code = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        String archetype = preferredArchetype == null ? "" : preferredArchetype.trim();
        List<DesignReference> candidates = references.findByStatusOrderByQualityScoreDescNameAsc("APPROVED").stream()
                .filter(row -> !"INSPIRATION_LINK".equals(row.sourceType))
                .filter(row -> code.isBlank()
                        || code.equalsIgnoreCase(row.eventCategory)
                        || "GENERAL".equalsIgnoreCase(row.eventCategory)
                        || compatibleFallback(code, row.eventCategory))
                .toList();
        if (candidates.isEmpty()) {
            return new Result(List.of(), "sql", mapper.createArrayNode());
        }

        Set<UUID> recent = recentIds(code);
        List<Scored> scored = new ArrayList<>();
        for (DesignReference row : candidates) {
            double score = row.qualityScore == null ? 5.0 : row.qualityScore.doubleValue();
            if (!archetype.isBlank() && archetype.equalsIgnoreCase(row.archetypeId)) {
                score += 2.5;
            }
            score += tagOverlap(row, answers) * 0.6;
            if (recent.contains(row.id)) {
                score -= 1.5;
            }
            scored.add(new Scored(row, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        String mode = "sql";
        List<DesignReference> selected = new ArrayList<>();
        if (embeddings.enabled() && embeddingClient.available()) {
            String query = queryText(category, preferredArchetype, answers);
            var vector = embeddingClient.embed(query);
            if (vector.isPresent()) {
                List<UUID> similar = embeddings.similarIds(vector.get(), 12);
                Set<UUID> allowed = new HashSet<>();
                scored.stream().limit(20).forEach(item -> allowed.add(item.row().id));
                for (UUID id : similar) {
                    if (allowed.contains(id) && selected.stream().noneMatch(row -> row.id.equals(id))) {
                        references.findById(id).ifPresent(selected::add);
                    }
                    if (selected.size() >= 5) {
                        break;
                    }
                }
                if (!selected.isEmpty()) {
                    mode = "hybrid";
                }
            }
        }
        if (selected.isEmpty()) {
            for (Scored item : scored) {
                selected.add(item.row());
                if (selected.size() >= 5) {
                    break;
                }
            }
        }
        int keep = Math.min(5, Math.max(3, Math.min(selected.size(), 5)));
        if (selected.size() > keep) {
            selected = new ArrayList<>(selected.subList(0, keep));
        }
        ArrayNode compact = mapper.createArrayNode();
        List<UUID> ids = new ArrayList<>();
        for (DesignReference row : selected) {
            ids.add(row.id);
            compact.add(service.compactForPrompt(row));
        }
        if (userId != null && !ids.isEmpty()) {
            service.recordRetrieval(null, userId, code, ids, mode);
        }
        return new Result(ids, mode, compact);
    }

    private Set<UUID> recentIds(String category) {
        Set<UUID> ids = new HashSet<>();
        List<DesignReferenceRetrievalLog> recent = category == null || category.isBlank()
                ? logs.findTop50ByOrderByCreatedAtDesc()
                : logs.findTop20ByEventCategoryOrderByCreatedAtDesc(category);
        for (DesignReferenceRetrievalLog log : recent) {
            if (log.referenceIds == null) {
                continue;
            }
            for (UUID id : log.referenceIds) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static double tagOverlap(DesignReference row, JsonNode answers) {
        if (answers == null) {
            return 0;
        }
        String style = answers.path("visualDirection").asText(answers.path("style").asText("")).toLowerCase(Locale.ROOT);
        String richness = answers.path("visualRichness").asText("").toLowerCase(Locale.ROOT);
        double score = 0;
        if (!style.isBlank() && row.archetypeId != null && style.contains(row.archetypeId.toLowerCase(Locale.ROOT))) {
            score += 2;
        }
        if (row.styleTags != null) {
            for (String tag : row.styleTags) {
                if (!tag.isBlank() && (style.contains(tag.toLowerCase(Locale.ROOT)) || richness.contains(tag.toLowerCase(Locale.ROOT)))) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private static String queryText(String category, String archetype, JsonNode answers) {
        StringBuilder text = new StringBuilder();
        text.append(category == null ? "" : category).append(' ');
        text.append(archetype == null ? "" : archetype).append(' ');
        if (answers != null) {
            text.append(answers.path("visualDirection").asText("")).append(' ');
            text.append(answers.path("style").asText("")).append(' ');
            text.append(answers.path("visualRichness").asText("")).append(' ');
            text.append(answers.path("motionPreference").asText(""));
        }
        return text.toString().trim();
    }

    private static boolean compatibleFallback(String requested, String stored) {
        if ("ENGAGEMENT".equals(requested) && "WEDDING".equals(stored)) {
            return true;
        }
        if ("BABY".equals(requested) && "BIRTHDAY".equals(stored)) {
            return true;
        }
        return "CONFERENCE".equals(requested) && "CORPORATE".equals(stored);
    }

    private record Scored(DesignReference row, double score) {
    }

    public record Result(List<UUID> ids, String mode, ArrayNode compact) {
        public ObjectNode asJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            ArrayNode idNode = node.putArray("referenceIds");
            for (UUID id : ids) {
                idNode.add(id.toString());
            }
            node.put("mode", mode);
            node.set("references", compact);
            return node;
        }
    }
}
