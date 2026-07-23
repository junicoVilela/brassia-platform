-- WTR-001: fontes de água e laudos. O laudo é imutável (append-only): uma
-- correção gera novo laudo e o antigo permanece disponível no histórico.

CREATE TABLE water_source (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_water_source_code UNIQUE (brewery_id, code)
);

CREATE TABLE water_report (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    source_id UUID NOT NULL REFERENCES water_source (id),
    collected_on DATE NOT NULL,
    method VARCHAR(16) NOT NULL,
    calcium NUMERIC(8, 2) NOT NULL,
    magnesium NUMERIC(8, 2) NOT NULL,
    sodium NUMERIC(8, 2) NOT NULL,
    sulfate NUMERIC(8, 2) NOT NULL,
    chloride NUMERIC(8, 2) NOT NULL,
    bicarbonate NUMERIC(8, 2) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_water_report_method CHECK (method IN ('LAB', 'TEST_STRIP', 'ION_METER', 'UTILITY')),
    CONSTRAINT ck_water_report_ions CHECK (
        calcium >= 0 AND magnesium >= 0 AND sodium >= 0
        AND sulfate >= 0 AND chloride >= 0 AND bicarbonate >= 0)
);

CREATE INDEX ix_water_report_source ON water_report (source_id, collected_on DESC);

-- Domínio de permissões de água + permissões da história ao grupo Administradores.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000007', NULL, 'water', 'Água', 8)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000026', '11111111-0000-0000-0000-000000000007',
     'water.read', 'Consultar fontes e laudos de água', false),
    ('22222222-0000-0000-0000-000000000027', '11111111-0000-0000-0000-000000000007',
     'water.manage', 'Cadastrar fontes e registrar laudos', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('water.read', 'water.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
