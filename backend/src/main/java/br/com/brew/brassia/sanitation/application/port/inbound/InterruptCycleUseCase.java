package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.UUID;

public interface InterruptCycleUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID cycleId, String reason) {}
}
