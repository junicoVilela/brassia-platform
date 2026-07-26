package br.com.brew.brassia.inventory.application.port.inbound;

import java.util.UUID;

/** Libera as reservas associadas a uma referência (ex.: OP cancelada). */
public interface ReleaseStockUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID reference) {}

    record Result(UUID reference, int releasedLots) {}
}
