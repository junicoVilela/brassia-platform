package br.com.brew.brassia.purchasing.application.service;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.planning.OrderDemandLookup;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Necessidade de compra (PUR-001): por ingrediente,
 * {@code sugerido = max(0, demanda + ponto de pedido − saldo)}. A demanda vem das
 * OPs liberadas, o saldo do estoque e o ponto de pedido (estoque de segurança) do
 * catálogo (PUR-001-A) — todos em unidade canônica. Itens totalmente cobertos são
 * omitidos.
 */
public final class PurchaseNeedHandler implements PurchaseNeedUseCase {

    private final OrderDemandLookup demandLookup;
    private final StockOnHandLookup stockLookup;
    private final IngredientPurchaseLookup catalog;

    public PurchaseNeedHandler(OrderDemandLookup demandLookup, StockOnHandLookup stockLookup,
            IngredientPurchaseLookup catalog) {
        this.demandLookup = Objects.requireNonNull(demandLookup);
        this.stockLookup = Objects.requireNonNull(stockLookup);
        this.catalog = Objects.requireNonNull(catalog);
    }

    /** Quantidade canônica de um ingrediente e a unidade canônica correspondente. */
    private record Amount(String unit, BigDecimal quantity) {}

    @Override
    public List<Need> handle(UUID breweryId) {
        var onHand = new LinkedHashMap<String, BigDecimal>();
        var reserved = new LinkedHashMap<String, BigDecimal>();
        for (var stock : stockLookup.onHandByIngredient(breweryId)) {
            var key = key(stock.ingredientId(), stock.unit());
            onHand.merge(key, stock.onHand(), BigDecimal::add);
            reserved.merge(key, stock.reserved(), BigDecimal::add);
        }

        var demandByIngredient = new LinkedHashMap<UUID, Amount>();
        for (var demand : demandLookup.aggregatedDemand(breweryId)) {
            demandByIngredient.put(demand.ingredientId(), new Amount(demand.unit(), demand.quantity()));
        }

        // Ponto de pedido do catálogo, convertido para a unidade canônica da dimensão.
        var reorderByIngredient = new LinkedHashMap<UUID, Amount>();
        for (var spec : catalog.findAll(breweryId)) {
            if (spec.reorderPoint() != null && spec.reorderPoint().signum() > 0) {
                reorderByIngredient.put(spec.ingredientId(), new Amount(
                        PurchaseUnitConversion.canonicalUnitOf(spec.useUnit()),
                        PurchaseUnitConversion.toCanonical(spec.reorderPoint(), spec.useUnit())));
            }
        }

        // Considera ingredientes com demanda OU com ponto de pedido (pode faltar sem OP liberada).
        var ingredientIds = new LinkedHashSet<UUID>();
        ingredientIds.addAll(demandByIngredient.keySet());
        ingredientIds.addAll(reorderByIngredient.keySet());

        var needs = new ArrayList<Need>();
        for (var ingredientId : ingredientIds) {
            var demand = demandByIngredient.get(ingredientId);
            var reorder = reorderByIngredient.get(ingredientId);
            var unit = demand != null ? demand.unit() : reorder.unit();
            var demandQty = demand != null ? demand.quantity() : BigDecimal.ZERO;
            var reorderQty = reorder != null ? reorder.quantity() : BigDecimal.ZERO;

            var key = key(ingredientId, unit);
            var available = onHand.getOrDefault(key, BigDecimal.ZERO);
            var reservedQty = reserved.getOrDefault(key, BigDecimal.ZERO);
            var suggested = demandQty.add(reorderQty).subtract(available);
            if (suggested.signum() > 0) {
                needs.add(new Need(ingredientId, demandQty, available, reservedQty, reorderQty, suggested, unit));
            }
        }
        return needs;
    }

    private static String key(UUID ingredientId, String unit) {
        return ingredientId + "|" + unit;
    }
}
