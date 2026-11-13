ALTER TABLE website_generation_jobs
    ALTER COLUMN model TYPE VARCHAR(160);

ALTER TABLE website_generation_jobs
    ADD COLUMN cost_usd NUMERIC(14, 8),
    ADD COLUMN latency_ms BIGINT,
    ADD COLUMN request_count INT NOT NULL DEFAULT 0,
    ADD COLUMN usage_json JSONB;

CREATE TABLE ai_model_settings (
    id SMALLINT PRIMARY KEY,
    planning_model VARCHAR(160) NOT NULL,
    generation_model VARCHAR(160) NOT NULL,
    repair_model VARCHAR(160) NOT NULL,
    planning_fallbacks VARCHAR(800) NOT NULL DEFAULT '',
    generation_fallbacks VARCHAR(800) NOT NULL DEFAULT '',
    repair_fallbacks VARCHAR(800) NOT NULL DEFAULT '',
    allow_paid_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    max_retries INT NOT NULL,
    max_repair_attempts INT NOT NULL,
    max_fallbacks INT NOT NULL,
    max_generations_per_user INT NOT NULL,
    max_requests_per_generation INT NOT NULL,
    max_cost_per_generation_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    daily_budget_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ai_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_ai_settings_retries CHECK (max_retries BETWEEN 0 AND 2),
    CONSTRAINT ck_ai_settings_repairs CHECK (max_repair_attempts BETWEEN 0 AND 2),
    CONSTRAINT ck_ai_settings_fallbacks CHECK (max_fallbacks BETWEEN 0 AND 2),
    CONSTRAINT ck_ai_settings_requests CHECK (max_requests_per_generation BETWEEN 1 AND 12)
);
