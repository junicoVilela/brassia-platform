package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListBatchesHandler implements ListBatchesUseCase {

    private final BatchRepository repository;

    public ListBatchesHandler(BatchRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<Batch> handle(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
