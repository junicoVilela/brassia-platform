-- CLN-004: verificar e liberar. Um ciclo concluído recebe as checagens (enxágue, visual,
-- ATP e micro) e é liberado (RELEASED) apenas com todas aprovadas — sanitização não passa
-- com limpeza reprovada; caso contrário é reprovado (REJECTED). Liberação publica evento.

ALTER TABLE sanitation_cleaning_cycle
    ADD COLUMN rinse_ok BOOLEAN,
    ADD COLUMN visual_ok BOOLEAN,
    ADD COLUMN atp_rlu NUMERIC(12, 3),
    ADD COLUMN atp_threshold NUMERIC(12, 3),
    ADD COLUMN micro_ok BOOLEAN,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN decided_at TIMESTAMPTZ;

ALTER TABLE sanitation_cleaning_cycle DROP CONSTRAINT ck_sanitation_cycle_status;
ALTER TABLE sanitation_cleaning_cycle ADD CONSTRAINT ck_sanitation_cycle_status
    CHECK (status IN ('IN_PROGRESS', 'INTERRUPTED', 'COMPLETED', 'RELEASED', 'REJECTED'));

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000057', '11111111-0000-0000-0000-000000000013',
     'sanitation.cycle.release', 'Liberar/reprovar ciclo de limpeza', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'sanitation.cycle.release'
ON CONFLICT (group_id, permission_id) DO NOTHING;
