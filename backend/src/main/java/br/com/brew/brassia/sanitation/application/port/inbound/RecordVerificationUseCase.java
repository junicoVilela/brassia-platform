package br.com.brew.brassia.sanitation.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecordVerificationUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID cycleId, boolean rinseOk, boolean visualOk,
            BigDecimal atpRlu, BigDecimal atpThreshold, boolean microOk) {}
}
