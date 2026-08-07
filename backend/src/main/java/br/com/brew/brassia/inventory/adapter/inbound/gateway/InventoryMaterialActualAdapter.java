package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.costing.MaterialActualSource;
import br.com.brew.brassia.inventory.domain.StockUnit;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O que a ordem separou e o que ela consumiu, por ingrediente (CST-002).
 *
 * <p>Os dois lados saem do mesmo ledger append-only, e é isso que torna a comparação possível
 * depois do fato: registrar consumo <em>libera</em> a reserva, mas o movimento de reserva continua
 * lá. Sem esse histórico, a base de preço desapareceria no instante em que ela passa a interessar.
 *
 * <p>Quantidades vão na unidade canônica e o custo vai como total, não como preço unitário: três
 * lotes do mesmo malte a preços diferentes têm uma média ponderada só, e quem pondera é quem tem
 * as duas colunas na mão.
 */
@Component
class InventoryMaterialActualAdapter implements MaterialActualSource {

    /**
     * Um movimento por lote, com preço da entrada.
     *
     * <p>Agrupa por lote e não por ingrediente porque o preço vive no lote; a soma por ingrediente
     * é feita depois, já convertida, para não misturar grama com quilo antes da conversão.
     */
    private static final String MOVEMENTS = """
            SELECT m.ingredient_id, i.name AS ingredient_name, l.unit, l.unit_cost,
                   SUM(m.quantity) AS quantity
            FROM stock_movement m
            JOIN stock_lot l ON l.id = m.lot_id AND l.brewery_id = m.brewery_id
            LEFT JOIN catalog_ingredient i ON i.id = m.ingredient_id AND i.brewery_id = m.brewery_id
            WHERE m.brewery_id = :brewery AND m.reference = :ref AND m.type = :type
              AND (:reason::varchar IS NULL OR m.reason = :reason)
            GROUP BY m.ingredient_id, i.name, l.unit, l.unit_cost
            """;

    private final JdbcClient jdbc;

    InventoryMaterialActualAdapter(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Actuals actualsFor(UUID breweryId, UUID orderId) {
        return new Actuals(movements(breweryId, orderId, "RESERVATION", null),
                movements(breweryId, orderId, "CONSUMPTION", ProductionStockGatewayAdapter.REASON));
    }

    private List<MaterialFact> movements(UUID breweryId, UUID orderId, String type, String reason) {
        return jdbc.sql(MOVEMENTS)
                .param("brewery", breweryId).param("ref", orderId).param("type", type)
                .param("reason", reason)
                .query(InventoryMaterialActualAdapter::fact)
                .list();
    }

    private static MaterialFact fact(ResultSet rs, int rowNum) throws SQLException {
        var unit = StockUnit.of(rs.getString("unit"));
        var quantity = rs.getBigDecimal("quantity");
        var unitCost = rs.getBigDecimal("unit_cost");
        var total = unitCost == null ? BigDecimal.ZERO : quantity.multiply(unitCost);
        // O preço por unidade canônica cai fora sozinho da divisão total ÷ quantidade canônica,
        // sem ninguém precisar lembrar que grama e quilo diferem por mil.
        return new MaterialFact(rs.getObject("ingredient_id", UUID.class), rs.getString("ingredient_name"),
                unit.toCanonical(quantity), unit.canonical(), total);
    }
}
