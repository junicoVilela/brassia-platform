-- CAL-002: correção aplicada no dia de brassa. Registro da decisão (não executa
-- ação física): calculadora versionada, medição de origem, hipótese (inputs),
-- efeito estimado (planejado) e valor realizado opcional. Gera evento.

CREATE TABLE production_applied_correction (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES production_batch (id) ON DELETE CASCADE,
    calculator VARCHAR(48) NOT NULL,
    source_measurement_id UUID,
    note VARCHAR(300),
    inputs JSONB NOT NULL DEFAULT '{}',
    planned_value NUMERIC(14, 4) NOT NULL,
    planned_unit VARCHAR(8) NOT NULL,
    realized_value NUMERIC(14, 4),
    applied_at TIMESTAMPTZ NOT NULL,
    applied_by UUID NOT NULL
);

CREATE INDEX ix_production_applied_correction_batch
    ON production_applied_correction (batch_id, applied_at);
