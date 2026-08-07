package br.com.brew.brassia.production.adapter.inbound.gateway;

import br.com.brew.brassia.shared.reporting.IndicatorGroup;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import br.com.brew.brassia.shared.reporting.OperationalIndicator.DrillDown;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** O que a produção mostra no painel (RPT-002). */
@Component
class ProductionIndicatorAdapter implements IndicatorSource {

    private final JdbcClient jdbc;

    ProductionIndicatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to) {
        var started = count("""
                SELECT COUNT(*) FROM production_batch
                WHERE brewery_id = :brewery AND started_at >= :from AND started_at < :to
                """, breweryId, from, to);
        var transferred = sum("""
                SELECT COALESCE(SUM(volume_liters), 0) FROM production_transfer
                WHERE brewery_id = :brewery AND transferred_at >= :from AND transferred_at < :to
                """, breweryId, from, to);
        var inProgress = count("""
                SELECT COUNT(*) FROM production_batch
                WHERE brewery_id = :brewery AND status = 'IN_PROGRESS'
                """, breweryId, null, null);

        return List.of(
                OperationalIndicator.inPeriod("producao.lotes_iniciados", IndicatorGroup.PRODUCTION,
                        "Lotes iniciados",
                        "Lotes de produção cuja brassagem começou dentro do período. Conta pelo início, "
                                + "não pelo fim: um lote que começou em julho e fermenta até agosto é "
                                + "produção de julho.",
                        started, "lotes", from, to, DrillDown.of("production.batches")),
                OperationalIndicator.inPeriod("producao.litros_transferidos", IndicatorGroup.PRODUCTION,
                        "Litros ao fermentador",
                        "Soma do volume transferido ao fermentador no período. É a cerveja que existiu "
                                + "de fato, não a planejada — a diferença entre as duas é o rendimento.",
                        transferred, "L", from, to, DrillDown.of("production.batches")),
                OperationalIndicator.snapshot("producao.lotes_em_andamento", IndicatorGroup.PRODUCTION,
                        "Lotes em andamento",
                        "Lotes na panela agora, sem transferência registrada. É foto do instante, não "
                                + "acumulado do período.",
                        inProgress, "lotes", to,
                        DrillDown.of("production.batches", "status", "IN_PROGRESS")));
    }

    private BigDecimal count(String sql, UUID breweryId, Instant from, Instant to) {
        return sum(sql, breweryId, from, to);
    }

    private BigDecimal sum(String sql, UUID breweryId, Instant from, Instant to) {
        var query = jdbc.sql(sql).param("brewery", breweryId);
        if (from != null) {
            query = query.param("from", Timestamp.from(from)).param("to", Timestamp.from(to));
        }
        return query.query(BigDecimal.class).single();
    }
}
