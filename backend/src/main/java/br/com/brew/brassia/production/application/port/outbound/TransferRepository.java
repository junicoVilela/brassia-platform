package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.BatchTransfer;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
    void insert(BatchTransfer transfer);

    Optional<BatchTransfer> findByBatch(UUID breweryId, UUID batchId);
}
