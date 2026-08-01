package br.com.brew.brassia.fermentation.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra a execução de uma etapa (FER-004). O planejado permanece; desvio e justificativa
 * entram ao lado dele no histórico.
 */
public interface ExecuteScheduleStepUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID stepId, Instant executedAt,
            String justification) {}
}
