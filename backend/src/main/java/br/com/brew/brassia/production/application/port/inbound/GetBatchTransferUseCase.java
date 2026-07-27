package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.BatchTransfer;
import java.util.Optional;
import java.util.UUID;

public interface GetBatchTransferUseCase {
    Optional<BatchTransfer> handle(UUID breweryId, UUID batchId);
}
