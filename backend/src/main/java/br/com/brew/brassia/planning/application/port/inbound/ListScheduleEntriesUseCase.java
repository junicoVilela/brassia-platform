package br.com.brew.brassia.planning.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListScheduleEntriesUseCase {
    List<Item> handle(Query query);

    record Query(UUID breweryId, Instant from, Instant to) {}

    record Item(UUID id, UUID recipeId, UUID equipmentId, UUID assignedUserId, BigDecimal plannedVolumeLiters,
            Instant scheduledStart, Instant scheduledEnd, String status) {}
}
