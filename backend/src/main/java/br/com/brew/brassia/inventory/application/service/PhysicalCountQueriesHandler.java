package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.inventory.application.port.inbound.PhysicalCountQueries;
import br.com.brew.brassia.inventory.application.port.outbound.PhysicalCountRepository;
import br.com.brew.brassia.inventory.domain.PhysicalCount;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PhysicalCountQueriesHandler implements PhysicalCountQueries {

    private final PhysicalCountRepository repository;

    public PhysicalCountQueriesHandler(PhysicalCountRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public PhysicalCount get(UUID breweryId, UUID countId) {
        return repository.findById(breweryId, countId)
                .orElseThrow(() -> new IllegalArgumentException("contagem inexistente"));
    }

    @Override
    public List<PhysicalCount> list(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
