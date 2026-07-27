-- PRD-003: medição do dia de brassa. Imutável (append-only): valor + unidade
-- (compatível com a grandeza, validada no domínio), temperatura, método, origem e
-- operador. Pode referenciar uma etapa do roteiro (contexto).

CREATE TABLE production_measurement (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES production_batch (id) ON DELETE CASCADE,
    step_id UUID,
    kind VARCHAR(16) NOT NULL,
    measured_value NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(8) NOT NULL,
    temperature_c NUMERIC(6, 2),
    method VARCHAR(120),
    source VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    CONSTRAINT ck_production_measurement_kind
        CHECK (kind IN ('DENSITY', 'TEMPERATURE', 'VOLUME', 'PH', 'COLOR', 'IBU')),
    CONSTRAINT ck_production_measurement_source
        CHECK (source IN ('MANUAL', 'DEVICE', 'IMPORTED'))
);

CREATE INDEX ix_production_measurement_batch ON production_measurement (batch_id, recorded_at);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000049', '11111111-0000-0000-0000-000000000012',
     'production.measurement.record', 'Registrar medições do dia de brassa', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'production.measurement.record'
ON CONFLICT (group_id, permission_id) DO NOTHING;
