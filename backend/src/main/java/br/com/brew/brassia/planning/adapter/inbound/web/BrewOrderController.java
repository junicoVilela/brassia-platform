package br.com.brew.brassia.planning.adapter.inbound.web;

import br.com.brew.brassia.planning.adapter.inbound.web.dto.BrewOrderDetailView;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.BrewOrderResponse;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.BrewOrderSummaryView;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.CancelBrewOrderRequest;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.CreateBrewOrderRequest;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.ReleaseBrewOrderRequest;
import br.com.brew.brassia.planning.application.port.inbound.CancelBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.inbound.CreateBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.inbound.GetBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ListBrewOrdersUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ReleaseBrewOrderUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brew-orders")
final class BrewOrderController {

    private final CreateBrewOrderUseCase createOrder;
    private final ListBrewOrdersUseCase listOrders;
    private final GetBrewOrderUseCase getOrder;
    private final ReleaseBrewOrderUseCase releaseOrder;
    private final CancelBrewOrderUseCase cancelOrder;

    BrewOrderController(CreateBrewOrderUseCase createOrder, ListBrewOrdersUseCase listOrders,
            GetBrewOrderUseCase getOrder, ReleaseBrewOrderUseCase releaseOrder, CancelBrewOrderUseCase cancelOrder) {
        this.createOrder = createOrder;
        this.listOrders = listOrders;
        this.getOrder = getOrder;
        this.releaseOrder = releaseOrder;
        this.cancelOrder = cancelOrder;
    }

    @PostMapping
    ResponseEntity<BrewOrderResponse> create(
            @Valid @RequestBody CreateBrewOrderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.order.manage");
        var result = createOrder.handle(new CreateBrewOrderUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.recipeId(), request.volumeLiters()));
        return ResponseEntity.created(URI.create("/api/v1/brew-orders/" + result.id()))
                .body(new BrewOrderResponse(result.id(), result.code(), result.status()));
    }

    @GetMapping
    PageResponse<BrewOrderSummaryView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.order.read");
        var result = listOrders.handle(new ListBrewOrdersUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(BrewOrderSummaryView::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    BrewOrderDetailView detail(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.order.read");
        return BrewOrderDetailView.from(getOrder.handle(principal.requireBrewery(), id));
    }

    @PostMapping("/{id}/release")
    BrewOrderResponse release(@PathVariable UUID id, @RequestBody(required = false) ReleaseBrewOrderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.order.manage");
        var assignedUserId = request == null ? null : request.assignedUserId();
        var result = releaseOrder.handle(new ReleaseBrewOrderUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, assignedUserId));
        return new BrewOrderResponse(result.id(), null, result.status());
    }

    @PostMapping("/{id}/cancel")
    BrewOrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelBrewOrderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.order.manage");
        var result = cancelOrder.handle(new CancelBrewOrderUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.reason()));
        return new BrewOrderResponse(result.id(), null, result.status());
    }
}
