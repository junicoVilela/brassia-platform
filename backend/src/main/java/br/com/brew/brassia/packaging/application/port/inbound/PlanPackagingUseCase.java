package br.com.brew.brassia.packaging.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Abre um plano de envase para um lote (PKG-001). */
public interface PlanPackagingUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, UUID batchId, UUID containerId, int plannedUnits,
            UUID lineEquipmentId, Instant plannedStart, Instant plannedEnd) {}

    record Result(UUID id, BigDecimal plannedVolumeLiters) {}
}
