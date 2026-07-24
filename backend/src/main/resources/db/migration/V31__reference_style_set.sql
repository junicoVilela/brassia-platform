-- STD-001: conjuntos versionados de estilos cervejeiros. Cada conjunto pertence a
-- uma autoridade/edição e a uma fonte (licença/permissão). Reusa as permissões
-- reference.read/manage/publish. O perfil detalhado só é gravado quando a permissão
-- da fonte é integral (gate aplicado no domínio).

CREATE TABLE style_set (
    id UUID PRIMARY KEY,
    brewery_id UUID REFERENCES brewery (id),
    source_id UUID NOT NULL REFERENCES reference_source (id),
    authority VARCHAR(30) NOT NULL,
    edition VARCHAR(40) NOT NULL,
    language VARCHAR(16) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    attribution VARCHAR(300),
    permission_status VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_style_set_authority CHECK (authority IN (
        'BJCP_BEER', 'BJCP_MEAD', 'BJCP_CIDER', 'BREWERS_ASSOCIATION', 'INTERNAL')),
    CONSTRAINT ck_style_set_permission CHECK (permission_status IN (
        'UNKNOWN', 'PENDING', 'LIMITED_PERMISSION', 'GRANTED', 'DENIED')),
    CONSTRAINT ck_style_set_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_style_set_effective CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Um conjunto por escopo/autoridade/edição/idioma (global usa o UUID zero).
CREATE UNIQUE INDEX uq_style_set_coordinates ON style_set (
    COALESCE(brewery_id, '00000000-0000-0000-0000-000000000000'), authority, edition, language);

CREATE TABLE style (
    id UUID PRIMARY KEY,
    style_set_id UUID NOT NULL REFERENCES style_set (id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    family VARCHAR(80),
    category VARCHAR(80),
    og_min NUMERIC(12, 4), og_max NUMERIC(12, 4), og_unit VARCHAR(16),
    fg_min NUMERIC(12, 4), fg_max NUMERIC(12, 4), fg_unit VARCHAR(16),
    abv_min NUMERIC(12, 4), abv_max NUMERIC(12, 4), abv_unit VARCHAR(16),
    ibu_min NUMERIC(12, 4), ibu_max NUMERIC(12, 4), ibu_unit VARCHAR(16),
    color_min NUMERIC(12, 4), color_max NUMERIC(12, 4), color_unit VARCHAR(16),
    general_impression VARCHAR(1000),
    detailed_profile TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_style_code UNIQUE (style_set_id, code)
);

CREATE INDEX ix_style_set ON style (style_set_id);
