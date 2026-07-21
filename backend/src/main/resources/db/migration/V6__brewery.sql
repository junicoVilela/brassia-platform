-- Cervejaria: o tenant do sistema. Preferências operacionais são a BRW-002.
CREATE TABLE brewery (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_brewery_code UNIQUE (code)
);

-- Permissões da cervejaria no catálogo RBAC + concessão ao grupo Administradores.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000004', NULL, 'brewery', 'Cervejaria', 5)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000006', '11111111-0000-0000-0000-000000000004', 'brewery.read', 'Listar cervejarias', false),
    ('22222222-0000-0000-0000-000000000007', '11111111-0000-0000-0000-000000000004', 'brewery.manage', 'Cadastrar/editar cervejaria', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('brewery.read', 'brewery.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
