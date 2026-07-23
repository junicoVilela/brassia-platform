package br.com.brew.brassia.equipment.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface CancelMaintenanceUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID equipmentId, UUID maintenanceId) {}
}
