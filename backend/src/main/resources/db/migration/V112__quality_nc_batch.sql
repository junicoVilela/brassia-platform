-- DEB-AIA-003 — a não conformidade passa a saber de que lote fala, e a numeração passa a existir.
--
-- O DÉBITO, E O QUE DELE JÁ TINHA SIDO RESOLVIDO. O registro da Sprint 14 apontava duas barreiras para o
-- copiloto abrir NC: (1) a NC não tem vínculo com lote, e (2) `code`, `description` e os três prazos são
-- NOT NULL e não vêm nos parâmetros da proposta. A segunda **encolheu sozinha**: a PRM-001 criou
-- `quality_capa_policy`, e desde então a abertura já deriva os três prazos da severidade quando nenhum é
-- informado. Sobraram o vínculo com lote, o código e a descrição.
--
-- POR QUE O VÍNCULO IMPORTA, E NÃO É DETALHE DE MODELAGEM. A NC liga hoje a um *desvio*, opcionalmente.
-- A proposta do copiloto afirma "abrir NC **para o lote**", e o modelo não sabia dizer isso: abrir sem o
-- vínculo entregaria um registro solto, perdendo exatamente o que a proposta afirma. E não é só a IA —
-- quem investiga uma reclamação de campo meses depois pergunta "quais NCs este lote teve?", e a resposta
-- exigia adivinhar pelo texto do título.
ALTER TABLE quality_non_conformity ADD COLUMN batch_id UUID;

-- Nulo é legítimo e permanece: NC de auditoria, de fornecedor ou de processo não tem lote. Tornar a
-- coluna obrigatória forçaria a inventar um lote para a não conformidade de um treinamento vencido.
CREATE INDEX ix_quality_nc_batch ON quality_non_conformity (brewery_id, batch_id)
    WHERE batch_id IS NOT NULL;

-- Numeração por cervejaria e ano, igual à das ordens de produção (V36).
--
-- `code` sempre foi obrigatório e único, e sempre foi digitado por quem abria. Isso funciona enquanto há
-- uma pessoa na frente da tela; um comando executado a partir de uma proposta não tem quem digite, e
-- gerar "NC-<uuid>" produziria um identificador que ninguém consegue ler em voz alta numa auditoria.
--
-- A sequência é por ANO porque é assim que se referencia não conformidade em auditoria — "a NC-2026-0007"
-- diz quando aconteceu. Reiniciar no ano é a razão de a chave ser (cervejaria, ano) e não só cervejaria.
CREATE TABLE quality_nc_sequence (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    year INTEGER NOT NULL,
    next_val BIGINT NOT NULL,
    PRIMARY KEY (brewery_id, year)
);
