package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.Batch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {
    void insert(Batch batch);

    boolean existsByOrder(UUID breweryId, UUID orderId);

    List<Batch> findAll(UUID breweryId);

    Optional<Batch> findById(UUID breweryId, UUID batchId);
}
