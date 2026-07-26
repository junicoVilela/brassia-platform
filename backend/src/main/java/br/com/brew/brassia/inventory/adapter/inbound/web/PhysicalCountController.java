package br.com.brew.brassia.inventory.adapter.inbound.web;

import br.com.brew.brassia.inventory.adapter.inbound.web.dto.CreateCountRequest;
import br.com.brew.brassia.inventory.adapter.inbound.web.dto.PhysicalCountView;
import br.com.brew.brassia.inventory.application.port.inbound.ApprovePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.CreatePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.PhysicalCountQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/inventory/counts")
final class PhysicalCountController {

    private final CreatePhysicalCountUseCase createCount;
    private final ApprovePhysicalCountUseCase approveCount;
    private final PhysicalCountQueries queries;

    PhysicalCountController(CreatePhysicalCountUseCase createCount, ApprovePhysicalCountUseCase approveCount,
            PhysicalCountQueries queries) {
        this.createCount = createCount;
        this.approveCount = approveCount;
        this.queries = queries;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateCountRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.count.manage");
        var lines = request.lines().stream()
                .map(l -> new CreatePhysicalCountUseCase.LineInput(l.lotId(), l.countedQuantity()))
                .toList();
        var result = createCount.handle(new CreatePhysicalCountUseCase.Command(
                principal.userId(), principal.requireBrewery(), lines));
        return ResponseEntity.created(URI.create("/api/v1/inventory/counts/" + result.id()))
                .body(Map.of("id", result.id(), "status", result.status()));
    }

    @PostMapping("/{id}/approve")
    Map<String, Object> approve(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.count.approve");
        var result = approveCount.handle(new ApprovePhysicalCountUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
        return Map.of("id", result.id(), "status", result.status(), "adjustments", result.adjustments());
    }

    @GetMapping
    List<PhysicalCountView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.count.read");
        return queries.list(principal.requireBrewery()).stream().map(PhysicalCountView::from).toList();
    }

    @GetMapping("/{id}")
    PhysicalCountView detail(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("inventory.count.read");
        return PhysicalCountView.from(queries.get(principal.requireBrewery(), id));
    }
}
