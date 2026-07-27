-- PRD-001: lote de produção (Batch) criado ao iniciar uma OP liberada. Referência
-- 1:1 à OP, snapshot da receita (nome + versão congelados) e roteiro do dia de
-- brassa derivado da receita. Multi-tenant por brewery_id.

CREATE TABLE production_batch (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    order_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    recipe_id UUID NOT NULL,
    recipe_version INTEGER NOT NULL,
    recipe_name VARCHAR(160) NOT NULL,
    volume_liters NUMERIC(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    started_by UUID NOT NULL,
    CONSTRAINT uq_production_batch_order UNIQUE (brewery_id, order_id),
    CONSTRAINT ck_production_batch_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_production_batch_brewery ON production_batch (brewery_id, started_at DESC);

CREATE TABLE production_batch_step (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES production_batch (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    type VARCHAR(16) NOT NULL,
    label VARCHAR(80) NOT NULL,
    CONSTRAINT ck_production_batch_step_type CHECK (type IN ('MASH', 'BOIL', 'WHIRLPOOL', 'TRANSFER'))
);

CREATE INDEX ix_production_batch_step_batch ON production_batch_step (batch_id, step_order);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000012', NULL, 'production', 'Produção', 14)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000047', '11111111-0000-0000-0000-000000000012',
     'production.batch.read', 'Consultar lotes de produção', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'production.batch.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
