-- AIA-002: avaliar lote.
--
-- Migration só de permissão, pelo mesmo motivo de V95: a avaliação não tem tabela.
--
-- O que sustenta uma avaliação são os fatos, e cada fato é calculado por quem responde por ele — volume pela
-- produção, ABV pelo motor de receita, desvio pela qualidade, custo pelo custeio. Guardar a avaliação criaria
-- uma cópia desses números que envelheceria: uma correção de medição em agosto deixaria para trás uma
-- avaliação de julho afirmando "todas as medições na faixa" com a mesma aparência de atual. Reavaliar é
-- perguntar de novo aos donos dos números, que é rápido e sempre certo.
--
-- Custo e latência da chamada estão no ledger de invocações; quem pediu, quantos fatos foram e quantas
-- afirmações foram descartadas estão na auditoria — nenhum dos dois guarda o texto da avaliação.

-- Avaliar um lote lê custo, qualidade e produção dele. Quem pode perguntar sobre um manual (`ai.answer.ask`)
-- não necessariamente pode ler o custo de um lote, então a alçada é separada.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000117', '11111111-0000-0000-0000-000000000025',
     'ai.assessment.batch', 'Avaliar risco de um lote com o copiloto — cada avaliação custa', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'ai.assessment.batch'
ON CONFLICT (group_id, permission_id) DO NOTHING;
