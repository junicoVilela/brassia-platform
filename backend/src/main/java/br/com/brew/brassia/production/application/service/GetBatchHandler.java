package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import java.util.Objects;
import java.util.UUID;

public final class GetBatchHandler implements GetBatchUseCase {

    private final BatchRepository repository;

    public GetBatchHandler(BatchRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Batch handle(UUID breweryId, UUID batchId) {
        return repository.findById(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
    }
}
