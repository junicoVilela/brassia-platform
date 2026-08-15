package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.application.port.inbound.ShipmentUseCases;
import br.com.brew.brassia.packaging.application.port.outbound.ShipmentRepository;
import br.com.brew.brassia.packaging.domain.Shipment;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Estorna a expedição registrada errada (FDS-003-A).
 *
 * <p><strong>A linha permanece.</strong> Apagar tornaria indistinguível "nunca houve expedição" de "houve
 * e foi estornada", e a segunda precisa ser demonstrável — inclusive para quem recebeu a comunicação de um
 * recall baseado nela.
 *
 * <p>O efeito no recall é consequência, não passo: as consultas que o alimentam passaram a olhar só
 * expedições vivas, então o saldo sem destino do lote volta a mostrar a cerveja que a expedição errada
 * escondia, sem que nada precise ser recalculado.
 */
public final class ReverseShipmentHandler implements ShipmentUseCases.Reverse {

    private final ShipmentRepository shipments;
    private final AuditTrail audit;

    public ReverseShipmentHandler(ShipmentRepository shipments, AuditTrail audit) {
        this.shipments = Objects.requireNonNull(shipments, "shipments");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public Shipment handle(Command command) {
        Objects.requireNonNull(command, "command");
        // Travada na leitura: dois estornos simultâneos leriam a mesma expedição viva e ambos passariam
        // pela checagem do agregado.
        var shipment = shipments.findForUpdate(command.breweryId(), command.shipmentId())
                .orElseThrow(() -> new IllegalArgumentException("expedição inexistente"));

        shipment.reverse(command.actorId(), command.reason(), Instant.now());
        shipments.updateReversal(shipment);

        // O motivo vai para a auditoria porque é a pergunta de quem investiga: não basta saber que a
        // expedição deixou de valer, é preciso saber se foi digitação, destino trocado ou carga que não saiu.
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                "packaging.shipment.reverse", "packaging.shipment", shipment.id().toString(),
                Map.of("units", String.valueOf(shipment.units()),
                        "destination", shipment.destination(),
                        "reason", shipment.reversal().orElseThrow().reason())));
        return shipment;
    }
}
