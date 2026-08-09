-- INT-002: webhooks assinados com retry controlado.
--
-- Duas tabelas: a assinatura (para onde e o quê) e o OUTBOX das entregas. A separação não é normalização
-- por hábito — é o que permite pausar um destino sem perder o que já estava a caminho.

CREATE TABLE webhook_subscription (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    name VARCHAR(120) NOT NULL,
    -- Só https, verificado no domínio. A assinatura HMAC protege a INTEGRIDADE do corpo, não o sigilo:
    -- um webhook em HTTP puro entrega em texto claro o que aconteceu na cervejaria para quem estiver no
    -- caminho.
    endpoint VARCHAR(500) NOT NULL,
    -- O segredo do HMAC. Não sai por leitura: não há endpoint que o devolva, e ele é entregue uma única
    -- vez na criação — mesmo raciocínio de uma API key. Quem o perde cria outra assinatura.
    secret VARCHAR(200) NOT NULL,
    -- Allowlist fechada de tipos, gravada com o NOME EXTERNO. Guardar o nome da classe faria uma
    -- refatoração nossa quebrar o contrato de quem consome.
    events TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_webhook_status CHECK (status IN ('ACTIVE', 'PAUSED', 'REVOKED')),
    CONSTRAINT ck_webhook_events CHECK (length(events) > 0),
    CONSTRAINT ck_webhook_https CHECK (endpoint LIKE 'https://%'),
    CONSTRAINT uq_webhook_name UNIQUE (brewery_id, name)
);

CREATE INDEX ix_webhook_active ON webhook_subscription (brewery_id, status)
    WHERE status = 'ACTIVE';

-- O OUTBOX.
--
-- A linha é gravada NA MESMA TRANSAÇÃO do comando que originou o evento. É isso, e só isso, que faz o
-- critério "falha não bloqueia domínio" ser estrutural em vez de intenção: se a liberação da OP reverter,
-- a entrega reverte junto — e o webhook "ordem liberada" não sai para uma ordem que não existe. Um webhook
-- não se desmanda; a única defesa é ele nunca ter saído.
--
-- O simétrico também vale: gravado o fato, a intenção de entregar está gravada com ele, e nenhum evento se
-- perde porque a aplicação caiu entre o commit e o envio.
CREATE TABLE webhook_delivery (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    subscription_id UUID NOT NULL REFERENCES webhook_subscription (id),
    event_type VARCHAR(60) NOT NULL,
    -- Identidade do FATO. Viaja no cabeçalho X-Brassia-Event-Id para que o destino possa deduplicar o
    -- nosso retry — a garantia deste lado é "ao menos uma vez", e a deduplicação do outro é o que a
    -- transforma em "exatamente uma" na prática.
    event_id VARCHAR(120) NOT NULL,
    -- O corpo é CONGELADO no enfileiramento. Recalculá-lo na hora de reenviar entregaria o estado de agora
    -- sob o nome de um evento de antes — o retry de "ordem liberada" descreveria a ordem como ela está
    -- hoje, possivelmente já cancelada.
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    -- Nulo quando terminal: entregue ou esgotada não têm próxima tentativa.
    next_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_response_status INTEGER,
    -- Truncado em 200 no domínio. Erro de terceiro pode conter qualquer coisa — inclusive eco do que
    -- mandamos —, e a coluna de erro de uma integração não é lugar para dado da cervejaria.
    last_error VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_delivery_status CHECK (status IN ('PENDING', 'DELIVERED', 'EXHAUSTED')),
    CONSTRAINT ck_delivery_attempts CHECK (attempts >= 0),
    -- Pendente tem próxima tentativa; terminal não tem. Sem isso, uma entrega pendente sem agendamento
    -- ficaria invisível para o despachante e nunca mais seria tentada — parada em silêncio.
    CONSTRAINT ck_delivery_schedule CHECK (
        (status = 'PENDING' AND next_attempt_at IS NOT NULL AND delivered_at IS NULL)
        OR (status = 'DELIVERED' AND next_attempt_at IS NULL AND delivered_at IS NOT NULL)
        OR (status = 'EXHAUSTED' AND next_attempt_at IS NULL AND delivered_at IS NULL)),
    -- Um fato, uma entrega por assinatura. É o que impede o mesmo evento de sair duas vezes quando o
    -- comando é repetido ou quando dois nós processam o mesmo evento de domínio.
    CONSTRAINT uq_delivery_event UNIQUE (subscription_id, event_id)
);

-- O índice do despachante: pendentes cuja hora chegou, mais antigas primeiro. Parcial porque a tabela
-- cresce com entregues e o despachante nunca olha para elas.
CREATE INDEX ix_delivery_due ON webhook_delivery (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_delivery_recent ON webhook_delivery (brewery_id, subscription_id, created_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000028', NULL, 'integration', 'Integrações', 33)
ON CONFLICT (id) DO NOTHING;

-- Criar uma assinatura é CRÍTICO e revogar não é, o que parece invertido e não é: criar aponta um fluxo de
-- dados da cervejaria para um endereço de fora, e é o ato que precisa de alçada e de trilha. Revogar
-- interrompe esse fluxo — e uma permissão difícil para "parar de mandar" produz o incentivo errado na hora
-- em que se descobre que o destino foi comprometido.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000124', '11111111-0000-0000-0000-000000000028',
     'integration.webhook.read', 'Consultar webhooks e entregas', false),
    ('22222222-0000-0000-0000-000000000125', '11111111-0000-0000-0000-000000000028',
     'integration.webhook.manage', 'Criar webhook — envia dados da cervejaria para fora', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('integration.webhook.read', 'integration.webhook.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
