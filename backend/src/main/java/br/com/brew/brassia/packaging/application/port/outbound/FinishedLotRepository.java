package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.FinishedLot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinishedLotRepository {

    void insert(FinishedLot lot);

    List<FinishedLot> findAll(UUID breweryId);

    Optional<FinishedLot> findById(UUID breweryId, UUID id);

    List<FinishedLot> findByBatch(UUID breweryId, UUID batchId);

    Optional<FinishedLot> findByRun(UUID breweryId, UUID runId);

    /** Quantos envases o lote de produção já teve — é a ordem do próximo, a partir de 1. */
    int countByBatch(UUID breweryId, UUID batchId);
}
