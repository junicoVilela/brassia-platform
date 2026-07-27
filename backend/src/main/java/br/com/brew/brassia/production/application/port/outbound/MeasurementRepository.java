package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.Measurement;
import java.util.List;
import java.util.UUID;

public interface MeasurementRepository {
    void insert(Measurement measurement);

    List<Measurement> findByBatch(UUID breweryId, UUID batchId);
}
