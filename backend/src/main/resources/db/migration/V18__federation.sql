-- SEC-014/015: federação SAML/OIDC — providers e vínculo por provider+subject.

CREATE TABLE federation_provider (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issuer_or_entity_id VARCHAR(500) NOT NULL,
    metadata_uri VARCHAR(1000),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    secret_reference VARCHAR(200),
    jit_mode BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_federation_provider_brewery_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_federation_protocol CHECK (protocol IN ('SAML', 'OIDC')),
    CONSTRAINT ck_federation_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED'))
);

CREATE TABLE external_identity (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES federation_provider (id),
    user_id UUID NOT NULL REFERENCES security_user (id),
    external_subject VARCHAR(500) NOT NULL,
    normalized_email_at_link VARCHAR(320),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_identity_provider_subject UNIQUE (provider_id, external_subject),
    CONSTRAINT uq_external_identity_provider_user UNIQUE (provider_id, user_id)
);

CREATE TABLE federation_certificate (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES federation_provider (id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL,
    certificate_pem TEXT NOT NULL,
    thumbprint_sha256 VARCHAR(64) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT ck_federation_cert_purpose CHECK (purpose IN ('SIGNING', 'ENCRYPTION'))
);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-00000000001c', '11111111-0000-0000-0000-000000000002',
     'security.federation.read', 'Consultar provedores de federação', false),
    ('22222222-0000-0000-0000-00000000001d', '11111111-0000-0000-0000-000000000002',
     'security.federation.manage', 'Administrar provedores de federação', true),
    ('22222222-0000-0000-0000-00000000001e', '11111111-0000-0000-0000-000000000002',
     'security.federation.validate', 'Validar metadata/configuração de federação', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('security.federation.read', 'security.federation.manage', 'security.federation.validate')
ON CONFLICT (group_id, permission_id) DO NOTHING;
