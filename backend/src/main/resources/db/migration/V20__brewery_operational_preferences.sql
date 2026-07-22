-- BRW-002: preferências operacionais da cervejaria ativa.
-- Linha atual mutável + revisão append-only: mudança futura não reescreve revisões.

CREATE TABLE brewery_operational_preferences (
    brewery_id UUID PRIMARY KEY REFERENCES brewery (id),
    volume_unit VARCHAR(8) NOT NULL,
    mass_unit VARCHAR(8) NOT NULL,
    temperature_unit VARCHAR(8) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    max_batch_volume NUMERIC(20, 6) NOT NULL,
    allow_negative_stock BOOLEAN NOT NULL DEFAULT false,
    stock_policy VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pref_volume_unit CHECK (volume_unit IN ('L', 'ML')),
    CONSTRAINT ck_pref_mass_unit CHECK (mass_unit IN ('KG', 'G')),
    CONSTRAINT ck_pref_temperature_unit CHECK (temperature_unit IN ('C', 'F')),
    CONSTRAINT ck_pref_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_pref_max_batch CHECK (max_batch_volume > 0),
    CONSTRAINT ck_pref_stock_policy CHECK (stock_policy IN ('FEFO', 'FIFO', 'NONE'))
);

CREATE TABLE brewery_operational_preferences_revision (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    version BIGINT NOT NULL,
    volume_unit VARCHAR(8) NOT NULL,
    mass_unit VARCHAR(8) NOT NULL,
    temperature_unit VARCHAR(8) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    max_batch_volume NUMERIC(20, 6) NOT NULL,
    allow_negative_stock BOOLEAN NOT NULL,
    stock_policy VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID,
    PRIMARY KEY (brewery_id, version)
);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-00000000001f', '11111111-0000-0000-0000-000000000004',
     'brewery.preferences.read', 'Consultar preferências operacionais', false),
    ('22222222-0000-0000-0000-000000000020', '11111111-0000-0000-0000-000000000004',
     'brewery.preferences.manage', 'Alterar preferências operacionais', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('brewery.preferences.read', 'brewery.preferences.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
