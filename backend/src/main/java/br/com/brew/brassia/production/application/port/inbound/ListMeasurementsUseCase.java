package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.Measurement;
import java.util.List;
import java.util.UUID;

public interface ListMeasurementsUseCase {
    List<Measurement> handle(UUID breweryId, UUID batchId);
}
