package br.com.brew.brassia.planning.application.port.inbound;

import java.util.UUID;

public interface CancelBrewOrderUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID orderId, String reason) {}

    record Result(UUID id, String status) {}
}
