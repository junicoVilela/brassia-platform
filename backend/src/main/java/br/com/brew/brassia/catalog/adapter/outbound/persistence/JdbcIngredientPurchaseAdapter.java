package br.com.brew.brassia.catalog.adapter.outbound.persistence;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Unidade de compra + identificação dos ingredientes ativos, para a lista de compras (PUR-002). */
@Repository
class JdbcIngredientPurchaseAdapter implements IngredientPurchaseLookup {

    private final JdbcClient jdbc;

    JdbcIngredientPurchaseAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PurchaseSpec> findAll(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, code, name, use_unit, purchase_unit
                FROM catalog_ingredient
                WHERE brewery_id = :brewery AND active = true
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> new PurchaseSpec(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("use_unit"),
                        rs.getString("purchase_unit")))
                .list();
    }
}
