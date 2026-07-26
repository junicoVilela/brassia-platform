-- STK-002: ledger de movimentos de estoque (append-only). O saldo (on-hand,
-- reservado, disponível) é sempre derivado da soma dos deltas — não há coluna
-- de saldo mutável.

CREATE TABLE stock_movement (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    lot_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    quantity NUMERIC(14, 4) NOT NULL,
    on_hand_delta NUMERIC(14, 4) NOT NULL,
    reserved_delta NUMERIC(14, 4) NOT NULL,
    reference UUID,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id UUID NOT NULL,
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_movement_type CHECK (type IN
        ('ENTRY', 'CONSUMPTION', 'RETURN', 'LOSS', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'RESERVATION', 'RELEASE'))
);

CREATE INDEX ix_stock_movement_lot ON stock_movement (brewery_id, lot_id, occurred_at);
