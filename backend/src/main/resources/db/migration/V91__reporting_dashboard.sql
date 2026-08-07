-- RPT-002: painel operacional.
--
-- NÃO HÁ TABELA. O painel é a soma de indicadores que cada módulo calcula sobre os próprios dados,
-- coletados por uma porta federada. Materializar o painel criaria números que envelhecem entre uma
-- brassagem e outra, e a fábrica passaria a olhar a foto de ontem achando que é a de hoje.
--
-- Também não há tabela de DEFINIÇÃO de indicador, e é decisão. A definição viaja junto com o número,
-- escrita por quem o calcula: o que conta como "desvio em aberto" é assunto da qualidade, e uma
-- tabela de definições editável por fora acabaria descrevendo um cálculo que o código não faz.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000108', '11111111-0000-0000-0000-000000000024',
     'reporting.dashboard.read', 'Consultar o painel operacional', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'reporting.dashboard.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
