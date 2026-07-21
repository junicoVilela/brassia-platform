CREATE TABLE IF NOT EXISTS brassia.recipe (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  code varchar(60) NOT NULL, name varchar(180) NOT NULL, status varchar(30) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  UNIQUE (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.recipe_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  recipe_id uuid NOT NULL REFERENCES brassia.recipe(id), version_number integer NOT NULL,
  equipment_snapshot jsonb NOT NULL, formula jsonb NOT NULL, targets jsonb NOT NULL,
  calculation_method_versions jsonb NOT NULL, published_at timestamptz,
  UNIQUE (recipe_id, version_number)
);
