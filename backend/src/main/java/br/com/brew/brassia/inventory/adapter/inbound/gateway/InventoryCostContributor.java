package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.costing.CostContributor;
import br.com.brew.brassia.packaging.PackagingPlanLookup;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O que o estoque sabe custar (CST-001): o insumo que a brassagem consumiu e a embalagem que o
 * envase consumiu.
 *
 * <p><strong>Preço do lote, não preço médio.</strong> O custo sai de {@code quantidade × unit_cost
 * do lote que saiu} — é por isso que a TRC-001-C teve de ser fechada antes: sem consumo por lote,
 * não há preço a aplicar, só a média de uma reserva que talvez nem tenha ido ao moinho.
 *
 * <p><strong>Só movimento de consumo entra.</strong> Reserva não é custo: ela segura estoque e pode
 * ser liberada inteira. Somar reserva daria um custo que some quando a OP é cancelada.
 */
@Component
class InventoryCostContributor implements CostContributor {

    /**
     * Uma linha por lote consumido, com o preço congelado na entrada.
     *
     * <p>O {@code reason} desempata o motivo do consumo: a coluna {@code reference} do ledger é um
     * UUID sem tipo, e sem o filtro um consumo lançado à mão entraria no custo como se fosse do
     * dia de brassa.
     */
    private static final String CONSUMED = """
            SELECT m.lot_id, m.ingredient_id, i.name AS ingredient_name, l.supplier_lot_code,
                   l.unit_cost, l.unit, SUM(m.quantity) AS quantity
            FROM stock_movement m
            JOIN stock_lot l ON l.id = m.lot_id AND l.brewery_id = m.brewery_id
            LEFT JOIN catalog_ingredient i ON i.id = m.ingredient_id AND i.brewery_id = m.brewery_id
            WHERE m.brewery_id = :brewery AND m.type = 'CONSUMPTION' AND m.reason = :reason
              AND m.reference IN (:refs)
            GROUP BY m.lot_id, m.ingredient_id, i.name, l.supplier_lot_code, l.unit_cost, l.unit
            ORDER BY i.name, l.supplier_lot_code
            """;

    private final JdbcClient jdbc;
    private final PackagingPlanLookup plans;

    InventoryCostContributor(JdbcClient jdbc, PackagingPlanLookup plans) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.plans = Objects.requireNonNull(plans);
    }

    @Override
    public List<CostLine> linesFor(UUID breweryId, CostScope scope) {
        var lines = new ArrayList<CostLine>();
        lines.addAll(consumed(breweryId, List.of(scope.orderId()), "brassagem", CostCategory.INGREDIENT));
        // Quais planos pertencem ao lote é pergunta do envase, respondida pela consulta publicada
        // dele; o ledger só sabe que aquele consumo apontava para "algum" plano.
        var planIds = plans.plansOfBatch(breweryId, scope.batchId());
        if (!planIds.isEmpty()) {
            lines.addAll(consumed(breweryId, planIds, "envase", CostCategory.PACKAGING));
        }
        return lines;
    }

    /**
     * A lacuna do insumo, agora por ordem.
     *
     * <p>Ordem sem consumo confirmado não tem custo de insumo — e o total sem ele não é "barato",
     * é incompleto. Antes da TRC-001-C a lacuna era da plataforma; hoje é um fato sobre esta ordem,
     * e some quando alguém registrar o que a brassagem usou.
     */
    @Override
    public List<CostGap> gapsFor(UUID breweryId, CostScope scope) {
        var gaps = new ArrayList<CostGap>();
        if (consumed(breweryId, List.of(scope.orderId()), "brassagem", CostCategory.INGREDIENT).isEmpty()) {
            gaps.add(new CostGap(CostCategory.INGREDIENT,
                    "o consumo do dia de brassa ainda não foi confirmado nesta ordem: o insumo não entra "
                            + "no custo"));
        }
        if (plans.plansOfBatch(breweryId, scope.batchId()).isEmpty()) {
            gaps.add(new CostGap(CostCategory.PACKAGING,
                    "este lote ainda não foi envasado: não há embalagem consumida a custear"));
        }
        return gaps;
    }

    private List<CostLine> consumed(UUID breweryId, List<UUID> references, String reason,
            CostCategory category) {
        return jdbc.sql(CONSUMED)
                .param("brewery", breweryId).param("refs", references).param("reason", reason)
                .query((rs, rowNum) -> line(rs, category, reason))
                .list();
    }

    private static CostLine line(ResultSet rs, CostCategory category, String reason) throws SQLException {
        var quantity = rs.getBigDecimal("quantity");
        var unitCost = rs.getBigDecimal("unit_cost");
        var name = rs.getString("ingredient_name");
        var lotCode = rs.getString("supplier_lot_code");
        var description = name != null ? name : "insumo " + rs.getObject("ingredient_id", UUID.class);
        var source = "consumo de " + reason + " — lote "
                + (lotCode != null ? lotCode : rs.getObject("lot_id", UUID.class).toString().substring(0, 8))
                + ", preço da entrada";
        return new CostLine(category, description, source, quantity, rs.getString("unit"), unitCost,
                total(quantity, unitCost));
    }

    /** Lote sem preço de entrada custa zero e não some da lista: some da lista é que seria pior. */
    private static BigDecimal total(BigDecimal quantity, BigDecimal unitCost) {
        return unitCost == null ? BigDecimal.ZERO : quantity.multiply(unitCost);
    }
}
