package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.domain.StockLot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockLotView(UUID id, UUID ingredientId, UUID supplierId, String supplierLotCode,
        BigDecimal receivedQuantity, String unit, BigDecimal unitCost, LocalDate expiryDate, String inspection,
        boolean available) {

    public static StockLotView from(StockLot l) {
        return new StockLotView(l.id().value(), l.ingredientId(), l.supplierId(), l.supplierLotCode(),
                l.receivedQuantity(), l.unit().name(), l.unitCost(), l.expiryDate(), l.inspection().name(),
                l.available());
    }
}
