-- GAS-002: linha de serviço e balanceamento.
-- A pressão de serviço não é escolha livre: ela é ditada pelo equilíbrio de carbonatação na
-- temperatura de serviço. Servir a outra pressão faz o barril ganhar ou perder CO₂ ao longo do
-- tempo, e a cerveja sai do padrão sem que ninguém tenha mexido nela.
-- O sistema calcula e recomenda; nenhuma válvula ou regulador é ajustado automaticamente.

-- Resistência do tubo vem da ficha do fabricante, não do sistema. A vazão de referência fica ao
-- lado da resistência porque é ela que permite escalar corretamente para outra vazão.
CREATE TABLE gas_line_resistance (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    material VARCHAR(60) NOT NULL,
    internal_diameter_mm NUMERIC(8, 2) NOT NULL,
    resistance_bar_per_meter NUMERIC(10, 4) NOT NULL,
    reference_flow_lpm NUMERIC(8, 3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    -- Material e diâmetro são a identidade do tubo; recadastrar só atualiza os números.
    CONSTRAINT uq_gas_line_resistance UNIQUE (brewery_id, material, internal_diameter_mm),
    CONSTRAINT ck_gas_line_resistance_positive CHECK (
        internal_diameter_mm > 0 AND resistance_bar_per_meter > 0 AND reference_flow_lpm > 0)
);

CREATE TABLE gas_service_line (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    point_of_use_equipment_id UUID NOT NULL,
    current_revision INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_gas_service_line_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_gas_service_line_revision CHECK (current_revision >= 0)
);

CREATE INDEX ix_gas_service_line_point ON gas_service_line (brewery_id, point_of_use_equipment_id);

-- Aplicar um balanceamento ACRESCENTA uma revisão; a anterior nunca é reescrita. A montagem física
-- de ontem é a única evidência de por que a cerveja de ontem saiu como saiu.
CREATE TABLE gas_service_line_revision (
    id UUID PRIMARY KEY,
    line_id UUID NOT NULL REFERENCES gas_service_line (id),
    brewery_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    material VARCHAR(60) NOT NULL,
    internal_diameter_mm NUMERIC(8, 2) NOT NULL,
    applied_length_meters NUMERIC(10, 3) NOT NULL,
    recommended_length_meters NUMERIC(10, 3) NOT NULL,
    applied_pressure_bar NUMERIC(10, 3) NOT NULL,
    elevation_meters NUMERIC(10, 3) NOT NULL,
    residual_pressure_bar NUMERIC(10, 3) NOT NULL,
    target_flow_lpm NUMERIC(8, 3) NOT NULL,
    serving_temp_c NUMERIC(6, 2) NOT NULL,
    target_co2_volumes NUMERIC(6, 3) NOT NULL,
    calculation_method VARCHAR(400) NOT NULL,
    calculator_version VARCHAR(20) NOT NULL,
    note VARCHAR(200),
    applied_by UUID NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_gas_service_line_revision UNIQUE (line_id, revision),
    CONSTRAINT ck_gas_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_gas_revision_positive CHECK (
        applied_length_meters > 0 AND recommended_length_meters >= 0 AND applied_pressure_bar > 0
        AND residual_pressure_bar >= 0 AND target_flow_lpm > 0 AND target_co2_volumes > 0
        AND internal_diameter_mm > 0)
    -- elevation_meters pode ser negativo: a torneira pode ficar abaixo do barril.
);

CREATE INDEX ix_gas_revision_line ON gas_service_line_revision (brewery_id, line_id, revision DESC);
