package br.com.brew.brassia.sanitation.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecordStepUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID cycleId, int sequence, BigDecimal measuredConcentrationPct,
            BigDecimal measuredTempC, Integer measuredTimeMinutes, String flow, String evidence,
            String outOfOrderReason, boolean override, String overrideReason) {}
}
