-- SEC-009: MFA (TOTP) e códigos de recuperação. Passkeys (WebAuthn) ficam para fatia seguinte.
-- Segredo TOTP fica cifrado em repouso com chave de aplicação (versão referenciada).

CREATE TABLE mfa_factor (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security_user (id),
    factor_type VARCHAR(20) NOT NULL,
    label VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    secret_ciphertext TEXT,
    secret_key_version INTEGER,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_mfa_factor_type CHECK (factor_type IN ('TOTP')),
    CONSTRAINT ck_mfa_factor_status CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT uq_mfa_factor_user_type UNIQUE (user_id, factor_type)
);

CREATE INDEX idx_mfa_factor_user ON mfa_factor (user_id);

CREATE TABLE recovery_code (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security_user (id),
    code_hash VARCHAR(128) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at TIMESTAMPTZ,
    CONSTRAINT uq_recovery_code_hash UNIQUE (code_hash)
);

CREATE INDEX idx_recovery_code_user ON recovery_code (user_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000011', '11111111-0000-0000-0000-000000000002',
     'security.mfa.manage', 'Administrar fatores MFA de terceiros', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p WHERE p.code = 'security.mfa.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
