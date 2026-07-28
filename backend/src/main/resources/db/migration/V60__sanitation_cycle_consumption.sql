-- CLN-005: consumo e otimização. Mede água (L), energia (kWh) e produto (kg) por ciclo
-- com execução encerrada. A comparação por POP é consultiva (read-only) — não reduz
-- parâmetros do POP; reduzir limite exige uma nova versão publicada (CLN-001).

ALTER TABLE sanitation_cleaning_cycle
    ADD COLUMN water_liters NUMERIC(14, 3),
    ADD COLUMN energy_kwh NUMERIC(14, 3),
    ADD COLUMN product_kg NUMERIC(14, 3),
    ADD COLUMN consumption_at TIMESTAMPTZ;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000058', '11111111-0000-0000-0000-000000000013',
     'sanitation.consumption.read', 'Consultar consumo/comparação de limpeza', false),
    ('22222222-0000-0000-0000-000000000059', '11111111-0000-0000-0000-000000000013',
     'sanitation.consumption.manage', 'Registrar consumo de ciclo de limpeza', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sanitation.consumption.read', 'sanitation.consumption.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
