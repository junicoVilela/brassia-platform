-- FER-002: leituras e curvas de fermentação. Densidade, temperatura, pressão e pH por lote,
-- de origem manual ou sensor. Leitura fora da faixa plausível é sinalizada (valid=false +
-- motivo), nunca rejeitada nem apagada. Ingestão idempotente pela chave natural
-- (lote, grandeza, origem, instante), para reenvio de sensor não duplicar a série.

CREATE TABLE fermentation_reading (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    kind VARCHAR(12) NOT NULL,
    source VARCHAR(8) NOT NULL,
    value NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(8) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    valid BOOLEAN NOT NULL,
    invalid_reason VARCHAR(200),
    CONSTRAINT uq_fermentation_reading_natural UNIQUE (batch_id, kind, source, measured_at),
    CONSTRAINT ck_fermentation_reading_kind CHECK (kind IN ('DENSITY', 'TEMPERATURE', 'PRESSURE', 'PH')),
    CONSTRAINT ck_fermentation_reading_source CHECK (source IN ('MANUAL', 'SENSOR')),
    CONSTRAINT ck_fermentation_reading_reason CHECK (valid OR invalid_reason IS NOT NULL)
);

-- Série temporal do lote (com e sem filtro por grandeza).
CREATE INDEX ix_fermentation_reading_series ON fermentation_reading (brewery_id, batch_id, kind, measured_at);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000062', '11111111-0000-0000-0000-000000000014',
     'fermentation.reading.read', 'Consultar leituras de fermentação', false),
    ('22222222-0000-0000-0000-000000000063', '11111111-0000-0000-0000-000000000014',
     'fermentation.reading.record', 'Registrar leituras de fermentação', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('fermentation.reading.read', 'fermentation.reading.record')
ON CONFLICT (group_id, permission_id) DO NOTHING;
