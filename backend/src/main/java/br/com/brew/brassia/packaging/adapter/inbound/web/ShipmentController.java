package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.ShipmentDtos;
import br.com.brew.brassia.packaging.application.port.inbound.ShipmentUseCases;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expedição do lote de produto acabado (TRC-001-D).
 *
 * <p>Registro, leitura e estorno. Não há edição nem exclusão: expedição é fato, e reescrever um destino
 * já comunicado num recall apagaria a única prova de que ele foi avisado. O estorno (FDS-003-A) mantém a
 * linha e marca que ela não vale mais.
 */
@RestController
@RequestMapping("/api/v1/packaging/shipments")
final class ShipmentController {

    private final ShipmentUseCases.Record record;
    private final ShipmentUseCases.Reverse reverse;
    private final ShipmentUseCases.Queries queries;

    ShipmentController(ShipmentUseCases.Record record, ShipmentUseCases.Reverse reverse,
            ShipmentUseCases.Queries queries) {
        this.record = record;
        this.reverse = reverse;
        this.queries = queries;
    }

    @GetMapping
    List<ShipmentDtos.ShipmentView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) UUID finishedLotId) {
        principal.requirePermission("packaging.plan.read");
        var shipments = finishedLotId == null
                ? queries.all(principal.requireBrewery())
                : queries.byLot(principal.requireBrewery(), finishedLotId);
        return ShipmentDtos.ShipmentView.from(shipments);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ShipmentDtos.ShipmentView record(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody ShipmentDtos.RecordShipmentRequest request) {
        principal.requirePermission("packaging.shipment.manage");
        return ShipmentDtos.ShipmentView.from(record.handle(new ShipmentUseCases.Record.Command(
                principal.requireBrewery(), principal.userId(), request.finishedLotId(),
                request.destination(), request.contact(), request.units(), request.shippedOn(),
                request.note())));
    }

    /**
     * Estorna a expedição registrada errada.
     *
     * <p>Alçada própria (`packaging.shipment.reverse`), separada de registrar: registrar é o trabalho do
     * dia; estornar desfaz um destino que pode já ter sido comunicado num recall. Quem faz um não faz
     * necessariamente o outro.
     */
    @PostMapping("/{shipmentId}/reversal")
    ShipmentDtos.ShipmentView reverse(@AuthenticationPrincipal SecurityPrincipal principal,
            @org.springframework.web.bind.annotation.PathVariable UUID shipmentId,
            @Valid @RequestBody ShipmentDtos.ReverseShipmentRequest request) {
        principal.requirePermission("packaging.shipment.reverse");
        return ShipmentDtos.ShipmentView.from(reverse.handle(new ShipmentUseCases.Reverse.Command(
                principal.requireBrewery(), principal.userId(), shipmentId, request.reason())));
    }
}