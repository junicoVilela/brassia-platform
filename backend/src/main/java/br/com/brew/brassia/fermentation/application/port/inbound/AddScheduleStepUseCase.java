package br.com.brew.brassia.fermentation.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Acrescenta uma etapa específica do lote (dry hop, cold crash, transferência). */
public interface AddScheduleStepUseCase {
    UUID handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, String name, String action, String condition,
            Integer conditionDays, BigDecimal targetGravity, Instant plannedStart, Instant plannedEnd,
            int toleranceHours, UUID responsibleUserId, boolean dependsOnPrevious) {}
}
