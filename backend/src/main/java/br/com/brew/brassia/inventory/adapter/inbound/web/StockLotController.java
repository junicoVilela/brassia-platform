package br.com.brew.brassia.inventory.adapter.inbound.web;

import br.com.brew.brassia.inventory.adapter.inbound.web.dto.LotPropertyView;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.ReceiveStockLotRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.RecordLotPropertiesRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.RecordMovementRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.RecordMovementResponse;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.StockBalanceView;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.StockLotView;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.StockMovementView;
import br.com.brew.brassia.inventory.application.port.inbound.GetStockBalanceUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockLotsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockMovementsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.RecordLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.RecordStockMovementUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/lots")
final class StockLotController {

    private final ReceiveStockLotUseCase receiveLot;
    private final ListStockLotsUseCase listLots;
    private final RecordStockMovementUseCase recordMovement;
    private final GetStockBalanceUseCase getBalance;
    private final ListStockMovementsUseCase listMovements;
    private final RecordLotPropertiesUseCase recordProperties;
    private final ListLotPropertiesUseCase listProperties;

    StockLotController(ReceiveStockLotUseCase receiveLot, ListStockLotsUseCase listLots,
            RecordStockMovementUseCase recordMovement, GetStockBalanceUseCase getBalance,
            ListStockMovementsUseCase listMovements, RecordLotPropertiesUseCase recordProperties,
            ListLotPropertiesUseCase listProperties) {
        this.receiveLot = receiveLot;
        this.listLots = listLots;
        this.recordMovement = recordMovement;
        this.getBalance = getBalance;
        this.listMovements = listMovements;
        this.recordProperties = recordProperties;
        this.listProperties = listProperties;
    }

    @PostMapping
    ResponseEntity<StockLotView> receive(
            @Valid @RequestBody ReceiveStockLotRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        var properties = request.properties() == null ? java.util.List.<RecordLotPropertiesUseCase.PropertyInput>of()
                : request.properties().stream().map(p -> p.toInput()).toList();
        var result = receiveLot.handle(new ReceiveStockLotUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.ingredientId(), request.supplierId(),
                request.supplierLotCode(), request.quantity(), request.unit(), request.unitCost(),
                request.expiryDate(), request.inspection(), properties));
        return ResponseEntity.created(URI.create("/api/v1/inventory/lots/" + result.id()))
                .body(new StockLotView(result.id(), request.ingredientId(), request.supplierId(),
                        request.supplierLotCode(), request.quantity(), request.unit(), request.unitCost(),
                        request.expiryDate(), request.inspection().toUpperCase(java.util.Locale.ROOT),
                        result.available()));
    }

    @GetMapping
    List<StockLotView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.read");
        return listLots.handle(principal.requireBrewery()).stream().map(StockLotView::from).toList();
    }

    @PostMapping("/{lotId}/movements")
    RecordMovementResponse recordMovement(
            @PathVariable UUID lotId,
            @Valid @RequestBody RecordMovementRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        var result = recordMovement.handle(new RecordStockMovementUseCase.Command(
                principal.userId(), principal.requireBrewery(), lotId, request.type(), request.quantity(),
                request.reason()));
        return new RecordMovementResponse(result.movementId(), result.onHand(), result.available());
    }

    @GetMapping("/{lotId}/balance")
    StockBalanceView balance(@PathVariable UUID lotId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.read");
        return StockBalanceView.from(getBalance.handle(principal.requireBrewery(), lotId));
    }

    @GetMapping("/{lotId}/movements")
    List<StockMovementView> movements(@PathVariable UUID lotId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.read");
        return listMovements.handle(principal.requireBrewery(), lotId).stream().map(StockMovementView::from).toList();
    }

    @PostMapping("/{lotId}/properties")
    ResponseEntity<List<UUID>> recordProperties(
            @PathVariable UUID lotId,
            @Valid @RequestBody RecordLotPropertiesRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        var result = recordProperties.handle(new RecordLotPropertiesUseCase.Command(
                principal.userId(), principal.requireBrewery(), lotId,
                request.properties().stream().map(p -> p.toInput()).toList()));
        return ResponseEntity.status(201).body(result.ids());
    }

    @GetMapping("/{lotId}/properties")
    List<LotPropertyView> properties(@PathVariable UUID lotId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.read");
        return listProperties.handle(principal.requireBrewery(), lotId).stream().map(LotPropertyView::from).toList();
    }
}
