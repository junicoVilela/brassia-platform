package br.com.brew.brassia.fermentation.adapter.inbound.gateway;

import br.com.brew.brassia.fermentation.FermentationCommands;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.fermentation.domain.ReadingSource;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * A ponta da fermentação para quem tem telemetria (FER-002 / DEB-INT-001).
 *
 * <p><strong>Não abre transação própria, e é deliberado.</strong> Quem chama é a ingestão do sensor, que já
 * roda dentro de uma — e o critério do débito pedia justamente que a leitura do sensor e o ponto na curva
 * caíssem juntos. Abrir uma transação aqui criaria o estado que o débito queria evitar: telemetria gravada
 * e curva sem o ponto, ou o inverso, dependendo de qual falhasse.
 *
 * <p><strong>A origem é fixada em {@code SENSOR} aqui, não recebida.</strong> Se viesse no comando, um
 * chamador poderia gravar como {@code MANUAL} uma medição que nenhum humano fez — e a distinção entre as
 * duas é o que permite conferir uma curva suspeita contra o que alguém realmente leu no visor.
 */
@Component
class FermentationCommandsAdapter implements FermentationCommands {

    private final ReadingRepository readings;
    private final BatchLookup batches;

    FermentationCommandsAdapter(ReadingRepository readings, BatchLookup batches) {
        this.readings = Objects.requireNonNull(readings, "readings");
        this.batches = Objects.requireNonNull(batches, "batches");
    }

    @Override
    public Recorded recordSensorReading(SensorReading reading) {
        Objects.requireNonNull(reading, "reading");
        if (!batches.exists(reading.breweryId(), reading.batchId())) {
            throw new IllegalArgumentException("lote inexistente: " + reading.batchId());
        }
        var recorded = FermentationReading.record(reading.breweryId(), reading.batchId(),
                ReadingKind.of(reading.kind()), ReadingSource.SENSOR, reading.value(), reading.unit(),
                reading.measuredAt());

        var result = readings.upsertIfAbsent(recorded);
        var stored = result.stored();
        return new Recorded(stored.id(), result.created(), stored.valid());
    }
}
