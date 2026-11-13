package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiRuntime {

    private static final short ROW = 1;

    private final PrismProperties properties;
    private final AiModelSettingsRepository settings;
    private final WebsiteGenerationJobRepository jobs;

    public AiRuntime(PrismProperties properties, AiModelSettingsRepository settings, WebsiteGenerationJobRepository jobs) {
        this.properties = properties;
        this.settings = settings;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PrismProperties.OpenRouter forThisRun() {
        PrismProperties.OpenRouter env = properties.getOpenrouter();
        PrismProperties.OpenRouter copy = copy(env);
        settings.findById(ROW).ifPresent(row -> overlay(copy, row));
        return copy;
    }

    @Transactional(readOnly = true)
    public void assertDailyBudget(PrismProperties.OpenRouter settings) {
        BigDecimal limit = settings.getDailyBudgetUsd();
        if (limit.signum() <= 0) {
            return;
        }
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal spent = jobs.costSince(start);
        if (spent != null && spent.compareTo(limit) >= 0) {
            throw ApiException.tooMany("The daily generation budget has been used.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view() {
        PrismProperties.OpenRouter current = forThisRun();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", settings.findById(ROW).isPresent() ? "admin" : "environment");
        body.put("planningModel", current.getPlanningModel());
        body.put("generationModel", current.getGenerationModel());
        body.put("repairModel", current.getRepairModel());
        body.put("planningFallbacks", OpenRouterModels.split(current.getPlanningFallbacks()));
        body.put("generationFallbacks", OpenRouterModels.split(current.getGenerationFallbacks()));
        body.put("repairFallbacks", OpenRouterModels.split(current.getRepairFallbacks()));
        body.put("allowPaidFallback", current.isAllowPaidFallback());
        body.put("maxRetries", current.getMaxRetries());
        body.put("maxRepairAttempts", current.getMaxRepairAttempts());
        body.put("maxFallbacks", current.getMaxFallbacks());
        body.put("maxGenerationsPerUser", properties.getCreation().getMaxGenerationsPerUser());
        settings.findById(ROW).ifPresent(row -> body.put("maxGenerationsPerUser", row.maxGenerationsPerUser));
        body.put("maxRequestsPerGeneration", current.getMaxRequestsPerGeneration());
        body.put("maxCostPerGenerationUsd", current.getMaxCostPerGenerationUsd());
        body.put("dailyBudgetUsd", current.getDailyBudgetUsd());
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        body.put("spentTodayUsd", jobs.costSince(start));
        return body;
    }

    @Transactional
    public Map<String, Object> update(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ApiException.badRequest("Model settings are missing.");
        }
        String planning = requiredModel(body, "planningModel");
        String generation = requiredModel(body, "generationModel");
        String repair = requiredModel(body, "repairModel");
        boolean allowPaid = body.path("allowPaidFallback").asBoolean(false);
        List<String> planningFallbacks = models(body, "planningFallbacks", allowPaid);
        List<String> generationFallbacks = models(body, "generationFallbacks", allowPaid);
        List<String> repairFallbacks = models(body, "repairFallbacks", allowPaid);
        AiModelSettings row = settings.findById(ROW).orElseGet(AiModelSettings::new);
        row.id = ROW;
        row.planningModel = planning;
        row.generationModel = generation;
        row.repairModel = repair;
        row.planningFallbacks = OpenRouterModels.join(planningFallbacks);
        row.generationFallbacks = OpenRouterModels.join(generationFallbacks);
        row.repairFallbacks = OpenRouterModels.join(repairFallbacks);
        row.allowPaidFallback = allowPaid;
        row.maxRetries = bound(body, "maxRetries", 2, 0, OpenRouterModels.MAX_RETRIES);
        row.maxRepairAttempts = bound(body, "maxRepairAttempts", 1, 0, 2);
        row.maxFallbacks = bound(body, "maxFallbacks", 2, 0, OpenRouterModels.MAX_FALLBACKS);
        row.maxGenerationsPerUser = bound(body, "maxGenerationsPerUser", properties.getCreation().getMaxGenerationsPerUser(), 1, 100);
        row.maxRequestsPerGeneration = bound(body, "maxRequestsPerGeneration", 8, 1, OpenRouterModels.MAX_REQUESTS);
        row.maxCostPerGenerationUsd = money(body, "maxCostPerGenerationUsd");
        row.dailyBudgetUsd = money(body, "dailyBudgetUsd");
        row.updatedAt = Instant.now();
        settings.save(row);
        return view();
    }

    public int generationLimit(PrismProperties.OpenRouter ignored) {
        return settings.findById(ROW).map(row -> row.maxGenerationsPerUser).orElse(Math.max(1, properties.getCreation().getMaxGenerationsPerUser()));
    }

    private PrismProperties.OpenRouter copy(PrismProperties.OpenRouter env) {
        PrismProperties.OpenRouter copy = new PrismProperties.OpenRouter();
        copy.setApiKey(env.getApiKey());
        copy.setBaseUrl(env.getBaseUrl());
        copy.setPlanningModel(env.getPlanningModel());
        copy.setGenerationModel(env.getGenerationModel());
        copy.setRepairModel(env.getRepairModel());
        copy.setPlanningFallbacks(env.getPlanningFallbacks());
        copy.setGenerationFallbacks(env.getGenerationFallbacks());
        copy.setRepairFallbacks(env.getRepairFallbacks());
        copy.setAllowPaidFallback(env.isAllowPaidFallback());
        copy.setTimeoutSeconds(env.getTimeoutSeconds());
        copy.setMaxRetries(Math.min(OpenRouterModels.MAX_RETRIES, Math.max(0, env.getMaxRetries())));
        copy.setMaxRepairAttempts(Math.min(2, Math.max(0, env.getMaxRepairAttempts())));
        copy.setMaxFallbacks(Math.min(OpenRouterModels.MAX_FALLBACKS, Math.max(0, env.getMaxFallbacks())));
        copy.setMaxRequestsPerGeneration(env.getMaxRequestsPerGeneration() <= 0 ? 8 : Math.min(OpenRouterModels.MAX_REQUESTS, env.getMaxRequestsPerGeneration()));
        copy.setMaxOutputTokens(env.getMaxOutputTokens());
        copy.setMaxCostPerGenerationUsd(env.getMaxCostPerGenerationUsd());
        copy.setDailyBudgetUsd(env.getDailyBudgetUsd());
        return copy;
    }

    private void overlay(PrismProperties.OpenRouter copy, AiModelSettings row) {
        copy.setPlanningModel(row.planningModel);
        copy.setGenerationModel(row.generationModel);
        copy.setRepairModel(row.repairModel);
        copy.setPlanningFallbacks(row.planningFallbacks);
        copy.setGenerationFallbacks(row.generationFallbacks);
        copy.setRepairFallbacks(row.repairFallbacks);
        copy.setAllowPaidFallback(row.allowPaidFallback);
        copy.setMaxRetries(row.maxRetries);
        copy.setMaxRepairAttempts(row.maxRepairAttempts);
        copy.setMaxFallbacks(row.maxFallbacks);
        copy.setMaxRequestsPerGeneration(row.maxRequestsPerGeneration);
        copy.setMaxCostPerGenerationUsd(row.maxCostPerGenerationUsd);
        copy.setDailyBudgetUsd(row.dailyBudgetUsd);
    }

    private String requiredModel(JsonNode body, String field) {
        String model = body.path(field).asText("").trim();
        if (!OpenRouterModels.validId(model)) {
            throw ApiException.badRequest("Enter a model id for " + field + ".");
        }
        return model;
    }

    private List<String> models(JsonNode body, String field, boolean allowPaid) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw ApiException.badRequest(field + " must be a list of model ids.");
        }
        java.util.ArrayList<String> models = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String model = item.asText("").trim();
            if (!OpenRouterModels.validId(model)) {
                throw ApiException.badRequest("Enter a model id for " + field + ".");
            }
            if (OpenRouterModels.isPaid(model) && !allowPaid) {
                throw ApiException.badRequest("Paid fallbacks stay off until paid fallback is enabled.");
            }
            models.add(model);
        }
        if (models.size() > OpenRouterModels.MAX_FALLBACKS) {
            throw ApiException.badRequest("Use at most " + OpenRouterModels.MAX_FALLBACKS + " fallback models.");
        }
        return models;
    }

    private int bound(JsonNode body, String field, int fallback, int min, int max) {
        int value = body.has(field) && body.get(field).canConvertToInt() ? body.get(field).asInt() : fallback;
        return Math.min(max, Math.max(min, value));
    }

    private BigDecimal money(JsonNode body, String field) {
        BigDecimal value = PrismSchemaCheck.money(body.get(field));
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(new BigDecimal("1000")) > 0) {
            return new BigDecimal("1000");
        }
        return value;
    }
}
