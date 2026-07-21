-- Trilha de auditoria persistida (append-only): ator, alvo, resultado, trace e
-- diff mascarado. Nunca contém senha/token/segredo em claro. Sem FK para brewery
-- (auditoria não acopla ao schema de outro módulo e sobrevive a remoções).
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    brewery_id UUID,
    actor_id UUID,
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(120),
    outcome VARCHAR(24) NOT NULL,
    trace_id VARCHAR(80),
    change_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_brewery_time ON audit_event (brewery_id, occurred_at DESC);

-- Permissão de leitura da auditoria no catálogo RBAC + grupo Administradores.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-00000000000b', '11111111-0000-0000-0000-000000000002', 'security.audit.read', 'Consultar auditoria de segurança', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p WHERE p.code = 'security.audit.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
