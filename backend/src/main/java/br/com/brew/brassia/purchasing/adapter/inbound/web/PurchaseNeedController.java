package br.com.brew.brassia.purchasing.adapter.inbound.web;

import br.com.brew.brassia.purchasing.adapter.inbound.web.dto.PurchaseNeedView;
import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchasing/needs")
final class PurchaseNeedController {

    private final PurchaseNeedUseCase purchaseNeed;

    PurchaseNeedController(PurchaseNeedUseCase purchaseNeed) {
        this.purchaseNeed = purchaseNeed;
    }

    @GetMapping
    List<PurchaseNeedView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("purchasing.purchase.read");
        return purchaseNeed.handle(principal.requireBrewery()).stream().map(PurchaseNeedView::from).toList();
    }
}
