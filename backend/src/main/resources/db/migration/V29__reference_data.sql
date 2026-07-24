-- REF-001: registro de fontes e datasets de dados de referência, com licença,
-- permissão, proveniência e checksum. Fonte pode ser global (brewery_id NULL,
-- curadoria BrassIA) ou privada de uma cervejaria. O payload bruto do dataset é
-- imutável; publicação depende da permissão da fonte (gate de licença).

CREATE TABLE reference_source (
    id UUID PRIMARY KEY,
    brewery_id UUID REFERENCES brewery (id),
    type VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    owner VARCHAR(160) NOT NULL,
    url VARCHAR(500),
    license_name VARCHAR(160) NOT NULL,
    permission_status VARCHAR(20) NOT NULL,
    attribution VARCHAR(300),
    review_frequency VARCHAR(60),
    responsible VARCHAR(160),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reference_source_type CHECK (type IN (
        'OFFICIAL_STANDARD', 'INTERCHANGE_STANDARD', 'MANUFACTURER',
        'ACCOUNT_INTEGRATION', 'MANUAL_CONTRIBUTION', 'BRASSIA_CURATION')),
    CONSTRAINT ck_reference_source_permission CHECK (permission_status IN (
        'UNKNOWN', 'PENDING', 'LIMITED_PERMISSION', 'GRANTED', 'DENIED'))
);

-- Nome único por escopo (global usa o UUID zero como chave do COALESCE).
CREATE UNIQUE INDEX uq_reference_source_name ON reference_source (
    COALESCE(brewery_id, '00000000-0000-0000-0000-000000000000'), lower(name));

CREATE TABLE reference_dataset (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES reference_source (id),
    dataset_version VARCHAR(60) NOT NULL,
    checksum CHAR(64) NOT NULL,
    source_system VARCHAR(160) NOT NULL,
    source_record_id VARCHAR(200),
    source_url VARCHAR(500),
    retrieved_at TIMESTAMPTZ NOT NULL,
    raw_payload TEXT NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    review_status VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reference_dataset_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_reference_dataset_review CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_reference_dataset_effective CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_reference_dataset_checksum CHECK (checksum ~ '^[a-f0-9]{64}$'),
    -- Idempotência: o mesmo conteúdo (checksum) de uma fonte não duplica.
    CONSTRAINT uq_reference_dataset_checksum UNIQUE (source_id, checksum)
);

CREATE INDEX ix_reference_dataset_source ON reference_dataset (source_id, effective_from DESC);

-- Domínio e permissões de dados de referência, concedidas ao grupo Administradores.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000008', NULL, 'reference', 'Dados de referência', 9)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000029', '11111111-0000-0000-0000-000000000008',
     'reference.read', 'Consultar fontes e datasets de referência', false),
    ('22222222-0000-0000-0000-000000000030', '11111111-0000-0000-0000-000000000008',
     'reference.manage', 'Registrar fontes e datasets de referência', true),
    ('22222222-0000-0000-0000-000000000031', '11111111-0000-0000-0000-000000000008',
     'reference.publish', 'Publicar datasets de referência', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('reference.read', 'reference.manage', 'reference.publish')
ON CONFLICT (group_id, permission_id) DO NOTHING;
