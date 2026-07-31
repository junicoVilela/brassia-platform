package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.UUID;

/** Aprova ou reprova uma coleta (YST-001). Decisão humana, terminal e auditada. */
public interface ReviewYeastHarvestUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID harvestId, boolean approve, String note) {}
}
