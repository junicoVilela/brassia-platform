package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.inventory.application.port.inbound.ListStockMovementsUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.domain.StockMovement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListStockMovementsHandler implements ListStockMovementsUseCase {

    private final StockLedgerRepository ledger;

    public ListStockMovementsHandler(StockLedgerRepository ledger) {
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public List<StockMovement> handle(UUID breweryId, UUID lotId) {
        return ledger.findByLot(breweryId, lotId);
    }
}
