package br.com.brew.brassia.equipment.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListEquipmentMaintenanceUseCase {
    List<Window> handle(Query query);

    record Query(UUID breweryId, UUID equipmentId) {}

    record Window(UUID id, UUID equipmentId, String kind, String instrument, Instant startAt, Instant endAt,
            String notes, String status, long version) {}
}
