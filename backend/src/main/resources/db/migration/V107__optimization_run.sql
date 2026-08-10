-- OPT-001 — Otimização e substituição assistida.
--
-- A corrida é um REGISTRO IMUTÁVEL do que se pediu e do que o solver respondeu. Só duas coisas mudam
-- depois: a explicação em linguagem natural e a marca de que alguém aplicou o resultado. Nenhuma das
-- duas toca as candidatas — se tocasse, o score deixaria de ser reproduzível a partir da entrada.
CREATE TABLE optimization_run (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    recipe_id UUID NOT NULL,
    -- Versão PUBLICADA da receita. Otimizar rascunho apontaria para uma composição que muda enquanto se
    -- otimiza; a reprodutibilidade começa por a entrada ser estável.
    recipe_version INTEGER NOT NULL,
    objective VARCHAR(20) NOT NULL,
    constraints JSONB NOT NULL,
    method VARCHAR(40) NOT NULL,
    -- Marca do catálogo derivada do CONTEÚDO lido, não da data: uma marca por data diria que a entrada
    -- mudou todo dia mesmo sem nada ter mudado, perdendo a informação que ela existe para dar.
    catalog_version VARCHAR(80) NOT NULL,
    -- Semente só existe em método que a usa. O domínio recusa a incoerência nos dois sentidos: semente
    -- em método determinístico sugeriria variação inexistente; a falta dela num método aleatório tornaria
    -- o resultado irreprodutível. Nos dois casos, o registro mentiria.
    seed BIGINT,
    candidates JSONB NOT NULL,
    infeasible JSONB,
    explanation TEXT,
    -- Aplicar cria uma VERSÃO NOVA de receita, por fora e sob revisão humana. Aqui só fica o ponteiro:
    -- se o otimizador pudesse escrever na receita, "revisado" viraria um campo que alguém marca em vez
    -- de um ato que alguém pratica.
    applied_recipe_version_id UUID,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_optimization_objective
        CHECK (objective IN ('COST', 'AVAILABILITY', 'TECHNICAL_TARGET')),
    -- Ou há candidatas, ou há inviabilidade explicada. Nunca as duas, nunca nenhuma: uma corrida sem
    -- candidata e sem inviabilidade seria um resultado vazio que ninguém sabe interpretar.
    CONSTRAINT ck_optimization_outcome CHECK (
        (jsonb_array_length(candidates) > 0 AND infeasible IS NULL)
        OR (jsonb_array_length(candidates) = 0 AND infeasible IS NOT NULL)
    ),
    -- Não se aplica o que não tem solução.
    CONSTRAINT ck_optimization_applied_is_feasible CHECK (
        applied_recipe_version_id IS NULL OR infeasible IS NULL
    )
);

CREATE INDEX ix_optimization_run_recipe ON optimization_run (brewery_id, recipe_id, requested_at DESC);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000033', NULL, 'optimization', 'Otimização assistida', 38)
ON CONFLICT (id) DO NOTHING;

-- Otimizar é barato e não muda nada — é uma leitura cara. Aplicar é que tem consequência, e por isso é
-- crítica: mesmo sendo só o registro do ponteiro, ela declara que aquela alternativa passou a valer.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000139', '11111111-0000-0000-0000-000000000033',
     'optimization.run.read', 'Consultar otimizações', false),
    ('22222222-0000-0000-0000-000000000140', '11111111-0000-0000-0000-000000000033',
     'optimization.run.execute', 'Executar otimização', false),
    ('22222222-0000-0000-0000-000000000141', '11111111-0000-0000-0000-000000000033',
     'optimization.run.apply', 'Registrar que uma alternativa virou versão de receita', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('optimization.run.read', 'optimization.run.execute', 'optimization.run.apply')
ON CONFLICT (group_id, permission_id) DO NOTHING;
