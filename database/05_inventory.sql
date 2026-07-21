CREATE TABLE IF NOT EXISTS brassia.ingredient_lot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  ingredient_id uuid NOT NULL, supplier_lot varchar(120), received_at timestamptz NOT NULL,
  expires_on date, quantity_received numeric(20,6) NOT NULL, unit varchar(24) NOT NULL,
  unit_cost numeric(20,6), status varchar(24) NOT NULL, version bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS brassia.stock_movement (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES brassia.brewery(id),
  ingredient_lot_id uuid NOT NULL REFERENCES brassia.ingredient_lot(id), movement_type varchar(30) NOT NULL,
  quantity numeric(20,6) NOT NULL CHECK (quantity > 0), unit varchar(24) NOT NULL,
  reference_type varchar(40), reference_id uuid, reason varchar(500), occurred_at timestamptz NOT NULL,
  recorded_by uuid, recorded_at timestamptz NOT NULL DEFAULT now()
);
