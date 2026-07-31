package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.FermentationReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReadingView(
        UUID id, UUID batchId, String kind, String source, BigDecimal value, String unit, Instant measuredAt,
        boolean valid, String invalidReason) {

    public static ReadingView from(FermentationReading r) {
        return new ReadingView(r.id(), r.batchId(), r.kind().name(), r.source().name(), r.value(), r.unit(),
                r.measuredAt(), r.valid(), r.invalidReason());
    }
}
