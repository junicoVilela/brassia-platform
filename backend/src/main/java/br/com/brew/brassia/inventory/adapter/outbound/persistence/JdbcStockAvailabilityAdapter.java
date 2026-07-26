package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * On-hand por ingrediente em unidade canônica: soma os deltas do ledger por lote
 * (na unidade do lote) e converte para a canônica da dimensão, agregando por
 * ingrediente.
 */
@Repository
class JdbcStockAvailabilityAdapter implements StockOnHandLookup {

    private final JdbcClient jdbc;

    JdbcStockAvailabilityAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<IngredientOnHand> onHandByIngredient(UUID breweryId) {
        var rows = jdbc.sql("""
                SELECT l.ingredient_id AS ingredient_id, l.unit AS unit,
                       COALESCE(SUM(m.on_hand_delta), 0) AS on_hand
                FROM stock_lot l JOIN stock_movement m ON m.lot_id = l.id
                WHERE l.brewery_id = :brewery
                GROUP BY l.ingredient_id, l.unit
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> new Object[] {
                        rs.getObject("ingredient_id", UUID.class), rs.getString("unit"),
                        rs.getBigDecimal("on_hand")})
                .list();

        // Converte cada (unidade do lote) para a canônica e agrega por ingrediente.
        var byIngredient = new LinkedHashMap<UUID, BigDecimal[]>(); // [onHandCanonical]; unidade canônica derivada
        var canonicalUnit = new LinkedHashMap<UUID, String>();
        for (var row : rows) {
            var ingredientId = (UUID) row[0];
            var unit = StockUnit.valueOf((String) row[1]);
            var onHand = (BigDecimal) row[2];
            var canonical = unit.toCanonical(onHand);
            byIngredient.merge(ingredientId, new BigDecimal[] {canonical},
                    (a, b) -> new BigDecimal[] {a[0].add(b[0])});
            canonicalUnit.put(ingredientId, unit.canonical());
        }

        return byIngredient.entrySet().stream()
                .map(e -> new IngredientOnHand(e.getKey(), e.getValue()[0], canonicalUnit.get(e.getKey())))
                .toList();
    }
}
