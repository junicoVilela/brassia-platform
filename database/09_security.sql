-- Identidade interna. Nunca persistir senha, token, recovery code ou segredo MFA em texto puro.
CREATE TABLE IF NOT EXISTS brassia.security_user (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email varchar(254) NOT NULL,
  normalized_email varchar(254) NOT NULL UNIQUE, display_name varchar(160) NOT NULL,
  status varchar(24) NOT NULL CHECK (status IN ('INVITED','ACTIVE','LOCKED','DISABLED')),
  email_verified_at timestamptz, locked_until timestamptz, failed_login_count integer NOT NULL DEFAULT 0,
  last_login_at timestamptz, password_changed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS brassia.password_credential (
  user_id uuid PRIMARY KEY REFERENCES brassia.security_user(id), password_hash varchar(512) NOT NULL,
  encoder_id varchar(40) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS brassia.password_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES brassia.security_user(id),
  password_hash varchar(512) NOT NULL, encoder_id varchar(40) NOT NULL,
  replaced_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS brassia.security_group (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid REFERENCES brassia.brewery(id),
  code varchar(80) NOT NULL, name varchar(160) NOT NULL, description varchar(500),
  system_group boolean NOT NULL DEFAULT false, active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  UNIQUE NULLS NOT DISTINCT (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.permission_domain (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), parent_id uuid REFERENCES brassia.permission_domain(id),
  code varchar(80) NOT NULL UNIQUE, name varchar(160) NOT NULL, sort_order integer NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS brassia.security_permission (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), domain_id uuid NOT NULL REFERENCES brassia.permission_domain(id),
  code varchar(120) NOT NULL UNIQUE, name varchar(160) NOT NULL, description varchar(500),
  critical boolean NOT NULL DEFAULT false, active boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS brassia.group_permission (
  group_id uuid NOT NULL REFERENCES brassia.security_group(id),
  permission_id uuid NOT NULL REFERENCES brassia.security_permission(id),
  granted_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (group_id, permission_id)
);
CREATE TABLE IF NOT EXISTS brassia.user_group_membership (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  user_id uuid NOT NULL REFERENCES brassia.security_user(id), group_id uuid NOT NULL REFERENCES brassia.security_group(id),
  valid_from timestamptz NOT NULL DEFAULT now(), valid_until timestamptz,
  granted_by uuid REFERENCES brassia.security_user(id), reason varchar(500), revoked_at timestamptz,
  CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE TABLE IF NOT EXISTS brassia.access_scope (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  scope_type varchar(30) NOT NULL CHECK (scope_type IN ('BREWERY','MODULE','RESOURCE')),
  module_code varchar(80), resource_type varchar(80), resource_id uuid,
  constraints_json jsonb NOT NULL DEFAULT '{}'::jsonb, active boolean NOT NULL DEFAULT true,
  CHECK (scope_type <> 'MODULE' OR module_code IS NOT NULL),
  CHECK (scope_type <> 'RESOURCE' OR (resource_type IS NOT NULL AND resource_id IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS brassia.group_access_scope (
  group_id uuid NOT NULL REFERENCES brassia.security_group(id), scope_id uuid NOT NULL REFERENCES brassia.access_scope(id),
  PRIMARY KEY (group_id, scope_id)
);

CREATE TABLE IF NOT EXISTS brassia.security_policy (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid REFERENCES brassia.brewery(id),
  policy_type varchar(40) NOT NULL, policy_version integer NOT NULL,
  config jsonb NOT NULL, active boolean NOT NULL DEFAULT true,
  effective_at timestamptz NOT NULL DEFAULT now(), created_by uuid REFERENCES brassia.security_user(id),
  UNIQUE NULLS NOT DISTINCT (brewery_id, policy_type, policy_version)
);

CREATE TABLE IF NOT EXISTS brassia.mfa_authenticator (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES brassia.security_user(id),
  authenticator_type varchar(24) NOT NULL CHECK (authenticator_type IN ('WEBAUTHN','TOTP')),
  label varchar(120) NOT NULL, status varchar(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
  credential_id varchar(1024), public_key bytea, signature_counter bigint,
  secret_ciphertext bytea, secret_key_version varchar(40),
  created_at timestamptz NOT NULL DEFAULT now(), verified_at timestamptz, last_used_at timestamptz,
  UNIQUE (credential_id),
  CHECK ((authenticator_type = 'WEBAUTHN' AND credential_id IS NOT NULL AND public_key IS NOT NULL)
      OR (authenticator_type = 'TOTP' AND secret_ciphertext IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS brassia.recovery_code (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES brassia.security_user(id),
  code_hash varchar(512) NOT NULL, generated_at timestamptz NOT NULL DEFAULT now(), used_at timestamptz
);
CREATE TABLE IF NOT EXISTS brassia.account_token (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES brassia.security_user(id),
  token_type varchar(30) NOT NULL CHECK (token_type IN ('INVITATION','EMAIL_VERIFICATION','PASSWORD_RESET')),
  token_hash varchar(512) NOT NULL UNIQUE, expires_at timestamptz NOT NULL,
  used_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), requested_ip_hash varchar(128)
);
CREATE TABLE IF NOT EXISTS brassia.trusted_device (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES brassia.security_user(id),
  device_public_id varchar(160) NOT NULL, label varchar(120), user_agent_hash varchar(128),
  first_seen_at timestamptz NOT NULL DEFAULT now(), last_seen_at timestamptz NOT NULL DEFAULT now(),
  trusted_until timestamptz, revoked_at timestamptz, UNIQUE (user_id, device_public_id)
);

CREATE TABLE IF NOT EXISTS brassia.login_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid REFERENCES brassia.security_user(id),
  attempted_identifier_hash varchar(128) NOT NULL, outcome varchar(24) NOT NULL,
  reason_code varchar(60) NOT NULL, ip_hash varchar(128), user_agent_hash varchar(128),
  device_id uuid REFERENCES brassia.trusted_device(id), trace_id varchar(80),
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS brassia.security_audit_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid REFERENCES brassia.brewery(id),
  actor_type varchar(24) NOT NULL, actor_id uuid, action varchar(120) NOT NULL,
  target_type varchar(80), target_id uuid, outcome varchar(24) NOT NULL,
  reason varchar(500), trace_id varchar(80), ip_hash varchar(128), user_agent_hash varchar(128),
  change_summary jsonb NOT NULL DEFAULT '{}'::jsonb, occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS brassia.temporary_access_grant (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  user_id uuid NOT NULL REFERENCES brassia.security_user(id), permission_id uuid NOT NULL REFERENCES brassia.security_permission(id),
  scope_id uuid REFERENCES brassia.access_scope(id), reason varchar(500) NOT NULL,
  valid_from timestamptz NOT NULL, valid_until timestamptz NOT NULL,
  requested_by uuid NOT NULL REFERENCES brassia.security_user(id), approved_by uuid REFERENCES brassia.security_user(id),
  revoked_at timestamptz, revoked_by uuid REFERENCES brassia.security_user(id),
  CHECK (valid_until > valid_from), CHECK (approved_by IS NULL OR approved_by <> requested_by)
);
CREATE TABLE IF NOT EXISTS brassia.service_account (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  code varchar(80) NOT NULL, name varchar(160) NOT NULL, active boolean NOT NULL DEFAULT true,
  owner_user_id uuid NOT NULL REFERENCES brassia.security_user(id), created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.api_credential (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), service_account_id uuid NOT NULL REFERENCES brassia.service_account(id),
  key_prefix varchar(24) NOT NULL, key_hash varchar(512) NOT NULL UNIQUE,
  scopes jsonb NOT NULL, expires_at timestamptz NOT NULL, last_used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz,
  CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS brassia.security_alert (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid REFERENCES brassia.brewery(id),
  user_id uuid REFERENCES brassia.security_user(id), alert_type varchar(60) NOT NULL,
  severity varchar(16) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  status varchar(20) NOT NULL CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','FALSE_POSITIVE')),
  evidence jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz, resolved_by uuid REFERENCES brassia.security_user(id)
);
CREATE TABLE IF NOT EXISTS brassia.access_review (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  name varchar(160) NOT NULL, status varchar(20) NOT NULL,
  reviewer_id uuid NOT NULL REFERENCES brassia.security_user(id), due_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz
);
CREATE TABLE IF NOT EXISTS brassia.access_review_item (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), review_id uuid NOT NULL REFERENCES brassia.access_review(id),
  user_id uuid NOT NULL REFERENCES brassia.security_user(id), group_id uuid REFERENCES brassia.security_group(id),
  decision varchar(20), justification varchar(500), decided_at timestamptz,
  UNIQUE NULLS NOT DISTINCT (review_id, user_id, group_id)
);

CREATE TABLE IF NOT EXISTS brassia.federation_provider (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid REFERENCES brassia.brewery(id),
  code varchar(80) NOT NULL, display_name varchar(160) NOT NULL,
  protocol varchar(20) NOT NULL CHECK (protocol IN ('SAML2','OIDC','LDAP')),
  status varchar(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','DISABLED','ERROR')),
  discovery_domain varchar(254), metadata_uri varchar(1000), issuer_or_entity_id varchar(1000),
  configuration jsonb NOT NULL DEFAULT '{}'::jsonb, secret_reference varchar(500),
  jit_mode varchar(24) NOT NULL DEFAULT 'INVITED_ONLY'
    CHECK (jit_mode IN ('DISABLED','INVITED_ONLY','VERIFIED_DOMAIN')),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0, UNIQUE NULLS NOT DISTINCT (brewery_id, code)
);
CREATE TABLE IF NOT EXISTS brassia.external_identity (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), provider_id uuid NOT NULL REFERENCES brassia.federation_provider(id),
  user_id uuid NOT NULL REFERENCES brassia.security_user(id), external_subject varchar(1024) NOT NULL,
  normalized_email_at_link varchar(254), linked_at timestamptz NOT NULL DEFAULT now(),
  linked_by uuid REFERENCES brassia.security_user(id), last_login_at timestamptz,
  UNIQUE (provider_id, external_subject), UNIQUE (provider_id, user_id)
);
CREATE TABLE IF NOT EXISTS brassia.federation_certificate (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), provider_id uuid NOT NULL REFERENCES brassia.federation_provider(id),
  purpose varchar(24) NOT NULL CHECK (purpose IN ('SIGNING','VERIFICATION','ENCRYPTION')),
  certificate_pem text NOT NULL, private_key_reference varchar(500), thumbprint_sha256 varchar(128) NOT NULL,
  valid_from timestamptz NOT NULL, valid_until timestamptz NOT NULL, active boolean NOT NULL DEFAULT true,
  CHECK (valid_until > valid_from), UNIQUE (provider_id, purpose, thumbprint_sha256)
);
CREATE TABLE IF NOT EXISTS brassia.provisioning_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), provider_id uuid NOT NULL REFERENCES brassia.federation_provider(id),
  external_id varchar(500), resource_type varchar(30) NOT NULL CHECK (resource_type IN ('USER','GROUP')),
  operation varchar(30) NOT NULL, outcome varchar(24) NOT NULL, idempotency_key varchar(200),
  error_code varchar(80), trace_id varchar(80), occurred_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE NULLS NOT DISTINCT (provider_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_membership_effective ON brassia.user_group_membership
  (brewery_id, user_id, valid_from, valid_until) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_login_event_time ON brassia.login_event (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_event_user_time ON brassia.login_event (user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_audit_tenant_time ON brassia.security_audit_event (brewery_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_temp_access_expiry ON brassia.temporary_access_grant (valid_until) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_account_token_expiry ON brassia.account_token (expires_at) WHERE used_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_api_credential_prefix ON brassia.api_credential (key_prefix) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_security_alert_open ON brassia.security_alert (severity, created_at DESC) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_federation_domain ON brassia.federation_provider (discovery_domain) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_external_identity_user ON brassia.external_identity (user_id);
CREATE INDEX IF NOT EXISTS idx_federation_cert_expiry ON brassia.federation_certificate (valid_until) WHERE active = true;
