package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "design_references")
public class DesignReference {

    @Id
    public UUID id;

    @Column(nullable = false, length = 80, unique = true)
    public String seedKey;

    @Column(nullable = false, length = 160)
    public String name;

    @Column(nullable = false, columnDefinition = "text")
    public String description = "";

    @Column(nullable = false, length = 20)
    public String status;

    @Column(nullable = false, length = 30)
    public String sourceType;

    @Column(nullable = false, length = 40)
    public String eventCategory;

    @Column(nullable = false, length = 64)
    public String archetypeId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    public String[] mood = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    public String[] styleTags = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode colourPalette;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode typography;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode layout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode patternJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode componentMap;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    public String[] missingComponents = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    public String[] keywords = new String[0];

    @Column(nullable = false, columnDefinition = "text")
    public String attribution = "";

    @Column(nullable = false, length = 40)
    public String usagePermission = "WEBAUX_ORIGINAL";

    @Column(columnDefinition = "text")
    public String inspirationUrl;

    @Column(nullable = false, precision = 4, scale = 2)
    public BigDecimal qualityScore = new BigDecimal("7.00");

    @Column(nullable = false)
    public int retrievalCount;

    public UUID desktopMediaId;

    public UUID mobileMediaId;

    @Column(length = 120)
    public String embeddingModel;

    public Instant embeddingUpdatedAt;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
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
