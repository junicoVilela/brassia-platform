package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.Shipment;
import java.util.List;
import java.util.UUID;

/** Persistência das expedições (TRC-001-D). */
public interface ShipmentRepository {

    void insert(Shipment shipment);

    List<Shipment> findByLot(UUID breweryId, UUID finishedLotId);

    List<Shipment> findAll(UUID breweryId);

    /** Usada pelo recall via porta publicada: os destinos de um conjunto de lotes, de uma vez. */
    List<Shipment> findByLots(UUID breweryId, List<UUID> finishedLotIds);

    /** Unidades já expedidas do lote — é contra elas que a próxima saída é conferida. */
    int shippedUnits(UUID breweryId, UUID finishedLotId);
}
