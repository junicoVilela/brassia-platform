-- RAG-002: responder com evidência.
--
-- Migration só de permissão: a resposta não tem tabela, e é de propósito.
--
-- Guardar a resposta criaria uma segunda verdade sobre as fontes. O que sustenta uma resposta é o
-- documento citado, que já está indexado e versionado; a resposta é derivada dele. Guardá-la faria com que
-- uma ficha substituída em junho deixasse para trás uma resposta de maio que continuaria afirmando a
-- concentração antiga com a mesma aparência de atual — e ninguém saberia que ela envelheceu.
--
-- O que precisa persistir já persiste: o custo e a latência da chamada estão no ledger de invocações
-- (AIA-001), e quem perguntou, quantas fontes foram consultadas e quantas citações conferiram estão na
-- trilha de auditoria. Nenhum dos dois guarda a pergunta ou a resposta, porque as duas podem carregar dado
-- sensível e nenhuma das perguntas que eles respondem precisa do conteúdo.

-- Perguntar ao copiloto é alçada própria: gasta dinheiro a cada pergunta e usa as fontes indexadas.
-- Consultar o gateway e indexar documento não implicam esta permissão, e nem o contrário.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000116', '11111111-0000-0000-0000-000000000025',
     'ai.answer.ask', 'Perguntar ao copiloto — cada pergunta custa', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'ai.answer.ask'
ON CONFLICT (group_id, permission_id) DO NOTHING;
