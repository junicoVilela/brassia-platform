package br.com.brew.brassia.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Reserva estoque de um ingrediente por FEFO, com concorrência segura (STK-003). */
public interface ReserveStockUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID ingredientId, BigDecimal quantity, String unit,
            UUID reference) {}

    record Allocation(UUID lotId, BigDecimal quantity, String unit) {}

    record Result(UUID ingredientId, BigDecimal reservedQuantity, String unit, List<Allocation> allocations) {}
}
