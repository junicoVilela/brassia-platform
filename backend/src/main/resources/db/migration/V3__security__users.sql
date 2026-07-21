-- Identidade interna. Nunca persistir senha, token ou segredo em texto puro.
-- O e-mail normalizado é a autoridade de unicidade (global); o vínculo com
-- cervejaria é feito por escopos/grupos (SEC-004/005), não aqui.
CREATE TABLE security_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_security_user_status CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT uq_security_user_normalized_email UNIQUE (normalized_email)
);

-- Token de conta (convite/verificação/reset): apenas o hash é persistido.
CREATE TABLE account_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security_user (id),
    token_type VARCHAR(30) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_account_token_type CHECK (token_type IN ('INVITATION', 'EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT uq_account_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_account_token_user ON account_token (user_id);
