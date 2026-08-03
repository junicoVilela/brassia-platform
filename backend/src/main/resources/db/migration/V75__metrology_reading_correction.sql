-- MTR-002: correção de leitura — curva do certificado e o registro da correção aplicada.
-- O valor bruto é IMUTÁVEL: a correção nasce ao lado da medição, nunca por cima dela. Corrigir de
-- novo cria outro registro, porque o histórico de como um número foi obtido é o que permite
-- auditar uma liberação meses depois.

-- Pontos conferidos pelo certificado: quando o valor verdadeiro era `reference`, o instrumento
-- indicou `measured`. Fora dessa faixa a correção é recusada em vez de extrapolada.
CREATE TABLE metrology_calibration_point (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    calibration_id UUID NOT NULL REFERENCES metrology_calibration (id) ON DELETE CASCADE,
    reference NUMERIC(14, 4) NOT NULL,
    measured NUMERIC(14, 4) NOT NULL,
    CONSTRAINT uq_metrology_calibration_point UNIQUE (calibration_id, measured)
);

CREATE INDEX ix_metrology_calibration_point_calibration
    ON metrology_calibration_point (calibration_id, measured);

CREATE TABLE metrology_reading_correction (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    instrument_id UUID NOT NULL REFERENCES metrology_instrument (id),
    -- Leitura de origem em outro módulo, quando houver. Guardada como referência opaca: a
    -- correção não acopla metrologia a quem produziu a medição.
    source_reading_id UUID,
    raw_value NUMERIC(14, 4) NOT NULL,
    corrected_value NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    sample_temp_c NUMERIC(14, 4),
    calibration_temp_c NUMERIC(14, 4),
    -- Passos aplicados, cada um com fórmula e versão: é o "resultado mostra fórmula e versão".
    steps JSONB NOT NULL,
    -- Aptidão do instrumento no momento da correção. Instrumento não apto não impede corrigir —
    -- entra como ressalva, no mesmo princípio de FSL-001: não muda o número, muda a confiança.
    instrument_fitness VARCHAR(20) NOT NULL,
    caveats JSONB NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    applied_by UUID NOT NULL,
    CONSTRAINT ck_metrology_correction_fitness CHECK (instrument_fitness IN ('FIT', 'EXPIRED',
        'UNCALIBRATED', 'REJECTED', 'BLOCKED', 'RETIRED')),
    CONSTRAINT ck_metrology_correction_steps CHECK (jsonb_array_length(steps) > 0)
);

CREATE INDEX ix_metrology_correction_instrument
    ON metrology_reading_correction (brewery_id, instrument_id, applied_at DESC);
CREATE INDEX ix_metrology_correction_source
    ON metrology_reading_correction (brewery_id, source_reading_id)
    WHERE source_reading_id IS NOT NULL;
