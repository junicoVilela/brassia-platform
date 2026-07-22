-- SEC-011: contas de serviço e API keys (segredo só em hash + prefixo).

CREATE TABLE service_account (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_account_brewery_code UNIQUE (brewery_id, code)
);

CREATE TABLE api_credential (
    id UUID PRIMARY KEY,
    service_account_id UUID NOT NULL REFERENCES service_account (id),
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_api_credential_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_credential_prefix_active
    ON api_credential (key_prefix)
    WHERE revoked_at IS NULL;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000012', '11111111-0000-0000-0000-000000000002',
     'security.service-account.read', 'Listar contas de serviço', false),
    ('22222222-0000-0000-0000-000000000013', '11111111-0000-0000-0000-000000000002',
     'security.service-account.manage', 'Criar/editar contas de serviço', true),
    ('22222222-0000-0000-0000-000000000014', '11111111-0000-0000-0000-000000000002',
     'security.api-credential.issue', 'Emitir/rotacionar API keys', true),
    ('22222222-0000-0000-0000-000000000015', '11111111-0000-0000-0000-000000000002',
     'security.api-credential.revoke', 'Revogar API keys', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN (
    'security.service-account.read', 'security.service-account.manage',
    'security.api-credential.issue', 'security.api-credential.revoke')
ON CONFLICT (group_id, permission_id) DO NOTHING;
