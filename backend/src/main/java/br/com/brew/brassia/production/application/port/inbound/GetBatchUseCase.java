package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.Batch;
import java.util.UUID;

public interface GetBatchUseCase {
    Batch handle(UUID breweryId, UUID batchId);
}
