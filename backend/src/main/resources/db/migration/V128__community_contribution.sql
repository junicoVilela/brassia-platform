-- COM-004 — comentário contextual, sugestão de alteração e aceite explícito com histórico.
--
-- A DECISÃO CENTRAL: ACEITAR NÃO ALTERA NADA. Duas razões, e as duas são estruturais:
--
--   1. O retrato publicado é CONGELADO (COM-001). Aplicar uma sugestão nele faria o que o público já
--      leu mudar depois — exatamente o que a decisão de congelar existe para impedir.
--   2. A receita de verdade é PRIVADA. Deixar que texto de alguém de fora a reescreva daria a estranhos
--      uma chave que nem o link de colaboração dá.
--
-- Então aceitar é registrar concordância: fica escrito que o autor achou boa, com data e nome. Aplicar é
-- ato dele, na receita dele, e vira versão nova. A cadeia segue auditável sem ninguém mexer no do outro.

CREATE TABLE community_contribution (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES community_published_recipe (id),
    -- A cervejaria de QUEM ESCREVEU, para a moderação da casa dele e para o isolamento das consultas
    -- internas. Ela nunca sai na resposta pública — quem lê vê o nome, e não de onde a pessoa é.
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    author_user_id UUID NOT NULL REFERENCES security_user (id),
    -- Congelado, como a atribuição do fork: autoria não muda quando a pessoa troca o nome de exibição.
    author_display_name VARCHAR(160) NOT NULL,
    kind VARCHAR(12) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    -- Onde na receita o texto se refere: "Malte Pilsen", "fervura". É o que torna o comentário
    -- CONTEXTUAL em vez de um mural — "o lúpulo está alto" sem dizer qual lúpulo não ajuda ninguém.
    context VARCHAR(120),
    status VARCHAR(10) NOT NULL,
    -- Quem decidiu e quando. Decidir duas vezes reescreveria isto, e é justamente este registro que
    -- torna a conversa um histórico auditável em vez de uma caixa de entrada.
    decided_at TIMESTAMPTZ,
    decided_by UUID,
    decision_note VARCHAR(500),
    -- Moderação (COM-005): esconder NÃO apaga. Moderação precisa poder ser revista, e texto apagado não
    -- se revisa.
    hidden_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_contribution_kind CHECK (kind IN ('COMMENT', 'SUGGESTION')),
    CONSTRAINT ck_contribution_status CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED')),
    -- Comentário não se decide: ele não propôs nada. O CHECK impede o estado que a tela não saberia
    -- desenhar — um elogio "aceito".
    CONSTRAINT ck_contribution_decidable CHECK (
        kind = 'SUGGESTION' OR status = 'OPEN'),
    -- Ou está aberta e sem decisão, ou está decidida com quem e quando. O meio-termo seria uma decisão
    -- sem responsável.
    CONSTRAINT ck_contribution_decision CHECK (
        (status = 'OPEN' AND decided_at IS NULL AND decided_by IS NULL)
        OR (status <> 'OPEN' AND decided_at IS NOT NULL AND decided_by IS NOT NULL))
);

-- A leitura da publicação: o que está visível, do mais recente para o mais antigo.
CREATE INDEX ix_contribution_publication
    ON community_contribution (publication_id, created_at DESC)
    WHERE hidden_at IS NULL;

-- A pergunta do autor: "o que falta eu decidir?".
CREATE INDEX ix_contribution_pending
    ON community_contribution (publication_id)
    WHERE kind = 'SUGGESTION' AND status = 'OPEN' AND hidden_at IS NULL;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000163', '11111111-0000-0000-0000-000000000036',
     'community.contribution.write', 'Comentar e sugerir em receitas publicadas', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'community.contribution.write'
ON CONFLICT (group_id, permission_id) DO NOTHING;

-- DECIDIR é do dono da publicação, e a alçada é `community.recipe.publish` — a mesma de "eu respondo por
-- esta receita lá fora". Quem publica é quem aceita ou recusa o que sugerem sobre ela.
