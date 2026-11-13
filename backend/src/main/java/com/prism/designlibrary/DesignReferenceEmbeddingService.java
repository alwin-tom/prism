package com.prism.designlibrary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class DesignReferenceEmbeddingService {

    private final DesignReferenceRepository references;
    private final DesignReferenceService service;
    private final EmbeddingClient embeddings;
    private final JdbcTemplate jdbc;

    public DesignReferenceEmbeddingService(
            DesignReferenceRepository references,
            DesignReferenceService service,
            EmbeddingClient embeddings,
            JdbcTemplate jdbc
    ) {
        this.references = references;
        this.service = service;
        this.embeddings = embeddings;
        this.jdbc = jdbc;
    }

    public boolean enabled() {
        return embeddings.available();
    }

    @Transactional
    public Map<String, Object> embedOne(UUID id) {
        DesignReference row = service.require(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        if (!embeddings.available()) {
            body.put("embedded", false);
            body.put("reason", "Embeddings are disabled or not configured.");
            return body;
        }
        Optional<float[]> vector = embeddings.embed(embedText(row));
        if (vector.isEmpty()) {
            body.put("embedded", false);
            body.put("reason", "The embedding provider returned no vector.");
            return body;
        }
        store(row.id, vector.get(), embeddings.modelName());
        row.embeddingModel = embeddings.modelName();
        row.embeddingUpdatedAt = Instant.now();
        references.save(row);
        body.put("embedded", true);
        body.put("model", embeddings.modelName());
        body.put("dimensions", vector.get().length);
        return body;
    }

    @Transactional
    public Map<String, Object> embedMissing(int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!embeddings.available()) {
            body.put("embedded", 0);
            body.put("reason", "Embeddings are disabled or not configured.");
            return body;
        }
        List<DesignReference> rows = references.findByStatusOrderByQualityScoreDescNameAsc("APPROVED").stream()
                .filter(row -> !"INSPIRATION_LINK".equals(row.sourceType))
                .filter(row -> row.embeddingUpdatedAt == null)
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
        int ok = 0;
        for (DesignReference row : rows) {
            if (Boolean.TRUE.equals(embedOne(row.id).get("embedded"))) {
                ok++;
            }
        }
        body.put("embedded", ok);
        body.put("attempted", rows.size());
        return body;
    }

    public List<UUID> similarIds(float[] query, int limit) {
        if (query == null || query.length == 0) {
            return List.of();
        }
        String literal = toVectorLiteral(query);
        return jdbc.query(
                """
                        select id from design_references
                        where status = 'APPROVED'
                          and source_type <> 'INSPIRATION_LINK'
                          and embedding is not null
                        order by embedding <=> ?::vector
                        limit ?
                        """,
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                literal,
                Math.max(1, Math.min(limit, 20))
        );
    }

    private void store(UUID id, float[] vector, String model) {
        jdbc.update(
                "update design_references set embedding = ?::vector, embedding_model = ?, embedding_updated_at = now() where id = ?",
                toVectorLiteral(vector),
                model,
                id
        );
    }

    static String embedText(DesignReference row) {
        StringJoiner joiner = new StringJoiner(" | ");
        joiner.add(row.name);
        joiner.add(row.description);
        joiner.add(row.eventCategory);
        joiner.add(row.archetypeId);
        if (row.mood != null) {
            joiner.add(String.join(" ", row.mood));
        }
        if (row.styleTags != null) {
            joiner.add(String.join(" ", row.styleTags));
        }
        if (row.keywords != null) {
            joiner.add(String.join(" ", row.keywords));
        }
        if (row.patternJson != null) {
            joiner.add(row.patternJson.path("hero").asText(""));
            joiner.add(row.patternJson.path("sectionRhythm").asText(""));
            joiner.add(row.patternJson.path("density").asText(""));
        }
        return joiner.toString();
    }

    static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
