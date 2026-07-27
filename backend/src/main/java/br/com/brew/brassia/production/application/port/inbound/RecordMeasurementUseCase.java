package br.com.brew.brassia.production.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/** Registra uma medição imutável no lote (PRD-003). */
public interface RecordMeasurementUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID stepId, String kind, BigDecimal value,
            String unit, BigDecimal temperatureC, String method, String source) {}

    record Result(UUID id) {}
}
