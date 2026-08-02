-- PKG-001: plano de envase — embalagem, quantidade, linha, janela e checklist.
-- O plano é intenção (PLANNED); RESERVED significa linha verificada e embalagem reservada,
-- e é o estado que a execução (PKG-003) vai consumir. CANCELLED é terminal e devolve a reserva.
-- O volume planejado é derivado de unidades × volume da embalagem, nunca informado.

CREATE TABLE packaging_plan (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    batch_id UUID NOT NULL,
    container_id UUID NOT NULL,
    container_volume_ml NUMERIC(10, 3) NOT NULL,
    planned_units INTEGER NOT NULL,
    planned_volume_liters NUMERIC(12, 3) NOT NULL,
    line_equipment_id UUID NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(10) NOT NULL,
    reserved_at TIMESTAMPTZ,
    reserved_by UUID,
    cancel_reason VARCHAR(200),
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_packaging_plan_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_packaging_plan_status CHECK (status IN ('PLANNED', 'RESERVED', 'CANCELLED')),
    CONSTRAINT ck_packaging_plan_units CHECK (planned_units > 0),
    CONSTRAINT ck_packaging_plan_container_volume CHECK (container_volume_ml > 0),
    CONSTRAINT ck_packaging_plan_window CHECK (planned_end > planned_start),
    -- Reserva sem responsável e instante não é rastreável.
    CONSTRAINT ck_packaging_plan_reserved CHECK (
        status <> 'RESERVED' OR (reserved_at IS NOT NULL AND reserved_by IS NOT NULL)),
    -- Cancelamento sem motivo esconde por que a embalagem voltou ao estoque.
    CONSTRAINT ck_packaging_plan_cancelled CHECK (
        status <> 'CANCELLED' OR (cancel_reason IS NOT NULL AND cancelled_at IS NOT NULL))
);

CREATE INDEX ix_packaging_plan_batch ON packaging_plan (brewery_id, batch_id, planned_start DESC);
CREATE INDEX ix_packaging_plan_line ON packaging_plan (brewery_id, line_equipment_id, planned_start);

-- Checklist: um registro por item confirmado, com quem confirmou e quando. Ausência de linha
-- é o item pendente; a PK impede que a mesma confirmação seja regravada e perca a evidência.
CREATE TABLE packaging_plan_checklist_item (
    plan_id UUID NOT NULL REFERENCES packaging_plan (id),
    brewery_id UUID NOT NULL,
    item VARCHAR(24) NOT NULL,
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_packaging_plan_checklist PRIMARY KEY (plan_id, item),
    CONSTRAINT ck_packaging_plan_checklist_item
        CHECK (item IN ('CONTAINER_INSPECTED', 'SEAL_TEST', 'GAS_SUPPLY'))
);

CREATE INDEX ix_packaging_plan_checklist_brewery ON packaging_plan_checklist_item (brewery_id, plan_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000015', NULL, 'packaging', 'Envase e embalagem', 17)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000069', '11111111-0000-0000-0000-000000000015',
     'packaging.plan.read', 'Consultar planos de envase', false),
    ('22222222-0000-0000-0000-000000000070', '11111111-0000-0000-0000-000000000015',
     'packaging.plan.manage', 'Planejar, reservar e cancelar envase', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('packaging.plan.read', 'packaging.plan.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
