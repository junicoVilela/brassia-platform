-- AIA-001: gateway de modelos.
--
-- Duas tabelas com naturezas opostas, de propósito. O ORÇAMENTO é decisão: alguém define quanto a
-- cervejaria aceita gastar por mês, e essa decisão muda com versão e autor. O LEDGER é evidência: cada
-- chamada ao modelo virou dinheiro no instante em que aconteceu, e nada disso se corrige depois.
--
-- É a mesma distinção que a sprint 12 firmou e o custo do lote repetiu: o que é intenção se edita, o
-- que é fato se acumula.

-- O teto de gasto mensal por cervejaria.
--
-- Guarda o LIMITE, nunca o gasto. O gasto do mês é somado do ledger a cada consulta: um contador
-- guardado aqui seria um segundo número sobre o mesmo fato, e dois números sobre o mesmo fato divergem
-- — o incremento perdido numa falha viraria orçamento que protege contra um consumo que ele não vê.
--
-- Linha ausente não é cervejaria sem teto: vale o teto padrão da instalação. Uma cervejaria descoberta
-- sem limite por esquecimento de cadastro é uma cervejaria sem proteção nenhuma.
CREATE TABLE ai_model_budget (
    brewery_id UUID PRIMARY KEY REFERENCES brewery (id),
    monthly_limit NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    -- Optimistic locking: teto de gasto é exatamente o número que duas pessoas ajustam no mesmo dia, e
    -- sobrescrever a decisão de alguém sem que ela saiba é pior do que pedir para tentar de novo.
    version BIGINT NOT NULL DEFAULT 1,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ai_budget_limit CHECK (monthly_limit >= 0),
    CONSTRAINT ck_ai_budget_version CHECK (version > 0)
);

-- Toda chamada ao modelo, inclusive as que falharam.
--
-- Registrar só o sucesso daria um relatório que subestima o gasto: uma resposta recusada por contrato
-- foi gerada e cobrada do mesmo jeito, e é justamente essa combinação — pagou e não serviu — que precisa
-- aparecer numa conta e apontar o prompt que está errado.
--
-- O que NÃO está aqui é tão deliberado quanto o que está: nem prompt, nem resposta, nem trecho de
-- documento. O conteúdo é a parte sensível (POP, laudo, dado de cliente) e esta tabela existe para
-- explicar custo e disponibilidade, não para guardar texto.
CREATE TABLE ai_model_invocation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- A IA não age sozinha: toda chamada tem autor humano, mesmo quando a resposta é automática.
    actor_id UUID NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    -- Seis casas: uma chamada custa frações de centavo, e arredondar cada uma para centavos zeraria
    -- quase todas — o total do mês daria zero e o teto não protegeria nada.
    cost NUMERIC(14, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    latency_millis BIGINT NOT NULL,
    -- Motivo por extenso, sem conteúdo: "orçamento esgotado", "modelo declinou", não o texto recusado.
    failure_reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ai_invocation_status CHECK (status IN
        ('SUCCEEDED', 'PROVIDER_DISABLED', 'PROVIDER_FAILED', 'REJECTED_CONTRACT', 'BUDGET_EXCEEDED')),
    CONSTRAINT ck_ai_invocation_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT ck_ai_invocation_cost CHECK (cost >= 0),
    CONSTRAINT ck_ai_invocation_latency CHECK (latency_millis >= 0),
    -- Sucesso não tem motivo de falha; falha sempre tem. Sem isto uma linha poderia dizer as duas coisas.
    CONSTRAINT ck_ai_invocation_reason CHECK (
        (status = 'SUCCEEDED' AND failure_reason IS NULL)
        OR (status <> 'SUCCEEDED' AND failure_reason IS NOT NULL))
);

-- As duas consultas reais: o gasto do mês (janela de tempo por cervejaria) e as últimas chamadas
-- (ordenadas por tempo, por cervejaria). Um índice serve as duas.
CREATE INDEX ix_ai_invocation_brewery_time ON ai_model_invocation (brewery_id, occurred_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000025', NULL, 'ai', 'Copiloto de IA', 30)
ON CONFLICT (id) DO NOTHING;

-- A verificação e o teto são críticos por motivos diferentes: a primeira gasta dinheiro a cada clique,
-- o segundo é o freio que decide quanto dinheiro pode ser gasto.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000111', '11111111-0000-0000-0000-000000000025',
     'ai.gateway.read', 'Consultar disponibilidade e custo do copiloto', false),
    ('22222222-0000-0000-0000-000000000112', '11111111-0000-0000-0000-000000000025',
     'ai.gateway.probe', 'Verificar a conectividade do copiloto — cada verificação custa', true),
    ('22222222-0000-0000-0000-000000000113', '11111111-0000-0000-0000-000000000025',
     'ai.budget.manage', 'Redefinir o teto de gasto mensal com IA', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('ai.gateway.read', 'ai.gateway.probe', 'ai.budget.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
