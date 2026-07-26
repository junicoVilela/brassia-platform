package br.com.brew.brassia.purchasing.adapter.inbound.web;

import br.com.brew.brassia.purchasing.adapter.inbound.web.dto.RegisterSupplierRequest;
import br.com.brew.brassia.purchasing.adapter.inbound.web.dto.SupplierView;
import br.com.brew.brassia.purchasing.application.port.inbound.ListSuppliersUseCase;
import br.com.brew.brassia.purchasing.application.port.inbound.RegisterSupplierUseCase;
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
@RequestMapping("/api/v1/suppliers")
final class SupplierController {

    private final RegisterSupplierUseCase registerSupplier;
    private final ListSuppliersUseCase listSuppliers;

    SupplierController(RegisterSupplierUseCase registerSupplier, ListSuppliersUseCase listSuppliers) {
        this.registerSupplier = registerSupplier;
        this.listSuppliers = listSuppliers;
    }

    @PostMapping
    ResponseEntity<SupplierView> register(
            @Valid @RequestBody RegisterSupplierRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("purchasing.supplier.manage");
        var result = registerSupplier.handle(new RegisterSupplierUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.name(), request.code()));
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + result.id()))
                .body(new SupplierView(result.id(), result.name(), result.code()));
    }

    @GetMapping
    List<SupplierView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("purchasing.supplier.read");
        return listSuppliers.handle(principal.requireBrewery()).stream().map(SupplierView::from).toList();
    }
}
