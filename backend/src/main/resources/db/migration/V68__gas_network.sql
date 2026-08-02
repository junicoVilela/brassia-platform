-- GAS-001: cilindros, rede (regulador/manifold), conexões, pressão e consumo.
-- O conteúdo do cilindro é rastreado por MASSA, não por pressão: em cilindro de CO₂ com fase
-- líquida o manômetro fica praticamente constante enquanto houver líquido, então estimar o
-- restante pela pressão daria um número errado com cara de certo.
-- Cilindro com requalificação vencida ou bloqueado não é alocado; a conexão só serve depois de
-- um teste de vazamento aprovado.

CREATE TABLE gas_cylinder (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    gas_type VARCHAR(8) NOT NULL,
    capacity_kg NUMERIC(10, 3) NOT NULL,
    tare_kg NUMERIC(10, 3) NOT NULL,
    content_kg NUMERIC(10, 3) NOT NULL,
    requalification_due_on DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    block_reason VARCHAR(200),
    location VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_gas_cylinder_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_gas_cylinder_gas_type CHECK (gas_type IN ('CO2', 'N2', 'MIX')),
    CONSTRAINT ck_gas_cylinder_status CHECK (status IN ('AVAILABLE', 'CONNECTED', 'EMPTY', 'BLOCKED')),
    CONSTRAINT ck_gas_cylinder_capacity CHECK (capacity_kg > 0 AND tare_kg > 0),
    CONSTRAINT ck_gas_cylinder_content CHECK (content_kg >= 0 AND content_kg <= capacity_kg),
    -- Bloqueio sem motivo esconde por que o cilindro saiu de circulação.
    CONSTRAINT ck_gas_cylinder_block CHECK (status <> 'BLOCKED' OR block_reason IS NOT NULL)
);

CREATE INDEX ix_gas_cylinder_brewery ON gas_cylinder (brewery_id, status, requalification_due_on);

CREATE TABLE gas_network_component (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    kind VARCHAR(10) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    max_pressure_bar NUMERIC(10, 3) NOT NULL,
    set_pressure_bar NUMERIC(10, 3),
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_gas_component_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_gas_component_kind CHECK (kind IN ('REGULATOR', 'MANIFOLD')),
    CONSTRAINT ck_gas_component_max CHECK (max_pressure_bar > 0),
    -- Manifold não tem ajuste; regulador nunca é ajustado acima do próprio limite.
    CONSTRAINT ck_gas_component_set CHECK (
        (kind = 'MANIFOLD' AND set_pressure_bar IS NULL)
        OR (kind = 'REGULATOR' AND set_pressure_bar > 0 AND set_pressure_bar <= max_pressure_bar))
);

CREATE INDEX ix_gas_component_brewery ON gas_network_component (brewery_id, kind, active);

-- Conexão = cilindro → regulador → (manifold) → ponto de uso. O teto de pressão da rede é o menor
-- limite entre os componentes e fica CONGELADO aqui: alterar depois o cadastro do regulador não
-- reescreve o que a linha montada suportava.
CREATE TABLE gas_connection (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    cylinder_id UUID NOT NULL REFERENCES gas_cylinder (id),
    regulator_id UUID NOT NULL REFERENCES gas_network_component (id),
    manifold_id UUID REFERENCES gas_network_component (id),
    point_of_use_equipment_id UUID NOT NULL,
    working_pressure_bar NUMERIC(10, 3) NOT NULL,
    network_max_pressure_bar NUMERIC(10, 3) NOT NULL,
    status VARCHAR(14) NOT NULL,
    connected_at TIMESTAMPTZ NOT NULL,
    connected_by UUID NOT NULL,
    leak_test_passed BOOLEAN,
    leak_test_method VARCHAR(120),
    leak_test_drop_bar NUMERIC(10, 3),
    leak_test_note VARCHAR(200),
    leak_test_by UUID,
    leak_test_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    disconnect_reason VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_gas_connection_status
        CHECK (status IN ('PENDING_TEST', 'SERVING', 'BLOCKED', 'DISCONNECTED')),
    CONSTRAINT ck_gas_connection_pressure CHECK (
        working_pressure_bar > 0 AND network_max_pressure_bar > 0
        AND working_pressure_bar <= network_max_pressure_bar),
    -- Servir sem teste de vazamento aprovado é o que esta história existe para impedir.
    CONSTRAINT ck_gas_connection_leak_test CHECK (status <> 'SERVING' OR leak_test_passed = true),
    CONSTRAINT ck_gas_connection_test_evidence CHECK (
        leak_test_passed IS NULL
        OR (leak_test_method IS NOT NULL AND leak_test_by IS NOT NULL AND leak_test_at IS NOT NULL)),
    -- Teste reprovado sem observação não é rastreável.
    CONSTRAINT ck_gas_connection_test_note CHECK (leak_test_passed IS DISTINCT FROM false OR leak_test_note IS NOT NULL),
    CONSTRAINT ck_gas_connection_disconnect CHECK (
        status <> 'DISCONNECTED' OR (disconnected_at IS NOT NULL AND disconnect_reason IS NOT NULL))
);

CREATE INDEX ix_gas_connection_brewery ON gas_connection (brewery_id, status, connected_at DESC);
CREATE INDEX ix_gas_connection_cylinder ON gas_connection (brewery_id, cylinder_id);
-- Um ponto de uso recebe um cilindro por vez; conexão desconectada libera o ponto.
CREATE UNIQUE INDEX uq_gas_connection_open_point ON gas_connection (brewery_id, point_of_use_equipment_id)
    WHERE status <> 'DISCONNECTED';
-- Um cilindro serve um ponto por vez.
CREATE UNIQUE INDEX uq_gas_connection_open_cylinder ON gas_connection (brewery_id, cylinder_id)
    WHERE status <> 'DISCONNECTED';

-- Medição é evidência: gravada inclusive quando denuncia sobrepressão, e nunca reescrita.
CREATE TABLE gas_pressure_reading (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES gas_connection (id),
    bar NUMERIC(10, 3) NOT NULL,
    temp_c NUMERIC(10, 3),
    over_pressure BOOLEAN NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_gas_pressure_positive CHECK (bar > 0)
);

CREATE INDEX ix_gas_pressure_connection ON gas_pressure_reading (brewery_id, connection_id, recorded_at DESC);

CREATE TABLE gas_consumption (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES gas_connection (id),
    cylinder_id UUID NOT NULL REFERENCES gas_cylinder (id),
    kg NUMERIC(10, 3) NOT NULL,
    reason VARCHAR(200),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_gas_consumption_positive CHECK (kg > 0)
);

CREATE INDEX ix_gas_consumption_connection ON gas_consumption (brewery_id, connection_id, recorded_at DESC);
CREATE INDEX ix_gas_consumption_cylinder ON gas_consumption (brewery_id, cylinder_id, recorded_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000016', NULL, 'gas', 'Gases e rede de CO₂', 18)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000071', '11111111-0000-0000-0000-000000000016',
     'gas.read', 'Consultar cilindros, rede e conexões de gás', false),
    ('22222222-0000-0000-0000-000000000072', '11111111-0000-0000-0000-000000000016',
     'gas.manage', 'Cadastrar e operar cilindros, rede e conexões de gás', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('gas.read', 'gas.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
