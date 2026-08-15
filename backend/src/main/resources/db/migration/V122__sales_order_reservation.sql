-- SAL-002 — pedido, reserva de lote e promessa de entrega.
--
-- O CRITÉRIO TRANSVERSAL DA SPRINT É LITERAL: "concorrência não vende estoque duas vezes" e "pedido e
-- estoque usam idempotência e concorrência". As duas coisas estão aqui, e nenhuma delas é checagem no
-- código — checagem prévia não sobrevive a duas requisições simultâneas, que é exatamente o que duas
-- telas abertas produzem.

CREATE TABLE sales_order (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL REFERENCES crm_customer (id),
    channel_id UUID NOT NULL REFERENCES sales_channel (id),
    -- PLACED, CANCELLED, FULFILLED. Não há rascunho: um pedido que não reservou nada não segura lote,
    -- e chamar isso de pedido faria a cervejaria contar como vendido o que outro cliente ainda pode levar.
    status VARCHAR(12) NOT NULL,
    placed_on DATE NOT NULL,
    -- Nulo é "a combinar", e é estado legítimo. Inventar uma data para o campo não ficar vazio seria
    -- prometer no lugar de quem vende.
    promised_for DATE,
    -- IDEMPOTÊNCIA. A chave vem do cliente e é única por cervejaria. Sem ela, um duplo clique ou um
    -- retry de rede cria dois pedidos que reservam o mesmo estoque duas vezes — e o segundo tira do
    -- próximo comprador uma cerveja que ninguém vai levar.
    idempotency_key VARCHAR(80),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_order_status CHECK (status IN ('PLACED', 'CANCELLED', 'FULFILLED')),
    CONSTRAINT ck_sales_order_promise CHECK (promised_for IS NULL OR promised_for >= placed_on)
);

CREATE UNIQUE INDEX ux_sales_order_code ON sales_order (brewery_id, code);

-- Índice PARCIAL: nulo é o estado de quem não mandou chave, e é legítimo — um pedido digitado à mão na
-- tela não tem por que ter uma. Um UNIQUE comum trataria dois pedidos sem chave como duplicata.
CREATE UNIQUE INDEX ux_sales_order_idempotency ON sales_order (brewery_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE sales_order_line (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    order_id UUID NOT NULL REFERENCES sales_order (id),
    product_id UUID NOT NULL REFERENCES sales_product (id),
    -- SKU congelado, como o batch_code do lote acabado: o pedido antigo continua legível se o produto
    -- for renomeado.
    sku VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    -- PREÇO CONGELADO. A lista de preço muda — é para isso que ela tem vigência —, e se a fatura
    -- relesse a lista, um aumento aplicado depois mudaria o valor de um pedido que o cliente já aceitou.
    unit_amount NUMERIC(14, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    tax_included BOOLEAN NOT NULL,
    CONSTRAINT ck_sales_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sales_line_amount CHECK (unit_amount > 0)
);

CREATE INDEX ix_sales_order_line_order ON sales_order_line (brewery_id, order_id);

-- A RESERVA APONTA PARA O LOTE, e não só para o produto. É o que faz o pedido manter rastreio: quando um
-- recall alcança um lote, "quem comprou disto?" precisa ter resposta antes de a cerveja sair. Reservar
-- "10 unidades de IPA lata" obrigaria a avisar todo mundo que comprou IPA.
CREATE TABLE sales_lot_reservation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    order_line_id UUID NOT NULL REFERENCES sales_order_line (id),
    finished_lot_id UUID NOT NULL REFERENCES packaging_finished_lot (id),
    lot_code VARCHAR(60) NOT NULL,
    units INTEGER NOT NULL,
    -- Validade congelada no momento da reserva: é ela que sustentou a promessa de entrega, e a
    -- checagem precisa do número que valia quando se prometeu.
    best_before DATE NOT NULL,
    CONSTRAINT ck_sales_reservation_units CHECK (units > 0)
);

CREATE INDEX ix_sales_reservation_line ON sales_lot_reservation (brewery_id, order_line_id);
-- O índice que um recall percorre: do lote para quem o reservou.
CREATE INDEX ix_sales_reservation_lot ON sales_lot_reservation (brewery_id, finished_lot_id);

-- O CONTADOR QUE IMPEDE VENDER DUAS VEZES.
--
-- Postgres não expressa "a soma das reservas deste lote cabe no lote" de forma declarativa — não há
-- assertion entre linhas. A saída é uma linha POR LOTE com o total e o reservado, e um CHECK que amarra
-- os dois. A reserva vira:
--
--   UPDATE ... SET reserved_units = reserved_units + :n
--    WHERE finished_lot_id = :id AND reserved_units + :n <= total_units
--
-- Duas requisições simultâneas disputam a MESMA LINHA: a segunda espera o commit da primeira e então
-- relê o valor já atualizado. Se não couber, o UPDATE afeta zero linhas e quem chamou sabe que perdeu a
-- corrida. O CHECK é a rede embaixo, para o caso de alguém escrever aqui por outro caminho.
--
-- Uma linha por lote também é o que torna a leitura de disponibilidade barata: quem monta um pedido
-- precisa saber quanto sobrou, e somar reservas a cada consulta seria o N+1 da REL-002 de novo.
CREATE TABLE sales_lot_availability (
    finished_lot_id UUID PRIMARY KEY REFERENCES packaging_finished_lot (id),
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    total_units INTEGER NOT NULL,
    reserved_units INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_sales_availability_total CHECK (total_units >= 0),
    CONSTRAINT ck_sales_availability_reserved CHECK (reserved_units >= 0
                                                     AND reserved_units <= total_units)
);

CREATE INDEX ix_sales_availability_brewery ON sales_lot_availability (brewery_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000156', '11111111-0000-0000-0000-000000000035',
     'sales.order.read', 'Consultar pedidos', false),
    ('22222222-0000-0000-0000-000000000157', '11111111-0000-0000-0000-000000000035',
     'sales.order.manage', 'Registrar e cancelar pedidos', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sales.order.read', 'sales.order.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
