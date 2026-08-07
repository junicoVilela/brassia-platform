package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.shared.reporting.IndicatorGroup;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import br.com.brew.brassia.shared.reporting.OperationalIndicator.DrillDown;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O que o estoque mostra no painel (RPT-002).
 *
 * <p>O saldo é derivado do ledger, como sempre — não há coluna de saldo a ler. É por isso que o
 * lote vencido só conta se ainda tiver quantidade: lote zerado que venceu não é problema de
 * ninguém.
 */
@Component
class InventoryIndicatorAdapter implements IndicatorSource {

    /** Trinta dias: prazo em que ainda dá para consumir, remanejar ou negociar com o fornecedor. */
    private static final int EXPIRY_HORIZON_DAYS = 30;

    private static final String EXPIRING = """
            SELECT COUNT(*) FROM stock_lot l
            WHERE l.brewery_id = :brewery AND l.expiry_date IS NOT NULL
              AND l.expiry_date <= :horizon
              AND (SELECT COALESCE(SUM(m.on_hand_delta), 0) FROM stock_movement m
                   WHERE m.brewery_id = l.brewery_id AND m.lot_id = l.id) > 0
            """;

    private final JdbcClient jdbc;

    InventoryIndicatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to) {
        var horizon = to.plus(EXPIRY_HORIZON_DAYS, ChronoUnit.DAYS)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        var expiring = jdbc.sql(EXPIRING)
                .param("brewery", breweryId).param("horizon", horizon)
                .query(BigDecimal.class).single();

        var received = jdbc.sql("""
                SELECT COUNT(*) FROM stock_lot
                WHERE brewery_id = :brewery AND received_at >= :from AND received_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query(BigDecimal.class).single();

        var consumed = jdbc.sql("""
                SELECT COALESCE(SUM(m.quantity * l.unit_cost), 0)
                FROM stock_movement m
                JOIN stock_lot l ON l.id = m.lot_id AND l.brewery_id = m.brewery_id
                WHERE m.brewery_id = :brewery AND m.type = 'CONSUMPTION'
                  AND m.occurred_at >= :from AND m.occurred_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query(BigDecimal.class).single();

        return List.of(
                OperationalIndicator.snapshot("estoque.lotes_vencendo", IndicatorGroup.STOCK,
                        "Lotes vencendo em " + EXPIRY_HORIZON_DAYS + " dias",
                        "Lotes com saldo em mãos cuja validade cai nos próximos " + EXPIRY_HORIZON_DAYS
                                + " dias. Lote zerado que venceu não conta: não há o que remanejar.",
                        expiring, "lotes", to, DrillDown.of("inventory.lots", "expiring", "true")),
                OperationalIndicator.inPeriod("estoque.lotes_recebidos", IndicatorGroup.STOCK,
                        "Lotes recebidos", "Entradas de estoque registradas no período.",
                        received, "lotes", from, to, DrillDown.of("inventory.lots")),
                OperationalIndicator.inPeriod("estoque.valor_consumido", IndicatorGroup.STOCK,
                        "Valor consumido",
                        "Quantidade consumida no período multiplicada pelo preço de entrada de cada "
                                + "lote que saiu. Não é o custo dos lotes de cerveja: consumo lançado "
                                + "fora de brassagem e envase também entra aqui.",
                        consumed, "", from, to, DrillDown.of("inventory.lots")));
    }
}
