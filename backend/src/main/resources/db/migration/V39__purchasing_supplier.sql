-- STK-001: cadastro mínimo de fornecedor (base p/ recebimento de lotes e
-- consolidação de compras por fornecedor).

CREATE TABLE supplier (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    code VARCHAR(40) NOT NULL,
    normalized_code VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_supplier_code UNIQUE (brewery_id, normalized_code)
);

CREATE INDEX ix_supplier_brewery ON supplier (brewery_id, name);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000010', NULL, 'purchasing', 'Compras', 12)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000036', '11111111-0000-0000-0000-000000000010',
     'purchasing.supplier.read', 'Consultar fornecedores', false),
    ('22222222-0000-0000-0000-000000000037', '11111111-0000-0000-0000-000000000010',
     'purchasing.supplier.manage', 'Cadastrar fornecedores', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('purchasing.supplier.read', 'purchasing.supplier.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
