package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.UUID;

public interface ReleaseCycleUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID cycleId) {}
}
