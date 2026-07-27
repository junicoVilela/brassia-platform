package br.com.brew.brassia.planning.application.port.inbound;

import java.util.UUID;

/** Reserva o estoque de todos os materiais de uma OP liberada, atomicamente (STK-003-A). */
public interface ReserveOrderMaterialsUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID orderId) {}

    record Result(UUID orderId, int reservedItems) {}
}
