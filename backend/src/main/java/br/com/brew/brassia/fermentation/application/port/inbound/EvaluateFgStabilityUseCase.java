package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FgStabilityResult;
import java.util.UUID;

/**
 * Avalia a estabilidade de FG de um lote (FER-003) sob o critério de um perfil publicado.
 * É consulta: emite um parecer explicável e não encerra a fermentação.
 */
public interface EvaluateFgStabilityUseCase {
    FgStabilityResult handle(UUID breweryId, UUID batchId, UUID profileId);
}
