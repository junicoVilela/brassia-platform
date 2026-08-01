package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FgStabilityResult;
import java.util.UUID;

/**
 * Avalia a estabilidade de FG de um lote (FER-003) sob o critério do perfil que rege a
 * agenda do lote (FER-004). É consulta: emite um parecer explicável e não encerra a
 * fermentação.
 */
public interface EvaluateFgStabilityUseCase {
    FgStabilityResult handle(UUID breweryId, UUID batchId);
}
