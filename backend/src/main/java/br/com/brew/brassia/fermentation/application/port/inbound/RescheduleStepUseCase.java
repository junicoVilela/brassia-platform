package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.ReschedulePreview;
import java.time.Instant;
import java.util.UUID;

/**
 * Move a data de uma etapa (FER-004). {@code apply=false} devolve a prévia sem gravar nada —
 * a propagação só acontece depois que o cervejeiro vê o que vai mudar.
 */
public interface RescheduleStepUseCase {
    ReschedulePreview handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID stepId, Instant newStart, boolean apply) {}
}
