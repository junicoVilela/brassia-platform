package br.com.brew.brassia.planning.application.port.inbound;

import java.util.UUID;

/** Inicia a produção de uma OP liberada (PRD-001): transição única + evento. */
public interface StartBrewOrderUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID orderId) {}

    record Result(UUID orderId, String status) {}
}
