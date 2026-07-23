-- REC-001: expande a receita para composição, equipamento, metas e processo.

ALTER TABLE recipe
    ADD COLUMN equipment_id UUID,
    ADD COLUMN batch_volume_liters NUMERIC(12, 3),
    ADD COLUMN target_og_points NUMERIC(8, 2),
    ADD COLUMN target_ibu NUMERIC(8, 2),
    ADD COLUMN target_color_ebc NUMERIC(8, 2),
    ADD COLUMN target_abv NUMERIC(6, 3),
    ADD COLUMN boil_time_minutes INTEGER;

CREATE TABLE recipe_item (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    stage VARCHAR(16) NOT NULL,
    quantity NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(8) NOT NULL,
    timing_minutes INTEGER,
    percentage NUMERIC(6, 3),
    position INTEGER NOT NULL,
    CONSTRAINT ck_recipe_item_stage
        CHECK (stage IN ('MASH', 'BOIL', 'WHIRLPOOL', 'FERMENTATION', 'PACKAGING')),
    CONSTRAINT ck_recipe_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_recipe_item_percentage CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100))
);

CREATE INDEX ix_recipe_item_recipe ON recipe_item (recipe_id, position);

-- Permissão de leitura de receitas (a de criação já existe na V5).
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000028', '11111111-0000-0000-0000-000000000001',
     'recipe.read', 'Consultar receitas', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'recipe.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
