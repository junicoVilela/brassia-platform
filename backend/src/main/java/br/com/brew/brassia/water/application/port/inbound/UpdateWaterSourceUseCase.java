package br.com.brew.brassia.water.application.port.inbound;

import java.util.UUID;

public interface UpdateWaterSourceUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID sourceId, String name, long version) {}

    record Result(UUID id, String code, String name, boolean active, long version) {}
}
