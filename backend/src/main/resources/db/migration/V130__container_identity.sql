-- CON-001 — identidade e ciclo do contêiner retornável.
--
-- A IDENTIDADE É DO CONTÊINER, E NÃO DA ETIQUETA. Por isso o identificador é tabela separada: um keg
-- reetiquetado continua sendo o mesmo keg, com a mesma inspeção e o mesmo histórico. Se o código fosse a
-- chave, trocar um adesivo descolado apagaria cinco anos de vida do vasilhame — e a genealogia que a
-- CON-002 vai pendurar aqui apontaria para o nada.
CREATE TABLE container (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- O código legível da casa ("KEG-0142"), pintado no vasilhame. Não é a etiqueta lida por máquina.
    code VARCHAR(40) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    nominal_capacity_liters NUMERIC(10, 3) NOT NULL,
    ownership VARCHAR(10) NOT NULL,
    condition VARCHAR(10) NOT NULL,
    state VARCHAR(16) NOT NULL,
    -- A inspeção mora aqui porque só a ÚLTIMA governa o enchimento. O histórico completo é outra
    -- história; o que esta precisa responder é "pode encher agora".
    inspected_at TIMESTAMPTZ,
    inspection_valid_until TIMESTAMPTZ,
    inspected_by UUID REFERENCES security_user (id),
    inspection_note VARCHAR(500),
    retired_at TIMESTAMPTZ,
    retirement_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_container_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_container_kind CHECK (kind IN ('KEG', 'CASK', 'GROWLER')),
    CONSTRAINT ck_container_ownership CHECK (ownership IN ('OWN', 'CUSTOMER', 'POOL')),
    CONSTRAINT ck_container_condition CHECK (condition IN ('GOOD', 'DAMAGED', 'CONDEMNED')),
    CONSTRAINT ck_container_state CHECK (state IN ('EMPTY', 'FILLED', 'IN_TRANSIT', 'AT_CUSTOMER',
                                                   'RETURNED', 'IN_MAINTENANCE', 'RETIRED')),
    CONSTRAINT ck_container_capacity CHECK (nominal_capacity_liters > 0),
    -- Ou não foi inspecionado, ou foi com data, validade e responsável. Meia inspeção seria uma
    -- conformidade sem quem a assine — e é ela que libera um vaso de pressão para encher.
    CONSTRAINT ck_container_inspection CHECK (
        (inspected_at IS NULL AND inspection_valid_until IS NULL AND inspected_by IS NULL)
        OR (inspected_at IS NOT NULL AND inspection_valid_until IS NOT NULL
            AND inspected_by IS NOT NULL AND inspection_valid_until > inspected_at)),
    -- A baixa é terminal e tem motivo: um ativo que sai do inventário sem explicação vira pergunta sem
    -- resposta seis meses depois.
    CONSTRAINT ck_container_retirement CHECK (
        (retired_at IS NULL AND retirement_reason IS NULL AND state <> 'RETIRED')
        OR (retired_at IS NOT NULL AND retirement_reason IS NOT NULL AND state = 'RETIRED'))
);

CREATE INDEX ix_container_state ON container (brewery_id, state) WHERE state <> 'RETIRED';

CREATE TABLE container_identifier (
    id UUID PRIMARY KEY,
    container_id UUID NOT NULL REFERENCES container (id),
    value VARCHAR(120) NOT NULL,
    technology VARCHAR(10) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    -- Aposentar não apaga: o registro de que aquele valor um dia apontou para este contêiner é o que
    -- permite entender uma leitura antiga.
    retired_at TIMESTAMPTZ,
    CONSTRAINT ck_identifier_tech CHECK (technology IN ('QR', 'BARCODE', 'RFID'))
);

-- UM CÓDIGO ATIVO APONTA PARA UM CONTÊINER SÓ. É a garantia que o código não consegue dar: duas telas
-- colando a mesma etiqueta em kegs diferentes passariam pelas duas checagens prévias e deixariam a
-- leitura ambígua para sempre. O índice é PARCIAL de propósito — o mesmo valor pode reaparecer na
-- história, desde que nunca em dois vínculos vivos ao mesmo tempo.
CREATE UNIQUE INDEX ux_identifier_active ON container_identifier (value) WHERE retired_at IS NULL;

CREATE INDEX ix_identifier_container ON container_identifier (container_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000037', NULL, 'container', 'Contêineres e distribuição', 42)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000165', '11111111-0000-0000-0000-000000000037',
     'container.read', 'Consultar contêineres', false),
    ('22222222-0000-0000-0000-000000000166', '11111111-0000-0000-0000-000000000037',
     'container.manage', 'Cadastrar, etiquetar e movimentar contêineres', false),
    -- Crítica: liberar um vaso de pressão para uso é atestado técnico, e dar baixa tira um ativo do
    -- inventário. Nenhuma das duas é operação de rotina.
    ('22222222-0000-0000-0000-000000000167', '11111111-0000-0000-0000-000000000037',
     'container.inspect', 'Registrar inspeção e dar baixa em contêiner', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('container.read', 'container.manage', 'container.inspect')
ON CONFLICT (group_id, permission_id) DO NOTHING;
