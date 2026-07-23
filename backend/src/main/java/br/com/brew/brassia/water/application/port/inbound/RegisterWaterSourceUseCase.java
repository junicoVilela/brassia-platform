package br.com.brew.brassia.water.application.port.inbound;

import java.util.UUID;

public interface RegisterWaterSourceUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, String name) {}

    record Result(UUID id, String code, String name, boolean active, long version) {}
}
