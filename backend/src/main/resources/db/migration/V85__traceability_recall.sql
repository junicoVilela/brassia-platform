-- FDS-003: recall — identificar origem, destinos, contatos e ações.
--
-- Duas tabelas, e a divisão entre elas é a decisão da história.
--
-- O ESCOPO NÃO É GUARDADO. Ele é derivado do grafo (TRC-001) a cada leitura, como o da quarentena:
-- um envase feito depois da abertura pertence ao recall, e uma lista congelada não o conheceria.
-- "Escopo reproduzível", que o critério pede, é isto: a mesma origem, a mesma profundidade e o
-- mesmo grafo respondem a mesma coisa — e o que mudou desde a abertura aparece declarado.
--
-- A COMUNICAÇÃO É GUARDADA, e tem de ser. Notificar um destino é um FATO sobre o que a cervejaria
-- fez, não uma consequência do grafo: derivar a lista de avisados apagaria a prova de que eles
-- foram avisados. Por isso cada destino alcançado na abertura vira uma linha própria, que nasce
-- pendente e é fechada quando alguém de fato falou com o cliente.
CREATE TABLE traceability_recall (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(30) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    node_id UUID NOT NULL,
    origin_label VARCHAR(200),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    opened_by UUID NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_by UUID,
    closed_at TIMESTAMPTZ,
    closing_summary VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_recall_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_recall_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_recall_close CHECK (
        (status = 'OPEN' AND closed_by IS NULL AND closed_at IS NULL AND closing_summary IS NULL)
        OR (status = 'CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL
            AND closing_summary IS NOT NULL))
);

CREATE INDEX ix_recall_open ON traceability_recall (brewery_id, status, opened_at DESC);

-- Um destino alcançado, e o que se fez a respeito.
--
-- Os dados do destino são COPIADOS da expedição, e é de propósito: o dossiê tem de continuar
-- dizendo para quem se ligou e em que número, mesmo que o cadastro mude depois. Aqui a cópia é a
-- coisa certa justamente porque o registro é sobre o passado — o oposto do escopo, que é sobre o
-- presente.
CREATE TABLE traceability_recall_notification (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    recall_id UUID NOT NULL REFERENCES traceability_recall (id) ON DELETE CASCADE,
    shipment_id UUID NOT NULL,
    finished_lot_code VARCHAR(60) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    contact VARCHAR(200),
    units INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    channel VARCHAR(40),
    note VARCHAR(500),
    notified_by UUID,
    notified_at TIMESTAMPTZ,
    CONSTRAINT uq_recall_notification UNIQUE (recall_id, shipment_id),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'NOTIFIED')),
    CONSTRAINT ck_notification_done CHECK (
        (status = 'PENDING' AND notified_by IS NULL AND notified_at IS NULL AND channel IS NULL)
        OR (status = 'NOTIFIED' AND notified_by IS NOT NULL AND notified_at IS NOT NULL
            AND channel IS NOT NULL))
);

CREATE INDEX ix_recall_notification ON traceability_recall_notification (brewery_id, recall_id, status);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000098', '11111111-0000-0000-0000-000000000020',
     'traceability.recall.read', 'Consultar recalls e o dossiê de cada um', false),
    ('22222222-0000-0000-0000-000000000099', '11111111-0000-0000-0000-000000000020',
     'traceability.recall.manage', 'Abrir recall, registrar comunicação e encerrar', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('traceability.recall.read', 'traceability.recall.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
