package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.Batch;
import java.util.UUID;

/** Conclui a etapa ativa do lote e ativa a próxima (PRD-002). */
public interface CompleteBatchStepUseCase {
    Batch handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID stepId) {}
}
