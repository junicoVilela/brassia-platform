-- Histórico de senhas para impedir reutilização das últimas N. Apenas hashes.
CREATE TABLE password_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security_user (id),
    password_hash VARCHAR(512) NOT NULL,
    encoder_id VARCHAR(40) NOT NULL,
    replaced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_history_user ON password_history (user_id, replaced_at DESC);
