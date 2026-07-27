package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.inventory.application.port.inbound.ListLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotPropertyRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListLotPropertiesHandler implements ListLotPropertiesUseCase {

    private final StockLotRepository lots;
    private final StockLotPropertyRepository properties;

    public ListLotPropertiesHandler(StockLotRepository lots, StockLotPropertyRepository properties) {
        this.lots = Objects.requireNonNull(lots);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<StockLotProperty> handle(UUID breweryId, UUID lotId) {
        lots.findById(breweryId, lotId)
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        return properties.findByLot(breweryId, lotId);
    }
}
