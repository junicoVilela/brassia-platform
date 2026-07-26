package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.purchasing.IngredientSourcingLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fornecedor preferencial e custo de referência por ingrediente = lote mais
 * recente (maior received_at). O custo do lote (por unidade recebida) é
 * convertido para a unidade canônica, para casar com a quantidade da necessidade.
 */
@Repository
class JdbcIngredientSourcingAdapter implements IngredientSourcingLookup {

    private final JdbcClient jdbc;

    JdbcIngredientSourcingAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Sourcing> preferredByIngredient(UUID breweryId) {
        return jdbc.sql("""
                SELECT DISTINCT ON (ingredient_id) ingredient_id, supplier_id, unit_cost, unit
                FROM stock_lot
                WHERE brewery_id = :brewery
                ORDER BY ingredient_id, received_at DESC
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> {
                    var unit = StockUnit.valueOf(rs.getString("unit"));
                    // custo/canônica = custo/unidade ÷ (canônica por unidade)
                    var factor = unit.toCanonical(BigDecimal.ONE);
                    var costPerCanonical = rs.getBigDecimal("unit_cost").divide(factor, 6, RoundingMode.HALF_UP);
                    return new Sourcing(
                            rs.getObject("ingredient_id", UUID.class),
                            rs.getObject("supplier_id", UUID.class),
                            costPerCanonical);
                })
                .list();
    }
}
