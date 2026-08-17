-- CON-003 — o vasilhame que está FORA de casa: prazo, caução, atraso e perda. E a higienização.
--
-- Avaria, manutenção e baixa já vieram na CON-001; o que faltava é o compromisso. A CON-002 sabe que o
-- keg está no cliente — sem prazo, "no cliente há dois dias" e "no cliente há sete meses" são a mesma
-- linha na tela.
CREATE TABLE container_loan (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    container_id UUID NOT NULL REFERENCES container (id),
    customer_id UUID NOT NULL REFERENCES crm_customer (id),
    -- Congelado: renomear o cliente não reescreve o comprovante de caução que ele tem na mão.
    customer_name VARCHAR(160) NOT NULL,
    lent_at TIMESTAMPTZ NOT NULL,
    due_on DATE NOT NULL,
    -- Caução é OPCIONAL: nem toda casa cobra, e obrigar um valor faria alguém digitar 1 real para poder
    -- seguir. Quando existe, tem moeda explícita — um número solto não é dinheiro.
    deposit_amount NUMERIC(12, 2),
    deposit_currency CHAR(3),
    returned_at TIMESTAMPTZ,
    -- PERDA NÃO É BAIXA. A CON-001 recusa dar baixa no que está com o cliente, e a recusa continua de
    -- pé: o vasilhame que não voltou tem outro fato, outro responsável e outro efeito sobre a caução.
    lost_at TIMESTAMPTZ,
    loss_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_loan_due CHECK (due_on >= lent_at::date),
    CONSTRAINT ck_loan_returned CHECK (returned_at IS NULL OR returned_at >= lent_at),
    -- Caução é par: valor sem moeda não é dinheiro, e moeda sem valor não é nada.
    CONSTRAINT ck_loan_deposit CHECK (
        (deposit_amount IS NULL AND deposit_currency IS NULL)
        OR (deposit_amount IS NOT NULL AND deposit_currency IS NOT NULL AND deposit_amount > 0)),
    -- A perda precisa de motivo: "perdido" sozinho não distingue o bar que fechou do keg roubado do
    -- caminhão, e as duas coisas terminam em conversas diferentes com o cliente.
    CONSTRAINT ck_loan_loss CHECK (
        (lost_at IS NULL AND loss_reason IS NULL)
        OR (lost_at IS NOT NULL AND length(btrim(coalesce(loss_reason, ''))) > 0)),
    -- Ou voltou, ou se perdeu — nunca os dois. Encerrar duas vezes reescreveria a data que a cobrança
    -- usou.
    CONSTRAINT ck_loan_single_end CHECK (returned_at IS NULL OR lost_at IS NULL)
);

-- UM EMPRÉSTIMO ABERTO POR VASILHAME. O mesmo keg emprestado a dois clientes ao mesmo tempo é
-- impossível no mundo e contabilizaria duas cauções — e a checagem prévia não sobrevive a duas telas
-- registrando saídas na mesma manhã.
CREATE UNIQUE INDEX ux_loan_open ON container_loan (container_id)
    WHERE returned_at IS NULL AND lost_at IS NULL;

-- A fila do dia: quem está atrasado, do mais antigo para o mais recente.
CREATE INDEX ix_loan_overdue ON container_loan (brewery_id, due_on)
    WHERE returned_at IS NULL AND lost_at IS NULL;

CREATE INDEX ix_loan_customer ON container_loan (brewery_id, customer_id, due_on DESC);

-- A HIGIENIZAÇÃO é o que a CON-001 chamava de "alguém diz que está limpo". Lá o ato era explícito e sem
-- lastro; aqui ele ganha nome, data e método. A pergunta real chega três meses depois: aquele keg foi
-- lavado antes da cerveja que o cliente reclamou?
CREATE TABLE container_sanitation (
    id UUID PRIMARY KEY,
    container_id UUID NOT NULL REFERENCES container (id),
    performed_at TIMESTAMPTZ NOT NULL,
    performed_by UUID NOT NULL REFERENCES security_user (id),
    -- Texto livre de propósito: padronizar isso é assunto de sanitização (CLN), e inventar uma lista
    -- aqui obrigaria o operador a escolher a opção menos errada.
    method VARCHAR(200) NOT NULL,
    note VARCHAR(500),
    CONSTRAINT ck_sanitation_method CHECK (length(btrim(method)) > 0)
);

CREATE INDEX ix_sanitation_container ON container_sanitation (container_id, performed_at DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000173', '11111111-0000-0000-0000-000000000037',
     'container.loan.manage', 'Registrar empréstimo, devolução e higienização', false),
    -- Crítica: declarar perda tira um ativo do inventário E retém dinheiro do cliente. É o tipo de ato
    -- que precisa de nome e trilha.
    ('22222222-0000-0000-0000-000000000174', '11111111-0000-0000-0000-000000000037',
     'container.loan.write_off', 'Declarar perda de vasilhame', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('container.loan.manage', 'container.loan.write_off')
ON CONFLICT (group_id, permission_id) DO NOTHING;
