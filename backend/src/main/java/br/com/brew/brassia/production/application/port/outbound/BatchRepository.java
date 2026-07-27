package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.Batch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {
    void insert(Batch batch);

    boolean existsByOrder(UUID breweryId, UUID orderId);

    List<Batch> findAll(UUID breweryId);

    Optional<Batch> findById(UUID breweryId, UUID batchId);

    /**
     * Conclui a etapa ATIVA e ativa a próxima (PRD-002), atômico e guardado pelo
     * estado. Retorna {@code false} se a etapa não estava ativa (fora de ordem).
     */
    boolean completeStep(UUID breweryId, UUID batchId, UUID stepId, UUID nextStepId, Instant at);
}
