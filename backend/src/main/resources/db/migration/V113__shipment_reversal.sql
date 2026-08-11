-- FDS-003-A — a expedição registrada errada passa a ter volta.
--
-- O DÉBITO E O ESCOPO. O registro da Sprint 12 dizia: "registrar é fato; devolução, cancelamento e
-- transferência entre destinos não existem", com critério de remoção apontando para as sprints 19/20. Só
-- uma das três é urgente, e não é comercial: **a expedição digitada errada**. Devolução e transferência
-- entre destinos são movimentação comercial e continuam fora — elas exigem cliente, pedido e nota, que é
-- exatamente o que a Sprint 12 se recusou a inventar por conta própria.
--
-- POR QUE ISSO NÃO PODE ESPERAR AS SPRINTS 19/20. Um erro de digitação na expedição contamina o recall,
-- que é onde o dado precisa estar certo: 200 unidades registradas para o distribuidor errado fazem o
-- simulado medir cobertura sobre um destino que nunca recebeu nada, e fazem o saldo sem destino do lote
-- mentir para menos — escondendo cerveja que ninguém sabe onde está.
--
-- ESTORNO NÃO APAGA. A linha permanece, e é o `AGENTS.md` que manda: movimento e evento de auditoria não
-- são apagados fisicamente. Apagar tornaria indistinguível "nunca houve expedição" de "houve e foi
-- estornada", e a segunda precisa ser demonstrável — inclusive para quem recebeu a comunicação de um
-- recall baseado nela.
ALTER TABLE packaging_shipment ADD COLUMN reversed_at TIMESTAMPTZ;
ALTER TABLE packaging_shipment ADD COLUMN reversed_by UUID;
ALTER TABLE packaging_shipment ADD COLUMN reversal_reason VARCHAR(500);

-- Estorno é registro completo ou ausente, como a contenção da NC (V77): quem, quando e por quê andam
-- juntos. Meio estorno seria uma expedição que não vale mais sem ninguém tendo dito por quê.
ALTER TABLE packaging_shipment
    ADD CONSTRAINT ck_shipment_reversal CHECK (
        (reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL)
    );

-- O recall varre expedições vivas. O índice parcial é o que impede a consulta de percorrer estornos
-- para descartá-los depois — e é a consulta mais sensível a tempo que existe na plataforma, porque
-- alguém a faz com o telefone na mão.
CREATE INDEX ix_shipment_lot_active ON packaging_shipment (brewery_id, finished_lot_id)
    WHERE reversed_at IS NULL;

-- Alçada própria, separada de registrar.
--
-- Registrar expedição é o trabalho do dia e muita gente faz. Estornar desfaz um destino que pode já ter
-- sido comunicado num recall — e quem faz um não faz necessariamente o outro. Não é crítica: o estorno
-- corrige um erro, e dificultá-lo demais empurraria quem opera a conviver com o dado errado.
-- O id continua a sequência das permissões (a última usada é a ...144). Colidir com um id existente
-- derruba a migration inteira, e o `ON CONFLICT (code)` não cobre isso — a chave primária é o id.
INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000145', d.id,
       'packaging.shipment.reverse', 'Estornar expedição registrada errada', false
FROM permission_domain d WHERE d.code = 'packaging'
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p WHERE p.code = 'packaging.shipment.reverse'
ON CONFLICT (group_id, permission_id) DO NOTHING;
