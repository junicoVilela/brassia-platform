package br.com.brew.brassia.sanitation.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecordConsumptionUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID cycleId, BigDecimal waterLiters, BigDecimal energyKwh,
            BigDecimal productKg) {}
}
