package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.StockMovement;
import java.util.List;
import java.util.UUID;

public interface ListStockMovementsUseCase {
    List<StockMovement> handle(UUID breweryId, UUID lotId);
}
