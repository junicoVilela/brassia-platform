package br.com.brew.brassia.inventory.adapter.inbound.web;

import br.com.brew.brassia.inventory.adapter.inbound.web.dto.ReleaseStockRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.ReserveStockRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.ReserveStockResponse;
import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
final class ReservationController {

    private final ReserveStockUseCase reserveStock;
    private final ReleaseStockUseCase releaseStock;

    ReservationController(ReserveStockUseCase reserveStock, ReleaseStockUseCase releaseStock) {
        this.reserveStock = reserveStock;
        this.releaseStock = releaseStock;
    }

    @PostMapping
    ReserveStockResponse reserve(
            @Valid @RequestBody ReserveStockRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        return ReserveStockResponse.from(reserveStock.handle(new ReserveStockUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.ingredientId(), request.quantity(),
                request.unit(), request.orderId())));
    }

    @PostMapping("/release")
    Map<String, Object> release(
            @Valid @RequestBody ReleaseStockRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.lot.manage");
        var result = releaseStock.handle(new ReleaseStockUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.orderId()));
        return Map.of("reference", result.reference(), "releasedLots", result.releasedLots());
    }
}
