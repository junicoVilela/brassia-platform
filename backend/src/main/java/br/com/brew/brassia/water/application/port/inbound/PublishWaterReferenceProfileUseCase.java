package br.com.brew.brassia.water.application.port.inbound;

import java.util.UUID;

public interface PublishWaterReferenceProfileUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID id) {}

    record Result(String status) {}
}
