CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(254),
    password_hash VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    role VARCHAR(20) NOT NULL,
    CONSTRAINT uq_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_user_role CHECK (role IN ('CUSTOMER', 'ADMIN', 'REVIEWER'))
);
CREATE INDEX idx_user_roles_user ON user_roles (user_id);

CREATE TABLE otp_verifications (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    verified_at TIMESTAMPTZ,
    request_ip VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_otp_attempts CHECK (attempts >= 0 AND attempts <= 10)
);
CREATE INDEX idx_otp_phone_created ON otp_verifications (phone_number, created_at DESC);
CREATE INDEX idx_otp_ip_created ON otp_verifications (request_ip, created_at DESC);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

CREATE TABLE event_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    code VARCHAR(40) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    thumbnail_url TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE templates (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES event_categories (id),
    name VARCHAR(120) NOT NULL,
    code VARCHAR(60) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    thumbnail_url TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_templates_category ON templates (category_id);

CREATE TABLE template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES templates (id),
    version_number INT NOT NULL,
    schema_json JSONB NOT NULL,
    default_configuration JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_template_version UNIQUE (template_id, version_number),
    CONSTRAINT ck_template_version_positive CHECK (version_number > 0)
);
CREATE INDEX idx_template_versions_template ON template_versions (template_id);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    category_id UUID NOT NULL REFERENCES event_categories (id),
    template_version_id UUID NOT NULL REFERENCES template_versions (id),
    event_name VARCHAR(160) NOT NULL,
    slug VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_days INT NOT NULL,
    approved_revision_id UUID,
    published_revision_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_events_slug UNIQUE (slug),
    CONSTRAINT ck_events_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_events_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED',
        'REJECTED', 'PUBLISHED', 'EXPIRED', 'SUSPENDED'
    )),
    CONSTRAINT ck_events_days CHECK (requested_days >= 1 AND requested_days <= 365)
);
CREATE INDEX idx_events_user ON events (user_id);
CREATE INDEX idx_events_status ON events (status);

CREATE TABLE event_revisions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    revision_number INT NOT NULL,
    configuration_json JSONB NOT NULL,
    submission_status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_event_revision UNIQUE (event_id, revision_number),
    CONSTRAINT ck_revision_status CHECK (submission_status IN (
        'DRAFT', 'SUBMITTED', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED'
    ))
);
CREATE INDEX idx_revisions_event ON event_revisions (event_id);
CREATE INDEX idx_revisions_queue ON event_revisions (submission_status, submitted_at);

ALTER TABLE events
    ADD CONSTRAINT fk_events_approved_revision
        FOREIGN KEY (approved_revision_id) REFERENCES event_revisions (id);
ALTER TABLE events
    ADD CONSTRAINT fk_events_published_revision
        FOREIGN KEY (published_revision_id) REFERENCES event_revisions (id);

CREATE TABLE event_reviews (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    revision_id UUID NOT NULL REFERENCES event_revisions (id),
    reviewer_id UUID NOT NULL REFERENCES users (id),
    decision VARCHAR(32) NOT NULL,
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED'))
);
CREATE INDEX idx_reviews_event ON event_reviews (event_id, created_at DESC);

CREATE TABLE event_publications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    revision_id UUID NOT NULL REFERENCES event_revisions (id),
    published_at TIMESTAMPTZ,
    unpublished_at TIMESTAMPTZ,
    publication_status VARCHAR(32) NOT NULL,
    CONSTRAINT ck_publication_status CHECK (publication_status IN ('PUBLISHED', 'UNPUBLISHED', 'EXPIRED'))
);
CREATE UNIQUE INDEX uq_one_published_publication
    ON event_publications (event_id) WHERE publication_status = 'PUBLISHED';
CREATE INDEX idx_publications_event ON event_publications (event_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    payment_type VARCHAR(32) NOT NULL,
    gateway VARCHAR(32) NOT NULL,
    gateway_order_id VARCHAR(80),
    gateway_payment_id VARCHAR(80),
    amount_paise INT NOT NULL,
    days INT NOT NULL,
    daily_rate_paise INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payment_type CHECK (payment_type IN ('INITIAL_HOSTING', 'HOSTING_EXTENSION')),
    CONSTRAINT ck_payment_gateway CHECK (gateway IN ('RAZORPAY', 'BYPASS')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_payment_amount CHECK (amount_paise >= 0),
    CONSTRAINT ck_payment_days CHECK (days >= 1),
    CONSTRAINT ck_bypass_no_gateway_ids CHECK (
        gateway <> 'BYPASS' OR (gateway_order_id IS NULL AND gateway_payment_id IS NULL)
    )
);
CREATE INDEX idx_payments_event ON payments (event_id, created_at DESC);
CREATE INDEX idx_payments_pending ON payments (status, created_at);
CREATE UNIQUE INDEX uq_payments_gateway_order
    ON payments (gateway, gateway_order_id) WHERE gateway_order_id IS NOT NULL;

CREATE TABLE hosting_periods (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    days INT NOT NULL,
    daily_rate_paise INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_hosting_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'EXPIRED')),
    CONSTRAINT ck_hosting_range CHECK (end_at > start_at),
    CONSTRAINT ck_hosting_days CHECK (days >= 1)
);
CREATE INDEX idx_hosting_event ON hosting_periods (event_id, start_at);
CREATE INDEX idx_hosting_expiry ON hosting_periods (status, end_at);

CREATE TABLE hosting_extensions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    hosting_period_id UUID NOT NULL REFERENCES hosting_periods (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    additional_days INT NOT NULL,
    amount_paise INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_extension_days CHECK (additional_days >= 1)
);

CREATE TABLE event_media (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    revision_id UUID REFERENCES event_revisions (id),
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    thumbnail_path TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_media_type CHECK (media_type IN ('IMAGE', 'VIDEO'))
);
CREATE INDEX idx_media_event ON event_media (event_id);
CREATE INDEX idx_media_revision ON event_media (revision_id);

CREATE TABLE event_rsvps (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    guest_name VARCHAR(160) NOT NULL,
    guest_phone VARCHAR(20),
    guest_email VARCHAR(254),
    attendance_status VARCHAR(20) NOT NULL,
    number_of_guests INT NOT NULL,
    message TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    request_ip VARCHAR(64),
    CONSTRAINT ck_rsvp_attendance CHECK (attendance_status IN ('ACCEPTED', 'DECLINED')),
    CONSTRAINT ck_rsvp_guests CHECK (number_of_guests >= 0 AND number_of_guests <= 20)
);
CREATE INDEX idx_rsvp_event ON event_rsvps (event_id, submitted_at DESC);
CREATE INDEX idx_rsvp_ip ON event_rsvps (event_id, request_ip, submitted_at DESC);

CREATE TABLE url_reservations (
    id UUID PRIMARY KEY,
    slug VARCHAR(60) NOT NULL UNIQUE,
    event_id UUID REFERENCES events (id),
    user_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ
);

CREATE TABLE whatsapp_messages (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users (id),
    phone_number VARCHAR(20) NOT NULL,
    template_name VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(128),
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_wa_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);
CREATE INDEX idx_wa_status ON whatsapp_messages (status, created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users (id),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    metadata JSONB,
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    dedupe_key VARCHAR(200),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);
CREATE INDEX idx_outbox_pending ON outbox_events (status, available_at);
CREATE UNIQUE INDEX uq_outbox_dedupe ON outbox_events (dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    gateway VARCHAR(32) NOT NULL,
    event_id VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_webhook_event UNIQUE (gateway, event_id)
);

CREATE TABLE event_views (
    event_id UUID NOT NULL REFERENCES events (id),
    viewed_on DATE NOT NULL,
    view_count INT NOT NULL,
    PRIMARY KEY (event_id, viewed_on),
    CONSTRAINT ck_view_count CHECK (view_count >= 0)
);
