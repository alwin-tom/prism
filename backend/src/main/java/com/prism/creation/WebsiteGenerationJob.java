package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_generation_jobs")
public class WebsiteGenerationJob {

    @Id
    public UUID id;

    public UUID sessionId;

    @Column(nullable = false)
    public UUID userId;

    public UUID eventId;

    @Column(nullable = false, length = 40)
    public String status;

    @Column(length = 120)
    public String model;

    @Column(nullable = false, length = 40)
    public String promptVersion;

    @Column(columnDefinition = "text")
    public String promptSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode planningResponseJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode generationResponseJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode generatedDocumentJson;

    @Column(columnDefinition = "text")
    public String errorMessage;

    @Column(nullable = false)
    public int inputTokens;

    @Column(nullable = false)
    public int outputTokens;

    @Column(precision = 14, scale = 8)
    public BigDecimal costUsd;

    public Long latencyMs;

    @Column(nullable = false)
    public int requestCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode usageJson;

    public Instant startedAt;

    public Instant completedAt;

    @Column(nullable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
