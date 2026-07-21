-- RBAC: catálogo hierárquico de permissões e grupos que as agregam.
-- Nesta fatia os grupos/associações são GLOBAIS: brewery_id é anulável e SEM FK
-- para brewery (que só existe na BRW-001). O escopo por cervejaria (access_scope)
-- é da SEC-005. Ausência de concessão significa negação.

CREATE TABLE permission_domain (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES permission_domain (id),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE security_permission (
    id UUID PRIMARY KEY,
    domain_id UUID NOT NULL REFERENCES permission_domain (id),
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    critical BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE security_group (
    id UUID PRIMARY KEY,
    brewery_id UUID,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    system_group BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_group_brewery_code UNIQUE NULLS NOT DISTINCT (brewery_id, code)
);

CREATE TABLE group_permission (
    group_id UUID NOT NULL REFERENCES security_group (id),
    permission_id UUID NOT NULL REFERENCES security_permission (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, permission_id)
);

CREATE TABLE user_group_membership (
    id UUID PRIMARY KEY,
    brewery_id UUID,
    user_id UUID NOT NULL REFERENCES security_user (id),
    group_id UUID NOT NULL REFERENCES security_group (id),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_until TIMESTAMPTZ,
    granted_by UUID REFERENCES security_user (id),
    reason VARCHAR(500),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_membership_validity CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT uq_membership_user_group UNIQUE (user_id, group_id)
);

CREATE INDEX idx_membership_user ON user_group_membership (user_id);

-- Seed do catálogo (UUIDs fixos, idempotente).
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000001', NULL, 'recipe', 'Receitas', 10),
    ('11111111-0000-0000-0000-000000000002', NULL, 'security', 'Segurança', 20),
    ('11111111-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002', 'security.user', 'Usuários', 21)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'recipe.create', 'Criar receita', false),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000003', 'security.user.read', 'Listar usuários', false),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000003', 'security.user.invite', 'Convidar usuário', true),
    ('22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000003', 'security.user.block', 'Bloquear/desbloquear usuário', true),
    ('22222222-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000003', 'security.user.disable', 'Desativar usuário', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_group (id, brewery_id, code, name, description, system_group) VALUES
    ('33333333-0000-0000-0000-000000000001', NULL, 'ADMINISTRATORS', 'Administradores', 'Grupo de sistema com acesso administrativo pleno', true)
ON CONFLICT (brewery_id, code) DO NOTHING;

-- Administradores recebem todas as permissões ativas do catálogo.
INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id FROM security_permission p WHERE p.active
ON CONFLICT (group_id, permission_id) DO NOTHING;
