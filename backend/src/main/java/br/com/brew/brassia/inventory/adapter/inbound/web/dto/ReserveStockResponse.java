package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReserveStockResponse(UUID ingredientId, BigDecimal reservedQuantity, String unit,
        List<AllocationView> allocations) {

    public record AllocationView(UUID lotId, BigDecimal quantity, String unit) {}

    public static ReserveStockResponse from(ReserveStockUseCase.Result r) {
        var allocations = r.allocations().stream()
                .map(a -> new AllocationView(a.lotId(), a.quantity(), a.unit()))
                .toList();
        return new ReserveStockResponse(r.ingredientId(), r.reservedQuantity(), r.unit(), allocations);
    }
}
