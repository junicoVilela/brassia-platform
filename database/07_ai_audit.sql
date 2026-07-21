CREATE TABLE IF NOT EXISTS brassia.audit_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid, user_id uuid,
  action varchar(100) NOT NULL, entity_type varchar(80), entity_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(), trace_id varchar(80), metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE IF NOT EXISTS brassia.outbox_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid, aggregate_type varchar(80) NOT NULL,
  aggregate_id uuid NOT NULL, event_type varchar(120) NOT NULL, event_version integer NOT NULL,
  payload jsonb NOT NULL, occurred_at timestamptz NOT NULL, published_at timestamptz,
  attempts integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS brassia.ai_interaction (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  user_id uuid NOT NULL, use_case varchar(80) NOT NULL, model_ref varchar(120) NOT NULL,
  prompt_version varchar(40) NOT NULL, source_refs jsonb NOT NULL, response_schema varchar(80) NOT NULL,
  latency_ms integer, input_tokens integer, output_tokens integer, accepted boolean,
  created_at timestamptz NOT NULL DEFAULT now()
);
