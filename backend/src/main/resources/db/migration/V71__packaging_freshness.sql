-- FSL-001: oxigênio (DO/TPO), purga, vedação e plano de vida útil.
-- Os números que traduzem ppb em dias vêm da CERVEJARIA, não do sistema: TPO é o que mais empurra
-- o envelhecimento, mas a conversão depende do estilo, da temperatura de estocagem e do padrão de
-- frescor da casa. Sem política configurada não há recomendação, e a validade vira decisão humana.

CREATE TABLE packaging_shelf_life_policy (
    brewery_id UUID PRIMARY KEY,
    fallback_days INTEGER NOT NULL,
    CONSTRAINT ck_shelf_life_policy_fallback CHECK (fallback_days >= 1)
);

CREATE TABLE packaging_shelf_life_tier (
    brewery_id UUID NOT NULL REFERENCES packaging_shelf_life_policy (brewery_id) ON DELETE CASCADE,
    max_tpo_ppb NUMERIC(10, 2) NOT NULL,
    shelf_life_days INTEGER NOT NULL,
    CONSTRAINT pk_shelf_life_tier PRIMARY KEY (brewery_id, max_tpo_ppb),
    CONSTRAINT ck_shelf_life_tier_tpo CHECK (max_tpo_ppb > 0),
    CONSTRAINT ck_shelf_life_tier_days CHECK (shelf_life_days >= 1)
);

-- A recomendação e o override convivem: o recomendado NUNCA é sobrescrito. Guardar os dois lado a
-- lado é o que permite, meses depois, saber se a validade impressa veio da evidência ou de uma
-- decisão humana — e, no segundo caso, por quê.
CREATE TABLE packaging_freshness (
    plan_id UUID PRIMARY KEY REFERENCES packaging_plan (id),
    brewery_id UUID NOT NULL,
    packaged_on DATE NOT NULL,
    dissolved_oxygen_ppb NUMERIC(10, 2) NOT NULL,
    total_package_oxygen_ppb NUMERIC(10, 2) NOT NULL,
    purge_method VARCHAR(120) NOT NULL,
    purge_verified BOOLEAN NOT NULL,
    seal_check_method VARCHAR(120) NOT NULL,
    seal_check_passed BOOLEAN NOT NULL,
    recommended_shelf_life_days INTEGER,
    recommended_best_before DATE,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    override_shelf_life_days INTEGER,
    override_best_before DATE,
    override_reason VARCHAR(200),
    overridden_by UUID,
    overridden_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_freshness_oxygen CHECK (dissolved_oxygen_ppb >= 0 AND total_package_oxygen_ppb >= 0),
    -- O oxigênio total inclui o dissolvido; TPO < DO é erro de leitura ou de unidade.
    CONSTRAINT ck_freshness_total_includes_dissolved
        CHECK (total_package_oxygen_ppb >= dissolved_oxygen_ppb),
    CONSTRAINT ck_freshness_recommendation CHECK (
        (recommended_shelf_life_days IS NULL) = (recommended_best_before IS NULL)),
    CONSTRAINT ck_freshness_recommendation_days CHECK (
        recommended_shelf_life_days IS NULL OR recommended_shelf_life_days >= 1),
    -- Override sem motivo e sem responsável não explica a data que a evidência não sustentava.
    CONSTRAINT ck_freshness_override CHECK (
        override_best_before IS NULL
        OR (override_shelf_life_days >= 1 AND override_reason IS NOT NULL
            AND overridden_by IS NOT NULL AND overridden_at IS NOT NULL))
);

CREATE INDEX ix_freshness_brewery ON packaging_freshness (brewery_id, packaged_on DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000073', '11111111-0000-0000-0000-000000000015',
     'packaging.policy.manage', 'Configurar a política de vida útil', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'packaging.policy.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
