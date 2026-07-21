CREATE TABLE IF NOT EXISTS brassia.brew_order (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  code varchar(60) NOT NULL, recipe_version_id uuid NOT NULL REFERENCES brassia.recipe_version(id),
  status varchar(30) NOT NULL, planned_start timestamptz, snapshot jsonb NOT NULL,
  version bigint NOT NULL DEFAULT 0, UNIQUE (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.batch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  brew_order_id uuid NOT NULL UNIQUE REFERENCES brassia.brew_order(id), code varchar(60) NOT NULL,
  status varchar(30) NOT NULL, started_at timestamptz, closed_at timestamptz,
  version bigint NOT NULL DEFAULT 0, UNIQUE (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.measurement (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  batch_id uuid NOT NULL REFERENCES brassia.batch(id), parameter varchar(60) NOT NULL,
  value numeric(20,8) NOT NULL, unit varchar(24) NOT NULL,
  temperature numeric(12,4), temperature_unit varchar(12), measured_at timestamptz NOT NULL,
  method varchar(80), instrument_id uuid, source varchar(20) NOT NULL, recorded_by uuid,
  recorded_at timestamptz NOT NULL DEFAULT now()
);
