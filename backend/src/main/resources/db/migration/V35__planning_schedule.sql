-- PLN-001: agenda de produção. Uma entrada = intenção de brassar uma receita
-- publicada, num equipamento, numa janela de tempo, sob um responsável.
-- Referências a outros módulos (recipe/equipment/usuário) são lógicas (UUID),
-- sem FK entre esquemas, preservando a independência dos módulos.

CREATE TABLE planning_schedule_entry (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    recipe_id UUID NOT NULL,
    equipment_id UUID NOT NULL,
    assigned_user_id UUID NOT NULL,
    planned_volume_liters NUMERIC(12, 3) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_planning_schedule_volume CHECK (planned_volume_liters > 0),
    CONSTRAINT ck_planning_schedule_window CHECK (scheduled_end > scheduled_start),
    CONSTRAINT ck_planning_schedule_status CHECK (status IN ('PLANNED'))
);

-- Consulta de conflito de equipamento (janela) e listagem por período.
CREATE INDEX ix_planning_schedule_equipment
    ON planning_schedule_entry (brewery_id, equipment_id, scheduled_start, scheduled_end);
CREATE INDEX ix_planning_schedule_period
    ON planning_schedule_entry (brewery_id, scheduled_start);

-- Backstop de concorrência: nenhum par de entradas PLANNED do mesmo equipamento
-- pode ter janelas sobrepostas (fecha a corrida check-then-insert do pré-check).
-- Usa tstzrange [) — janelas apenas adjacentes não conflitam.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE planning_schedule_entry
    ADD CONSTRAINT ex_planning_schedule_no_overlap
    EXCLUDE USING gist (
        brewery_id WITH =,
        equipment_id WITH =,
        tstzrange(scheduled_start, scheduled_end) WITH &&
    ) WHERE (status = 'PLANNED');

-- Domínio e permissões de planejamento.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000009', NULL, 'planning', 'Planejamento', 11)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000032', '11111111-0000-0000-0000-000000000009',
     'planning.schedule.read', 'Consultar agenda de produção', false),
    ('22222222-0000-0000-0000-000000000033', '11111111-0000-0000-0000-000000000009',
     'planning.schedule.manage', 'Planejar produção (agenda)', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('planning.schedule.read', 'planning.schedule.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
