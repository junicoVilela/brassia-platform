package br.com.brew.brassia.digitaltwin.application.service;

import br.com.brew.brassia.digitaltwin.application.port.inbound.ControlChartQueries;
import br.com.brew.brassia.digitaltwin.domain.ControlLimits;
import br.com.brew.brassia.digitaltwin.domain.ControlSignal;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchMeasurementLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monta a carta de controle de uma grandeza (SPC-001).
 *
 * <p><strong>A série é montada na ordem em que as medições aconteceram, atravessando os lotes.</strong> Uma
 * carta de controle é sobre o <em>processo</em>, não sobre um lote: o que ela procura é o momento em que o
 * processo mudou, e esse momento cai entre dois lotes tanto quanto dentro de um.
 *
 * <p><strong>Este serviço não guarda nada.</strong> Uma carta é uma leitura da série que já existe — as
 * medições são o registro, e elas estão na produção. Persistir a carta criaria uma cópia que envelhece: uma
 * medição corrigida amanhã deixaria a carta de hoje afirmando um limite que os dados não sustentam mais.
 * Pelo mesmo motivo não há versão: recalcular é barato e sempre reflete o que se sabe agora.
 */
public final class ControlChartService implements ControlChartQueries {

    private final BatchLookup batches;
    private final BatchMeasurementLookup measurements;

    public ControlChartService(BatchLookup batches, BatchMeasurementLookup measurements) {
        this.batches = Objects.requireNonNull(batches, "batches");
        this.measurements = Objects.requireNonNull(measurements, "measurements");
    }

    @Override
    public Chart analyze(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.batchIds() == null || request.batchIds().isEmpty()) {
            throw new IllegalArgumentException("informe ao menos um lote");
        }
        if (request.kind() == null || request.kind().isBlank()) {
            throw new IllegalArgumentException("informe a grandeza");
        }

        var points = new ArrayList<Point>();
        String unit = null;

        for (var batchId : request.batchIds()) {
            // Cada lote é resolvido dentro da cervejaria pela consulta publicada: lote alheio não resolve.
            var batch = batches.find(request.breweryId(), batchId).orElse(null);
            if (batch == null || !request.recipeId().equals(batch.recipeId())) {
                continue;
            }
            for (var reading : measurements.ofBatch(request.breweryId(), batchId, request.kind())) {
                if (unit == null) {
                    unit = reading.unit();
                } else if (!unit.equals(reading.unit())) {
                    // Misturar °C e °F na mesma carta produziria limites que não descrevem nada. Recusar é
                    // melhor que converter em silêncio: a conversão pertence a quem registrou a medição.
                    throw new MixedUnitsException(request.kind(), unit, reading.unit());
                }
                points.add(new Point(batchId, reading.value(), reading.measuredAt()));
            }
        }

        // A ordenação é por instante de medição, atravessando os lotes: é a ordem em que o processo
        // aconteceu, e é a única em que sequência e tendência significam alguma coisa.
        points.sort(java.util.Comparator.comparing(Point::measuredAt));

        var values = points.stream().map(Point::value).toList();
        var limits = ControlLimits.from(values);
        var signals = ControlSignal.detect(values, limits);

        return new Chart(request.kind(), unit, List.copyOf(points), limits, signals, signals.isEmpty());
    }

    /** A série tem unidades diferentes; não há carta possível. */
    public static final class MixedUnitsException extends RuntimeException {

        private final String kind;

        MixedUnitsException(String kind, String first, String other) {
            super("a série de " + kind + " mistura " + first + " e " + other);
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }
    }
}
