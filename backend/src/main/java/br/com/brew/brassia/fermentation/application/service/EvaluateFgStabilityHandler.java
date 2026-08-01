package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.EvaluateFgStabilityUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ScheduleRepository;
import br.com.brew.brassia.fermentation.domain.FgStability;
import br.com.brew.brassia.fermentation.domain.FgStabilityResult;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Objects;
import java.util.UUID;

/**
 * Avalia a estabilidade de FG (FER-003) cruzando a série de densidade do lote com o critério
 * do perfil que rege a agenda do lote. Não audita nem emite evento: é consulta, não comando —
 * encerrar a fermentação continua sendo decisão humana.
 *
 * <p>Desde a FER-004 o perfil é <strong>derivado do lote</strong> (via agenda), e não mais
 * recebido por parâmetro — o que encerra o débito FER-003-1.
 */
public final class EvaluateFgStabilityHandler implements EvaluateFgStabilityUseCase {

    private final ReadingRepository readings;
    private final ProfileRepository profiles;
    private final ScheduleRepository schedules;
    private final BatchLookup batches;

    public EvaluateFgStabilityHandler(ReadingRepository readings, ProfileRepository profiles,
            ScheduleRepository schedules, BatchLookup batches) {
        this.readings = Objects.requireNonNull(readings);
        this.profiles = Objects.requireNonNull(profiles);
        this.schedules = Objects.requireNonNull(schedules);
        this.batches = Objects.requireNonNull(batches);
    }

    @Override
    public FgStabilityResult handle(UUID breweryId, UUID batchId) {
        if (!batches.exists(breweryId, batchId)) {
            throw new IllegalArgumentException("lote inexistente: " + batchId);
        }
        // O perfil vem da agenda do lote; sem agenda não há critério a aplicar.
        var schedule = schedules.findByBatch(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "lote sem agenda de fermentação: planeje a agenda para definir o critério de FG"));
        var profile = profiles.findById(breweryId, schedule.profileId())
                .orElseThrow(() -> new IllegalArgumentException("perfil da agenda inexistente"));

        var series = readings.findSeries(breweryId, batchId, ReadingKind.DENSITY);
        return FgStability.evaluate(series, profile.stability());
    }
}
