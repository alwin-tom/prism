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
@Table(name = "generated_assets")
public class GeneratedAsset {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    public UUID websiteGenerationJobId;

    @Column(nullable = false, length = 80)
    public String assetKey;

    @Column(nullable = false, length = 40)
    public String assetType;

    @Column(length = 80)
    public String sectionId;

    @Column(length = 80)
    public String targetNodeId;

    public UUID mediaId;

    @Column(nullable = false)
    public int version = 1;

    public String originalFilename;

    public String storedFilename;

    @Column(length = 80)
    public String mimeType;

    public Long fileSize;

    public Integer width;

    public Integer height;

    @Column(length = 20)
    public String aspectRatio;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String prompt;

    @Column(columnDefinition = "TEXT")
    public String purpose;

    @Column(nullable = false, length = 40)
    public String provider = "openrouter";

    @Column(length = 160)
    public String modelId;

    @Column(nullable = false, length = 40)
    public String status;

    public BigDecimal estimatedCostUsd;

    public BigDecimal actualCostUsd;

    @Column(nullable = false, length = 8)
    public String currency = "USD";

    @Column(name = "base64_content", columnDefinition = "TEXT")
    public String base64Content;

    @Column(columnDefinition = "TEXT")
    public String errorMessage;

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
