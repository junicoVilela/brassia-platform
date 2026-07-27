package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.util.List;
import java.util.UUID;

public interface ListLotPropertiesUseCase {
    List<StockLotProperty> handle(UUID breweryId, UUID lotId);
}
