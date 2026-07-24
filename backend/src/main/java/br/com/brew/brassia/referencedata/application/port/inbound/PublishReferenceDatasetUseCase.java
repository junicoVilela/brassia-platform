package br.com.brew.brassia.referencedata.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

public interface PublishReferenceDatasetUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID datasetId) {}

    record Result(UUID id, String status, Instant publishedAt) {}
}
