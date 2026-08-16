-- CON-002 — o que está dentro do vasilhame, e por onde ele andou.
--
-- ISTO É EVENTO, E NÃO CAMPO. Um "lote_atual" na tabela do contêiner responderia "o que está dentro
-- agora" e perderia "o que estava dentro em 12 de março" — que é exatamente a pergunta de um recall. Um
-- keg vive anos e passa por dezenas de lotes; a única forma de a genealogia sobreviver a isso é o vínculo
-- nascer histórico.
CREATE TABLE container_fill (
    id UUID PRIMARY KEY,
    container_id UUID NOT NULL REFERENCES container (id),
    -- Sem chave estrangeira para finished_lot: ela mora em packaging, e módulo não lê tabela de módulo.
    -- A integridade vem do caso de uso, que só grava depois de o lote responder por si.
    finished_lot_id UUID NOT NULL,
    -- O código viaja junto, congelado. Ele é o que aparece na tela e no aviso de recall, e buscá-lo em
    -- outro módulo a cada leitura de histórico faria a rastreabilidade depender de uma consulta viva.
    lot_code VARCHAR(60) NOT NULL,
    volume_liters NUMERIC(10, 3) NOT NULL,
    filled_at TIMESTAMPTZ NOT NULL,
    filled_by UUID NOT NULL REFERENCES security_user (id),
    -- Esvaziar FECHA o período; não apaga. É este intervalo que liga o keg entregue ao lote recolhido.
    emptied_at TIMESTAMPTZ,
    CONSTRAINT ck_fill_volume CHECK (volume_liters > 0),
    CONSTRAINT ck_fill_period CHECK (emptied_at IS NULL OR emptied_at >= filled_at)
);

-- UM CONTÊINER TEM NO MÁXIMO UM CONTEÚDO VIVO. Dois lotes no mesmo vasilhame seria mistura sem registro,
-- e o recall não saberia o que recolher. A checagem prévia no código não sobrevive a duas telas enchendo
-- o mesmo keg ao mesmo tempo; o índice parcial sobrevive.
CREATE UNIQUE INDEX ux_fill_current ON container_fill (container_id) WHERE emptied_at IS NULL;

-- A consulta do recall: que vasilhames tiveram este lote, e quando.
CREATE INDEX ix_fill_lot ON container_fill (finished_lot_id, filled_at);
CREATE INDEX ix_fill_container ON container_fill (container_id, filled_at DESC);

-- POR ONDE ELE ANDOU. "Onde está" é a última linha; o histórico é o que responde quantos dias ele ficou
-- parado num cliente — a conta que a CON-003 vai fazer para depósito e atraso.
CREATE TABLE container_location (
    id UUID PRIMARY KEY,
    container_id UUID NOT NULL REFERENCES container (id),
    kind VARCHAR(12) NOT NULL,
    -- Texto livre nesta fatia: o depósito, o nome do bar. O vínculo com o cliente de verdade chega com a
    -- entrega (LOG-002), que é quem sabe a quem entregou.
    place VARCHAR(160),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_location_kind CHECK (kind IN ('WAREHOUSE', 'IN_TRANSIT', 'CUSTOMER', 'THIRD_PARTY'))
);

CREATE INDEX ix_location_container ON container_location (container_id, recorded_at DESC);
