-- WTR-002: perfil mineral alvo. A mistura (simulação) é calculada e não é
-- persistida; apenas o perfil-alvo é armazenado. Reaproveita permissões water.*.

CREATE TABLE water_profile (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    calcium NUMERIC(8, 2) NOT NULL,
    magnesium NUMERIC(8, 2) NOT NULL,
    sodium NUMERIC(8, 2) NOT NULL,
    sulfate NUMERIC(8, 2) NOT NULL,
    chloride NUMERIC(8, 2) NOT NULL,
    bicarbonate NUMERIC(8, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_water_profile_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_water_profile_ions CHECK (
        calcium >= 0 AND magnesium >= 0 AND sodium >= 0
        AND sulfate >= 0 AND chloride >= 0 AND bicarbonate >= 0)
);
