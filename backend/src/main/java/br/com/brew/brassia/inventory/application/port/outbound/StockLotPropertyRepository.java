package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.util.List;
import java.util.UUID;

public interface StockLotPropertyRepository {
    void insert(StockLotProperty property);

    boolean existsByProperty(UUID breweryId, UUID lotId, String property);

    List<StockLotProperty> findByLot(UUID breweryId, UUID lotId);
}
