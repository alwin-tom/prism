CREATE TABLE generated_assets (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    website_generation_job_id UUID REFERENCES website_generation_jobs (id) ON DELETE SET NULL,
    asset_key VARCHAR(80) NOT NULL,
    asset_type VARCHAR(40) NOT NULL,
    section_id VARCHAR(80),
    target_node_id VARCHAR(80),
    media_id UUID REFERENCES event_media (id) ON DELETE SET NULL,
    version INT NOT NULL DEFAULT 1,
    original_filename VARCHAR(255),
    stored_filename VARCHAR(255),
    mime_type VARCHAR(80),
    file_size BIGINT,
    width INT,
    height INT,
    aspect_ratio VARCHAR(20),
    prompt TEXT NOT NULL,
    purpose TEXT,
    provider VARCHAR(40) NOT NULL DEFAULT 'openrouter',
    model_id VARCHAR(160),
    status VARCHAR(40) NOT NULL,
    estimated_cost_usd NUMERIC(12, 6),
    actual_cost_usd NUMERIC(12, 6),
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    base64_content TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_generated_asset_type CHECK (asset_type IN (
        'HERO', 'BACKGROUND', 'GALLERY', 'ILLUSTRATION', 'OTHER'
    )),
    CONSTRAINT ck_generated_asset_status CHECK (status IN (
        'PENDING', 'QUEUED', 'GENERATING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED'
    )),
    CONSTRAINT uq_generated_asset_key UNIQUE (event_id, asset_key, version)
);
CREATE INDEX idx_generated_assets_event ON generated_assets (event_id, created_at DESC);
CREATE INDEX idx_generated_assets_status ON generated_assets (event_id, status);
CREATE INDEX idx_generated_assets_job ON generated_assets (website_generation_job_id);

CREATE TABLE image_generation_jobs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    generated_asset_id UUID NOT NULL REFERENCES generated_assets (id) ON DELETE CASCADE,
    website_generation_job_id UUID REFERENCES website_generation_jobs (id) ON DELETE SET NULL,
    asset_key VARCHAR(80) NOT NULL,
    prompt TEXT NOT NULL,
    asset_type VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL DEFAULT 'openrouter',
    model_id VARCHAR(160),
    status VARCHAR(40) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 2,
    estimated_cost_usd NUMERIC(12, 6),
    actual_cost_usd NUMERIC(12, 6),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_details TEXT,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_image_job_status CHECK (status IN (
        'PENDING', 'QUEUED', 'GENERATING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED'
    )),
    CONSTRAINT uq_image_job_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_image_jobs_event ON image_generation_jobs (event_id, created_at DESC);
CREATE INDEX idx_image_jobs_status ON image_generation_jobs (status, updated_at);
CREATE INDEX idx_image_jobs_asset ON image_generation_jobs (generated_asset_id);
CREATE INDEX idx_image_jobs_website_job ON image_generation_jobs (website_generation_job_id);

ALTER TABLE website_generation_jobs
    DROP CONSTRAINT ck_generation_status;

ALTER TABLE website_generation_jobs
    ADD CONSTRAINT ck_generation_status CHECK (status IN (
        'QUEUED', 'PLANNING', 'GENERATING', 'VALIDATING', 'REPAIRING', 'IMAGES', 'COMPLETED', 'FAILED', 'CANCELLED'
    ));
