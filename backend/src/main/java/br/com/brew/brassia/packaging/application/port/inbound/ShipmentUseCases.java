package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.Shipment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Expedição do lote de produto acabado (TRC-001-D). */
public interface ShipmentUseCases {

    interface Record {
        Shipment handle(Command command);

        record Command(UUID breweryId, UUID actorId, UUID finishedLotId, String destination, String contact,
                int units, LocalDate shippedOn, String note) {}
    }

    /**
     * Estorno da expedição registrada errada (FDS-003-A).
     *
     * <p>Não é devolução nem transferência entre destinos — essas são movimentação comercial e dependem
     * de cliente e pedido. É o caminho de volta de um registro errado, que hoje não existe e contamina o
     * recall.
     */
    interface Reverse {
        Shipment handle(Command command);

        record Command(UUID breweryId, UUID actorId, UUID shipmentId, String reason) {}
    }

    interface Queries {
        List<Shipment> byLot(UUID breweryId, UUID finishedLotId);

        List<Shipment> all(UUID breweryId);
    }
}
