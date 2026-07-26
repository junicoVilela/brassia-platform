package br.com.brew.brassia.inventory.application.port.inbound;

import java.util.UUID;

public interface ApprovePhysicalCountUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID countId) {}

    record Result(UUID id, String status, int adjustments) {}
}
