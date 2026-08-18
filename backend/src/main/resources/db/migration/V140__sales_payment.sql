-- DEB-SAL-002 — o limite de crédito passa a medir recebível, e não só compromisso.
--
-- O QUE ERA: o comprometido somava pedidos PLACED. Um pedido entregue e não pago saía da conta, e a
-- limitação estava declarada no domínio, na migration, no contrato e num teste que existia só para
-- documentá-la. Faltava a baixa de pagamento.
--
-- O RECEBIMENTO É EVENTO, E NÃO SALDO. Um campo "valor pago" no pedido responderia "quanto falta" e
-- perderia "quem pagou o quê, e quando" — que é a pergunta de qualquer conferência com o financeiro. E um
-- saldo que se sobrescreve não se audita.
CREATE TABLE sales_payment (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    order_id UUID NOT NULL REFERENCES sales_order (id),
    amount NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    received_on DATE NOT NULL,
    -- Sem o meio, a conciliação com o extrato vira adivinhação: "R$ 1.200 no dia 12" existe três vezes
    -- num extrato movimentado.
    method VARCHAR(40) NOT NULL,
    note VARCHAR(500),
    recorded_by UUID NOT NULL REFERENCES security_user (id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ESTORNO É EVENTO COMPENSATÓRIO, E NÃO EDIÇÃO. Um recebimento lançado errado não se apaga: o
    -- registro que se reescreve parece original e diz outra coisa. Mesmo princípio da prova de entrega.
    reverses_payment_id UUID REFERENCES sales_payment (id),
    -- Valor negativo seria estorno disfarçado de recebimento, e a soma bateria sem que ninguém
    -- conseguisse explicar de onde veio. O sinal vem da existência do estorno, e não do número.
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_method CHECK (length(btrim(method)) > 0),
    -- Estorno precisa de motivo: sem ele, quem confere seis meses depois não sabe se foi engano de
    -- digitação, cheque devolvido ou pedido cancelado — e as três levam a conversas diferentes.
    CONSTRAINT ck_payment_reversal_note CHECK (
        reverses_payment_id IS NULL OR length(btrim(coalesce(note, ''))) > 0)
);

-- UM ESTORNO POR RECEBIMENTO. Estornar duas vezes o mesmo lançamento tiraria da conta um dinheiro que só
-- entrou uma vez, e o cliente ganharia limite que não tem.
CREATE UNIQUE INDEX ux_payment_reversal ON sales_payment (reverses_payment_id)
    WHERE reverses_payment_id IS NOT NULL;

-- A consulta do limite: o que cada cliente já pagou, por moeda.
CREATE INDEX ix_payment_order ON sales_payment (brewery_id, order_id, currency);

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000178', d.id,
       'sales.payment.record', 'Registrar recebimento de pedido', false
FROM permission_domain d WHERE d.code = 'sales'
ON CONFLICT (id) DO NOTHING;

-- Crítica: estornar tira dinheiro da conta e devolve limite ao cliente.
INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000179', d.id,
       'sales.payment.reverse', 'Estornar recebimento', true
FROM permission_domain d WHERE d.code = 'sales'
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sales.payment.record', 'sales.payment.reverse')
ON CONFLICT (group_id, permission_id) DO NOTHING;
