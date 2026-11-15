package com.prism.imagegen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_generation_jobs")
public class ImageGenerationJob {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public UUID generatedAssetId;

    public UUID websiteGenerationJobId;

    @Column(nullable = false, length = 80)
    public String assetKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String prompt;

    @Column(nullable = false, length = 40)
    public String assetType;

    @Column(nullable = false, length = 40)
    public String provider = "openrouter";

    @Column(length = 160)
    public String modelId;

    @Column(nullable = false, length = 40)
    public String status;

    @Column(nullable = false)
    public int attemptCount;

    @Column(nullable = false)
    public int maxAttempts = 2;

    public BigDecimal estimatedCostUsd;

    public BigDecimal actualCostUsd;

    public Instant startedAt;

    public Instant completedAt;

    @Column(columnDefinition = "TEXT")
    public String errorDetails;

    @Column(nullable = false, length = 120)
    public String idempotencyKey;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
