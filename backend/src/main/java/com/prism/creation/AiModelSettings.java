package com.prism.creation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_model_settings")
public class AiModelSettings {

    @Id
    public Short id;

    @Column(nullable = false, length = 160)
    public String planningModel;

    @Column(nullable = false, length = 160)
    public String generationModel;

    @Column(nullable = false, length = 160)
    public String repairModel;

    @Column(nullable = false, length = 800)
    public String planningFallbacks;

    @Column(nullable = false, length = 800)
    public String generationFallbacks;

    @Column(nullable = false, length = 800)
    public String repairFallbacks;

    @Column(nullable = false)
    public boolean allowPaidFallback;

    @Column(nullable = false)
    public int maxRetries;

    @Column(nullable = false)
    public int maxRepairAttempts;

    @Column(nullable = false)
    public int maxFallbacks;

    @Column(nullable = false)
    public int maxGenerationsPerUser;

    @Column(nullable = false)
    public int maxRequestsPerGeneration;

    @Column(nullable = false, precision = 14, scale = 8)
    public BigDecimal maxCostPerGenerationUsd;

    @Column(nullable = false, precision = 14, scale = 8)
    public BigDecimal dailyBudgetUsd;

    @Column(nullable = false)
    public Instant updatedAt;
}
