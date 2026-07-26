package br.com.brew.brassia.purchasing.adapter.inbound.web;

import br.com.brew.brassia.purchasing.adapter.inbound.web.dto.ShoppingListView;
import br.com.brew.brassia.purchasing.application.port.inbound.ShoppingListUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchasing/shopping-list")
final class ShoppingListController {

    private final ShoppingListUseCase shoppingList;

    ShoppingListController(ShoppingListUseCase shoppingList) {
        this.shoppingList = shoppingList;
    }

    @GetMapping
    List<ShoppingListView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("purchasing.purchase.read");
        // Custos só para quem tem a permissão específica (exportação sem expor custo).
        var includeCosts = principal.hasPermission("purchasing.cost.read");
        return shoppingList.handle(principal.requireBrewery(), includeCosts).stream()
                .map(ShoppingListView::from).toList();
    }
}
