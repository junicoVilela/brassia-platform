-- SAL-004 — o teto de crédito passa a valer na porta do vendedor, e furá-lo deixa nome e motivo.
--
-- O QUE ERA: o teto só era conferido no portal do cliente. Pela porta interna — a do vendedor — o pedido
-- entrava sem consultar limite nenhum. Um vendedor passava do teto que o portal recusa, e ninguém
-- percebia: o mesmo cliente tinha dois tratamentos dependendo de por onde o pedido entrou.
--
-- POR QUE NÃO É RECUSA DURA. No portal não há vendedor por perto, e a recusa é a única resposta honesta.
-- Na porta interna há uma pessoa que sabe coisas que o sistema não sabe — o pagamento que cai hoje, o
-- acordo de ontem, o cliente de dez anos. Recusar duro faria essa pessoa cadastrar um teto maior "só
-- por hoje" e esquecer de voltar, e aí o limite deixa de existir para sempre em vez de por um pedido.
--
-- POR QUE NÃO É LIVRE. Furar em silêncio faz o teto virar decoração. O que passa acima do limite carrega
-- quem autorizou, quando e por quê — e é isso que permite o dono perguntar depois.
ALTER TABLE sales_order
    ADD COLUMN credit_override_reason VARCHAR(500),
    ADD COLUMN credit_override_by UUID REFERENCES security_user (id),
    ADD COLUMN credit_override_at TIMESTAMPTZ,
    -- Tudo ou nada: uma autorização pela metade — motivo sem autor, autor sem data — é um registro que
    -- não responde a pergunta para a qual ele existe.
    ADD CONSTRAINT ck_sales_order_credit_override CHECK (
        (credit_override_reason IS NULL AND credit_override_by IS NULL
             AND credit_override_at IS NULL)
        OR (length(btrim(coalesce(credit_override_reason, ''))) > 0 AND credit_override_by IS NOT NULL
             AND credit_override_at IS NOT NULL));

-- Crítica: ela deixa passar uma venda acima do que a casa decidiu carregar daquele cliente.
INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000180', d.id,
       'sales.order.credit_override', 'Autorizar pedido acima do limite de crédito', true
FROM permission_domain d WHERE d.code = 'sales'
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p WHERE p.code = 'sales.order.credit_override'
ON CONFLICT (group_id, permission_id) DO NOTHING;
