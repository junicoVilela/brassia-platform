package br.com.brew.brassia.referencedata.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

public interface PublishStyleSetUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID styleSetId) {}

    record Result(UUID id, String status, Instant publishedAt) {}
}
