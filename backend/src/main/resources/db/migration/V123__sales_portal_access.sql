-- SAL-003 — o cliente entra no portal, vê o que é dele, e compra dentro do teto.
--
-- O PROBLEMA QUE ESTA MIGRATION EXISTE PARA RESOLVER. Até aqui, `SecurityPrincipal` carrega cervejaria e
-- permissões, e todo endpoint de vendas filtra só por cervejaria. Um usuário externo com
-- `sales.order.read` veria os pedidos de TODOS os clientes da cervejaria — e o `TenantIsolationTest`,
-- que varre o SQL exigindo `brewery_id`, não cobre esse segundo eixo porque ele nem sabe que existe.
--
-- A DECISÃO DO MANTENEDOR (2026-08-15): endpoints próprios de portal, em `/api/v1/portal/**`, que sempre
-- filtram pelo cliente do principal e nunca reaproveitam os handlers internos. O isolamento vira
-- ESTRUTURAL: não existe caminho no código do portal que consiga ver outro cliente, e um endpoint
-- interno novo não pode vazar porque o portal não passa por ele.

-- O VÍNCULO. Um usuário do portal é um security_user comum — mesma autenticação, mesma sessão, mesmo
-- histórico de senha — amarrado a UM cliente. Não há tabela de identidade separada de propósito:
-- duplicar autenticação significaria duplicar MFA, bloqueio por tentativa, expiração e recuperação de
-- senha, e a segunda cópia é a que fica para trás quando algo é corrigido.
CREATE TABLE sales_portal_user (
    user_id UUID PRIMARY KEY REFERENCES security_user (id),
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    customer_id UUID NOT NULL REFERENCES crm_customer (id),
    -- O canal em que este cliente compra. É ele que decide os preços que o portal mostra: "preços
    -- próprios" do critério da história é exatamente isto — a lista do canal dele, e não uma lista
    -- paralela por cliente, que seria uma segunda verdade sobre o mesmo produto.
    channel_id UUID NOT NULL REFERENCES sales_channel (id),
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL
);

-- Um usuário, um cliente. A chave primária no usuário já garante, e o índice abaixo serve à pergunta
-- inversa: quem tem acesso ao portal deste cliente?
CREATE INDEX ix_portal_user_customer ON sales_portal_user (brewery_id, customer_id);

-- O TETO DE COMPROMISSO EM ABERTO.
--
-- Fica em `sales`, e não em `crm_customer`, porque é termo comercial e não dado cadastral: o cliente
-- existe sem ele, e colocar dinheiro na tabela do CRM faria o cadastro conhecer moeda para responder
-- uma pergunta de venda.
--
-- ELE MEDE COMPROMISSO, E NÃO RECEBÍVEL — e isto é limitação declarada, não descuido. Um limite de
-- crédito de verdade compara o teto com o que o cliente DEVE, e para isso é preciso baixa de pagamento,
-- que a plataforma não tem (fora do escopo da sprint). O que dá para medir é a soma dos pedidos
-- CONFIRMADOS E NÃO ENTREGUES. A consequência, que ninguém deve descobrir sozinho: um pedido entregue e
-- não pago sai da conta. Ver DEB-SAL-002.
CREATE TABLE sales_customer_credit (
    customer_id UUID PRIMARY KEY REFERENCES crm_customer (id),
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    ceiling_amount NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- Teto zero bloquearia toda compra, o que não é limite de crédito e sim cliente suspenso — e
    -- suspender é outra decisão, que se toma desativando o cliente.
    CONSTRAINT ck_sales_credit_positive CHECK (ceiling_amount > 0),
    CONSTRAINT ck_sales_credit_currency CHECK (currency = upper(currency))
);

CREATE INDEX ix_sales_credit_brewery ON sales_customer_credit (brewery_id);

-- A AUSÊNCIA DA LINHA SIGNIFICA "SEM LIMITE", e é o padrão seguro: não recusar por falta de decisão é
-- reversível; recusar um pedido bom porque alguém chutou um teto não é — o cliente compra de outro.

-- Duas permissões, e a separação é o ponto. `portal.access` é a ÚNICA que um usuário de portal recebe:
-- ela não abre nada interno, e é o que garante que ele não alcance /api/v1/sales/**. Conceder acesso e
-- definir teto são atos de gestão comercial, e ficam com quem já administra vendas.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000158', '11111111-0000-0000-0000-000000000035',
     'portal.access', 'Entrar no portal do cliente', false),
    ('22222222-0000-0000-0000-000000000159', '11111111-0000-0000-0000-000000000035',
     'sales.portal.manage', 'Conceder acesso ao portal e definir limite', true)
ON CONFLICT (id) DO NOTHING;

-- `portal.access` NÃO entra no grupo de administração: quem administra a cervejaria não precisa dela, e
-- concedê-la a todos faria o portal deixar de ser do cliente. Ela é dada usuário a usuário, junto do
-- vínculo.
INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'sales.portal.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
