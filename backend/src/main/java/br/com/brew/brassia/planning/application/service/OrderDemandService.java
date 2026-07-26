package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.OrderDemandLookup;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.MaterialExplosion;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Demanda de materiais das OPs liberadas (PUR-001): explode a receita publicada
 * de cada ordem pelo volume planejado e agrega por ingrediente em unidade
 * canônica. Cálculo de leitura — não reserva nem persiste.
 */
public final class OrderDemandService implements OrderDemandLookup {

    private final BrewOrderRepository orders;
    private final RecipeLookup recipes;

    public OrderDemandService(BrewOrderRepository orders, RecipeLookup recipes) {
        this.orders = Objects.requireNonNull(orders);
        this.recipes = Objects.requireNonNull(recipes);
    }

    @Override
    public List<IngredientDemand> aggregatedDemand(UUID breweryId) {
        // chave: ingredientId|unidade canônica → quantidade acumulada
        var totals = new LinkedHashMap<String, IngredientDemand>();
        for (var order : orders.findReleased(breweryId)) {
            var composition = recipes.findPublishedComposition(breweryId, order.recipeId());
            if (composition.isEmpty()) {
                continue;
            }
            var comp = composition.get();
            var components = comp.items().stream()
                    .map(i -> new MaterialExplosion.Component(i.ingredientId(), i.quantity(), i.unit()))
                    .toList();
            var lines = MaterialExplosion.explode(components, comp.batchVolumeLiters(), order.volumeLiters(),
                    BigDecimal.ZERO);
            for (var line : lines) {
                var key = line.ingredientId() + "|" + line.unit();
                var current = totals.get(key);
                var quantity = current == null ? line.requiredQuantity()
                        : current.quantity().add(line.requiredQuantity());
                totals.put(key, new IngredientDemand(line.ingredientId(), quantity, line.unit()));
            }
        }
        return new ArrayList<>(totals.values());
    }
}
