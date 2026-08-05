-- TRC-001-D: expedição — para onde o lote de produto acabado foi.
--
-- É a metade de fora da fábrica, aberta desde a TRC-001. O lote existia e tinha identidade desde a
-- TRC-001-B; o que faltava era o destino, e sem destino um recall identifica a origem e não alcança
-- ninguém. É por isso que esta tabela nasce junto com a FDS-003, e não antes: ela existe para
-- responder "a quem avisar".
--
-- FATIA MÍNIMA, DE PROPÓSITO. Não há pedido, nota fiscal, preço nem cliente cadastrado: distribuição
-- comercial é assunto das sprints 19 e 20, e antecipá-la aqui criaria um cadastro de clientes pela
-- porta dos fundos, que aquelas sprints teriam de desmanchar. O que existe é o que o recall precisa:
-- para onde foi, quanto foi, com quem falar.
CREATE TABLE packaging_shipment (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    finished_lot_id UUID NOT NULL REFERENCES packaging_finished_lot (id),
    -- Texto livre, e não FK para cliente: o cliente não existe como agregado, e inventá-lo agora
    -- seria decidir por duas sprints futuras. Quem digita é quem responde pelo destino.
    destination VARCHAR(200) NOT NULL,
    contact VARCHAR(200),
    units INTEGER NOT NULL,
    shipped_on DATE NOT NULL,
    note VARCHAR(500),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_shipment_units CHECK (units > 0)
);

-- O recall varre as expedições de um lote; é a consulta que existe para ser feita.
CREATE INDEX ix_shipment_lot ON packaging_shipment (brewery_id, finished_lot_id);
CREATE INDEX ix_shipment_date ON packaging_shipment (brewery_id, shipped_on DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000097', d.id,
       'packaging.shipment.manage', 'Registrar expedição de lote de produto acabado', false
FROM permission_domain d WHERE d.code = 'packaging'
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'packaging.shipment.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
