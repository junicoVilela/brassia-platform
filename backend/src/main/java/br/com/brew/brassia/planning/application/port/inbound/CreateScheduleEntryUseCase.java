package br.com.brew.brassia.planning.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface CreateScheduleEntryUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID recipeId, UUID equipmentId, UUID assignedUserId,
            BigDecimal plannedVolumeLiters, Instant scheduledStart, Instant scheduledEnd) {}

    record Result(UUID id, String status) {}
}
