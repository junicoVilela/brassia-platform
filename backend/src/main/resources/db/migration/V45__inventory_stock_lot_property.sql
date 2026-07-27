-- STK-005: valores medidos específicos do lote (alfa-ácido, extrato, umidade,
-- células, COA). Pertencem ao lote (não ao catálogo); imutáveis (write-once por
-- propriedade) e privados do tenant — não são republicados como referência global.
-- Registram fonte (manual/importado/sugerido) e confiança do vínculo.

CREATE TABLE stock_lot_property (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES stock_lot (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    property VARCHAR(60) NOT NULL,
    measured_value NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(16),
    source VARCHAR(16) NOT NULL,
    confidence VARCHAR(8) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    CONSTRAINT uq_stock_lot_property UNIQUE (lot_id, property),
    CONSTRAINT ck_stock_lot_property_source CHECK (source IN ('MANUAL', 'IMPORTED', 'SUGGESTED')),
    CONSTRAINT ck_stock_lot_property_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX ix_stock_lot_property_lot ON stock_lot_property (lot_id);
