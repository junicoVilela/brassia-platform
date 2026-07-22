-- SEC-012: throttle de login e alertas de segurança.

CREATE TABLE login_throttle (
    subject_hash VARCHAR(128) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    failure_count INTEGER NOT NULL DEFAULT 0,
    penalty_until TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (subject_hash, subject_type),
    CONSTRAINT ck_login_throttle_type CHECK (subject_type IN ('EMAIL', 'IP'))
);

CREATE TABLE security_alert (
    id UUID PRIMARY KEY,
    brewery_id UUID REFERENCES brewery (id),
    user_id UUID REFERENCES security_user (id),
    alert_type VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES security_user (id),
    CONSTRAINT ck_security_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_security_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE INDEX idx_security_alert_brewery_status ON security_alert (brewery_id, status, created_at DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000016', '11111111-0000-0000-0000-000000000002',
     'security.alert.read', 'Consultar alertas de segurança', false),
    ('22222222-0000-0000-0000-000000000017', '11111111-0000-0000-0000-000000000002',
     'security.alert.manage', 'Reconhecer/resolver alertas', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('security.alert.read', 'security.alert.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
