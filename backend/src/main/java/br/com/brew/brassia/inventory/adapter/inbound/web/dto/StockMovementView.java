package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.domain.StockMovement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementView(UUID id, String type, BigDecimal quantity, BigDecimal onHandDelta,
        BigDecimal reservedDelta, String reason, Instant occurredAt) {

    public static StockMovementView from(StockMovement m) {
        return new StockMovementView(m.id(), m.type().name(), m.quantity(), m.onHandDelta(), m.reservedDelta(),
                m.reason(), m.occurredAt());
    }
}
