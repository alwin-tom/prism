package com.prism.moderation;

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
@Table(name = "website_moderation_findings")
public class WebsiteModerationFinding {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID reportId;

    @Column(nullable = false, length = 40)
    public String category;

    @Column(nullable = false, length = 16)
    public String severity;

    @Column(nullable = false, precision = 5, scale = 4)
    public BigDecimal confidence = BigDecimal.ZERO;

    @Column(length = 120)
    public String pageId;

    @Column(length = 120)
    public String sectionId;

    @Column(length = 120)
    public String elementId;

    @Column(length = 40)
    public String elementType;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String matchedText;

    public Integer startOffset;

    public Integer endOffset;

    @Column(columnDefinition = "TEXT")
    public String contextText;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String reason;

    @Column(columnDefinition = "TEXT")
    public String suggestion;

    @Column(nullable = false, length = 32)
    public String reviewStatus = "PENDING";

    @Column(columnDefinition = "TEXT")
    public String administratorNote;

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
        if (reviewStatus == null) {
            reviewStatus = "PENDING";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
