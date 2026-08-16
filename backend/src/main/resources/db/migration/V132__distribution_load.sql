-- LOG-001 — carga, roteiro e responsável.
--
-- PLANEJAR E LIBERAR SÃO ATOS DE PESSOAS DIFERENTES. A conferência existe para encontrar o erro de quem
-- montou, e quem montou relê o próprio trabalho enxergando o que quis colocar. Os dois campos separados
-- não são burocracia: são o que torna a separação verificável depois.
CREATE TABLE distribution_load (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    scheduled_for DATE NOT NULL,
    capacity_liters NUMERIC(10, 3) NOT NULL,
    planned_by UUID NOT NULL REFERENCES security_user (id),
    driver_id UUID REFERENCES security_user (id),
    vehicle VARCHAR(60),
    status VARCHAR(12) NOT NULL,
    released_by UUID REFERENCES security_user (id),
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_load_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_load_status CHECK (status IN ('PLANNED', 'RELEASED', 'IN_ROUTE', 'CLOSED',
                                                'CANCELLED')),
    CONSTRAINT ck_load_capacity CHECK (capacity_liters > 0),
    -- QUEM MONTOU NÃO LIBERA, e a garantia é estrutural. A checagem no domínio dá a mensagem boa; esta
    -- linha é o que impede a regra de ser contornada por qualquer caminho futuro — importação, correção
    -- manual, um endpoint novo que esqueça de chamar o agregado.
    CONSTRAINT ck_load_segregation CHECK (released_by IS NULL OR released_by <> planned_by),
    -- Ou não foi liberada, ou foi com quem e quando.
    CONSTRAINT ck_load_release CHECK (
        (released_by IS NULL AND released_at IS NULL)
        OR (released_by IS NOT NULL AND released_at IS NOT NULL))
);

CREATE INDEX ix_load_day ON distribution_load (brewery_id, scheduled_for DESC);

CREATE TABLE distribution_load_stop (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL REFERENCES distribution_load (id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES crm_customer (id),
    -- Nome congelado: a parada foi combinada com aquele nome, e renomear o cliente não reescreve o
    -- romaneio que já saiu impresso.
    customer_name VARCHAR(160) NOT NULL,
    sequence INTEGER NOT NULL,
    -- A janela é COMPROMISSO, e não previsão: é o que o bar ouviu para decidir quem fica na porta.
    -- Nula é estado legítimo — obrigar uma faria alguém inventar "8h às 18h".
    window_from TIMESTAMPTZ,
    window_to TIMESTAMPTZ,
    CONSTRAINT ck_stop_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_stop_window CHECK (
        (window_from IS NULL AND window_to IS NULL)
        OR (window_from IS NOT NULL AND window_to IS NOT NULL AND window_to > window_from))
);

-- DUAS PARADAS NA MESMA POSIÇÃO é ambiguidade que o motorista resolve inventando — e a rota que ele
-- inventar não é a que a janela combinada pressupõe.
CREATE UNIQUE INDEX ux_stop_sequence ON distribution_load_stop (load_id, sequence);

CREATE TABLE distribution_load_item (
    id UUID PRIMARY KEY,
    stop_id UUID NOT NULL REFERENCES distribution_load_stop (id) ON DELETE CASCADE,
    -- Denormalizado de propósito: é o que permite a garantia abaixo existir sem uma junção que índice
    -- nenhum consegue fazer.
    load_id UUID NOT NULL REFERENCES distribution_load (id) ON DELETE CASCADE,
    -- Sem chave estrangeira para container: ela mora em outro módulo. A integridade vem do caso de uso,
    -- que só grava depois de o vasilhame responder por si.
    container_id UUID NOT NULL,
    volume_liters NUMERIC(10, 3) NOT NULL,
    CONSTRAINT ck_item_volume CHECK (volume_liters > 0)
);

CREATE INDEX ix_item_stop ON distribution_load_item (stop_id);

-- O MESMO VASILHAME NÃO VAI EM DUAS PARADAS DA MESMA CARGA. Seria entrega prometida duas vezes, e uma
-- delas vai faltar. A checagem prévia não sobrevive a duas telas montando a mesma rota ao mesmo tempo,
-- que é exatamente o que acontece na véspera.
--
-- O QUE ESTE ÍNDICE NÃO GARANTE, e está registrado como DEB-LOG-001: o mesmo keg em DUAS CARGAS abertas.
-- Essa condição depende do estado da carga, que mora na outra tabela, e índice não faz junção. Hoje ela é
-- checada no caso de uso — o que basta para o engano do dia a dia e não basta para duas telas simultâneas.
-- A saída conhecida é o vasilhame ficar IN_TRANSIT ao ser carregado, e aí o próprio ciclo do contêiner o
-- torna indisponível; isso chega com a LOG-002, que é quem move o estado na saída.
CREATE UNIQUE INDEX ux_item_container_per_load ON distribution_load_item (load_id, container_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000038', NULL, 'distribution', 'Distribuição', 43)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000168', '11111111-0000-0000-0000-000000000038',
     'distribution.load.read', 'Consultar cargas e roteiros', false),
    ('22222222-0000-0000-0000-000000000169', '11111111-0000-0000-0000-000000000038',
     'distribution.load.plan', 'Montar carga e roteiro', false),
    -- Alçada SEPARADA da de montar, e é ela que faz a separação de deveres existir de verdade: dar as
    -- duas à mesma pessoa devolve o problema, com mais passos.
    ('22222222-0000-0000-0000-000000000170', '11111111-0000-0000-0000-000000000038',
     'distribution.load.release', 'Conferir e liberar carga para sair', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('distribution.load.read', 'distribution.load.plan', 'distribution.load.release')
ON CONFLICT (group_id, permission_id) DO NOTHING;
