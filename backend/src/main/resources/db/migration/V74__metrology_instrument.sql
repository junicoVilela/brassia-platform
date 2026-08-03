-- MTR-001: cadastro metrológico — instrumento, padrão de referência e certificados de calibração.
-- A aptidão do instrumento NÃO é coluna: é derivada do estado cadastral + última calibração + data.
-- Guardar "apto" criaria um valor que envelhece sozinho e passa a mentir no dia seguinte ao
-- vencimento. O certificado é histórico imutável: vencer não apaga nada.

CREATE TABLE metrology_standard (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    certificate_number VARCHAR(60) NOT NULL,
    issuer VARCHAR(120) NOT NULL,
    -- Órgão/rede que sustenta a rastreabilidade (RBC, INMETRO). Sem ela a calibração vira ritual.
    traceability VARCHAR(120) NOT NULL,
    valid_until DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_metrology_standard_code UNIQUE (brewery_id, code)
);

CREATE INDEX ix_metrology_standard_brewery ON metrology_standard (brewery_id, valid_until);

CREATE TABLE metrology_instrument (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    range_min NUMERIC(14, 4) NOT NULL,
    range_max NUMERIC(14, 4) NOT NULL,
    resolution NUMERIC(14, 4) NOT NULL,
    accuracy NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    location VARCHAR(120) NOT NULL,
    state VARCHAR(10) NOT NULL,
    block_reason VARCHAR(200),
    critical_use BOOLEAN NOT NULL DEFAULT false,
    last_calibration_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_metrology_instrument_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_metrology_instrument_type CHECK (type IN ('THERMOMETER', 'HYDROMETER', 'PH_METER',
        'SCALE', 'PRESSURE_GAUGE', 'OXYGEN_METER', 'FLOW_METER')),
    CONSTRAINT ck_metrology_instrument_state CHECK (state IN ('ACTIVE', 'BLOCKED', 'RETIRED')),
    -- Faixa incoerente envenena toda leitura futura, então o banco também a guarda.
    CONSTRAINT ck_metrology_instrument_range CHECK (range_min < range_max),
    CONSTRAINT ck_metrology_instrument_resolution CHECK (resolution > 0 AND resolution <= range_max - range_min),
    CONSTRAINT ck_metrology_instrument_accuracy CHECK (accuracy > 0 AND accuracy <= range_max - range_min),
    -- Bloqueio e baixa sem motivo escondem por que o instrumento saiu de circulação.
    CONSTRAINT ck_metrology_instrument_block CHECK (state = 'ACTIVE' OR block_reason IS NOT NULL),
    -- Baixado não fica designado para ponto crítico.
    CONSTRAINT ck_metrology_instrument_retired_critical CHECK (state <> 'RETIRED' OR critical_use = false)
);

CREATE INDEX ix_metrology_instrument_brewery ON metrology_instrument (brewery_id, state, critical_use);

CREATE TABLE metrology_calibration (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    instrument_id UUID NOT NULL REFERENCES metrology_instrument (id),
    standard_id UUID NOT NULL REFERENCES metrology_standard (id),
    standard_code VARCHAR(40) NOT NULL,
    performed_on DATE NOT NULL,
    due_on DATE NOT NULL,
    performed_by VARCHAR(120) NOT NULL,
    certificate_number VARCHAR(60) NOT NULL,
    result VARCHAR(30) NOT NULL,
    max_deviation NUMERIC(14, 4) NOT NULL,
    restriction VARCHAR(200),
    note VARCHAR(500),
    CONSTRAINT ck_metrology_calibration_result CHECK (result IN ('APPROVED', 'APPROVED_WITH_RESTRICTION',
        'REJECTED')),
    CONSTRAINT ck_metrology_calibration_due CHECK (due_on > performed_on),
    CONSTRAINT ck_metrology_calibration_deviation CHECK (max_deviation >= 0),
    -- Restrição é obrigatória quando aprova com restrição, e proibida no resto: "aprovado com
    -- restrição" sem dizer qual restrição não informa nada.
    CONSTRAINT ck_metrology_calibration_restriction CHECK (
        (result = 'APPROVED_WITH_RESTRICTION' AND restriction IS NOT NULL)
        OR (result <> 'APPROVED_WITH_RESTRICTION' AND restriction IS NULL))
);

CREATE INDEX ix_metrology_calibration_instrument
    ON metrology_calibration (brewery_id, instrument_id, performed_on DESC);

ALTER TABLE metrology_instrument
    ADD CONSTRAINT fk_metrology_instrument_last_calibration
    FOREIGN KEY (last_calibration_id) REFERENCES metrology_calibration (id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000017', NULL, 'metrology', 'Metrologia', 22)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000074', '11111111-0000-0000-0000-000000000017',
     'metrology.instrument.read', 'Consultar instrumentos, calibrações e padrões', false),
    ('22222222-0000-0000-0000-000000000075', '11111111-0000-0000-0000-000000000017',
     'metrology.instrument.manage', 'Cadastrar instrumentos e registrar calibrações', false),
    ('22222222-0000-0000-0000-000000000076', '11111111-0000-0000-0000-000000000017',
     'metrology.standard.manage', 'Cadastrar e renovar padrões de referência', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('metrology.instrument.read', 'metrology.instrument.manage', 'metrology.standard.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
