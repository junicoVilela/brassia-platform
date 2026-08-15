-- SAL-001-B — o lote acabado passa a ser liberado, e "vendável" ganha definição.
--
-- A DECISÃO DO MANTENEDOR (2026-08-15): um lote é vendável quando está LIBERADO PELA QUALIDADE, DENTRO
-- DA VALIDADE e NÃO BLOQUEADO POR QUARENTENA. A DUV-SAL-001 fica respondida.
--
-- LIBERAÇÃO É ATO ASSINADO, E NÃO DEDUÇÃO. A alternativa considerada era derivar "liberado" de "não há
-- não conformidade nem desvio em aberto". Foi recusada por dois motivos: um lote NUNCA MEDIDO passaria
-- como liberado (BatchQualityLookup.unmeasured() é verdadeiro e nada reclama), e a auditoria que pergunta
-- "quem liberou este lote?" receberia "o sistema deduziu". Em alimento, liberação é decisão registrada.
--
-- POR QUE A TABELA FICA EM PACKAGING, E NÃO EM QUALITY. A liberação é um ESTADO DO LOTE, e o lote é de
-- packaging. Se o registro morasse em quality, a expedição — que já mora em packaging — precisaria
-- consultá-lo para recusar lote não liberado, e isso criaria packaging → quality em cima de um
-- quality → packaging recém-criado: ciclo, exatamente como em CLN-004-A e FDS-004-A (ver ADR-0016).
--
-- A ALÇADA, ESSA SIM, É DA QUALIDADE. A permissão nasce no domínio `quality` porque quem decide liberar
-- é a qualidade, mesmo que o dado seja do lote. Separar "de quem é a decisão" de "de quem é o dado" é o
-- que permite a fronteira sobreviver sem mentir sobre quem manda.

CREATE TABLE packaging_finished_lot_release (
    -- Um lote, no máximo uma liberação. A chave primária no próprio lote é o que torna a operação
    -- idempotente e impede duas liberações concorrentes de existirem — liberar duas vezes com
    -- responsáveis diferentes deixaria a auditoria sem saber quem respondeu pelo lote.
    finished_lot_id UUID PRIMARY KEY REFERENCES packaging_finished_lot (id),
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    released_by UUID NOT NULL,
    released_at TIMESTAMPTZ NOT NULL,
    -- Observação da liberação. Opcional: o ato já é a afirmação, e exigir texto faria o operador
    -- escrever "ok" para poder seguir — que é pior que campo vazio, porque parece justificativa.
    note VARCHAR(500)
);

CREATE INDEX ix_finished_lot_release_brewery ON packaging_finished_lot_release (brewery_id, released_at);

-- Não há revogação de liberação, e é decisão. Um lote liberado que depois se mostra problemático é caso
-- de QUARENTENA ou RECALL — mecanismos que já existem (FDS-002), alcançam por herança e deixam rastro do
-- porquê. Apagar a liberação faria sumir o fato de que alguém a assinou, que é justamente o que a
-- investigação precisa saber.

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000155', d.id,
       'quality.lot.release', 'Liberar lote acabado para venda', true
FROM permission_domain d WHERE d.code = 'quality'
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'quality.lot.release'
ON CONFLICT (group_id, permission_id) DO NOTHING;
