-- COM-001 — a receita sai de casa, e o que sai é escolhido campo a campo.
--
-- O RETRATO É UMA CÓPIA CONGELADA, E NÃO UMA VISTA. Publica-se uma VERSÃO: a receita continua evoluindo
-- em casa, e o que está lá fora é o retrato daquele momento, com o número da versão à vista. Uma vista
-- faria a edição privada de amanhã alterar em silêncio o que o público já leu — e o autor descobriria
-- ter publicado algo que nunca revisou.
--
-- O QUE ELE NÃO CONTÉM É A PARTE IMPORTANTE. O JSON é montado por ALLOWLIST no domínio
-- (PublicRecipeSnapshot), campo a campo. A alternativa — serializar a receita removendo o que não pode
-- sair — é blacklist, e blacklist falha do lado errado: o dia em que alguém acrescentar um custo
-- estimado ou um fornecedor preferencial à receita, ele vaza POR PADRÃO. Ficam de fora, por decisão:
-- brewery_id, ingredient_id (que aponta para o catálogo, onde moram preço de compra e fornecedor),
-- equipment_id e a linhagem interna.

CREATE TABLE community_published_recipe (
    id UUID PRIMARY KEY,
    -- A cervejaria fica aqui DENTRO, e nunca no retrato: ela é o que a autorização usa do lado de
    -- dentro, e é justamente o que o plano de testes proíbe de aparecer em busca e feed.
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    recipe_id UUID NOT NULL REFERENCES recipe (id),
    -- A versão publicada. Sem ela a fonte não é reproduzível: "a IPA da Ana" não diz qual.
    recipe_version BIGINT NOT NULL,
    author_user_id UUID NOT NULL REFERENCES security_user (id),
    -- Nome congelado: a atribuição não muda se a pessoa trocar o nome de exibição depois, e continua
    -- legível mesmo se o usuário for desativado.
    author_display_name VARCHAR(160) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(1000),
    -- Lista fechada, ao contrário de canal de venda e atividade de mão de obra, que são cadastro: a
    -- diferença é efeito jurídico. Quem escrevesse "livre" estaria dizendo nada, e quem copiasse
    -- acreditando naquilo ficaria exposto.
    license VARCHAR(24) NOT NULL,
    visibility VARCHAR(12) NOT NULL,
    snapshot JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    -- Despublicar NÃO apaga: o que já foi lido não se desfaz, e um fork feito enquanto estava pública
    -- continua legítimo. O que a despublicação faz é tirar de circulação daqui para a frente.
    unpublished_at TIMESTAMPTZ,
    CONSTRAINT ck_community_license CHECK (license IN ('CC0', 'CC_BY', 'CC_BY_SA', 'CC_BY_NC',
                                                       'ALL_RIGHTS_RESERVED')),
    CONSTRAINT ck_community_visibility CHECK (visibility IN ('PRIVATE', 'BREWERY', 'LINK', 'UNLISTED',
                                                             'PUBLIC')),
    CONSTRAINT ck_community_version CHECK (recipe_version > 0)
);

-- Uma publicação por versão de receita. Republicar a MESMA versão duas vezes criaria duas entradas
-- concorrendo na busca, com títulos possivelmente diferentes, e ninguém saberia qual é a boa.
CREATE UNIQUE INDEX ux_community_recipe_version
    ON community_published_recipe (recipe_id, recipe_version);

-- O índice da busca: só o que está no ar e listado. Parcial porque a busca nunca olha o resto, e um
-- índice cheio de linha que ela não lê é peso sem retorno.
CREATE INDEX ix_community_listed ON community_published_recipe (published_at DESC)
    WHERE unpublished_at IS NULL AND visibility = 'PUBLIC';

CREATE INDEX ix_community_by_brewery ON community_published_recipe (brewery_id, published_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000036', NULL, 'community', 'Comunidade', 41)
ON CONFLICT (id) DO NOTHING;

-- Ler a biblioteca é de todos; PUBLICAR é ato de quem responde pela receita, e é crítico: o que sai não
-- volta — quem leu, leu, e um fork feito enquanto estava público continua legítimo mesmo depois de
-- despublicar.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000161', '11111111-0000-0000-0000-000000000036',
     'community.library.read', 'Consultar a biblioteca de receitas', false),
    ('22222222-0000-0000-0000-000000000162', '11111111-0000-0000-0000-000000000036',
     'community.recipe.publish', 'Publicar receita na biblioteca', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('community.library.read', 'community.recipe.publish')
ON CONFLICT (group_id, permission_id) DO NOTHING;
