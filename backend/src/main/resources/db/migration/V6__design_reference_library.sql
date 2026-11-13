-- Requires pgvector (e.g. image pgvector/pgvector:pg16). Production must install the extension before migrate.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE design_reference_media (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(260) NOT NULL,
    stored_filename VARCHAR(80) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    width INT,
    height INT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_design_ref_media_kind CHECK (kind IN ('DESKTOP', 'MOBILE', 'SECTION')),
    CONSTRAINT ck_design_ref_media_size CHECK (file_size >= 0)
);

CREATE TABLE design_references (
    id UUID PRIMARY KEY,
    seed_key VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    event_category VARCHAR(40) NOT NULL,
    archetype_id VARCHAR(64) NOT NULL,
    mood TEXT[] NOT NULL DEFAULT '{}',
    style_tags TEXT[] NOT NULL DEFAULT '{}',
    colour_palette JSONB NOT NULL DEFAULT '{}'::jsonb,
    typography JSONB NOT NULL DEFAULT '{}'::jsonb,
    layout JSONB NOT NULL DEFAULT '{}'::jsonb,
    pattern_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    component_map JSONB NOT NULL DEFAULT '{}'::jsonb,
    missing_components TEXT[] NOT NULL DEFAULT '{}',
    keywords TEXT[] NOT NULL DEFAULT '{}',
    attribution TEXT NOT NULL DEFAULT '',
    usage_permission VARCHAR(40) NOT NULL DEFAULT 'WEBAUX_ORIGINAL',
    inspiration_url TEXT,
    quality_score NUMERIC(4, 2) NOT NULL DEFAULT 7.00,
    retrieval_count INT NOT NULL DEFAULT 0,
    desktop_media_id UUID REFERENCES design_reference_media (id),
    mobile_media_id UUID REFERENCES design_reference_media (id),
    embedding vector(1536),
    embedding_model VARCHAR(120),
    embedding_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_design_ref_seed_key UNIQUE (seed_key),
    CONSTRAINT ck_design_ref_status CHECK (status IN ('DRAFT', 'APPROVED', 'ARCHIVED')),
    CONSTRAINT ck_design_ref_source CHECK (source_type IN ('ORIGINAL', 'LICENSED', 'INSPIRATION_LINK', 'STITCH')),
    CONSTRAINT ck_design_ref_quality CHECK (quality_score >= 0 AND quality_score <= 10),
    CONSTRAINT ck_design_ref_retrieval CHECK (retrieval_count >= 0)
);

CREATE INDEX idx_design_ref_status ON design_references (status);
CREATE INDEX idx_design_ref_category ON design_references (event_category);
CREATE INDEX idx_design_ref_archetype ON design_references (archetype_id);
CREATE INDEX idx_design_ref_quality ON design_references (quality_score DESC);
CREATE INDEX idx_design_ref_source ON design_references (source_type);
CREATE INDEX idx_design_ref_mood ON design_references USING GIN (mood);
CREATE INDEX idx_design_ref_tags ON design_references USING GIN (style_tags);
CREATE INDEX idx_design_ref_keywords ON design_references USING GIN (keywords);

CREATE TABLE design_reference_sections (
    id UUID PRIMARY KEY,
    reference_id UUID NOT NULL REFERENCES design_references (id) ON DELETE CASCADE,
    section_key VARCHAR(80) NOT NULL,
    purpose TEXT NOT NULL DEFAULT '',
    composition VARCHAR(120) NOT NULL DEFAULT '',
    pattern_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    component_map JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_design_ref_section UNIQUE (reference_id, section_key),
    CONSTRAINT ck_design_ref_section_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_design_ref_sections_ref ON design_reference_sections (reference_id, sort_order);

CREATE TABLE design_reference_retrieval_log (
    id UUID PRIMARY KEY,
    job_id UUID,
    user_id UUID,
    event_category VARCHAR(40),
    reference_ids UUID[] NOT NULL DEFAULT '{}',
    retrieval_mode VARCHAR(20) NOT NULL DEFAULT 'sql',
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_design_ref_retrieval_created ON design_reference_retrieval_log (created_at DESC);
CREATE INDEX idx_design_ref_retrieval_category ON design_reference_retrieval_log (event_category, created_at DESC);
