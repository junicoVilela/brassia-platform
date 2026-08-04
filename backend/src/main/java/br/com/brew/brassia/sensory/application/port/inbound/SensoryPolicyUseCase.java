package br.com.brew.brassia.sensory.application.port.inbound;

import br.com.brew.brassia.sensory.domain.SensoryPolicy;
import java.util.UUID;

/**
 * Leitura e ajuste da escala da ficha (PRM-001).
 *
 * <p>Mudar a escala não afeta sessão nenhuma já criada: a escala é congelada na sessão.
 */
public interface SensoryPolicyUseCase {

    SensoryPolicy get(UUID breweryId);

    SensoryPolicy update(UUID actorId, UUID breweryId, int maxScore);
}
