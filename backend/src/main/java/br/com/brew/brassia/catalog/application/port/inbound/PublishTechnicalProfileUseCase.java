package br.com.brew.brassia.catalog.application.port.inbound;

import java.util.UUID;

public interface PublishTechnicalProfileUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID ingredientId) {}

    record Result(String status) {}
}
