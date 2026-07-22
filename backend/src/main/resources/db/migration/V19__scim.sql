-- SEC-016: SCIM — eventos de provisionamento e allowlist de grupos externos.

CREATE TABLE provisioning_event (
    id UUID PRIMARY KEY,
    provider_id UUID REFERENCES federation_provider (id),
    external_id VARCHAR(500) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(200),
    error_code VARCHAR(80),
    trace_id VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_provisioning_resource CHECK (resource_type IN ('USER', 'GROUP')),
    CONSTRAINT ck_provisioning_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'NOOP'))
);

CREATE UNIQUE INDEX uq_provisioning_idempotency
    ON provisioning_event (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE scim_group_mapping (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES federation_provider (id),
    external_group_id VARCHAR(500) NOT NULL,
    security_group_id UUID NOT NULL REFERENCES security_group (id),
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_scim_group_mapping UNIQUE (provider_id, external_group_id)
);
