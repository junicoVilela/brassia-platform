-- BOP-001: ordem de produção (OP). Gerada de uma receita publicada, com código
-- único por cervejaria e um snapshot congelado (cálculo da receita + perfil do
-- equipamento) em JSONB. Referências a outros módulos são lógicas (UUID).

CREATE TABLE brew_order (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    recipe_id UUID NOT NULL,
    recipe_version INTEGER NOT NULL,
    volume_liters NUMERIC(12, 3) NOT NULL,
    snapshot JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_brew_order_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_brew_order_volume CHECK (volume_liters > 0),
    CONSTRAINT ck_brew_order_status CHECK (status IN
        ('DRAFT', 'RELEASED', 'IN_PRODUCTION', 'FERMENTING', 'CONDITIONING', 'PACKAGED', 'CLOSED', 'CANCELLED'))
);

CREATE INDEX ix_brew_order_brewery ON brew_order (brewery_id, created_at DESC);

-- Sequência atômica do código por cervejaria/ano (código OP-<ano>-<n>).
CREATE TABLE brew_order_sequence (
    brewery_id UUID NOT NULL,
    year INTEGER NOT NULL,
    next_val BIGINT NOT NULL,
    PRIMARY KEY (brewery_id, year)
);

-- Permissões de ordens de produção.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000034', '11111111-0000-0000-0000-000000000009',
     'planning.order.read', 'Consultar ordens de produção', false),
    ('22222222-0000-0000-0000-000000000035', '11111111-0000-0000-0000-000000000009',
     'planning.order.manage', 'Criar/gerir ordens de produção', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('planning.order.read', 'planning.order.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
