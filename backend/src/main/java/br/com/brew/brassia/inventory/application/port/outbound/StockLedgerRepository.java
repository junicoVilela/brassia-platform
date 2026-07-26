package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.StockBalance;
import br.com.brew.brassia.inventory.domain.StockMovement;
import java.util.List;
import java.util.UUID;

/** Ledger append-only de estoque; o saldo é sempre derivado dos movimentos. */
public interface StockLedgerRepository {
    void append(StockMovement movement);

    StockBalance balance(UUID breweryId, UUID lotId);

    List<StockMovement> findByLot(UUID breweryId, UUID lotId);
}
