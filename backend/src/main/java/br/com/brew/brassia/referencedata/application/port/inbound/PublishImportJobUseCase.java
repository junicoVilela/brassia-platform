package br.com.brew.brassia.referencedata.application.port.inbound;

import java.util.UUID;

/** Publica um job em revisão, materializando um reference_dataset publicado. */
public interface PublishImportJobUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID jobId) {}

    record Result(UUID jobId, String status, UUID datasetId) {}
}
