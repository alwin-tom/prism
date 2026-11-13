CREATE TABLE event_page_documents (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    revision_id UUID NOT NULL REFERENCES event_revisions (id),
    schema_version INT NOT NULL,
    document_json JSONB NOT NULL,
    version_number INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_editor_document_revision UNIQUE (revision_id),
    CONSTRAINT ck_editor_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_editor_version_number CHECK (version_number > 0)
);
CREATE INDEX idx_editor_documents_event ON event_page_documents (event_id);

CREATE TABLE editor_document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES event_page_documents (id),
    version_number INT NOT NULL,
    document_json JSONB NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_editor_version UNIQUE (document_id, version_number)
);

CREATE TABLE editor_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category_id UUID REFERENCES event_categories (id),
    preview_image TEXT,
    document_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);
