package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.inventory.application.port.inbound.ListStockLotsUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListStockLotsHandler implements ListStockLotsUseCase {

    private final StockLotRepository repository;

    public ListStockLotsHandler(StockLotRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<StockLot> handle(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
