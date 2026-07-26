-- PUR-001: necessidade de compra. Cálculo derivado (demanda das OPs liberadas −
-- saldo em estoque); não há tabela nova, apenas a permissão de leitura.

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000043', '11111111-0000-0000-0000-000000000010',
     'purchasing.purchase.read', 'Consultar necessidade de compra', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'purchasing.purchase.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
