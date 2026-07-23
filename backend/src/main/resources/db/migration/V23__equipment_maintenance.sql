-- EQP-002: janelas de manutenção/calibração do equipamento (indisponibilidade).
-- Enquanto SCHEDULED, o equipamento fica indisponível no intervalo e não pode
-- ser reservado. Calibração referencia o instrumento associado.

CREATE TABLE equipment_maintenance (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    equipment_id UUID NOT NULL REFERENCES equipment (id),
    kind VARCHAR(16) NOT NULL,
    instrument VARCHAR(160),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_maintenance_kind CHECK (kind IN ('MAINTENANCE', 'CALIBRATION')),
    CONSTRAINT ck_maintenance_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_maintenance_range CHECK (ends_at > starts_at)
);

CREATE INDEX ix_equipment_maintenance_window
    ON equipment_maintenance (equipment_id, starts_at, ends_at);

-- Nova permissão de gestão de manutenção; a leitura reaproveita equipment.read.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000025', '11111111-0000-0000-0000-000000000006',
     'equipment.maintenance.manage', 'Planejar manutenção/calibração', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'equipment.maintenance.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
