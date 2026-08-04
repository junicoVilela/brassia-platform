package br.com.brew.brassia.metrology.application.port.inbound;

import br.com.brew.brassia.metrology.domain.CalibrationPolicy;
import java.util.Map;
import java.util.UUID;

/** Leitura e ajuste da periodicidade de calibração por tipo (PRM-001). */
public interface CalibrationPolicyUseCase {

    CalibrationPolicy get(UUID breweryId);

    /** Substitui a política inteira; tipo ausente do mapa volta a exigir vencimento informado. */
    CalibrationPolicy replace(UUID actorId, UUID breweryId, Map<String, Integer> monthsByType);
}
