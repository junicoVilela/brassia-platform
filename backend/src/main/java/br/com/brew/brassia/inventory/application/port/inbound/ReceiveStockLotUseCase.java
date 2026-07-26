package br.com.brew.brassia.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ReceiveStockLotUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID ingredientId, UUID supplierId, String supplierLotCode,
            BigDecimal quantity, String unit, BigDecimal unitCost, LocalDate expiryDate, String inspection) {}

    record Result(UUID id, boolean available) {}
}
