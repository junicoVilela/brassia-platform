package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.AppliedCorrection;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Aplica (registra a decisão de) uma correção no lote (CAL-002): evento + planejado vs realizado. */
public interface ApplyCorrectionUseCase {
    AppliedCorrection handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, String calculator, Map<String, BigDecimal> inputs,
            UUID sourceMeasurementId, String note, BigDecimal realizedValue) {}
}
