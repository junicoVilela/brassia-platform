package br.com.brew.brassia.quality.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registro de medição contra um ponto do plano (QLT-001). */
public final class MeasurementCommands {

    private MeasurementCommands() {
    }

    public interface Record {
        Outcome handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, UUID pointId, UUID batchId,
                UUID instrumentId, BigDecimal value, String note, Instant measuredAt) {}

        /** @param deviationId presente quando a medição saiu da faixa e abriu desvio */
        record Outcome(UUID measurementId, boolean withinSpec, UUID deviationId) {}
    }
}
