CREATE TABLE website_moderation_reports (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    revision_id UUID NOT NULL REFERENCES event_revisions (id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    overall_risk VARCHAR(16) NOT NULL DEFAULT 'CLEAN',
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    explanation TEXT,
    provider VARCHAR(40) NOT NULL DEFAULT 'openrouter',
    model_id VARCHAR(160),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_details TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_moderation_report_revision UNIQUE (revision_id),
    CONSTRAINT ck_moderation_report_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'RETRY_REQUIRED'
    )),
    CONSTRAINT ck_moderation_overall_risk CHECK (overall_risk IN (
        'CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'CLEAN'
    ))
);
CREATE INDEX idx_moderation_reports_event ON website_moderation_reports (event_id, created_at DESC);
CREATE INDEX idx_moderation_reports_status ON website_moderation_reports (status, updated_at);
CREATE INDEX idx_moderation_reports_risk ON website_moderation_reports (overall_risk, status);

CREATE TABLE website_moderation_findings (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES website_moderation_reports (id) ON DELETE CASCADE,
    category VARCHAR(40) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    page_id VARCHAR(120),
    section_id VARCHAR(120),
    element_id VARCHAR(120),
    element_type VARCHAR(40),
    matched_text TEXT NOT NULL,
    start_offset INT,
    end_offset INT,
    context_text TEXT,
    reason TEXT NOT NULL,
    suggestion TEXT,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    administrator_note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_moderation_finding_severity CHECK (severity IN (
        'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'
    )),
    CONSTRAINT ck_moderation_finding_category CHECK (category IN (
        'PROFANITY', 'HARASSMENT', 'HATE_SPEECH', 'THREATS', 'VIOLENCE',
        'SEXUAL_CONTENT', 'BULLYING', 'DEFAMATION', 'SELF_HARM',
        'ILLEGAL_ACTIVITY', 'SCAM_FRAUD', 'OTHER'
    )),
    CONSTRAINT ck_moderation_finding_review_status CHECK (review_status IN (
        'PENDING', 'CONFIRMED', 'FALSE_POSITIVE', 'RESOLVED', 'DISMISSED'
    ))
);
CREATE INDEX idx_moderation_findings_report ON website_moderation_findings (report_id, severity);
CREATE INDEX idx_moderation_findings_review ON website_moderation_findings (review_status, updated_at);

CREATE TABLE moderation_audit_logs (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES website_moderation_reports (id) ON DELETE CASCADE,
    finding_id UUID REFERENCES website_moderation_findings (id) ON DELETE SET NULL,
    administrator_id UUID NOT NULL REFERENCES users (id),
    action VARCHAR(64) NOT NULL,
    previous_value TEXT,
    new_value TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_moderation_audit_report ON moderation_audit_logs (report_id, created_at DESC);
CREATE INDEX idx_moderation_audit_admin ON moderation_audit_logs (administrator_id, created_at DESC);
