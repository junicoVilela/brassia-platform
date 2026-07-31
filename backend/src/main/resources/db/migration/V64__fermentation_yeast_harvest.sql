-- YST-001: coleta de levedura — origem, geração, condição, viabilidade e armazenamento.
-- A coleta nasce em quarentena; aprovar/reprovar é decisão humana e terminal, e reprovada
-- (contaminação, odor, viabilidade baixa) nunca volta a ficar disponível para reúso.
-- A geração é derivada da coleta-mãe, então genealogia e geração não podem divergir.

CREATE TABLE fermentation_yeast_harvest (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    strain_id UUID NOT NULL,
    source_batch_id UUID NOT NULL,
    parent_harvest_id UUID REFERENCES fermentation_yeast_harvest (id),
    generation INTEGER NOT NULL,
    harvested_at TIMESTAMPTZ NOT NULL,
    viability_percent NUMERIC(5, 2) NOT NULL,
    condition VARCHAR(200) NOT NULL,
    storage_location VARCHAR(120) NOT NULL,
    storage_temp_c NUMERIC(5, 2) NOT NULL,
    status VARCHAR(12) NOT NULL,
    review_note VARCHAR(200),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    CONSTRAINT uq_yeast_harvest_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_yeast_harvest_status CHECK (status IN ('QUARANTINE', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_yeast_harvest_generation CHECK (generation >= 1),
    CONSTRAINT ck_yeast_harvest_viability CHECK (viability_percent BETWEEN 0 AND 100),
    -- Geração 1 é levedura comprada (sem mãe); acima disso a linhagem é obrigatória.
    CONSTRAINT ck_yeast_harvest_lineage CHECK ((generation = 1) = (parent_harvest_id IS NULL)),
    -- Reprovação sem motivo não é rastreável.
    CONSTRAINT ck_yeast_harvest_review CHECK (
        status <> 'REJECTED' OR (review_note IS NOT NULL AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL))
);

CREATE INDEX ix_yeast_harvest_brewery ON fermentation_yeast_harvest (brewery_id, status, harvested_at DESC);
CREATE INDEX ix_yeast_harvest_parent ON fermentation_yeast_harvest (parent_harvest_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000064', '11111111-0000-0000-0000-000000000014',
     'fermentation.yeast.read', 'Consultar coletas de levedura', false),
    ('22222222-0000-0000-0000-000000000065', '11111111-0000-0000-0000-000000000014',
     'fermentation.yeast.manage', 'Registrar e revisar coletas de levedura', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('fermentation.yeast.read', 'fermentation.yeast.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
