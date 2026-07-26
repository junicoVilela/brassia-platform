package br.com.brew.brassia.planning.application.port.inbound;

import java.util.UUID;

public interface ReleaseBrewOrderUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID orderId, UUID assignedUserId) {}

    record Result(UUID id, String status) {}
}
