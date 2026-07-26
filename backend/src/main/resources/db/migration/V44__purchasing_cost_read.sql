-- PUR-002: lista de compras consolidada. A lista em si usa purchasing.purchase.read
-- (PUR-001); esta permissão adicional libera a exibição/exportação de custos.

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000044', '11111111-0000-0000-0000-000000000010',
     'purchasing.cost.read', 'Ver custos na lista de compras', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'purchasing.cost.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
