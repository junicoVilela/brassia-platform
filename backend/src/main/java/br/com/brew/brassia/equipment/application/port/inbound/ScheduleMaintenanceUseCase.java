package br.com.brew.brassia.equipment.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

public interface ScheduleMaintenanceUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID equipmentId, String kind, String instrument,
            Instant startAt, Instant endAt, String notes) {}

    record Result(UUID id, UUID equipmentId, String kind, String instrument, Instant startAt, Instant endAt,
            String notes, String status, long version) {}
}
