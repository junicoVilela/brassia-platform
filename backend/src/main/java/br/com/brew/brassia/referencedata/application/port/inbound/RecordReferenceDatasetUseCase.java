package br.com.brew.brassia.referencedata.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra (em rascunho) um dataset de uma fonte. O checksum é calculado do
 * payload bruto; enviar o mesmo conteúdo é idempotente (retorna o existente).
 */
public interface RecordReferenceDatasetUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID sourceId, String datasetVersion, String rawPayload,
            String sourceSystem, String sourceRecordId, String sourceUrl, Instant retrievedAt, Instant effectiveFrom,
            Instant effectiveTo) {}

    record Result(UUID id, String checksum, boolean created) {}
}
