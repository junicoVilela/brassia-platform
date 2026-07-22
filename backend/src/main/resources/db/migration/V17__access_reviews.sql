-- SEC-013: revisão de acessos e regras de segregação de funções.

CREATE TABLE access_review (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES security_user (id),
    due_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_access_review_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE access_review_item (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES access_review (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES security_user (id),
    group_id UUID NOT NULL REFERENCES security_group (id),
    decision VARCHAR(20),
    justification VARCHAR(500),
    decided_at TIMESTAMPTZ,
    CONSTRAINT ck_access_review_item_decision CHECK (
        decision IS NULL OR decision IN ('KEEP', 'REMOVE'))
);

CREATE INDEX idx_access_review_item_review ON access_review_item (review_id);

CREATE TABLE segregation_rule (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    left_permission_code VARCHAR(120) NOT NULL,
    right_permission_code VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_segregation_rule UNIQUE (brewery_id, left_permission_code, right_permission_code),
    CONSTRAINT ck_segregation_distinct CHECK (left_permission_code <> right_permission_code)
);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000018', '11111111-0000-0000-0000-000000000002',
     'security.access-review.read', 'Consultar revisões de acesso', false),
    ('22222222-0000-0000-0000-000000000019', '11111111-0000-0000-0000-000000000002',
     'security.access-review.manage', 'Criar revisões de acesso', true),
    ('22222222-0000-0000-0000-00000000001a', '11111111-0000-0000-0000-000000000002',
     'security.access-review.decide', 'Decidir itens de revisão', true),
    ('22222222-0000-0000-0000-00000000001b', '11111111-0000-0000-0000-000000000002',
     'security.segregation.manage', 'Configurar segregação de funções', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN (
    'security.access-review.read', 'security.access-review.manage',
    'security.access-review.decide', 'security.segregation.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
