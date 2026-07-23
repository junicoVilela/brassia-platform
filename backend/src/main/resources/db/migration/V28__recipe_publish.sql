-- REC-004: publicação de versão. Publicada é congelada (imutável); alteração
-- gera nova versão (novo rascunho derivado), preservando o snapshot publicado.

ALTER TABLE recipe
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN previous_recipe_id UUID REFERENCES recipe (id);

-- Versões de uma receita compartilham o nome; a unicidade passa a considerar a
-- versão. Criar uma nova receita com nome já usado continua barrado na aplicação.
ALTER TABLE recipe DROP CONSTRAINT uq_recipe_brewery_name;
ALTER TABLE recipe ADD CONSTRAINT uq_recipe_brewery_name_version
    UNIQUE (brewery_id, normalized_name, version);
