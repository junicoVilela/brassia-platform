-- STK-002-A: exceção autorizada de saldo negativo. Uma saída que deixaria o
-- on_hand < 0 continua sendo rejeitada (409), salvo quando o solicitante tem
-- esta permissão E pede explicitamente (allowNegative) — o override é auditado.

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000046', '11111111-0000-0000-0000-000000000011',
     'inventory.stock.override', 'Autorizar saldo negativo em saída de estoque', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'inventory.stock.override'
ON CONFLICT (group_id, permission_id) DO NOTHING;
