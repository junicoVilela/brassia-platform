-- TRC-001: genealogia completa (insumo → OP → lote → levedura → envase).
--
-- Não há tabela nova, e é de propósito. O grafo é DERIVADO das ligações que cada módulo já
-- registra na própria tabela — production_batch.order_id, packaging_plan.batch_id,
-- stock_movement.reference, fermentation_yeast_harvest.pitched_batch_id. Materializá-lo criaria
-- uma segunda verdade que envelheceria a cada envase, pelo mesmo motivo que o saldo de estoque
-- (STK-002) e a aptidão do instrumento (MTR-001) também são derivados.
--
-- O que a migration cria é a permissão de leitura e um índice: a busca para trás a partir de uma
-- OP varre stock_movement por `reference`, que até aqui só era consultado por lote.
CREATE INDEX IF NOT EXISTS ix_stock_movement_reference
    ON stock_movement (brewery_id, reference, type);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000020', NULL, 'traceability', 'Rastreabilidade', 25)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000091', '11111111-0000-0000-0000-000000000020',
     'traceability.genealogy.read', 'Consultar a genealogia de um lote, insumo ou envase', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'traceability.genealogy.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
