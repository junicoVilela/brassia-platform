-- WTR-003: perfis de água de referência (água histórica de cidade/região),
-- educativos e versionados, distintos de fonte/laudo/perfil-alvo. Nunca aplicados
-- automaticamente. Guardam íons, alcalinidade/dureza/pH e proveniência (fonte).
-- Reusa as permissões water.read/water.manage.

CREATE TABLE water_reference_profile (
    id UUID PRIMARY KEY,
    brewery_id UUID REFERENCES brewery (id),
    name VARCHAR(160) NOT NULL,
    region VARCHAR(160),
    edition VARCHAR(40) NOT NULL,
    calcium NUMERIC(8, 2) NOT NULL,
    magnesium NUMERIC(8, 2) NOT NULL,
    sodium NUMERIC(8, 2) NOT NULL,
    sulfate NUMERIC(8, 2) NOT NULL,
    chloride NUMERIC(8, 2) NOT NULL,
    bicarbonate NUMERIC(8, 2) NOT NULL,
    alkalinity NUMERIC(8, 2),
    hardness NUMERIC(8, 2),
    ph NUMERIC(4, 2),
    source_id UUID,
    source_name VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_water_reference_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_water_reference_ions CHECK (
        calcium >= 0 AND magnesium >= 0 AND sodium >= 0
        AND sulfate >= 0 AND chloride >= 0 AND bicarbonate >= 0),
    CONSTRAINT ck_water_reference_ph CHECK (ph IS NULL OR (ph >= 0 AND ph <= 14))
);

-- Nome+edição únicos por escopo (global usa o UUID zero).
CREATE UNIQUE INDEX uq_water_reference_profile ON water_reference_profile (
    COALESCE(brewery_id, '00000000-0000-0000-0000-000000000000'), lower(name), edition);
