package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.ListMeasurementsUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.domain.Measurement;
import br.com.brew.brassia.production.domain.UnknownBatchException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListMeasurementsHandler implements ListMeasurementsUseCase {

    private final BatchRepository batches;
    private final MeasurementRepository measurements;

    public ListMeasurementsHandler(BatchRepository batches, MeasurementRepository measurements) {
        this.batches = Objects.requireNonNull(batches);
        this.measurements = Objects.requireNonNull(measurements);
    }

    @Override
    public List<Measurement> handle(UUID breweryId, UUID batchId) {
        batches.findById(breweryId, batchId)
                .orElseThrow(() -> new UnknownBatchException(batchId));
        return measurements.findByBatch(breweryId, batchId);
    }
}
