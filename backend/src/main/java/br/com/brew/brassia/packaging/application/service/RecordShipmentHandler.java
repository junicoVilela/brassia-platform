package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.application.port.inbound.ShipmentUseCases;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.application.port.outbound.ShipmentRepository;
import br.com.brew.brassia.packaging.domain.PackagingBlockedException;
import br.com.brew.brassia.packaging.domain.Shipment;
import br.com.brew.brassia.packaging.domain.ShipmentExceedsLotException;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.QuarantineCheck;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registra a saída de um lote de produto acabado (TRC-001-D).
 *
 * <p>Duas recusas, e as duas existem por causa do recall. A primeira: <strong>não se expede mais do
 * que o lote tem</strong> — um destino com unidades inventadas faria o recall procurar caixas que
 * nunca saíram, e a soma das expedições é o que ele usa para dizer quanto está na rua. A segunda:
 * <strong>lote em quarentena não sai</strong> (FDS-002), que é a metade da contenção que faltava —
 * até aqui a quarentena impedia envasar, e deixava passar justamente o embarque.
 */
public final class RecordShipmentHandler implements ShipmentUseCases.Record {

    private final ShipmentRepository shipments;
    private final FinishedLotRepository finishedLots;
    private final QuarantineCheck quarantines;
    private final AuditTrail audit;

    public RecordShipmentHandler(ShipmentRepository shipments, FinishedLotRepository finishedLots,
            QuarantineCheck quarantines, AuditTrail audit) {
        this.shipments = Objects.requireNonNull(shipments);
        this.finishedLots = Objects.requireNonNull(finishedLots);
        this.quarantines = Objects.requireNonNull(quarantines);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Shipment handle(Command command) {
        var lot = finishedLots.findById(command.breweryId(), command.finishedLotId())
                .orElseThrow(() -> new IllegalArgumentException("lote de produto acabado inexistente"));

        quarantines.blocking(command.breweryId(), NodeType.FINISHED_LOT, lot.id())
                .ifPresent(block -> {
                    throw new PackagingBlockedException(List.of(
                            new PackagingBlockedException.Blocker(block.code(), block.message())));
                });

        var alreadyShipped = shipments.shippedUnits(command.breweryId(), lot.id());
        if (alreadyShipped + command.units() > lot.units()) {
            throw new ShipmentExceedsLotException(lot.units(), alreadyShipped, command.units());
        }

        var shipment = Shipment.record(command.breweryId(), lot.id(), command.destination(),
                command.contact(), command.units(), command.shippedOn(), command.note(),
                command.actorId(), Instant.now());
        shipments.insert(shipment);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "packaging.shipment.record",
                "packaging.finished-lot", lot.id().toString(),
                Map.of("code", lot.code(), "destination", shipment.destination(),
                        "units", String.valueOf(shipment.units()),
                        "shippedOn", shipment.shippedOn().toString())));
        return shipment;
    }
}
