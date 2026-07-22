-- Concessão de acesso temporário: uma permissão pontual, com justificativa e
-- vigência, concedida a um usuário na cervejaria ativa. Concessão de permissão
-- crítica só vige após aprovação de um segundo usuário (approved_by <> requested_by).
-- Sem FK para brewery (mesma razão do audit_event: não acopla ao schema de outro módulo).
CREATE TABLE temporary_access_grant (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES security_user (id),
    permission_id UUID NOT NULL REFERENCES security_permission (id),
    reason VARCHAR(500) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    requested_by UUID NOT NULL REFERENCES security_user (id),
    approved_by UUID REFERENCES security_user (id),
    approved_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES security_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_temp_access_window CHECK (valid_until > valid_from),
    CONSTRAINT ck_temp_access_approver CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

-- Concessões vigentes por usuário/cervejaria (usado na resolução de permissões do login).
CREATE INDEX idx_temp_access_effective ON temporary_access_grant
    (brewery_id, user_id, valid_until) WHERE revoked_at IS NULL;

-- Permissões de gestão do acesso temporário no catálogo + grupo Administradores.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-00000000000c', '11111111-0000-0000-0000-000000000002', 'security.temporary-access.request', 'Solicitar acesso temporário', true),
    ('22222222-0000-0000-0000-00000000000d', '11111111-0000-0000-0000-000000000002', 'security.temporary-access.approve', 'Aprovar acesso temporário', true),
    ('22222222-0000-0000-0000-00000000000e', '11111111-0000-0000-0000-000000000002', 'security.temporary-access.revoke', 'Revogar acesso temporário', true),
    ('22222222-0000-0000-0000-00000000000f', '11111111-0000-0000-0000-000000000002', 'security.temporary-access.read', 'Consultar concessões temporárias', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('security.temporary-access.request', 'security.temporary-access.approve',
                 'security.temporary-access.revoke', 'security.temporary-access.read')
ON CONFLICT (group_id, permission_id) DO NOTHING;
