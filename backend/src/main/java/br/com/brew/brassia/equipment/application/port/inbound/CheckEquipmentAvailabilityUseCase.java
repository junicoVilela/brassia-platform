package br.com.brew.brassia.equipment.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface CheckEquipmentAvailabilityUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID equipmentId, Instant from, Instant to) {}

    record Result(UUID equipmentId, Instant from, Instant to, boolean available) {}
}
