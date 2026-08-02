-- PKG-003: execução do envase — unidades, volume, rejeitos e perdas.
-- A perda é DERIVADA (entrada − envasado − rejeitado), não digitada: aceitar perda ao lado dos
-- outros três números permitiria um balanço que não fecha, e é isso que esta história impede.
-- Rejeito consome embalagem igual: uma lata cheia e descartada é uma lata gasta.

-- O plano ganha o estado EXECUTED. Os dois estados terminais não se equivalem: cancelado devolve
-- a embalagem, executado a consumiu — por isso plano executado não é cancelável.
ALTER TABLE packaging_plan DROP CONSTRAINT ck_packaging_plan_status;
ALTER TABLE packaging_plan ADD CONSTRAINT ck_packaging_plan_status
    CHECK (status IN ('PLANNED', 'RESERVED', 'EXECUTED', 'CANCELLED'));

CREATE TABLE packaging_run (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL UNIQUE REFERENCES packaging_plan (id),
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    container_volume_ml NUMERIC(10, 3) NOT NULL,
    input_volume_liters NUMERIC(12, 3) NOT NULL,
    produced_units INTEGER NOT NULL,
    rejected_units INTEGER NOT NULL,
    packaged_volume_liters NUMERIC(12, 3) NOT NULL,
    rejected_volume_liters NUMERIC(12, 3) NOT NULL,
    losses_liters NUMERIC(12, 3) NOT NULL,
    note VARCHAR(200),
    executed_at TIMESTAMPTZ NOT NULL,
    executed_by UUID NOT NULL,
    CONSTRAINT ck_packaging_run_units CHECK (produced_units >= 0 AND rejected_units >= 0
        AND produced_units + rejected_units > 0),
    CONSTRAINT ck_packaging_run_input CHECK (input_volume_liters > 0),
    CONSTRAINT ck_packaging_run_losses CHECK (losses_liters >= 0),
    -- O banco também guarda o balanço: envasado + rejeitado + perdas = o que saiu do tanque.
    CONSTRAINT ck_packaging_run_balance CHECK (
        packaged_volume_liters + rejected_volume_liters + losses_liters = input_volume_liters)
);

CREATE INDEX ix_packaging_run_batch ON packaging_run (brewery_id, batch_id, executed_at DESC);
