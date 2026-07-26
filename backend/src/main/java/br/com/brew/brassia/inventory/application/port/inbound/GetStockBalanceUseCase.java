package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.StockBalance;
import java.util.UUID;

public interface GetStockBalanceUseCase {
    StockBalance handle(UUID breweryId, UUID lotId);
}
