package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import br.com.brew.brassia.planning.application.port.inbound.ListScheduleEntriesUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Entrada da agenda para o calendário. */
public record ScheduleEntryView(
        UUID id,
        UUID recipeId,
        UUID equipmentId,
        UUID assignedUserId,
        BigDecimal plannedVolumeLiters,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status) {

    public static ScheduleEntryView from(ListScheduleEntriesUseCase.Item item) {
        return new ScheduleEntryView(item.id(), item.recipeId(), item.equipmentId(), item.assignedUserId(),
                item.plannedVolumeLiters(), item.scheduledStart(), item.scheduledEnd(), item.status());
    }
}
