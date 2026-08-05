package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.Shipment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos da expedição (TRC-001-D). */
public final class ShipmentDtos {

    private ShipmentDtos() {
    }

    public record RecordShipmentRequest(@NotNull UUID finishedLotId,
            @NotBlank @Size(max = 200) String destination,
            @Size(max = 200) String contact,
            @Min(1) int units,
            @NotNull LocalDate shippedOn,
            @Size(max = 500) String note) {}

    /**
     * @param contact pode vir nulo — destino sem contato é lacuna que o recall mostra, não esconde
     */
    public record ShipmentView(UUID id, UUID finishedLotId, String destination, String contact, int units,
            LocalDate shippedOn, String note) {

        public static ShipmentView from(Shipment shipment) {
            return new ShipmentView(shipment.id(), shipment.finishedLotId(), shipment.destination(),
                    shipment.contact(), shipment.units(), shipment.shippedOn(), shipment.note());
        }

        public static List<ShipmentView> from(List<Shipment> shipments) {
            return shipments.stream().map(ShipmentView::from).toList();
        }
    }
}
