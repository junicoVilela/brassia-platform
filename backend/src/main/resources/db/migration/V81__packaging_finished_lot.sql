-- TRC-001-B: lote de produto acabado — a cerveja que saiu da linha, identificada.
--
-- Até aqui a rastreabilidade para a frente terminava na execução do envase: havia um número de
-- unidades dentro do registro de execução, que não é uma coisa que se recolhe. Um recall precisa
-- apontar para um objeto — "as 780 latas do LOTE-100/1" — e é esse objeto que esta tabela cria.
--
-- Fica no envase, não no estoque: `stock_lot` é sobre insumo comprado, com fornecedor obrigatório e
-- referência a item de catálogo. Produto acabado não tem fornecedor e não é ingrediente; forçá-lo
-- ali exigiria um fornecedor falso e um tipo de ingrediente que não é ingrediente.
--
-- NÃO guarda validade. Ela vem da evidência de oxigênio (FSL-001), por plano, e pode ser sobreposta
-- com justificativa — copiá-la para cá criaria uma segunda verdade que divergiria do override em
-- silêncio.
CREATE TABLE packaging_finished_lot (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    -- Um envase, um lote: a unicidade é o que torna a criação idempotente.
    run_id UUID NOT NULL UNIQUE REFERENCES packaging_run (id),
    plan_id UUID NOT NULL REFERENCES packaging_plan (id),
    batch_id UUID NOT NULL,
    -- Código do lote de produção congelado: o rótulo impresso não muda se o lote for renomeado.
    batch_code VARCHAR(40) NOT NULL,
    container_id UUID NOT NULL,
    container_volume_ml NUMERIC(10, 3) NOT NULL,
    -- Só o que ficou bom. Rejeito consumiu embalagem e não virou produto: contá-lo aqui faria o
    -- recall procurar latas que ninguém pode devolver.
    units INTEGER NOT NULL,
    volume_liters NUMERIC(12, 3) NOT NULL,
    packaged_on DATE NOT NULL,
    CONSTRAINT uq_finished_lot_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_finished_lot_units CHECK (units > 0),
    CONSTRAINT ck_finished_lot_volume CHECK (volume_liters > 0)
);

CREATE INDEX ix_finished_lot_batch ON packaging_finished_lot (brewery_id, batch_id);
CREATE INDEX ix_finished_lot_packaged ON packaging_finished_lot (brewery_id, packaged_on DESC);
