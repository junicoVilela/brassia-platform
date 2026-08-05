package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.packaging.application.port.inbound.ShipmentUseCases;
import br.com.brew.brassia.packaging.application.port.outbound.ShipmentRepository;
import br.com.brew.brassia.packaging.domain.Shipment;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Leituras da expedição (TRC-001-D). */
public final class ShipmentQueriesHandler implements ShipmentUseCases.Queries {

    private final ShipmentRepository shipments;

    public ShipmentQueriesHandler(ShipmentRepository shipments) {
        this.shipments = Objects.requireNonNull(shipments);
    }

    @Override
    public List<Shipment> byLot(UUID breweryId, UUID finishedLotId) {
        return shipments.findByLot(breweryId, finishedLotId);
    }

    @Override
    public List<Shipment> all(UUID breweryId) {
        return shipments.findAll(breweryId);
    }
}
