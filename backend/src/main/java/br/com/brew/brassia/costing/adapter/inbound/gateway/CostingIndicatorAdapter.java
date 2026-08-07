package br.com.brew.brassia.costing.adapter.inbound.gateway;

import br.com.brew.brassia.shared.reporting.IndicatorGroup;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import br.com.brew.brassia.shared.reporting.OperationalIndicator.DrillDown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O que o custo mostra no painel (RPT-002).
 *
 * <p><strong>Só custo fechado entra.</strong> O custo aberto ainda muda — um envase a mais altera o
 * por litro —, e um painel que somasse os dois teria um total diferente a cada visita sem nada ter
 * acontecido. O que fica de fora é dito na lacuna, não escondido.
 */
@Component
class CostingIndicatorAdapter implements IndicatorSource {

    private final JdbcClient jdbc;

    CostingIndicatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to) {
        var closed = jdbc.sql("""
                SELECT COUNT(*) AS quantidade,
                       COALESCE(SUM(c.total_cost), 0) AS total,
                       COALESCE(SUM(c.volume_liters), 0) AS litros,
                       COUNT(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM costing_batch_cost_gap g WHERE g.cost_id = c.id)) AS incompletos
                FROM costing_batch_cost c
                WHERE c.brewery_id = :brewery AND c.closed_at >= :from AND c.closed_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new Closed(rs.getLong("quantidade"), rs.getBigDecimal("total"),
                        rs.getBigDecimal("litros"), rs.getLong("incompletos")))
                .single();

        var indicators = new ArrayList<OperationalIndicator>();
        indicators.add(OperationalIndicator.inPeriod("custo.lotes_apurados", IndicatorGroup.COST,
                "Lotes com custo fechado",
                "Lotes cuja apuração foi assinada dentro do período. Conta pela data do fechamento, "
                        + "não pela do lote: apurar em agosto um lote de julho é trabalho de agosto.",
                BigDecimal.valueOf(closed.quantidade()), "lotes", from, to,
                DrillDown.of("costing.batchCosts")));

        var perLiter = OperationalIndicator.inPeriod("custo.medio_por_litro", IndicatorGroup.COST,
                "Custo médio por litro",
                "Soma dos custos fechados no período dividida pela soma dos litros desses mesmos "
                        + "lotes. É média ponderada por volume, e não média das médias — senão o lote "
                        + "de 50 L pesaria igual ao de 400 L.",
                divide(closed.total(), closed.litros()), "/L", from, to,
                DrillDown.of("costing.batchCosts"));
        if (closed.quantidade() == 0) {
            perLiter = perLiter.withGap("nenhum custo foi fechado no período: não há do que tirar média");
        } else if (closed.incompletos() > 0) {
            perLiter = perLiter.withGap(closed.incompletos() + " dos custos fechados têm lacuna "
                    + "declarada (mão de obra, utilidade): a média é menor que a verdade");
        }
        indicators.add(perLiter);

        return List.copyOf(indicators);
    }

    private static BigDecimal divide(BigDecimal total, BigDecimal liters) {
        if (liters == null || liters.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(liters, 4, RoundingMode.HALF_UP);
    }

    private record Closed(long quantidade, BigDecimal total, BigDecimal litros, long incompletos) {}
}
