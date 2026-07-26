package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.inventory.application.port.inbound.GetStockBalanceUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockBalance;
import java.util.Objects;
import java.util.UUID;

public final class GetStockBalanceHandler implements GetStockBalanceUseCase {

    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;

    public GetStockBalanceHandler(StockLotRepository lots, StockLedgerRepository ledger) {
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public StockBalance handle(UUID breweryId, UUID lotId) {
        if (lots.findById(breweryId, lotId).isEmpty()) {
            throw new IllegalArgumentException("lote inexistente");
        }
        return ledger.balance(breweryId, lotId);
    }
}
