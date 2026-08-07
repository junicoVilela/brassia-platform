package br.com.brew.brassia.quality.adapter.inbound.gateway;

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
 * O que a qualidade mostra no painel (RPT-002).
 *
 * <p>A conformidade sai com lacuna declarada quando não houve medição no período. Um percentual
 * sobre zero medições seria 100% ou 0% conforme a conta, e os dois enganam do mesmo jeito: a
 * fábrica que não mediu nada não é a fábrica que passou em tudo.
 */
@Component
class QualityIndicatorAdapter implements IndicatorSource {

    private final JdbcClient jdbc;

    QualityIndicatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to) {
        var totals = jdbc.sql("""
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE within_spec) AS within
                FROM quality_measurement
                WHERE brewery_id = :brewery AND measured_at >= :from AND measured_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new long[] {rs.getLong("total"), rs.getLong("within")})
                .single();

        var openDeviations = jdbc.sql("""
                SELECT COUNT(*) FROM quality_deviation
                WHERE brewery_id = :brewery AND status = 'OPEN'
                """)
                .param("brewery", breweryId).query(BigDecimal.class).single();

        var openNonConformities = jdbc.sql("""
                SELECT COUNT(*) FROM quality_non_conformity
                WHERE brewery_id = :brewery AND status <> 'CLOSED'
                """)
                .param("brewery", breweryId).query(BigDecimal.class).single();

        var indicators = new ArrayList<OperationalIndicator>();
        var conformity = OperationalIndicator.inPeriod("qualidade.conformidade",
                IndicatorGroup.QUALITY, "Conformidade das medições",
                "Percentual das medições do período que ficaram dentro da faixa do plano de controle "
                        + "pelo qual foram julgadas.",
                percent(totals[1], totals[0]), "%", from, to, DrillDown.of("quality.controlPlans"));
        indicators.add(totals[0] == 0
                ? conformity.withGap("não houve medição no período: o percentual não fala de nada")
                : conformity);

        indicators.add(OperationalIndicator.snapshot("qualidade.desvios_abertos", IndicatorGroup.QUALITY,
                "Desvios em aberto",
                "Desvios de faixa ainda não encerrados, de qualquer período. É foto do agora: desvio "
                        + "aberto em março continua sendo problema em agosto.",
                openDeviations, "desvios", to, DrillDown.of("quality.controlPlans")));

        indicators.add(OperationalIndicator.snapshot("qualidade.nc_abertas", IndicatorGroup.QUALITY,
                "Não conformidades em aberto",
                "NCs que ainda não chegaram a encerrada — inclui as contidas, investigadas e com plano "
                        + "de ação, porque nenhuma delas terminou.",
                openNonConformities, "NCs", to, DrillDown.of("quality.nonConformities")));

        return List.copyOf(indicators);
    }

    private static BigDecimal percent(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
