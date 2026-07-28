-- FER-001: perfil de fermentação versionado. Rascunho editável → publicado (imutável;
-- o histórico não é reescrito). Estágios ordenados com setpoint de temperatura, rampa,
-- pressão e critério de avanço (tempo/densidade/manual) + exige confirmação. Multi-tenant.

CREATE TABLE fermentation_profile (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fermentation_profile_version UNIQUE (brewery_id, code, version),
    CONSTRAINT ck_fermentation_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX ix_fermentation_profile_brewery ON fermentation_profile (brewery_id, code, version);

CREATE TABLE fermentation_profile_stage (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES fermentation_profile (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    stage_order INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    target_temp_c NUMERIC(5, 2) NOT NULL,
    ramp_hours INTEGER,
    pressure_psi NUMERIC(6, 2),
    condition VARCHAR(8) NOT NULL,
    condition_days INTEGER,
    target_gravity NUMERIC(6, 4),
    requires_confirmation BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_fermentation_stage_order UNIQUE (profile_id, stage_order),
    CONSTRAINT ck_fermentation_stage_condition CHECK (condition IN ('TIME', 'GRAVITY', 'MANUAL')),
    CONSTRAINT ck_fermentation_stage_time CHECK (condition <> 'TIME' OR condition_days IS NOT NULL),
    CONSTRAINT ck_fermentation_stage_gravity CHECK (condition <> 'GRAVITY' OR target_gravity IS NOT NULL)
);

CREATE INDEX ix_fermentation_profile_stage ON fermentation_profile_stage (profile_id, stage_order);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000014', NULL, 'fermentation', 'Fermentação e leveduras', 16)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000060', '11111111-0000-0000-0000-000000000014',
     'fermentation.profile.read', 'Consultar perfis de fermentação', false),
    ('22222222-0000-0000-0000-000000000061', '11111111-0000-0000-0000-000000000014',
     'fermentation.profile.manage', 'Cadastrar e publicar perfis de fermentação', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('fermentation.profile.read', 'fermentation.profile.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
