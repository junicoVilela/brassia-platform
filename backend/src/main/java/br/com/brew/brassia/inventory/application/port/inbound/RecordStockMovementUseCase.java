package br.com.brew.brassia.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Registra um movimento manual no ledger (consumo, devolução, perda, ajuste). */
public interface RecordStockMovementUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID lotId, String type, BigDecimal quantity, String reason) {}

    record Result(UUID movementId, BigDecimal onHand, BigDecimal available) {}
}
