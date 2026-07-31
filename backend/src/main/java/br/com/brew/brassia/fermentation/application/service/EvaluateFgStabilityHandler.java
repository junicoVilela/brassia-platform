package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.EvaluateFgStabilityUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.domain.FgStability;
import br.com.brew.brassia.fermentation.domain.FgStabilityResult;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Objects;
import java.util.UUID;

/**
 * Avalia a estabilidade de FG (FER-003) cruzando a série de densidade do lote com o critério
 * do perfil informado. Não audita nem emite evento: é consulta, não comando — encerrar a
 * fermentação continua sendo decisão humana.
 *
 * <p>O perfil vem por parâmetro porque o vínculo persistente lote↔perfil só chega na FER-004.
 */
public final class EvaluateFgStabilityHandler implements EvaluateFgStabilityUseCase {

    private final ReadingRepository readings;
    private final ProfileRepository profiles;
    private final BatchLookup batches;

    public EvaluateFgStabilityHandler(ReadingRepository readings, ProfileRepository profiles, BatchLookup batches) {
        this.readings = Objects.requireNonNull(readings);
        this.profiles = Objects.requireNonNull(profiles);
        this.batches = Objects.requireNonNull(batches);
    }

    @Override
    public FgStabilityResult handle(UUID breweryId, UUID batchId, UUID profileId) {
        if (!batches.exists(breweryId, batchId)) {
            throw new IllegalArgumentException("lote inexistente: " + batchId);
        }
        var profile = profiles.findById(breweryId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("perfil inexistente: " + profileId));
        // Só perfil publicado governa um parecer: rascunho ainda muda debaixo da avaliação.
        if (profile.draftStatus()) {
            throw new IllegalStateException("perfil em rascunho não pode reger avaliação de estabilidade");
        }

        var series = readings.findSeries(breweryId, batchId, ReadingKind.DENSITY);
        return FgStability.evaluate(series, profile.stability());
    }
}
