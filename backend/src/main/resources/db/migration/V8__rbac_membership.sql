-- Associação passa a ser única por (usuário, grupo, cervejaria): o mesmo usuário
-- pode pertencer ao mesmo grupo em cervejarias diferentes; a associação global
-- (brewery_id NULL) permanece única por usuário+grupo.
ALTER TABLE user_group_membership DROP CONSTRAINT uq_membership_user_group;
ALTER TABLE user_group_membership
    ADD CONSTRAINT uq_membership_user_group_brewery UNIQUE NULLS NOT DISTINCT (user_id, group_id, brewery_id);

-- Permissões de administração de acessos no catálogo + grupo Administradores.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000008', '11111111-0000-0000-0000-000000000002', 'security.permission.read', 'Consultar catálogo de permissões', false),
    ('22222222-0000-0000-0000-000000000009', '11111111-0000-0000-0000-000000000002', 'security.group.read', 'Listar grupos', false),
    ('22222222-0000-0000-0000-00000000000a', '11111111-0000-0000-0000-000000000002', 'security.membership.manage', 'Associar/desassociar usuário a grupo', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('security.permission.read', 'security.group.read', 'security.membership.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
