-- REC-003: metas cervejeiras calculadas e persistidas (método e versão).
-- Uma linha corrente por receita; recalcular substitui.

CREATE TABLE recipe_metrics (
    recipe_id UUID PRIMARY KEY REFERENCES recipe (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    og_points NUMERIC(8, 1) NOT NULL,
    og_sg NUMERIC(6, 4) NOT NULL,
    fg_points NUMERIC(8, 1) NOT NULL,
    fg_sg NUMERIC(6, 4) NOT NULL,
    abv NUMERIC(6, 2) NOT NULL,
    ibu NUMERIC(8, 1) NOT NULL,
    color_ebc NUMERIC(8, 1) NOT NULL,
    attenuation_percent NUMERIC(6, 1) NOT NULL,
    method VARCHAR(40) NOT NULL,
    version INTEGER NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_recipe_metrics_brewery ON recipe_metrics (brewery_id);
