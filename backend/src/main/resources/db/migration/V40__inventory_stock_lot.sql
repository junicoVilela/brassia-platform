-- STK-001: recebimento de lote de insumo. Quantidade recebida é o fato de
-- entrada; o saldo disponível derivará do ledger (STK-002). Referências a
-- ingrediente/fornecedor são lógicas (UUID).

CREATE TABLE stock_lot (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_lot_code VARCHAR(80),
    received_quantity NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(8) NOT NULL,
    unit_cost NUMERIC(14, 4) NOT NULL,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    inspection VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_stock_lot_quantity CHECK (received_quantity > 0),
    CONSTRAINT ck_stock_lot_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_stock_lot_unit CHECK (unit IN ('KG', 'G', 'MG', 'L', 'ML', 'UNIT')),
    CONSTRAINT ck_stock_lot_inspection CHECK (inspection IN ('APPROVED', 'BLOCKED'))
);

-- Consulta por cervejaria + validade (FEFO/índice de validade nas próximas histórias).
CREATE INDEX ix_stock_lot_brewery ON stock_lot (brewery_id, ingredient_id, expiry_date);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000011', NULL, 'inventory', 'Estoque', 13)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000038', '11111111-0000-0000-0000-000000000011',
     'inventory.lot.read', 'Consultar lotes de estoque', false),
    ('22222222-0000-0000-0000-000000000039', '11111111-0000-0000-0000-000000000011',
     'inventory.lot.manage', 'Receber/gerir lotes de estoque', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('inventory.lot.read', 'inventory.lot.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
