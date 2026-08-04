package br.com.brew.brassia.quality.application.port.inbound;

import br.com.brew.brassia.quality.domain.CapaPolicy;
import java.util.Map;
import java.util.UUID;

/** Leitura e ajuste dos prazos do CAPA por severidade (PRM-001). */
public interface CapaPolicyUseCase {

    CapaPolicy get(UUID breweryId);

    /** Substitui a política inteira; severidade ausente volta a exigir prazos informados. */
    CapaPolicy replace(UUID actorId, UUID breweryId, Map<String, Deadlines> bySeverity);

    record Deadlines(int containmentDays, int investigationDays, int verificationDays) {}
}
