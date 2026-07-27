-- PUR-002-A: tamanho da embalagem de compra do ingrediente (ex.: saco de 25 KG),
-- expresso na unidade de compra. Opcional; quando presente, a lista de compras
-- arredonda a quantidade para múltiplos de pacote fechado.

ALTER TABLE catalog_ingredient
    ADD COLUMN purchase_package_size NUMERIC(14, 4);

ALTER TABLE catalog_ingredient
    ADD CONSTRAINT ck_catalog_ingredient_package_size
        CHECK (purchase_package_size IS NULL OR purchase_package_size > 0);
