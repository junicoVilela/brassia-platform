package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.domain.PhysicalCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PhysicalCountView(UUID id, String status, Instant createdAt, Instant approvedAt,
        List<LineView> lines) {

    public record LineView(UUID lotId, UUID ingredientId, String unit, BigDecimal countedQuantity,
            BigDecimal systemQuantity, BigDecimal difference) {}

    public static PhysicalCountView from(PhysicalCount c) {
        var lines = c.lines().stream()
                .map(l -> new LineView(l.lotId(), l.ingredientId(), l.unit().name(), l.countedQuantity(),
                        l.systemQuantity(), l.difference()))
                .toList();
        return new PhysicalCountView(c.id().value(), c.status().name(), c.createdAt(), c.approvedAt(), lines);
    }
}
