package com.prism.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "prism")
public class PrismProperties {

    private boolean otpBypassEnabled;
    private boolean paymentBypassEnabled;
    private int dailyHostingPricePaise = 50_000;
    private String defaultCurrency = "INR";
    private String publicBaseUrl = "http://localhost:5173";
    private String apiBaseUrl = "http://localhost:8080";
    private final Scheduling scheduling = new Scheduling();
    private final Security security = new Security();
    private final Jwt jwt = new Jwt();
    private final Media media = new Media();
    private final Whatsapp whatsapp = new Whatsapp();
    private final Razorpay razorpay = new Razorpay();
    private final Admin admin = new Admin();

    private final Creation creation = new Creation();
    private final OpenRouter openrouter = new OpenRouter();
    private final Image image = new Image();
    private final UiModels uiModels = new UiModels();

    public Creation getCreation() {
        return creation;
    }

    public OpenRouter getOpenrouter() {
        return openrouter;
    }

    public Image getImage() {
        return image;
    }

    public UiModels getUiModels() {
        return uiModels;
    }

    public boolean isOtpBypassEnabled() {
        return otpBypassEnabled;
    }

    public void setOtpBypassEnabled(boolean otpBypassEnabled) {
        this.otpBypassEnabled = otpBypassEnabled;
    }

    public boolean isPaymentBypassEnabled() {
        return paymentBypassEnabled;
    }

    public void setPaymentBypassEnabled(boolean paymentBypassEnabled) {
        this.paymentBypassEnabled = paymentBypassEnabled;
    }

    public int getDailyHostingPricePaise() {
        return dailyHostingPricePaise;
    }

    public void setDailyHostingPricePaise(int dailyHostingPricePaise) {
        this.dailyHostingPricePaise = dailyHostingPricePaise;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public Scheduling getScheduling() {
        return scheduling;
    }

    public Security getSecurity() {
        return security;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Media getMedia() {
        return media;
    }

    public Whatsapp getWhatsapp() {
        return whatsapp;
    }

    public Razorpay getRazorpay() {
        return razorpay;
    }

    public Admin getAdmin() {
        return admin;
    }

    /**
     * Website-design model strategy. Backed by {@code WEBAUX_USE_FREE_UI_MODELS}
     * and premium OpenRouter model ids. Unrelated AI features (moderation, image gen)
     * must not read this block.
     */
    public static class UiModels {
        /** Raw env value. Missing/invalid defaults to free mode via {@link #isUseFreeUiModels()}. */
        private String useFreeUiModels = "true";
        private String premiumPrimaryModel = "anthropic/claude-opus-5.5";
        private String premiumSecondaryModel = "openai/gpt-6-astra";
        private String premiumFallbackModel = "google/gemini-4-carbon";
        private String premiumRefinementModel = "moonshotai/kimi-k3.1-pro";

        public String getUseFreeUiModels() {
            return useFreeUiModels;
        }

        public void setUseFreeUiModels(String useFreeUiModels) {
            this.useFreeUiModels = useFreeUiModels;
        }

        public boolean isUseFreeUiModels() {
            if (useFreeUiModels == null || useFreeUiModels.isBlank()) {
                return true;
            }
            String value = useFreeUiModels.trim().toLowerCase();
            if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) {
                return false;
            }
            return true;
        }

        public String getPremiumPrimaryModel() {
            return premiumPrimaryModel;
        }

        public void setPremiumPrimaryModel(String premiumPrimaryModel) {
            this.premiumPrimaryModel = premiumPrimaryModel;
        }

        public String getPremiumSecondaryModel() {
            return premiumSecondaryModel;
        }

        public void setPremiumSecondaryModel(String premiumSecondaryModel) {
            this.premiumSecondaryModel = premiumSecondaryModel;
        }

        public String getPremiumFallbackModel() {
            return premiumFallbackModel;
        }

        public void setPremiumFallbackModel(String premiumFallbackModel) {
            this.premiumFallbackModel = premiumFallbackModel;
        }

        public String getPremiumRefinementModel() {
            return premiumRefinementModel;
        }

        public void setPremiumRefinementModel(String premiumRefinementModel) {
            this.premiumRefinementModel = premiumRefinementModel;
        }
    }

    public static class Creation {
        private String designEmbeddingProvider = "";
        private String designEmbeddingModel = "";
        private int designEmbeddingDimensions = 1536;
        private int maxGenerationsPerUser = 20;

        public String getDesignEmbeddingProvider() {
            return designEmbeddingProvider;
        }

        public void setDesignEmbeddingProvider(String designEmbeddingProvider) {
            this.designEmbeddingProvider = designEmbeddingProvider;
        }

        public String getDesignEmbeddingModel() {
            return designEmbeddingModel;
        }

        public void setDesignEmbeddingModel(String designEmbeddingModel) {
            this.designEmbeddingModel = designEmbeddingModel;
        }

        public int getDesignEmbeddingDimensions() {
            return designEmbeddingDimensions;
        }

        public void setDesignEmbeddingDimensions(int designEmbeddingDimensions) {
            this.designEmbeddingDimensions = designEmbeddingDimensions;
        }

        public int getMaxGenerationsPerUser() {
            return maxGenerationsPerUser;
        }

        public void setMaxGenerationsPerUser(int maxGenerationsPerUser) {
            this.maxGenerationsPerUser = maxGenerationsPerUser;
        }
    }

    public static class OpenRouter {
        private String apiKey = "";
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String planningModel = "";
        private String generationModel = "";
        private String repairModel = "";
        private String moderationModel = "";
        private String planningFallbacks = "";
        private String generationFallbacks = "";
        private String repairFallbacks = "";
        private String moderationFallbacks = "";
        private boolean allowPaidFallback;
        private int timeoutSeconds = 120;
        private int maxRetries = 2;
        private int maxRepairAttempts = 1;
        private int maxFallbacks = 2;
        private int maxRequestsPerGeneration = 8;
        private int maxOutputTokens = 8000;
        private BigDecimal maxCostPerGenerationUsd = BigDecimal.ZERO;
        private BigDecimal dailyBudgetUsd = BigDecimal.ZERO;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPlanningModel() { return planningModel; }
        public void setPlanningModel(String planningModel) { this.planningModel = planningModel; }
        public String getGenerationModel() { return generationModel; }
        public void setGenerationModel(String generationModel) { this.generationModel = generationModel; }
        public String getRepairModel() { return repairModel; }
        public void setRepairModel(String repairModel) { this.repairModel = repairModel; }
        public String getModerationModel() { return moderationModel; }
        public void setModerationModel(String moderationModel) { this.moderationModel = moderationModel; }
        public String getPlanningFallbacks() { return planningFallbacks; }
        public void setPlanningFallbacks(String planningFallbacks) { this.planningFallbacks = planningFallbacks; }
        public String getGenerationFallbacks() { return generationFallbacks; }
        public void setGenerationFallbacks(String generationFallbacks) { this.generationFallbacks = generationFallbacks; }
        public String getRepairFallbacks() { return repairFallbacks; }
        public void setRepairFallbacks(String repairFallbacks) { this.repairFallbacks = repairFallbacks; }
        public String getModerationFallbacks() { return moderationFallbacks; }
        public void setModerationFallbacks(String moderationFallbacks) { this.moderationFallbacks = moderationFallbacks; }
        public boolean isAllowPaidFallback() { return allowPaidFallback; }
        public void setAllowPaidFallback(boolean allowPaidFallback) { this.allowPaidFallback = allowPaidFallback; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int getMaxRepairAttempts() { return maxRepairAttempts; }
        public void setMaxRepairAttempts(int maxRepairAttempts) { this.maxRepairAttempts = maxRepairAttempts; }
        public int getMaxFallbacks() { return maxFallbacks; }
        public void setMaxFallbacks(int maxFallbacks) { this.maxFallbacks = maxFallbacks; }
        public int getMaxRequestsPerGeneration() { return maxRequestsPerGeneration; }
        public void setMaxRequestsPerGeneration(int maxRequestsPerGeneration) { this.maxRequestsPerGeneration = maxRequestsPerGeneration; }
        public BigDecimal getMaxCostPerGenerationUsd() { return maxCostPerGenerationUsd == null ? BigDecimal.ZERO : maxCostPerGenerationUsd; }
        public void setMaxCostPerGenerationUsd(BigDecimal maxCostPerGenerationUsd) { this.maxCostPerGenerationUsd = maxCostPerGenerationUsd; }
        public BigDecimal getDailyBudgetUsd() { return dailyBudgetUsd == null ? BigDecimal.ZERO : dailyBudgetUsd; }
        public void setDailyBudgetUsd(BigDecimal dailyBudgetUsd) { this.dailyBudgetUsd = dailyBudgetUsd; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    }

    public static class Scheduling {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Security {
        private boolean cookieSecure;

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Jwt {
        private String secret = "";
        private long accessTokenExpiry = 900;
        private long refreshTokenExpiry = 604_800;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getAccessTokenExpiry() {
            return accessTokenExpiry;
        }

        public void setAccessTokenExpiry(long accessTokenExpiry) {
            this.accessTokenExpiry = accessTokenExpiry;
        }

        public long getRefreshTokenExpiry() {
            return refreshTokenExpiry;
        }

        public void setRefreshTokenExpiry(long refreshTokenExpiry) {
            this.refreshTokenExpiry = refreshTokenExpiry;
        }
    }

    public static class Image {
        private boolean enabled;
        private String provider = "openrouter";
        private boolean freeFirst = true;
        private boolean paidFallbackEnabled;
        private int maxImagesPerWebsite = 3;
        private BigDecimal maxCostPerImageUsd = new BigDecimal("0.05");
        private BigDecimal maxCostPerWebsiteUsd = new BigDecimal("0.15");
        private int maxRetries = 2;
        private int modelCacheMinutes = 360;
        private int waitSeconds = 120;
        private boolean base64Enabled;
        private int base64MaxSizeKb = 2048;
        private String preferredModels = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public boolean isFreeFirst() { return freeFirst; }
        public void setFreeFirst(boolean freeFirst) { this.freeFirst = freeFirst; }
        public boolean isPaidFallbackEnabled() { return paidFallbackEnabled; }
        public void setPaidFallbackEnabled(boolean paidFallbackEnabled) { this.paidFallbackEnabled = paidFallbackEnabled; }
        public int getMaxImagesPerWebsite() { return maxImagesPerWebsite; }
        public void setMaxImagesPerWebsite(int maxImagesPerWebsite) { this.maxImagesPerWebsite = maxImagesPerWebsite; }
        public BigDecimal getMaxCostPerImageUsd() { return maxCostPerImageUsd == null ? BigDecimal.ZERO : maxCostPerImageUsd; }
        public void setMaxCostPerImageUsd(BigDecimal maxCostPerImageUsd) { this.maxCostPerImageUsd = maxCostPerImageUsd; }
        public BigDecimal getMaxCostPerWebsiteUsd() { return maxCostPerWebsiteUsd == null ? BigDecimal.ZERO : maxCostPerWebsiteUsd; }
        public void setMaxCostPerWebsiteUsd(BigDecimal maxCostPerWebsiteUsd) { this.maxCostPerWebsiteUsd = maxCostPerWebsiteUsd; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int getModelCacheMinutes() { return modelCacheMinutes; }
        public void setModelCacheMinutes(int modelCacheMinutes) { this.modelCacheMinutes = modelCacheMinutes; }
        public int getWaitSeconds() { return waitSeconds; }
        public void setWaitSeconds(int waitSeconds) { this.waitSeconds = waitSeconds; }
        public boolean isBase64Enabled() { return base64Enabled; }
        public void setBase64Enabled(boolean base64Enabled) { this.base64Enabled = base64Enabled; }
        public int getBase64MaxSizeKb() { return base64MaxSizeKb; }
        public void setBase64MaxSizeKb(int base64MaxSizeKb) { this.base64MaxSizeKb = base64MaxSizeKb; }
        public String getPreferredModels() { return preferredModels; }
        public void setPreferredModels(String preferredModels) { this.preferredModels = preferredModels; }
    }

    public static class Media {
        private String storagePath = "./data/media";
        private int maxImageSizeMb = 10;
        private int maxVideoSizeMb = 100;
        private int retentionDaysAfterExpiry = 90;

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }

        public int getMaxImageSizeMb() {
            return maxImageSizeMb;
        }

        public void setMaxImageSizeMb(int maxImageSizeMb) {
            this.maxImageSizeMb = maxImageSizeMb;
        }

        public int getMaxVideoSizeMb() {
            return maxVideoSizeMb;
        }

        public void setMaxVideoSizeMb(int maxVideoSizeMb) {
            this.maxVideoSizeMb = maxVideoSizeMb;
        }

        public int getRetentionDaysAfterExpiry() {
            return retentionDaysAfterExpiry;
        }

        public void setRetentionDaysAfterExpiry(int retentionDaysAfterExpiry) {
            this.retentionDaysAfterExpiry = retentionDaysAfterExpiry;
        }
    }

    public static class Whatsapp {
        private boolean enabled;
        private String baseUrl = "https://graph.facebook.com";
        private String apiVersion = "";
        private String phoneNumberId = "";
        private String businessAccountId = "";
        private String accessToken = "";
        private String otpTemplateName = "prism_otp";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        public void setPhoneNumberId(String phoneNumberId) {
            this.phoneNumberId = phoneNumberId;
        }

        public String getBusinessAccountId() {
            return businessAccountId;
        }

        public void setBusinessAccountId(String businessAccountId) {
            this.businessAccountId = businessAccountId;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getOtpTemplateName() {
            return otpTemplateName;
        }

        public void setOtpTemplateName(String otpTemplateName) {
            this.otpTemplateName = otpTemplateName;
        }
    }

    public static class Razorpay {
        private boolean enabled;
        private String keyId = "";
        private String keySecret = "";
        private String webhookSecret = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }

    public static class Admin {
        private String initialName = "WebAux Admin";
        private String initialPhone = "";
        private String initialPassword = "";

        public String getInitialName() {
            return initialName;
        }

        public void setInitialName(String initialName) {
            this.initialName = initialName;
        }

        public String getInitialPhone() {
            return initialPhone;
        }

        public void setInitialPhone(String initialPhone) {
            this.initialPhone = initialPhone;
        }

        public String getInitialPassword() {
            return initialPassword;
        }

        public void setInitialPassword(String initialPassword) {
            this.initialPassword = initialPassword;
        }
    }
}
