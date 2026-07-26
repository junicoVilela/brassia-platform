package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.StockLot;
import java.util.List;
import java.util.UUID;

public interface ListStockLotsUseCase {
    List<StockLot> handle(UUID breweryId);
}
