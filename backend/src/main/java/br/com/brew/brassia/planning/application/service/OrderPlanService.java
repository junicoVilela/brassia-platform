package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.MaterialExplosion;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O plano de material de uma ordem (CST-002): a mesma explosão da necessidade de compra, recortada
 * para uma ordem.
 *
 * <p>Perda percentual zero, de propósito. A explosão aceita uma folga para a compra — comprar
 * exatamente o necessário deixa a fábrica sem margem —, mas o plano contra o qual se mede o consumo
 * é o da receita, não o da compra. Somar folga aqui faria toda brassagem parecer econômica.
 */
public final class OrderPlanService implements OrderPlanLookup {

    private final BrewOrderRepository orders;
    private final RecipeLookup recipes;

    public OrderPlanService(BrewOrderRepository orders, RecipeLookup recipes) {
        this.orders = Objects.requireNonNull(orders);
        this.recipes = Objects.requireNonNull(recipes);
    }

    @Override
    public Optional<OrderPlan> planOf(UUID breweryId, UUID orderId) {
        var order = orders.findById(breweryId, orderId).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        var composition = recipes.findPublishedComposition(breweryId, order.recipeId()).orElse(null);
        if (composition == null) {
            // A receita saiu de publicação: a ordem existe, o plano dela não é mais recuperável.
            return Optional.of(new OrderPlan(order.volumeLiters(), order.recipeVersion(), null, List.of()));
        }
        var components = composition.items().stream()
                .map(item -> new MaterialExplosion.Component(item.ingredientId(), item.quantity(),
                        item.unit()))
                .toList();
        var materials = MaterialExplosion
                .explode(components, composition.batchVolumeLiters(), order.volumeLiters(), BigDecimal.ZERO)
                .stream()
                .map(line -> new PlannedMaterial(line.ingredientId(), line.requiredQuantity(), line.unit()))
                .toList();
        return Optional.of(new OrderPlan(order.volumeLiters(), order.recipeVersion(),
                composition.version(), materials));
    }
}
