package br.com.brew.brassia.production.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

/** Cria um item na central de alertas/ações do lote (PRD-006). */
public interface CreateAlertUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, String kind, String message, Instant plannedAt,
            Instant occurredAt) {}

    record Result(UUID id) {}
}
