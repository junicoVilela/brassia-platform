-- FCST-001 — a previsão de demanda ganha alçada.
--
-- MIGRATION SÓ DE PERMISSÃO, E ISSO É DECISÃO. A previsão não é persistida: ela é derivada do histórico
-- de pedidos no momento da pergunta, como o custo aberto do lote. Guardá-la criaria uma segunda verdade
-- que envelhece a cada pedido novo, e alguém acabaria decidindo em cima de um número calculado no mês
-- passado sem saber disso.
--
-- Se um dia for preciso comparar "o que prevíamos" com "o que aconteceu" — que é uma pergunta legítima e
-- diferente —, aí sim nasce uma tabela de previsões arquivadas, com a versão do método junto. Ela não
-- nasce aqui porque ninguém pediu essa pergunta ainda.

-- Não é crítica: ler previsão não muda nada. O que seria crítico é ela virar ordem de produção
-- sozinha — e isso não existe, por critério transversal da sprint.
INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000160', d.id,
       'forecast.demand.read', 'Consultar previsão de demanda', false
FROM permission_domain d WHERE d.code = 'sales'
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'forecast.demand.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
