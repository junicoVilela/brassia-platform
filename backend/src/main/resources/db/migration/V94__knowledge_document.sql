-- RAG-001: base de conhecimento indexada.
--
-- BUSCA TEXTUAL DO POSTGRESQL, NÃO VETORIAL (ver docs/adr/0015-knowledge-retrieval.md).
-- `docs/01_ARCHITECTURE.md` põe busca vetorial como opcional e sujeita a ADR. `tsvector` com o
-- dicionário português resolve o problema desta sprint sem infraestrutura nova: as perguntas aqui são
-- sobre termos técnicos concretos — "peracético", "torque", "alcalinidade" — que aparecem literalmente
-- nos documentos. Embedding paga por sinônimo e paráfrase, que é problema de outro dia.

-- Configuração de busca em português SEM ACENTO.
--
-- Não é refinamento: em português, quem digita apressado escreve "peracetico", e o dicionário
-- `portuguese` puro não acha "peracético" a partir disso — a busca simplesmente devolve nada, e quem
-- pergunta conclui que o documento não existe. `unaccent` normaliza os dois lados, texto e pergunta, e
-- é extensão de confiança no PostgreSQL 13+, instalável pelo dono do schema sem superusuário.
--
-- A configuração precisa existir antes da coluna gerada que a usa: `to_tsvector` com nome de
-- configuração literal é IMMUTABLE, que é o requisito de uma coluna gerada.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TEXT SEARCH CONFIGURATION portuguese_unaccent (COPY = portuguese);
ALTER TEXT SEARCH CONFIGURATION portuguese_unaccent
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, portuguese_stem;

-- O documento e a vigência dele.
--
-- Documento indexado é IMUTÁVEL: corrigir o texto apagaria a base de uma resposta já dada — alguém leu
-- "0,15%" citando este documento. Versão nova é linha nova. A única coluna que muda depois é
-- `effective_to`, porque "até quando valeu" só se sabe quando algo substitui.
CREATE TABLE knowledge_document (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    type VARCHAR(30) NOT NULL,
    -- Código estável entre versões: é o que liga a versão nova à antiga.
    code VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    version INTEGER NOT NULL,
    -- Vigência em data de calendário, não instante: um laudo vale "a partir de 1º de abril", não
    -- "a partir de 1º de abril às 03:00 UTC". Fim nulo é "ainda vigente", não "desconhecido".
    effective_from DATE NOT NULL,
    effective_to DATE,
    -- A permissão exigida é ATRIBUTO DO DOCUMENTO, não verificação que a borda faz e a busca confia.
    -- É o que faz o filtro valer quando a recuperação for chamada de um job ou de um evento, onde não
    -- existe borda HTTP nenhuma para verificar nada.
    required_permission VARCHAR(80) NOT NULL,
    -- Manual de bomba não responde sobre a caldeira. Nulo = não se refere a equipamento específico.
    equipment_id UUID,
    source_uri VARCHAR(500),
    checksum CHAR(64) NOT NULL,
    indexed_by UUID NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_knowledge_type CHECK (type IN
        ('EQUIPMENT_MANUAL', 'SAFETY_DATA_SHEET', 'LAB_REPORT', 'OPERATING_PROCEDURE', 'TECHNICAL_NOTE')),
    CONSTRAINT ck_knowledge_version CHECK (version > 0),
    CONSTRAINT ck_knowledge_effectivity CHECK (effective_to IS NULL OR effective_to >= effective_from),
    -- Uma versão por código: duas "versão 3" do mesmo documento fariam a citação deixar de identificar
    -- qual documento respondeu.
    CONSTRAINT uq_knowledge_code_version UNIQUE (brewery_id, code, version)
);

CREATE INDEX ix_knowledge_doc_code ON knowledge_document (brewery_id, code, version DESC);

-- Os trechos, que são a unidade de evidência: é o trecho que se cita e é sobre ele que se confere.
--
-- `search_vector` é coluna GERADA: derivá-la na escrita garante que índice e texto nunca divergem — um
-- gatilho ou uma atualização em código poderiam ser esquecidos numa inserção nova, e a busca passaria a
-- não achar um documento que está lá.
CREATE TABLE knowledge_chunk (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    -- Posição no documento: "trecho 4 do manual da bomba" permite conferir; "algum lugar do manual"
    -- não permite.
    ordinal INTEGER NOT NULL,
    content TEXT NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('portuguese_unaccent', content)) STORED,
    CONSTRAINT ck_knowledge_chunk_ordinal CHECK (ordinal >= 0),
    CONSTRAINT uq_knowledge_chunk_ordinal UNIQUE (document_id, ordinal)
);

-- GIN é o índice da busca textual: é ele que faz `@@` não varrer a tabela.
CREATE INDEX ix_knowledge_chunk_search ON knowledge_chunk USING GIN (search_vector);
CREATE INDEX ix_knowledge_chunk_doc ON knowledge_chunk (brewery_id, document_id, ordinal);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000026', NULL, 'knowledge', 'Base de conhecimento', 31)
ON CONFLICT (id) DO NOTHING;

-- Ler e indexar são alçadas diferentes: quem consulta um manual não decide o que entra na base de que
-- a IA vai citar. Indexar é crítico porque muda a fonte das respostas de todo mundo.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000114', '11111111-0000-0000-0000-000000000026',
     'knowledge.document.read', 'Consultar a base de conhecimento', false),
    ('22222222-0000-0000-0000-000000000115', '11111111-0000-0000-0000-000000000026',
     'knowledge.document.index', 'Indexar documento — muda a fonte das respostas', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('knowledge.document.read', 'knowledge.document.index')
ON CONFLICT (group_id, permission_id) DO NOTHING;
