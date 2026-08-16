-- COM-003 — o fork cria cópia independente, e a linhagem é atribuição congelada.
--
-- O CRITÉRIO DA HISTÓRIA É LITERAL: "sem acesso futuro ao conteúdo privado do autor". Por isso NADA aqui
-- é ponteiro vivo para o conteúdo: nome do autor, título e licença são gravados como estavam no momento
-- do fork. Se o autor renomear a publicação, fechar a visibilidade ou despublicar, a atribuição continua
-- correta e o forkador NÃO ganha nada novo.
--
-- O identificador da publicação fica guardado para a tela poder oferecer o link de volta — e não para dar
-- acesso: abrir aquela publicação continua passando pela matriz de visibilidade. Se o autor fechou, o
-- forkador vê a atribuição e não vê o conteúdo, que é o comportamento certo.

CREATE TABLE community_recipe_fork (
    id UUID PRIMARY KEY,
    -- A cervejaria de QUEM COPIOU. A do autor não entra: ela é o inquilino dele, e o retrato público
    -- nunca a carregou.
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- A receita nova, que é dele e evolui sozinha.
    recipe_id UUID NOT NULL REFERENCES recipe (id),
    source_publication_id UUID NOT NULL REFERENCES community_published_recipe (id),
    -- CONGELADOS. Atribuição não muda quando a pessoa troca o nome de exibição, e continua legível
    -- mesmo se a publicação sair de circulação.
    source_author_name VARCHAR(160) NOT NULL,
    source_title VARCHAR(160) NOT NULL,
    -- A licença de origem é a obrigação que sobrevive à cópia: CC BY-SA exige que o derivado continue
    -- aberto, e sem registrar isso ninguém saberia seis meses depois.
    source_license VARCHAR(24) NOT NULL,
    source_recipe_version BIGINT NOT NULL,
    forked_by UUID NOT NULL,
    forked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fork_license CHECK (source_license IN ('CC0', 'CC_BY', 'CC_BY_SA', 'CC_BY_NC',
                                                         'ALL_RIGHTS_RESERVED'))
);

-- Uma receita tem no máximo uma origem: ela foi copiada de um lugar, ou é original.
CREATE UNIQUE INDEX ux_fork_recipe ON community_recipe_fork (recipe_id);

-- Quem forkou o quê — a pergunta do autor: "quantos copiaram a minha?".
CREATE INDEX ix_fork_source ON community_recipe_fork (source_publication_id, forked_at DESC);

CREATE INDEX ix_fork_brewery ON community_recipe_fork (brewery_id, forked_at DESC);

-- Forkar é criar receita na própria casa: a alçada é a de criar receita, e não uma nova. Fatiar em duas
-- faria a tela pedir dois papéis para uma decisão só — e quem pode criar uma receita do zero pode criar
-- uma inspirada na de outro.
