package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.util.List;
import java.util.UUID;

public interface ListLotPropertiesUseCase {
    /** @param history true retorna todas as revisões; false, só a atual de cada propriedade (STK-005-A) */
    List<StockLotProperty> handle(UUID breweryId, UUID lotId, boolean history);
}
