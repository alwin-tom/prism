CREATE TABLE website_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    icon VARCHAR(40) NOT NULL,
    event_category_code VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE questionnaire_definitions (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES website_categories (id),
    version INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    configuration_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_questionnaire_version UNIQUE (category_id, version),
    CONSTRAINT ck_questionnaire_version CHECK (version > 0)
);
CREATE INDEX idx_questionnaire_category ON questionnaire_definitions (category_id, active);

CREATE TABLE website_creation_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    category_id UUID NOT NULL REFERENCES website_categories (id),
    questionnaire_version INT NOT NULL,
    event_id UUID REFERENCES events (id),
    answers_json JSONB NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_creation_status CHECK (status IN (
        'IN_PROGRESS', 'ANSWERS_COMPLETED', 'GENERATION_PENDING', 'GENERATING',
        'GENERATION_FAILED', 'GENERATED', 'EDITING', 'SUBMITTED'
    ))
);
CREATE INDEX idx_creation_user ON website_creation_sessions (user_id, updated_at DESC);

CREATE TABLE website_generation_jobs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES website_creation_sessions (id),
    user_id UUID NOT NULL REFERENCES users (id),
    event_id UUID REFERENCES events (id),
    status VARCHAR(40) NOT NULL,
    model VARCHAR(120),
    prompt_version VARCHAR(40) NOT NULL,
    prompt_snapshot TEXT,
    planning_response_json JSONB,
    generation_response_json JSONB,
    generated_document_json JSONB,
    error_message TEXT,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_generation_status CHECK (status IN (
        'QUEUED', 'PLANNING', 'GENERATING', 'VALIDATING', 'REPAIRING', 'COMPLETED', 'FAILED', 'CANCELLED'
    ))
);
CREATE INDEX idx_generation_session ON website_generation_jobs (session_id, created_at DESC);
CREATE INDEX idx_generation_user ON website_generation_jobs (user_id);
