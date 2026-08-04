-- PRM-001: parametrização por cervejaria.
-- Cada módulo guarda a SUA política, na sua própria tabela. Centralizar tudo em `brewery` faria
-- esse módulo conhecer pressão, requalificação, calibração, prazos de CAPA e escala sensorial —
-- conceitos de cinco outros módulos, e o ModularityTest acusaria com razão.
-- INVARIANTE DA HISTÓRIA: o parâmetro é OPCIONAL e a ausência dele preserva o comportamento
-- anterior. Ganhar o campo não faz a plataforma passar a inventar número.

-- Validade da liberação de CIP (fecha PKG-001-A). NULL = não expira por tempo, como antes.
CREATE TABLE sanitation_cleaning_policy (
    brewery_id UUID PRIMARY KEY,
    validity_hours INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cleaning_policy_hours CHECK (validity_hours IS NULL
        OR (validity_hours > 0 AND validity_hours <= 8760))
);

-- Periodicidade de requalificação de cilindro (fecha GAS-001-B). NULL = vencimento informado.
CREATE TABLE gas_policy (
    brewery_id UUID PRIMARY KEY,
    requalification_months INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_gas_policy_months CHECK (requalification_months IS NULL
        OR (requalification_months > 0 AND requalification_months <= 240))
);

-- Periodicidade de calibração POR TIPO: o prazo de um termômetro não é o de uma balança.
-- Tipo ausente da tabela = vencimento continua vindo do certificado.
CREATE TABLE metrology_calibration_policy (
    brewery_id UUID NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    months INTEGER NOT NULL,
    PRIMARY KEY (brewery_id, instrument_type),
    CONSTRAINT ck_calibration_policy_type CHECK (instrument_type IN ('THERMOMETER', 'HYDROMETER',
        'PH_METER', 'SCALE', 'PRESSURE_GAUGE', 'OXYGEN_METER', 'FLOW_METER')),
    CONSTRAINT ck_calibration_policy_months CHECK (months > 0 AND months <= 120)
);

-- Prazos do CAPA POR SEVERIDADE, em dias corridos da abertura (fecha QLT-002-A).
-- Severidade ausente = prazos informados na abertura da não conformidade.
CREATE TABLE quality_capa_policy (
    brewery_id UUID NOT NULL,
    severity VARCHAR(10) NOT NULL,
    containment_days INTEGER NOT NULL,
    investigation_days INTEGER NOT NULL,
    verification_days INTEGER NOT NULL,
    PRIMARY KEY (brewery_id, severity),
    CONSTRAINT ck_capa_policy_severity CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    CONSTRAINT ck_capa_policy_positive CHECK (containment_days > 0 AND investigation_days > 0
        AND verification_days > 0),
    -- A mesma ordem que o agregado impõe nas fases.
    CONSTRAINT ck_capa_policy_order CHECK (investigation_days >= containment_days
        AND verification_days >= investigation_days)
);

-- Escala da ficha sensorial. O conjunto de atributos continua fixo — parametrizá-lo
-- reestruturaria a ficha e ficou fora desta história.
CREATE TABLE sensory_policy (
    brewery_id UUID PRIMARY KEY,
    max_score SMALLINT NOT NULL DEFAULT 10,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sensory_policy_scale CHECK (max_score >= 3 AND max_score <= 100)
);

-- A escala é CONGELADA NA SESSÃO quando ela é criada. Mudar o parâmetro depois não reinterpreta
-- sessão nenhuma: uma nota 8 dada numa sessão de escala 10 não vira 8 de 50.
ALTER TABLE sensory_session ADD COLUMN max_score SMALLINT NOT NULL DEFAULT 10;
ALTER TABLE sensory_session ADD CONSTRAINT ck_sensory_session_scale
    CHECK (max_score >= 3 AND max_score <= 100);

-- O teto passa a ser o da sessão, então o banco só garante o piso; o limite superior é validado
-- pelo domínio contra a escala congelada.
ALTER TABLE sensory_evaluation DROP CONSTRAINT ck_sensory_evaluation_scores;
ALTER TABLE sensory_evaluation ADD CONSTRAINT ck_sensory_evaluation_scores
    CHECK (appearance >= 0 AND aroma >= 0 AND flavor >= 0 AND body >= 0 AND overall >= 0);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000086', '11111111-0000-0000-0000-000000000013',
     'sanitation.policy.manage', 'Configurar a validade da liberação de limpeza', false),
    ('22222222-0000-0000-0000-000000000087', '11111111-0000-0000-0000-000000000016',
     'gas.policy.manage', 'Configurar a periodicidade de requalificação', false),
    ('22222222-0000-0000-0000-000000000088', '11111111-0000-0000-0000-000000000017',
     'metrology.policy.manage', 'Configurar a periodicidade de calibração', false),
    ('22222222-0000-0000-0000-000000000089', '11111111-0000-0000-0000-000000000018',
     'quality.policy.manage', 'Configurar os prazos do CAPA', false),
    ('22222222-0000-0000-0000-000000000090', '11111111-0000-0000-0000-000000000019',
     'sensory.policy.manage', 'Configurar a escala da ficha sensorial', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sanitation.policy.manage', 'gas.policy.manage', 'metrology.policy.manage',
                 'quality.policy.manage', 'sensory.policy.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
