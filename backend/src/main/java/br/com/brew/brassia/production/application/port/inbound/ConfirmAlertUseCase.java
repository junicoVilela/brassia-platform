package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.BatchAlert;
import java.util.UUID;

/** Confirma um alerta/ação (PRD-006): idempotente e auditado. */
public interface ConfirmAlertUseCase {
    BatchAlert handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID alertId) {}
}
