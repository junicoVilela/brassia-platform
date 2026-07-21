-- Histórico de tentativas de login. Pseudonimizado: identificador, IP e user-agent
-- são armazenados apenas como hash (retenção sem expor dado pessoal em claro).
CREATE TABLE login_event (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES security_user (id),
    attempted_identifier_hash VARCHAR(128) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    reason_code VARCHAR(60) NOT NULL,
    ip_hash VARCHAR(128),
    user_agent_hash VARCHAR(128),
    trace_id VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_login_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_login_event_user_time ON login_event (user_id, occurred_at DESC);
