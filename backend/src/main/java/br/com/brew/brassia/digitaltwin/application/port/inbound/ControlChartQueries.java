package br.com.brew.brassia.digitaltwin.application.port.inbound;

import br.com.brew.brassia.digitaltwin.domain.ControlLimits;
import br.com.brew.brassia.digitaltwin.domain.ControlSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Carta de controle de uma grandeza ao longo de vários lotes (SPC-001). */
public interface ControlChartQueries {

    Chart analyze(Request request);

    /**
     * @param batchIds a amostra, em ordem cronológica de quem pede. A ordem importa: sequência e tendência
     *                 só existem no tempo, e uma lista ordenada por outra coisa produziria sinais que o
     *                 processo nunca deu.
     */
    record Request(UUID breweryId, UUID recipeId, String kind, List<UUID> batchIds) {
    }

    /**
     * A carta.
     *
     * <p><strong>Os limites vêm calculados da própria série; a especificação, se existir, viaja separada e
     * nomeada.</strong> São duas linhas diferentes no gráfico com significados diferentes, e juntá-las num
     * campo só seria a confusão que esta história existe para impedir.
     */
    record Chart(
            String kind,
            String unit,
            List<Point> points,
            ControlLimits controlLimits,
            List<ControlSignal> signals,
            boolean inControl) {
    }

    record Point(UUID batchId, BigDecimal value, Instant measuredAt) {
    }
}
