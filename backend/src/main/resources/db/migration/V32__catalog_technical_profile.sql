-- CAT-003: perfis técnicos de referência dos ingredientes (maltes, lúpulos,
-- culturas, adjuntos). O catálogo guarda faixas de referência + proveniência
-- (fonte); valores por safra/lote pertencem ao estoque. Perfil passa por revisão
-- (DRAFT) e só alimenta cálculo quando publicado. Reusa catalog.ingredient.*.

CREATE TABLE ingredient_technical_profile (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    ingredient_id UUID NOT NULL REFERENCES catalog_ingredient (id) ON DELETE CASCADE,
    manufacturer VARCHAR(160),
    origin VARCHAR(160),
    form VARCHAR(60),
    purpose VARCHAR(60),
    laboratory VARCHAR(160),
    lab_code VARCHAR(60),
    descriptors TEXT,
    source_id UUID,
    source_name VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_technical_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT uq_technical_profile_ingredient UNIQUE (brewery_id, ingredient_id)
);

CREATE TABLE ingredient_property_range (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES ingredient_technical_profile (id) ON DELETE CASCADE,
    property VARCHAR(60) NOT NULL,
    min_value NUMERIC(14, 4),
    max_value NUMERIC(14, 4),
    unit VARCHAR(16),
    CONSTRAINT uq_property_range UNIQUE (profile_id, property)
);

CREATE INDEX ix_property_range_profile ON ingredient_property_range (profile_id);
