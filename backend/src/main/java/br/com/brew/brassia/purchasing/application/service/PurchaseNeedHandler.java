package br.com.brew.brassia.purchasing.application.service;

import br.com.brew.brassia.planning.OrderDemandLookup;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Necessidade de compra (PUR-001): para cada ingrediente, sugere
 * {@code max(0, demanda − saldo em mãos)}. A demanda vem das OPs liberadas e o
 * saldo do estoque, ambos em unidade canônica. Só há sugestão quando a demanda
 * supera o saldo — itens totalmente cobertos são omitidos.
 */
public final class PurchaseNeedHandler implements PurchaseNeedUseCase {

    private final OrderDemandLookup demandLookup;
    private final StockOnHandLookup stockLookup;

    public PurchaseNeedHandler(OrderDemandLookup demandLookup, StockOnHandLookup stockLookup) {
        this.demandLookup = Objects.requireNonNull(demandLookup);
        this.stockLookup = Objects.requireNonNull(stockLookup);
    }

    @Override
    public List<Need> handle(UUID breweryId) {
        var onHand = new LinkedHashMap<String, BigDecimal>();
        var reserved = new LinkedHashMap<String, BigDecimal>();
        for (var stock : stockLookup.onHandByIngredient(breweryId)) {
            var key = key(stock.ingredientId(), stock.unit());
            onHand.merge(key, stock.onHand(), BigDecimal::add);
            reserved.merge(key, stock.reserved(), BigDecimal::add);
        }

        var needs = new ArrayList<Need>();
        for (var demand : demandLookup.aggregatedDemand(breweryId)) {
            var key = key(demand.ingredientId(), demand.unit());
            var available = onHand.getOrDefault(key, BigDecimal.ZERO);
            var reservedQty = reserved.getOrDefault(key, BigDecimal.ZERO);
            var suggested = demand.quantity().subtract(available);
            if (suggested.signum() > 0) {
                needs.add(new Need(demand.ingredientId(), demand.quantity(), available, reservedQty,
                        suggested, demand.unit()));
            }
        }
        return needs;
    }

    private static String key(UUID ingredientId, String unit) {
        return ingredientId + "|" + unit;
    }
}
