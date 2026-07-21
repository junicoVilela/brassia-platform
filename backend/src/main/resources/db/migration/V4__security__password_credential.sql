-- Credencial de senha da conta interna. Nunca em texto puro: apenas o hash
-- (encoder embutido/registrado). Uma credencial por usuário.
CREATE TABLE password_credential (
    user_id UUID PRIMARY KEY REFERENCES security_user (id),
    password_hash VARCHAR(512) NOT NULL,
    encoder_id VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
