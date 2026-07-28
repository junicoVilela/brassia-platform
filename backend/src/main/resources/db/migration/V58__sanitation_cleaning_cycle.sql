-- CLN-003: execução de ciclo de limpeza/sanitização. O ciclo referencia uma versão
-- publicada de POP e um equipamento; ao iniciar, congela um snapshot das etapas/faixas.
-- Parâmetro medido fora da ficha é bloqueado, salvo override com justificativa (alçada).

CREATE TABLE sanitation_cleaning_cycle (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    procedure_id UUID NOT NULL,
    procedure_code VARCHAR(40) NOT NULL,
    procedure_version INT NOT NULL,
    equipment_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    interrupt_reason VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT ck_sanitation_cycle_status
        CHECK (status IN ('IN_PROGRESS', 'INTERRUPTED', 'COMPLETED'))
);

CREATE INDEX ix_sanitation_cycle_brewery ON sanitation_cleaning_cycle (brewery_id, started_at DESC);

CREATE TABLE sanitation_cycle_step (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL REFERENCES sanitation_cleaning_cycle (id),
    brewery_id UUID NOT NULL,
    step_order INT NOT NULL,
    method VARCHAR(120) NOT NULL,
    product VARCHAR(120),
    concentration_min_pct NUMERIC(10, 3),
    concentration_max_pct NUMERIC(10, 3),
    temp_min_c NUMERIC(10, 3),
    temp_max_c NUMERIC(10, 3),
    time_minutes INT,
    prohibition VARCHAR(200),
    evidence_required BOOLEAN NOT NULL,
    status VARCHAR(8) NOT NULL,
    measured_concentration_pct NUMERIC(10, 3),
    measured_temp_c NUMERIC(10, 3),
    measured_time_minutes INT,
    flow_actual VARCHAR(200),
    evidence VARCHAR(500),
    out_of_order_reason VARCHAR(500),
    overridden BOOLEAN NOT NULL DEFAULT false,
    override_reason VARCHAR(500),
    executed_at TIMESTAMPTZ,
    CONSTRAINT ck_sanitation_cycle_step_status CHECK (status IN ('PENDING', 'DONE')),
    CONSTRAINT uq_sanitation_cycle_step UNIQUE (cycle_id, step_order)
);

CREATE INDEX ix_sanitation_cycle_step_cycle ON sanitation_cycle_step (brewery_id, cycle_id, step_order);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000054', '11111111-0000-0000-0000-000000000013',
     'sanitation.cycle.read', 'Consultar ciclos de limpeza', false),
    ('22222222-0000-0000-0000-000000000055', '11111111-0000-0000-0000-000000000013',
     'sanitation.cycle.execute', 'Executar ciclos de limpeza', false),
    ('22222222-0000-0000-0000-000000000056', '11111111-0000-0000-0000-000000000013',
     'sanitation.cycle.override', 'Registrar parâmetro fora da ficha (alçada)', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sanitation.cycle.read', 'sanitation.cycle.execute', 'sanitation.cycle.override')
ON CONFLICT (group_id, permission_id) DO NOTHING;
