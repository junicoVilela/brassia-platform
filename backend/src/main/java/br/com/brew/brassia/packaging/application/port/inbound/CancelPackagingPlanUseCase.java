package br.com.brew.brassia.packaging.application.port.inbound;

import java.util.UUID;

/** Cancela o plano e devolve a embalagem reservada ao estoque (PKG-001). */
public interface CancelPackagingPlanUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID planId, String reason) {}
}
