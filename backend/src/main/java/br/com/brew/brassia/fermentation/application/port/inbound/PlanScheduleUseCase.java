package br.com.brew.brassia.fermentation.application.port.inbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Cria a linha do tempo do lote a partir de um perfil publicado (FER-004). O vínculo
 * lote↔perfil resultante é o que permite derivar o critério de estabilidade de FG do lote.
 */
public interface PlanScheduleUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID profileId, Instant start,
            UUID responsibleUserId, Integer defaultDurationDays, Integer toleranceHours) {}

    record Result(UUID id, int steps) {}
}
