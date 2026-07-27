package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.BatchAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {
    void insert(BatchAlert alert);

    List<BatchAlert> findByBatch(UUID breweryId, UUID batchId);

    Optional<BatchAlert> findById(UUID breweryId, UUID alertId);

    /**
     * Confirma um alerta PENDENTE (idempotência guardada pelo estado). Retorna
     * {@code false} se já não estava pendente (já confirmado).
     */
    boolean markConfirmed(UUID breweryId, UUID alertId, Instant at, UUID by);
}
