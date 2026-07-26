-- STK-004: inventário físico. A contagem é evidência imutável; a aprovação gera
-- movimentos de ajuste no ledger (STK-002) para conciliar o saldo ao contado.

CREATE TABLE physical_count (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT ck_physical_count_status CHECK (status IN ('OPEN', 'APPROVED'))
);

CREATE INDEX ix_physical_count_brewery ON physical_count (brewery_id, created_at DESC);

CREATE TABLE physical_count_line (
    id UUID PRIMARY KEY,
    count_id UUID NOT NULL REFERENCES physical_count (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    lot_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    unit VARCHAR(8) NOT NULL,
    counted_quantity NUMERIC(14, 4) NOT NULL,
    system_quantity NUMERIC(14, 4) NOT NULL,
    CONSTRAINT ck_physical_count_line_counted CHECK (counted_quantity >= 0)
);

CREATE INDEX ix_physical_count_line_count ON physical_count_line (count_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000040', '11111111-0000-0000-0000-000000000011',
     'inventory.count.read', 'Consultar contagens físicas', false),
    ('22222222-0000-0000-0000-000000000041', '11111111-0000-0000-0000-000000000011',
     'inventory.count.manage', 'Registrar contagens físicas', false),
    ('22222222-0000-0000-0000-000000000042', '11111111-0000-0000-0000-000000000011',
     'inventory.count.approve', 'Aprovar contagens físicas (gera ajustes)', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('inventory.count.read', 'inventory.count.manage', 'inventory.count.approve')
ON CONFLICT (group_id, permission_id) DO NOTHING;
