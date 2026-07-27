-- STK-005-A: correção com histórico. O valor por lote deixa de ser write-once
-- (uma linha por propriedade) e passa a ser append-only: cada gravação adiciona
-- uma revisão; a "atual" é a mais recente (recorded_at). Preserva as anteriores
-- como evidência.

ALTER TABLE stock_lot_property
    DROP CONSTRAINT uq_stock_lot_property;

-- Consulta da revisão atual/histórico por lote+propriedade.
CREATE INDEX ix_stock_lot_property_current
    ON stock_lot_property (lot_id, property, recorded_at DESC);
