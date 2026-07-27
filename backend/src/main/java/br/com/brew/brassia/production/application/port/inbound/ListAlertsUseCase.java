package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.BatchAlert;
import java.util.List;
import java.util.UUID;

public interface ListAlertsUseCase {
    List<BatchAlert> handle(UUID breweryId, UUID batchId);
}
