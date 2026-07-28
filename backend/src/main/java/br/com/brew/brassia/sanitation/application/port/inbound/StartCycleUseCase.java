package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.UUID;

public interface StartCycleUseCase {
    UUID handle(Command command);

    record Command(UUID actorId, UUID breweryId, String procedureCode, UUID equipmentId) {}
}
