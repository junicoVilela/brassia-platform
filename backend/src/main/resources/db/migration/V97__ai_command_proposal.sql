-- AIA-003: propor comando.
--
-- Esta é a PRIMEIRA história da sprint que precisa de tabela, e a razão é precisa: a proposta tem de
-- sobreviver entre o instante em que a IA sugere e o instante em que uma pessoa decide. Sem isso não haveria
-- onde registrar quem consentiu, e "a IA fez" seria a única explicação possível para uma alteração de custo
-- ou de qualidade.
--
-- Compare com V95 e V96, que são só de permissão: resposta e avaliação são derivadas das fontes e dos fatos, e
-- guardá-las criaria cópias que envelhecem. Uma decisão humana é o oposto — é um fato do passado, e fato do
-- passado se guarda.
CREATE TABLE ai_command_proposal (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Allowlist fechada, espelhada da enum ProposedAction. O CHECK existe para que uma ação removida do código
    -- não continue aceitável por uma inserção antiga de outro caminho.
    action VARCHAR(40) NOT NULL,
    -- Parâmetros do comando proposto. JSONB porque o conjunto de chaves varia por ação, e cada ação declara as
    -- suas — o domínio recusa chave faltando e chave inesperada.
    parameters JSONB NOT NULL,
    -- Obrigatória: uma proposta sem o motivo não dá a quem decide o que ele precisa para decidir, e
    -- "a IA sugeriu" não é motivo.
    rationale VARCHAR(1000) NOT NULL,
    -- Quem PEDIU a proposta. A IA não propõe sozinha.
    proposed_by UUID NOT NULL,
    proposed_at TIMESTAMPTZ NOT NULL,
    -- Proposta vence. Ela foi feita sobre os fatos de um instante — custo incompleto, medição fora da faixa,
    -- tanque sujo — e aceitá-la dias depois é agir sobre um retrato antigo, convincente justamente porque
    -- parece atual.
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(12) NOT NULL,
    -- Quem CONFIRMOU, que não é necessariamente quem pediu — e é a coluna que a história existe para produzir.
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    CONSTRAINT ck_proposal_action CHECK (action IN
        ('CLOSE_BATCH_COST', 'OPEN_NON_CONFORMITY', 'SCHEDULE_CLEANING_CYCLE')),
    CONSTRAINT ck_proposal_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_proposal_validity CHECK (expires_at > proposed_at),
    -- Pendente não tem decisão; decidida sempre tem autor e instante. Sem isto uma linha poderia dizer que
    -- ninguém decidiu e ao mesmo tempo quem decidiu.
    CONSTRAINT ck_proposal_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

-- A consulta real: as propostas da cervejaria, das mais recentes para as mais antigas.
CREATE INDEX ix_proposal_brewery_time ON ai_command_proposal (brewery_id, proposed_at DESC);

-- Propor é alçada de IA; CONFIRMAR é alçada do comando proposto, exigida no aceite e não cadastrada aqui —
-- ela já existe, é a permissão do módulo dono da ação (`costing.cost.close`, `quality.nc.manage`,
-- `sanitation.cycle.execute`). É essa separação que impede "propor" de ser um caminho lateral para fazer pela
-- IA o que a pessoa não pode fazer pela porta da frente.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000118', '11111111-0000-0000-0000-000000000025',
     'ai.command.propose', 'Pedir ao copiloto propostas de comando — confirmar exige a alçada do comando', true),
    ('22222222-0000-0000-0000-000000000119', '11111111-0000-0000-0000-000000000025',
     'ai.command.read', 'Consultar propostas do copiloto e as decisões tomadas', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('ai.command.propose', 'ai.command.read')
ON CONFLICT (group_id, permission_id) DO NOTHING;
