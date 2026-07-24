-- REF-002: pipeline de importação (staging). Um job recebe o payload bruto, é
-- validado e, se aprovado, publica um reference_dataset. Não contamina o
-- catálogo: o dataset só existe após a publicação. Reusa as permissões
-- reference.manage (submeter) e reference.publish (publicar) do REF-001.

CREATE TABLE import_job (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES reference_source (id),
    brewery_id UUID REFERENCES brewery (id),
    dataset_version VARCHAR(60) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum CHAR(64) NOT NULL,
    raw_payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_dataset_id UUID REFERENCES reference_dataset (id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_job_status CHECK (status IN (
        'RECEIVED', 'VALIDATING', 'REVIEW_REQUIRED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_import_job_size CHECK (size_bytes >= 0)
);

CREATE INDEX ix_import_job_source ON import_job (source_id, created_at DESC);

CREATE TABLE import_job_issue (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES import_job (id) ON DELETE CASCADE,
    line INT,
    field VARCHAR(120),
    code VARCHAR(60) NOT NULL,
    message VARCHAR(500) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_job_issue_severity CHECK (severity IN ('ERROR', 'WARNING'))
);

CREATE INDEX ix_import_job_issue_job ON import_job_issue (job_id);
