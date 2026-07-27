package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.Batch;
import java.util.List;
import java.util.UUID;

public interface ListBatchesUseCase {
    List<Batch> handle(UUID breweryId);
}
