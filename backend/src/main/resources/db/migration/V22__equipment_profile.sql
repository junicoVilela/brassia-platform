-- EQP-001: perfil de equipamento (capacidade, perdas, eficiência, evaporação).
-- Linha atual mutável com lock otimista + revisão append-only por versão.

CREATE TABLE equipment (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    capacity_liters NUMERIC(12, 3) NOT NULL,
    dead_space_liters NUMERIC(12, 3) NOT NULL,
    mash_efficiency_percent NUMERIC(5, 2) NOT NULL,
    boil_off_liters_per_hour NUMERIC(12, 3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_equipment_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_equipment_capacity CHECK (capacity_liters > 0),
    CONSTRAINT ck_equipment_dead_space
        CHECK (dead_space_liters >= 0 AND dead_space_liters <= capacity_liters),
    CONSTRAINT ck_equipment_efficiency
        CHECK (mash_efficiency_percent > 0 AND mash_efficiency_percent <= 100),
    CONSTRAINT ck_equipment_boil_off CHECK (boil_off_liters_per_hour >= 0)
);

CREATE INDEX ix_equipment_brewery ON equipment (brewery_id);

CREATE TABLE equipment_revision (
    equipment_id UUID NOT NULL,
    brewery_id UUID NOT NULL,
    version BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    capacity_liters NUMERIC(12, 3) NOT NULL,
    dead_space_liters NUMERIC(12, 3) NOT NULL,
    mash_efficiency_percent NUMERIC(5, 2) NOT NULL,
    boil_off_liters_per_hour NUMERIC(12, 3) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID,
    PRIMARY KEY (equipment_id, version)
);

-- Domínio de permissões de equipamentos + permissões da história ao grupo Administradores.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000006', NULL, 'equipment', 'Equipamentos', 7)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000023', '11111111-0000-0000-0000-000000000006',
     'equipment.read', 'Consultar equipamentos', false),
    ('22222222-0000-0000-0000-000000000024', '11111111-0000-0000-0000-000000000006',
     'equipment.manage', 'Cadastrar/editar equipamentos', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('equipment.read', 'equipment.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
