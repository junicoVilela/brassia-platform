-- CAT-001: ingredientes tipados do catálogo (maltes, lúpulos, leveduras, sais,
-- adjuntos, embalagens). Atributos específicos por tipo em JSONB, validados no
-- domínio. Multi-tenant por brewery_id; código único por cervejaria.

CREATE TABLE catalog_ingredient (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    type VARCHAR(16) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    use_unit VARCHAR(8) NOT NULL,
    purchase_unit VARCHAR(8) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_catalog_ingredient_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_catalog_ingredient_type
        CHECK (type IN ('MALT', 'HOP', 'YEAST', 'SALT', 'ADJUNCT', 'PACKAGING'))
);

CREATE INDEX ix_catalog_ingredient_brewery_type ON catalog_ingredient (brewery_id, type);

-- Domínio de permissões do catálogo + permissões da história, concedidas ao
-- grupo de sistema Administradores.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000005', NULL, 'catalog', 'Catálogo', 6)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000021', '11111111-0000-0000-0000-000000000005',
     'catalog.ingredient.read', 'Consultar ingredientes', false),
    ('22222222-0000-0000-0000-000000000022', '11111111-0000-0000-0000-000000000005',
     'catalog.ingredient.manage', 'Cadastrar/editar ingredientes', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('catalog.ingredient.read', 'catalog.ingredient.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
