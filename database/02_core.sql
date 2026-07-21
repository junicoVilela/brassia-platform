CREATE TABLE IF NOT EXISTS brassia.brewery (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(40) NOT NULL UNIQUE,
  name varchar(160) NOT NULL, timezone varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
