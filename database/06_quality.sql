CREATE TABLE IF NOT EXISTS brassia.sanitation_run (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  equipment_id uuid NOT NULL, procedure_version_id uuid NOT NULL, status varchar(30) NOT NULL,
  started_at timestamptz, completed_at timestamptz, released_at timestamptz,
  released_by uuid, evidence jsonb NOT NULL DEFAULT '{}'::jsonb, version bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS brassia.quality_case (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  code varchar(60) NOT NULL, batch_id uuid, severity varchar(20) NOT NULL, status varchar(24) NOT NULL,
  description text NOT NULL, containment text, root_cause text, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (brewery_id, code)
);
