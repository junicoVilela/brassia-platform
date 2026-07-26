package br.com.brew.brassia.planning.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Verifica conflito de equipamento sem alterar estado (simulação). */
public interface SimulateScheduleUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID equipmentId, Instant scheduledStart, Instant scheduledEnd) {}

    record Conflict(UUID entryId, Instant scheduledStart, Instant scheduledEnd) {}

    record Result(boolean hasConflict, List<Conflict> conflicts) {}
}
