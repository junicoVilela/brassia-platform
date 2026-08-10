-- EXP-001 — Lote dividido.
--
-- Um experimento é um registro do que se PLANEJOU antes de existir resultado. As colunas de plano
-- (hipótese, fatores, grandezas) não têm caminho de UPDATE na aplicação, e o motivo não é purismo: um
-- experimento cuja hipótese pode ser reescrita depois de ver o resultado sempre confirma a hipótese — e
-- fica indistinguível de um que realmente previu o efeito.
CREATE TABLE experiment_plan (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    recipe_id UUID NOT NULL,
    hypothesis TEXT NOT NULL,
    control_batch_id UUID NOT NULL,
    variant_batch_id UUID NOT NULL,
    -- Sensorial planejado e sensorial cego são colunas separadas porque geram limitações DIFERENTES:
    -- não avaliar é uma restrição, avaliar sabendo qual copo é a variante é outra.
    sensory_planned BOOLEAN NOT NULL,
    sensory_blind BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    planned_by UUID NOT NULL,
    planned_at TIMESTAMPTZ NOT NULL,
    -- Conclusão: nula enquanto não houver leitura. Nulo aqui é "ainda não se concluiu", que é diferente
    -- de "concluiu-se que não houve efeito" — este último é conclusion_supported = false.
    conclusion_supported BOOLEAN,
    conclusion_observation TEXT,
    concluded_by UUID,
    concluded_at TIMESTAMPTZ,
    CONSTRAINT ck_experiment_distinct_batches CHECK (control_batch_id <> variant_batch_id),
    -- O banco também recusa concluir pela metade: ou os quatro campos existem, ou nenhum. Sem isto, uma
    -- falha no meio da escrita deixaria uma conclusão sem autor — e uma conclusão sem autor é um número
    -- sem ninguém por trás.
    CONSTRAINT ck_experiment_conclusion_complete CHECK (
        (conclusion_supported IS NULL AND conclusion_observation IS NULL
             AND concluded_by IS NULL AND concluded_at IS NULL)
        OR (conclusion_supported IS NOT NULL AND conclusion_observation IS NOT NULL
             AND concluded_by IS NOT NULL AND concluded_at IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_concluded_has_conclusion CHECK (
        status <> 'CONCLUDED' OR conclusion_supported IS NOT NULL
    )
);

CREATE INDEX ix_experiment_plan_brewery_recipe ON experiment_plan (brewery_id, recipe_id);

-- O MESMO PAR DE LOTES NÃO ENTRA EM DOIS EXPERIMENTOS ATIVOS.
--
-- Não é higiene de dados: dois experimentos sobre o mesmo par testam variáveis diferentes nos mesmos
-- lotes, e aí nenhuma das duas está isolada. A unicidade parcial deixa o histórico livre — experimentos
-- concluídos e abandonados sobre o mesmo par continuam registrados.
CREATE UNIQUE INDEX uq_experiment_active_pair
    ON experiment_plan (brewery_id, control_batch_id, variant_batch_id)
    WHERE status IN ('PLANNED', 'RUNNING');

-- Os fatores IGUAIS são gravados junto com o que difere, e é deliberado: "o resto ficou igual" é a
-- afirmação sobre a qual toda a conclusão se apoia. Sem os iguais registrados, ninguém pode conferir,
-- meses depois, que o tanque era mesmo o mesmo.
CREATE TABLE experiment_factor (
    experiment_id UUID NOT NULL REFERENCES experiment_plan (id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    control_value VARCHAR(200) NOT NULL,
    variant_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (experiment_id, name)
);

CREATE TABLE experiment_measurement_plan (
    experiment_id UUID NOT NULL REFERENCES experiment_plan (id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    PRIMARY KEY (experiment_id, kind)
);

-- As limitações são DERIVADAS do plano e recalculadas na leitura, não gravadas.
--
-- Gravá-las abriria a possibilidade de existir uma conclusão cuja lista de limitações foi editada — que é
-- exatamente o que esta história impede. Derivar custa uma linha de código e fecha o caminho.

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000030', NULL, 'experiment', 'Experimentos', 35)
ON CONFLICT (id) DO NOTHING;

-- Concluir é crítico e separado de planejar: quem escreve a leitura do experimento decide o que a
-- cervejaria vai passar a acreditar sobre a própria receita.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000128', '11111111-0000-0000-0000-000000000030',
     'experiment.plan.read', 'Consultar experimentos', false),
    ('22222222-0000-0000-0000-000000000129', '11111111-0000-0000-0000-000000000030',
     'experiment.plan.write', 'Planejar e conduzir experimentos', false),
    ('22222222-0000-0000-0000-000000000130', '11111111-0000-0000-0000-000000000030',
     'experiment.plan.conclude', 'Concluir experimento — define o que se passa a acreditar', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('experiment.plan.read', 'experiment.plan.write', 'experiment.plan.conclude')
ON CONFLICT (group_id, permission_id) DO NOTHING;
