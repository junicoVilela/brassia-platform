package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.domain.Measurement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeasurementView(
        UUID id, UUID stepId, String kind, BigDecimal value, String unit, BigDecimal temperatureC, String method,
        String source, Instant recordedAt, UUID recordedBy) {

    public static MeasurementView from(Measurement m) {
        return new MeasurementView(m.id(), m.stepId(), m.kind().name(), m.value(), m.unit(), m.temperatureC(),
                m.method(), m.source().name(), m.recordedAt(), m.recordedBy());
    }
}
