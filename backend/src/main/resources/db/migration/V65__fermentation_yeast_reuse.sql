-- YST-002: recomendação de reutilização e uso confirmado da coleta.
-- A política de repitch fica na cervejaria (e não no perfil de fermentação) porque a decisão
-- é da casa, não do estilo — e assim a recomendação não depende do vínculo lote↔perfil, que
-- só chega na FER-004. Cervejaria sem política configurada usa o padrão do domínio.

CREATE TABLE fermentation_yeast_policy (
    brewery_id UUID PRIMARY KEY,
    max_generation INTEGER NOT NULL,
    max_age_days INTEGER NOT NULL,
    min_viability_percent NUMERIC(5, 2) NOT NULL,
    CONSTRAINT ck_yeast_policy_generation CHECK (max_generation >= 1),
    CONSTRAINT ck_yeast_policy_age CHECK (max_age_days >= 1),
    CONSTRAINT ck_yeast_policy_viability CHECK (min_viability_percent BETWEEN 0 AND 100)
);

-- Uso confirmado consome a coleta e a vincula ao lote de destino, para a mesma levedura não
-- ser pitchada duas vezes.
ALTER TABLE fermentation_yeast_harvest
    ADD COLUMN pitched_batch_id UUID,
    ADD COLUMN pitched_at TIMESTAMPTZ;

ALTER TABLE fermentation_yeast_harvest
    DROP CONSTRAINT ck_yeast_harvest_status;

ALTER TABLE fermentation_yeast_harvest
    ADD CONSTRAINT ck_yeast_harvest_status CHECK (status IN ('QUARANTINE', 'APPROVED', 'REJECTED', 'USED')),
    -- Coleta usada sempre aponta para o lote que a recebeu; o inverso também vale.
    ADD CONSTRAINT ck_yeast_harvest_pitch CHECK (
        (status = 'USED') = (pitched_batch_id IS NOT NULL AND pitched_at IS NOT NULL));

CREATE INDEX ix_yeast_harvest_pitched ON fermentation_yeast_harvest (pitched_batch_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000066', '11111111-0000-0000-0000-000000000014',
     'fermentation.yeast.policy.manage', 'Configurar a política de reutilização de levedura', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'fermentation.yeast.policy.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
