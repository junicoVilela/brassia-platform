package br.com.brew.brassia.fermentation.adapter.inbound.gateway;

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

/**
 * O que a fermentação mostra no painel (RPT-002).
 *
 * <p>Leitura inválida entra num indicador próprio em vez de sumir. Sensor que mandou número
 * impossível é informação sobre o sensor, e um painel que só mostrasse as leituras boas esconderia
 * exatamente o equipamento que precisa de manutenção.
 */
@Component
class FermentationIndicatorAdapter implements IndicatorSource {

    private final JdbcClient jdbc;

    FermentationIndicatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to) {
        var readings = jdbc.sql("""
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE NOT valid) AS invalid,
                       COUNT(DISTINCT batch_id) AS batches
                FROM fermentation_reading
                WHERE brewery_id = :brewery AND measured_at >= :from AND measured_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new long[] {
                        rs.getLong("total"), rs.getLong("invalid"), rs.getLong("batches")})
                .single();

        return List.of(
                OperationalIndicator.inPeriod("fermentacao.lotes_acompanhados",
                        IndicatorGroup.FERMENTATION, "Lotes com leitura",
                        "Lotes distintos que receberam ao menos uma leitura de fermentação no período. "
                                + "Não é o número de lotes fermentando: lote sem leitura nenhuma não "
                                + "aparece aqui, e é justamente o que se quer notar.",
                        BigDecimal.valueOf(readings[2]), "lotes", from, to,
                        DrillDown.of("fermentation.readings")),
                OperationalIndicator.inPeriod("fermentacao.leituras", IndicatorGroup.FERMENTATION,
                        "Leituras registradas",
                        "Todas as leituras do período — manuais e de sensor, válidas e inválidas.",
                        BigDecimal.valueOf(readings[0]), "leituras", from, to,
                        DrillDown.of("fermentation.readings")),
                OperationalIndicator.inPeriod("fermentacao.leituras_invalidas",
                        IndicatorGroup.FERMENTATION, "Leituras invalidadas",
                        "Leituras recusadas pela validação no período. É indicador do instrumento, não "
                                + "do lote: número alto aqui costuma ser sensor a calibrar.",
                        BigDecimal.valueOf(readings[1]), "leituras", from, to,
                        DrillDown.of("fermentation.readings")));
    }
}
