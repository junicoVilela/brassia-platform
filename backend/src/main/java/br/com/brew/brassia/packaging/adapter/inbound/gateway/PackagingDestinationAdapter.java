package br.com.brew.brassia.packaging.adapter.inbound.gateway;

import br.com.brew.brassia.packaging.application.port.outbound.ShipmentRepository;
import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Os destinos que o envase conhece (TRC-001-D): as expedições dos lotes de produto acabado que
 * estiverem no escopo do recall.
 *
 * <p>Só olha nós de produto acabado — é de lá que a cerveja sai. Nó de outro tipo não contribui
 * destino nenhum, e é assim que a lacuna aparece como lacuna em vez de virar uma junção vazia.
 */
@Component
class PackagingDestinationAdapter implements DestinationSource {

    private final ShipmentRepository shipments;

    PackagingDestinationAdapter(ShipmentRepository shipments) {
        this.shipments = Objects.requireNonNull(shipments);
    }

    @Override
    public List<Destination> destinationsOf(UUID breweryId, List<Node> scope) {
        var lots = new HashMap<UUID, Node>();
        for (Node node : scope) {
            if (node.type() == NodeType.FINISHED_LOT) {
                lots.put(node.id(), node);
            }
        }
        if (lots.isEmpty()) {
            return List.of();
        }
        return shipments.findByLots(breweryId, List.copyOf(lots.keySet())).stream()
                .map(shipment -> new Destination(shipment.id(),
                        lots.getOrDefault(shipment.finishedLotId(),
                                Node.of(NodeType.FINISHED_LOT, shipment.finishedLotId())),
                        shipment.destination(), shipment.contact(), shipment.units()))
                .toList();
    }
}
