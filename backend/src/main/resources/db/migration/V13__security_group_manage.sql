-- Administração de grupos (SEC-004 fatia 3): criar/atualizar grupos customizados
-- e substituir o conjunto de permissões. Grupo de sistema permanece imutável na aplicação.

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000010', '11111111-0000-0000-0000-000000000002',
     'security.group.manage', 'Criar e atualizar grupos e suas permissões', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'security.group.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
