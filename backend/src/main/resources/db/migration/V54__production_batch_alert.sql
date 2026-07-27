-- PRD-006: central de alertas/ações do lote. Timeline persistida (sobrevive a
-- recarga/reconexão) de adições, etapas, medições atrasadas e decisões pendentes;
-- guarda o planejado e o realizado (atraso/impacto) sem avançar etapa. Confirmação
-- idempotente e auditada. Multi-tenant por brewery_id.

CREATE TABLE production_batch_alert (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES production_batch (id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    message VARCHAR(300) NOT NULL,
    planned_at TIMESTAMPTZ,
    occurred_at TIMESTAMPTZ,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID,
    CONSTRAINT ck_production_batch_alert_kind
        CHECK (kind IN ('ADDITION', 'STEP', 'MEASUREMENT', 'DECISION')),
    CONSTRAINT ck_production_batch_alert_status CHECK (status IN ('PENDING', 'CONFIRMED'))
);

CREATE INDEX ix_production_batch_alert_batch
    ON production_batch_alert (batch_id, planned_at, created_at);
