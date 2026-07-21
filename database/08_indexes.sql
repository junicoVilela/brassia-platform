CREATE INDEX IF NOT EXISTS idx_recipe_brewery_status ON brassia.recipe (brewery_id, status);
CREATE INDEX IF NOT EXISTS idx_order_brewery_status ON brassia.brew_order (brewery_id, status, planned_start);
CREATE INDEX IF NOT EXISTS idx_batch_brewery_status ON brassia.batch (brewery_id, status, started_at);
CREATE INDEX IF NOT EXISTS idx_measurement_batch_time ON brassia.measurement (brewery_id, batch_id, measured_at);
CREATE INDEX IF NOT EXISTS idx_lot_brewery_expiry ON brassia.ingredient_lot (brewery_id, status, expires_on);
CREATE INDEX IF NOT EXISTS idx_stock_ref ON brassia.stock_movement (brewery_id, reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON brassia.outbox_event (published_at, occurred_at) WHERE published_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_entity ON brassia.audit_event (brewery_id, entity_type, entity_id, occurred_at DESC);
