-- CST-001-A — a mão de obra ganha fonte, e o custo do lote deixa de ter essa lacuna.
--
-- O CRITÉRIO DE REMOÇÃO, LITERAL: "existir apontamento de hora por lote ou por etapa, e um contribuinte
-- implementar a porta". As duas metades estão aqui.
--
-- ONDE CADA COISA MORA, E POR QUÊ. A HORA é da produção: quem trabalhou, de quando até quando, em qual
-- lote. O DINHEIRO é do custeio: quanto vale a hora. Separar não é preciosismo — é o que permite ajustar a
-- taxa sem reescrever apontamento, e o que evita a produção precisar conhecer moeda para registrar que
-- alguém passou seis horas na brassa.
CREATE TABLE production_labor_entry (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    batch_id UUID NOT NULL REFERENCES production_batch (id),
    -- O que se estava fazendo. Texto e não enum: a divisão de trabalho de uma cervejaria de três pessoas
    -- não é a mesma de uma de trinta, e um enum imporia a de quem escreveu o código.
    activity VARCHAR(120) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    -- Quantas pessoas. Duas pessoas por três horas custam seis horas-homem, e registrar "3 h" perderia
    -- metade do custo — que é justamente a metade que a cervejaria paga.
    people INTEGER NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_labor_entry_period CHECK (ended_at > started_at),
    CONSTRAINT ck_labor_entry_people CHECK (people >= 1)
);

CREATE INDEX ix_labor_entry_batch ON production_labor_entry (brewery_id, batch_id, started_at);

-- A taxa da casa, no mesmo espírito das políticas da PRM-001.
--
-- UMA TAXA, E NÃO UMA POR PESSOA. Custo de mão de obra por lote é custo médio da hora produtiva — salário,
-- encargos e ociosidade diluídos. Uma taxa por pessoa transformaria o custo do lote numa função de quem
-- estava escalado naquele dia, e o mesmo lote sairia mais caro na semana em que o cervejeiro sênior
-- trabalhou. Se um dia isso for necessário, entra como taxa por atividade, que é a divisão que a
-- cervejaria realmente enxerga.
CREATE TABLE costing_labor_rate (
    brewery_id UUID PRIMARY KEY REFERENCES brewery (id),
    cost_per_hour NUMERIC(14, 4) NOT NULL,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_labor_rate_positive CHECK (cost_per_hour > 0)
);

-- Permissões: apontar hora é trabalho do dia; definir quanto vale a hora é decisão de gestão.
INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000146', d.id,
       'production.labor.record', 'Apontar horas trabalhadas no lote', false
FROM permission_domain d WHERE d.code = 'production'
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000147', d.id,
       'costing.labor-rate.manage', 'Definir o custo da hora de trabalho', false
FROM permission_domain d WHERE d.code = 'costing'
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('production.labor.record', 'costing.labor-rate.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
