-- FER-004: linha do tempo e agenda de fermentação do lote. Nasce de um perfil publicado —
-- vínculo que também dá ao lote o perfil que rege a estabilidade de FG (remove o débito
-- FER-003-1) — e admite etapas específicas do lote (dry hop, cold crash, transferência).
-- Cada etapa tem ação, janela planejada, condição de avanço, tolerância e responsável.
-- O planejado nunca é reescrito: executado, desvio e justificativa convivem com ele.

CREATE TABLE fermentation_schedule (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    profile_id UUID NOT NULL REFERENCES fermentation_profile (id),
    profile_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- Uma agenda por lote: a linha do tempo do lote é única.
    CONSTRAINT uq_fermentation_schedule_batch UNIQUE (brewery_id, batch_id)
);

CREATE TABLE fermentation_schedule_step (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES fermentation_schedule (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    action VARCHAR(16) NOT NULL,
    condition VARCHAR(8) NOT NULL,
    condition_days INTEGER,
    target_gravity NUMERIC(6, 4),
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    tolerance_hours INTEGER NOT NULL,
    responsible_user_id UUID NOT NULL,
    depends_on_previous BOOLEAN NOT NULL DEFAULT true,
    status VARCHAR(8) NOT NULL,
    executed_at TIMESTAMPTZ,
    justification VARCHAR(300),
    CONSTRAINT uq_fermentation_schedule_step_order UNIQUE (schedule_id, step_order),
    CONSTRAINT ck_fermentation_step_action
        CHECK (action IN ('RAMP', 'REST', 'DRY_HOP', 'COLD_CRASH', 'TRANSFER', 'CONDITIONING')),
    CONSTRAINT ck_fermentation_step_condition CHECK (condition IN ('TIME', 'GRAVITY', 'MANUAL')),
    CONSTRAINT ck_fermentation_step_time CHECK (condition <> 'TIME' OR condition_days IS NOT NULL),
    CONSTRAINT ck_fermentation_step_gravity CHECK (condition <> 'GRAVITY' OR target_gravity IS NOT NULL),
    CONSTRAINT ck_fermentation_step_window CHECK (planned_end >= planned_start),
    CONSTRAINT ck_fermentation_step_tolerance CHECK (tolerance_hours >= 0),
    CONSTRAINT ck_fermentation_step_status CHECK (status IN ('PLANNED', 'DONE')),
    -- Etapa executada sempre registra quando; o planejado ao lado continua intacto.
    CONSTRAINT ck_fermentation_step_executed CHECK ((status = 'DONE') = (executed_at IS NOT NULL))
);

CREATE INDEX ix_fermentation_schedule_step ON fermentation_schedule_step (schedule_id, step_order);
CREATE INDEX ix_fermentation_schedule_pending
    ON fermentation_schedule_step (brewery_id, status, planned_end);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000067', '11111111-0000-0000-0000-000000000014',
     'fermentation.schedule.read', 'Consultar a agenda de fermentação', false),
    ('22222222-0000-0000-0000-000000000068', '11111111-0000-0000-0000-000000000014',
     'fermentation.schedule.manage', 'Planejar, replanejar e executar etapas da fermentação', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('fermentation.schedule.read', 'fermentation.schedule.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
