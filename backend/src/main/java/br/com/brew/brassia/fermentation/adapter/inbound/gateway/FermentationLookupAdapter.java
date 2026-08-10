package br.com.brew.brassia.fermentation.adapter.inbound.gateway;

import br.com.brew.brassia.fermentation.FermentationLookup;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ScheduleRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ScheduleStepStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * O retrato da fermentação para quem avalia risco (DEB-AIA-001).
 *
 * <p>Junta três fontes que respondem por coisas diferentes — leituras, agenda e levedura — e é por isso que
 * o retrato é montado aqui e não numa consulta só: cada uma tem dono e ciclo próprios, e uma junção em SQL
 * amarraria as três tabelas num formato que qualquer mudança em uma delas quebraria.
 */
@Component
class FermentationLookupAdapter implements FermentationLookup {

    private final ReadingRepository readings;
    private final ScheduleRepository schedules;
    private final YeastHarvestRepository harvests;
    private final Clock clock;

    /**
     * A construtora de produção, marcada porque há duas.
     *
     * <p>Sem {@code @Autowired} explícito o Spring não escolhe entre elas e procura a construtora padrão,
     * que não existe — o contexto inteiro deixa de subir. Falha que nenhum teste de unidade pega, porque
     * eles chamam a construtora diretamente.
     */
    @Autowired
    FermentationLookupAdapter(ReadingRepository readings, ScheduleRepository schedules,
            YeastHarvestRepository harvests) {
        this(readings, schedules, harvests, Clock.systemUTC());
    }

    FermentationLookupAdapter(ReadingRepository readings, ScheduleRepository schedules,
            YeastHarvestRepository harvests, Clock clock) {
        this.readings = Objects.requireNonNull(readings, "readings");
        this.schedules = Objects.requireNonNull(schedules, "schedules");
        this.harvests = Objects.requireNonNull(harvests, "harvests");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Snapshot> ofBatch(UUID breweryId, UUID batchId) {
        var latest = readings.latestOf(breweryId, batchId);
        var schedule = schedules.findByBatch(breweryId, batchId);
        var yeast = harvests.findPitchedInto(breweryId, batchId).map(h -> h.generation());

        // "A fermentação não conhece este lote" é resposta diferente de "conhece e não há o que contar".
        // Sem esta guarda, um lote que nem chegou ao fermentador viria com zeros — e zero de etapa atrasada
        // num lote sem agenda leria como lote em dia.
        if (latest.count() == 0 && schedule.isEmpty() && yeast.isEmpty()) {
            return Optional.empty();
        }

        var agora = clock.instant();
        int total = schedule.map(s -> s.steps().size()).orElse(0);
        int executadas = schedule
                .map(s -> (int) s.steps().stream()
                        .filter(step -> step.status() == ScheduleStepStatus.DONE).count())
                .orElse(0);
        int atrasadas = schedule.map(s -> s.lateSteps(agora).size()).orElse(0);

        return Optional.of(new Snapshot(latest.count(), measurement(latest.density()),
                measurement(latest.temperature()), executadas, total, atrasadas, yeast.orElse(null)));
    }

    private static Measurement measurement(FermentationReading reading) {
        return reading == null ? null
                : new Measurement(reading.value(), reading.unit(), reading.measuredAt());
    }
}
