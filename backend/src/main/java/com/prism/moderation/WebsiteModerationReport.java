package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_moderation_reports")
public class WebsiteModerationReport {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public UUID revisionId;

    @Column(nullable = false)
    public int revisionNumber;

    @Column(nullable = false, length = 32)
    public String status;

    @Column(nullable = false, length = 16)
    public String overallRisk = "CLEAN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode summaryJson;

    @Column(columnDefinition = "TEXT")
    public String explanation;

    @Column(nullable = false, length = 40)
    public String provider = "openrouter";

    @Column(length = 160)
    public String modelId;

    @Column(nullable = false)
    public int attemptCount;

    @Column(nullable = false)
    public int maxAttempts = 3;

    public Instant startedAt;

    public Instant completedAt;

    public Long durationMs;

    @Column(columnDefinition = "TEXT")
    public String errorDetails;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = "PENDING";
        }
        if (overallRisk == null) {
            overallRisk = "CLEAN";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
