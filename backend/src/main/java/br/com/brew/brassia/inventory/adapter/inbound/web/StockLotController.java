package br.com.brew.brassia.inventory.adapter.inbound.web;

import br.com.brew.brassia.inventory.adapter.inbound.web.dto.ReceiveStockLotRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.StockLotView;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockLotsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/lots")
final class StockLotController {

    private final ReceiveStockLotUseCase receiveLot;
    private final ListStockLotsUseCase listLots;

    StockLotController(ReceiveStockLotUseCase receiveLot, ListStockLotsUseCase listLots) {
        this.receiveLot = receiveLot;
        this.listLots = listLots;
    }

    @PostMapping
    ResponseEntity<StockLotView> receive(
            @Valid @RequestBody ReceiveStockLotRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        var result = receiveLot.handle(new ReceiveStockLotUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.ingredientId(), request.supplierId(),
                request.supplierLotCode(), request.quantity(), request.unit(), request.unitCost(),
                request.expiryDate(), request.inspection()));
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
}
