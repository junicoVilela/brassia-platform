-- PKG-002: carbonatação decidida para um plano de envase.
-- É decisão confirmada, não número de passagem: guarda entradas, método, versão da fórmula,
-- resultado e quem confirmou. Recalcular substitui a decisão inteira (1:1 com o plano), para
-- entrada e resultado nunca divergirem.
-- Temperatura e CO₂ residual são obrigatórios nos dois métodos: no priming, ignorar o residual
-- pede açúcar demais e estoura a embalagem; na forçada, a mesma pressão carbonata menos a quente.

CREATE TABLE packaging_carbonation (
    plan_id UUID PRIMARY KEY REFERENCES packaging_plan (id),
    brewery_id UUID NOT NULL,
    method VARCHAR(8) NOT NULL,
    target_volumes NUMERIC(6, 3) NOT NULL,
    reference_temp_c NUMERIC(6, 2) NOT NULL,
    residual_volumes NUMERIC(6, 3) NOT NULL,
    priming_sugar VARCHAR(24),
    priming_sugar_grams NUMERIC(10, 2),
    pressure_bar NUMERIC(8, 3),
    calculation_method VARCHAR(400) NOT NULL,
    calculator_version VARCHAR(20) NOT NULL,
    alerts TEXT,
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_packaging_carbonation_method CHECK (method IN ('PRIMING', 'FORCED')),
    CONSTRAINT ck_packaging_carbonation_target CHECK (target_volumes > 0),
    CONSTRAINT ck_packaging_carbonation_residual CHECK (residual_volumes >= 0),
    CONSTRAINT ck_packaging_carbonation_sugar
        CHECK (priming_sugar IS NULL OR priming_sugar IN ('SUCROSE', 'DEXTROSE_MONOHYDRATE', 'DRY_MALT_EXTRACT')),
    -- Priming carbonata com açúcar e não aplica pressão; forçada é o oposto. Misturar os dois
    -- esconderia qual caminho produziu o resultado.
    CONSTRAINT ck_packaging_carbonation_priming CHECK (
        method <> 'PRIMING'
        OR (priming_sugar IS NOT NULL AND priming_sugar_grams >= 0 AND pressure_bar IS NULL)),
    CONSTRAINT ck_packaging_carbonation_forced CHECK (
        method <> 'FORCED'
        OR (pressure_bar >= 0 AND priming_sugar IS NULL AND priming_sugar_grams IS NULL)),
    -- Priming sobre CO₂ que já atinge o alvo é sobrepressão garantida.
    CONSTRAINT ck_packaging_carbonation_headroom CHECK (
        method <> 'PRIMING' OR residual_volumes < target_volumes)
);

CREATE INDEX ix_packaging_carbonation_brewery ON packaging_carbonation (brewery_id, plan_id);
