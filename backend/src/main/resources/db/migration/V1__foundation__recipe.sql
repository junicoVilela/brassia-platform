CREATE TABLE recipe (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_recipe_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT uq_recipe_brewery_name UNIQUE (brewery_id, normalized_name)
);

CREATE INDEX idx_recipe_brewery_status ON recipe (brewery_id, status);
