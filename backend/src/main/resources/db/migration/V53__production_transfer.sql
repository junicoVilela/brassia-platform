-- PRD-005: transferência do lote ao fermentador. Uma por lote; registra volume,
-- OG, perdas e o fermentador destino. A transferência move o lote para FERMENTING.

ALTER TABLE production_batch DROP CONSTRAINT ck_production_batch_status;
ALTER TABLE production_batch
    ADD CONSTRAINT ck_production_batch_status
        CHECK (status IN ('IN_PROGRESS', 'FERMENTING', 'COMPLETED', 'CANCELLED'));

CREATE TABLE production_transfer (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES production_batch (id) ON DELETE CASCADE,
    destination_equipment_id UUID NOT NULL,
    volume_liters NUMERIC(10, 2) NOT NULL,
    og_sg NUMERIC(6, 4) NOT NULL,
    losses_liters NUMERIC(10, 2) NOT NULL DEFAULT 0,
    transferred_at TIMESTAMPTZ NOT NULL,
    transferred_by UUID NOT NULL,
    CONSTRAINT uq_production_transfer_batch UNIQUE (batch_id),
    CONSTRAINT ck_production_transfer_volume CHECK (volume_liters > 0),
    CONSTRAINT ck_production_transfer_losses CHECK (losses_liters >= 0)
);

CREATE INDEX ix_production_transfer_brewery ON production_transfer (brewery_id, transferred_at DESC);
