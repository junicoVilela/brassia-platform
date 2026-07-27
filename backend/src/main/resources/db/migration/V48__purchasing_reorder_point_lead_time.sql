-- PUR-001-A: ponto de pedido (estoque de segurança) por ingrediente e lead time
-- do fornecedor. Ambos opcionais. O ponto de pedido entra no cálculo de
-- necessidade (max(0, demanda + ponto de pedido − saldo)); o lead time é
-- informativo na lista de compras (antecedência do fornecedor).

ALTER TABLE catalog_ingredient
    ADD COLUMN reorder_point NUMERIC(14, 4);

ALTER TABLE catalog_ingredient
    ADD CONSTRAINT ck_catalog_ingredient_reorder_point
        CHECK (reorder_point IS NULL OR reorder_point >= 0);

ALTER TABLE supplier
    ADD COLUMN lead_time_days INTEGER;

ALTER TABLE supplier
    ADD CONSTRAINT ck_supplier_lead_time
        CHECK (lead_time_days IS NULL OR lead_time_days >= 0);
