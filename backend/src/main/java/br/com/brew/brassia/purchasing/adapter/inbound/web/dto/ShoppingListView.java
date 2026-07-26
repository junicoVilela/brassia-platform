package br.com.brew.brassia.purchasing.adapter.inbound.web.dto;

import br.com.brew.brassia.purchasing.application.port.inbound.ShoppingListUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShoppingListView(
        UUID supplierId, String supplierName, List<ItemView> items, BigDecimal estimatedTotal) {

    public static ShoppingListView from(ShoppingListUseCase.SupplierGroup group) {
        return new ShoppingListView(
                group.supplierId(), group.supplierName(),
                group.items().stream().map(ItemView::from).toList(),
                group.estimatedTotal());
    }

    public record ItemView(
            UUID ingredientId, String ingredientCode, String ingredientName,
            BigDecimal demand, BigDecimal onHand, BigDecimal reserved, BigDecimal suggested, String unit,
            BigDecimal purchaseQuantity, String purchaseUnit,
            BigDecimal unitCost, BigDecimal estimatedCost) {

        static ItemView from(ShoppingListUseCase.Item item) {
            return new ItemView(
                    item.ingredientId(), item.ingredientCode(), item.ingredientName(),
                    item.demand(), item.onHand(), item.reserved(), item.suggested(), item.unit(),
                    item.purchaseQuantity(), item.purchaseUnit(),
                    item.unitCost(), item.estimatedCost());
        }
    }
}
