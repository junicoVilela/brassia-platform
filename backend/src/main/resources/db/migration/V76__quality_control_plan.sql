-- QLT-001: plano de controle, medição e desvio.
-- O plano é VERSIONADO e publicado: rascunho edita, publicado julga e nunca muda. Sem isso,
-- apertar um limite hoje transformaria em desvio uma medição que estava conforme ontem — a
-- medição grava contra qual versão foi julgada.
-- A severidade é do PONTO, não da medição: quem decide o quanto importa sair da faixa é quem
-- escreveu o plano, antes de qualquer medida existir.

CREATE TABLE quality_control_plan (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    -- Receita a que o plano se aplica; NULL vale para todas (controles de casa: higiene, água).
    recipe_id UUID,
    stage VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    version INTEGER NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_quality_plan_code_version UNIQUE (brewery_id, code, version),
    CONSTRAINT ck_quality_plan_stage CHECK (stage IN ('BREWING', 'FERMENTATION', 'MATURATION',
        'PACKAGING', 'STORAGE')),
    CONSTRAINT ck_quality_plan_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_quality_plan_version CHECK (version >= 1)
);

CREATE INDEX ix_quality_plan_brewery ON quality_control_plan (brewery_id, status, stage);

CREATE TABLE quality_control_point (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES quality_control_plan (id) ON DELETE CASCADE,
    parameter VARCHAR(120) NOT NULL,
    -- Limite unilateral é caso normal: "O₂ ≤ 50 ppb" não tem piso, e exigir um obrigaria a
    -- inventá-lo — limite inventado vira desvio inventado.
    spec_min NUMERIC(14, 4),
    spec_max NUMERIC(14, 4),
    spec_target NUMERIC(14, 4),
    unit VARCHAR(20) NOT NULL,
    frequency_kind VARCHAR(20) NOT NULL,
    every_hours INTEGER,
    -- Ponto sem ação não é controle, é observação: ninguém sabe o que fazer com o desvio.
    action VARCHAR(500) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    critical BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_quality_point_parameter UNIQUE (plan_id, parameter),
    CONSTRAINT ck_quality_point_limits CHECK (spec_min IS NOT NULL OR spec_max IS NOT NULL),
    CONSTRAINT ck_quality_point_range CHECK (spec_min IS NULL OR spec_max IS NULL OR spec_min < spec_max),
    CONSTRAINT ck_quality_point_target CHECK (
        spec_target IS NULL
        OR ((spec_min IS NULL OR spec_target >= spec_min) AND (spec_max IS NULL OR spec_target <= spec_max))),
    CONSTRAINT ck_quality_point_frequency CHECK (frequency_kind IN ('PER_BATCH', 'PER_HOURS', 'PER_SHIFT',
        'PER_PACKAGING_RUN')),
    -- Intervalo só faz sentido na cadência por horas.
    CONSTRAINT ck_quality_point_hours CHECK (
        (frequency_kind = 'PER_HOURS' AND every_hours IS NOT NULL AND every_hours > 0)
        OR (frequency_kind <> 'PER_HOURS' AND every_hours IS NULL)),
    CONSTRAINT ck_quality_point_severity CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL'))
);

CREATE INDEX ix_quality_point_plan ON quality_control_point (plan_id, parameter);

CREATE TABLE quality_measurement (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES quality_control_plan (id),
    -- Versão pela qual foi julgada: permite dizer, meses depois, contra qual faixa foi aprovada.
    plan_version INTEGER NOT NULL,
    point_id UUID NOT NULL REFERENCES quality_control_point (id),
    parameter VARCHAR(120) NOT NULL,
    batch_id UUID,
    instrument_id UUID,
    -- Aptidão do instrumento no momento. Em ponto não crítico a medição passa mesmo com
    -- instrumento vencido, mas passa dizendo que passou assim.
    instrument_fitness VARCHAR(20),
    value NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    within_spec BOOLEAN NOT NULL,
    note VARCHAR(500),
    measured_at TIMESTAMPTZ NOT NULL,
    measured_by UUID NOT NULL
);

CREATE INDEX ix_quality_measurement_plan ON quality_measurement (brewery_id, plan_id, measured_at DESC);
CREATE INDEX ix_quality_measurement_batch ON quality_measurement (brewery_id, batch_id, measured_at DESC)
    WHERE batch_id IS NOT NULL;

CREATE TABLE quality_deviation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    measurement_id UUID NOT NULL UNIQUE REFERENCES quality_measurement (id),
    plan_id UUID NOT NULL REFERENCES quality_control_plan (id),
    point_id UUID NOT NULL REFERENCES quality_control_point (id),
    parameter VARCHAR(120) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    bound VARCHAR(10) NOT NULL,
    limit_value NUMERIC(14, 4) NOT NULL,
    measured_value NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    -- Ação copiada do ponto no momento da abertura: o desvio precisa dizer o que fazer mesmo que
    -- uma versão futura do plano mude a prescrição.
    action VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by UUID NOT NULL,
    CONSTRAINT ck_quality_deviation_severity CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    CONSTRAINT ck_quality_deviation_bound CHECK (bound IN ('BELOW_MIN', 'ABOVE_MAX')),
    CONSTRAINT ck_quality_deviation_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX ix_quality_deviation_open ON quality_deviation (brewery_id, status, opened_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000018', NULL, 'quality', 'Qualidade', 23)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000077', '11111111-0000-0000-0000-000000000018',
     'quality.plan.read', 'Consultar planos de controle, medições e desvios', false),
    ('22222222-0000-0000-0000-000000000078', '11111111-0000-0000-0000-000000000018',
     'quality.plan.manage', 'Criar, editar e publicar planos de controle', false),
    ('22222222-0000-0000-0000-000000000079', '11111111-0000-0000-0000-000000000018',
     'quality.measurement.record', 'Registrar medições contra o plano de controle', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('quality.plan.read', 'quality.plan.manage', 'quality.measurement.record')
ON CONFLICT (group_id, permission_id) DO NOTHING;
