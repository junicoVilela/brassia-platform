package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotPropertyView(
        UUID id, String property, BigDecimal value, String unit, String source, String confidence,
        Instant recordedAt) {

    public static LotPropertyView from(StockLotProperty p) {
        return new LotPropertyView(p.id(), p.property(), p.measuredValue(), p.unit(), p.source().name(),
                p.confidence().name(), p.recordedAt());
    }
}
