package br.com.brew.brassia.fermentation.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ingestão de leitura de fermentação (FER-002), idempotente pela chave natural. */
public interface RecordReadingUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, String kind, String source, BigDecimal value,
            String unit, Instant measuredAt) {}

    record Result(UUID id, boolean created, boolean valid, String invalidReason) {}
}
