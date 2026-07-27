-- PRD-002: modo passo a passo. Cada etapa ganha estado sequencial
-- (PENDING → ACTIVE → DONE) e marcos server-aware (started_at/completed_at) para
-- o cronômetro derivar o decorrido. Permissão de operação do lote.

ALTER TABLE production_batch_step
    ADD COLUMN step_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE production_batch_step
    ADD CONSTRAINT ck_production_batch_step_status
        CHECK (step_status IN ('PENDING', 'ACTIVE', 'DONE'));

-- Lotes já existentes: ativa a primeira etapa de cada lote (menor step_order).
UPDATE production_batch_step s
SET step_status = 'ACTIVE', started_at = b.started_at
FROM production_batch b
WHERE s.batch_id = b.id
  AND s.step_order = (SELECT MIN(x.step_order) FROM production_batch_step x WHERE x.batch_id = s.batch_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000048', '11111111-0000-0000-0000-000000000012',
     'production.batch.manage', 'Operar lotes de produção (avançar etapas)', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'production.batch.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
