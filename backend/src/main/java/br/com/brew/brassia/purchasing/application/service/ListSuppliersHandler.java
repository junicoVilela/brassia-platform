package br.com.brew.brassia.purchasing.application.service;

import br.com.brew.brassia.purchasing.application.port.inbound.ListSuppliersUseCase;
import br.com.brew.brassia.purchasing.application.port.outbound.SupplierRepository;
import br.com.brew.brassia.purchasing.domain.Supplier;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListSuppliersHandler implements ListSuppliersUseCase {

    private final SupplierRepository repository;

    public ListSuppliersHandler(SupplierRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<Supplier> handle(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
