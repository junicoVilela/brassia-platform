-- CRM-001 — o cliente ganha cadastro, e a pessoa ganha prazo.
--
-- O QUE HAVIA. Nada. A expedição (TRC-001-D) grava destino e contato como TEXTO LIVRE, e o comentário
-- do próprio `Shipment` diz por quê: "não há pedido, nota nem cliente cadastrado (…) criar um cadastro
-- de clientes por aqui seria decidir por elas [as sprints 19 e 20]". Esta é a migration que decide.
--
-- A SEPARAÇÃO É O DESENHO, E NÃO NORMALIZAÇÃO POR ESPORTE. Cliente e contato são duas tabelas porque
-- são duas naturezas jurídicas. O cliente é organização: pedido, nota e custo precisam continuar
-- apontando para ele para sempre, e ele não tem direito ao esquecimento. O contato é pessoa: tem prazo
-- de retenção e direito de apagamento. Numa tabela só, o primeiro pedido de exclusão obrigaria a
-- escolher entre apagar a pessoa e destruir o histórico comercial — e não existe resposta boa para
-- essa escolha.

CREATE TABLE crm_customer (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    legal_name VARCHAR(200) NOT NULL,
    trade_name VARCHAR(200),
    -- Texto livre, sem validação de CNPJ ou CPF (DEC-CRM-002). Cliente estrangeiro não tem CNPJ, e
    -- recusar cadastro por formato seria a plataforma decidindo com quem a cervejaria pode vender. Se a
    -- emissão fiscal entrar (INT-008), a validação nasce lá, onde o provedor homologado já a exige.
    tax_id VARCHAR(40),
    -- Não se apaga cliente: desativa-se. Remover deixaria expedição apontando para o nada, e é o
    -- histórico de expedição que um recall percorre para saber a quem avisar.
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_crm_customer_legal_name CHECK (length(btrim(legal_name)) > 0)
);

CREATE INDEX ix_crm_customer_brewery ON crm_customer (brewery_id, active, legal_name);

-- Documento único POR CERVEJARIA, e só quando existe. Índice parcial porque nulo é o estado legítimo de
-- quem ainda não mandou o documento — e um UNIQUE comum trataria dois cadastros sem documento como
-- duplicata, que é justamente o caso mais comum no começo.
CREATE UNIQUE INDEX ux_crm_customer_tax_id ON crm_customer (brewery_id, tax_id)
    WHERE tax_id IS NOT NULL;

CREATE TABLE crm_contact (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    customer_id UUID NOT NULL REFERENCES crm_customer (id),
    -- Nulos depois da anonimização, e é por isso que não são NOT NULL. A obrigatoriedade do nome vive
    -- no domínio, na criação; aqui ela não pode viver, porque apagar a pessoa é operação legítima.
    name VARCHAR(160),
    email VARCHAR(254),
    phone VARCHAR(40),
    role VARCHAR(80),
    -- A casca. A linha sobrevive ao apagamento para que expedição e pedido continuem apontando para
    -- algo — a diferença entre "foi para alguém que pediu para ser esquecido" e um buraco no histórico.
    anonymized_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- Ou a pessoa existe e tem nome, ou foi anonimizada e não tem nada. O estado intermediário
    -- (anonimizada mas com nome) seria um apagamento que não apagou, e é o que este CHECK impede.
    CONSTRAINT ck_crm_contact_anonymized CHECK (
        (anonymized_at IS NULL AND name IS NOT NULL AND length(btrim(name)) > 0)
        OR (anonymized_at IS NOT NULL AND name IS NULL AND email IS NULL AND phone IS NULL
            AND role IS NULL))
);

CREATE INDEX ix_crm_contact_customer ON crm_contact (brewery_id, customer_id);

-- Para a varredura de retenção achar quem venceu sem varrer a tabela inteira.
CREATE INDEX ix_crm_contact_vivos ON crm_contact (brewery_id, created_at)
    WHERE anonymized_at IS NULL;

-- O LIVRO DE CONSENTIMENTO. Só cresce: não há UPDATE nem DELETE previsto, e é isso que o torna
-- auditável. A pergunta que a cervejaria vai precisar responder não é "ela aceita?", é "ela aceitava
-- quando mandamos aquilo?" — e a segunda só tem resposta se as decisões antigas continuarem existindo.
CREATE TABLE crm_consent_entry (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    contact_id UUID NOT NULL REFERENCES crm_contact (id),
    -- Por finalidade, e não por pessoa: aceitar oferta comercial não é aceitar responder pesquisa.
    -- Só finalidade que se apoia em CONSENTIMENTO entra aqui; TRANSACTIONAL se apoia em contrato e
    -- não é consentida nem revogável, senão revogar a oferta derrubaria junto o aviso de entrega.
    purpose VARCHAR(20) NOT NULL,
    decision VARCHAR(10) NOT NULL,
    -- O instante do MUNDO, não o da digitação. Decisão dada por telefone na segunda pode ser
    -- registrada na quarta, depois de outra — e quem manda na ordem é este campo.
    decided_at TIMESTAMPTZ NOT NULL,
    -- Como se sabe que ela decidiu ("formulário do site", "assinatura em contrato"). Obrigatório:
    -- consentimento que não se consegue demonstrar vale o mesmo que nenhum.
    source VARCHAR(200) NOT NULL,
    -- Pode ser diferente de quem decidiu: um vendedor registra o que o cliente disse no telefone, e a
    -- auditoria precisa saber que houve intermediário.
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_crm_consent_purpose CHECK (purpose IN ('MARKETING', 'SURVEY')),
    CONSTRAINT ck_crm_consent_decision CHECK (decision IN ('GRANTED', 'REVOKED')),
    CONSTRAINT ck_crm_consent_source CHECK (length(btrim(source)) > 0)
);

CREATE INDEX ix_crm_consent_contact ON crm_consent_entry (brewery_id, contact_id, purpose, decided_at);

-- A POLÍTICA DE RETENÇÃO, no mesmo espírito das políticas da casa (PRM-001): o número é da cervejaria,
-- não do código. Uma linha por cervejaria, e a AUSÊNCIA da linha significa "nada expira" — que é o
-- padrão seguro. Não anonimizar por falta de decisão é reversível; anonimizar cedo demais não é, e
-- junto com o dado vai embora o contato que talvez fosse preciso numa convocação de recall.
CREATE TABLE crm_retention_policy (
    brewery_id UUID PRIMARY KEY REFERENCES brewery (id),
    days_after_last_interaction INTEGER NOT NULL,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_crm_retention_positive CHECK (days_after_last_interaction > 0)
);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000034', NULL, 'crm', 'Clientes e contatos', 39)
ON CONFLICT (id) DO NOTHING;

-- Quatro permissões, e a divisão não é burocrática. Cadastrar cliente é trabalho de vendas; APAGAR uma
-- pessoa é irreversível e crítica; e definir por quanto tempo a casa guarda dado pessoal é decisão de
-- gestão, não de quem atende o balcão.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000148', '11111111-0000-0000-0000-000000000034',
     'crm.customer.read', 'Consultar clientes e contatos', false),
    ('22222222-0000-0000-0000-000000000149', '11111111-0000-0000-0000-000000000034',
     'crm.customer.manage', 'Cadastrar clientes, contatos e consentimentos', false),
    ('22222222-0000-0000-0000-000000000150', '11111111-0000-0000-0000-000000000034',
     'crm.contact.anonymize', 'Anonimizar contato a pedido do titular', true),
    ('22222222-0000-0000-0000-000000000151', '11111111-0000-0000-0000-000000000034',
     'crm.retention.manage', 'Definir o prazo de retenção de dado pessoal', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('crm.customer.read', 'crm.customer.manage', 'crm.contact.anonymize',
                 'crm.retention.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
