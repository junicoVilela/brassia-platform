package br.com.brew.brassia.purchasing.application.service;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.purchasing.IngredientSourcingLookup;
import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import br.com.brew.brassia.purchasing.application.port.inbound.ShoppingListUseCase;
import br.com.brew.brassia.purchasing.application.port.outbound.SupplierRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consolida a necessidade de compra (PUR-001) por fornecedor preferencial de cada
 * ingrediente (último lote recebido), convertendo para a unidade de compra e,
 * quando permitido, estimando custo pelo custo do último lote. Não cria pedido.
 */
public final class ShoppingListHandler implements ShoppingListUseCase {

    private static final String NO_SUPPLIER = "Sem fornecedor definido";

    private final PurchaseNeedUseCase purchaseNeed;
    private final IngredientSourcingLookup sourcing;
    private final IngredientPurchaseLookup catalog;
    private final SupplierRepository suppliers;

    public ShoppingListHandler(PurchaseNeedUseCase purchaseNeed, IngredientSourcingLookup sourcing,
            IngredientPurchaseLookup catalog, SupplierRepository suppliers) {
        this.purchaseNeed = Objects.requireNonNull(purchaseNeed);
        this.sourcing = Objects.requireNonNull(sourcing);
        this.catalog = Objects.requireNonNull(catalog);
        this.suppliers = Objects.requireNonNull(suppliers);
    }

    @Override
    public List<SupplierGroup> handle(UUID breweryId, boolean includeCosts) {
        var needs = purchaseNeed.handle(breweryId);
        if (needs.isEmpty()) {
            return List.of();
        }

        var sourcingByIngredient = sourcing.preferredByIngredient(breweryId).stream()
                .collect(Collectors.toMap(IngredientSourcingLookup.Sourcing::ingredientId, s -> s, (a, b) -> a));
        var specByIngredient = catalog.findAll(breweryId).stream()
                .collect(Collectors.toMap(IngredientPurchaseLookup.PurchaseSpec::ingredientId, s -> s, (a, b) -> a));
        var supplierName = suppliers.findAll(breweryId).stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s.name(), (a, b) -> a));

        // Preserva a ordem de aparição dos fornecedores; grupo sem fornecedor usa chave nula.
        var groups = new LinkedHashMap<UUID, List<Item>>();
        for (var need : needs) {
            var source = sourcingByIngredient.get(need.ingredientId());
            var spec = specByIngredient.get(need.ingredientId());
            var supplierId = source == null ? null : source.supplierId();

            var purchaseUnit = spec == null ? need.unit() : spec.purchaseUnit();
            var converted = PurchaseUnitConversion.convert(need.suggested(), need.unit(), purchaseUnit);

            BigDecimal unitCost = null;
            BigDecimal estimatedCost = null;
            if (includeCosts && source != null && source.unitCostPerCanonical() != null) {
                unitCost = source.unitCostPerCanonical();
                estimatedCost = need.suggested().multiply(unitCost);
            }

            groups.computeIfAbsent(supplierId, k -> new ArrayList<>()).add(new Item(
                    need.ingredientId(),
                    spec == null ? null : spec.code(),
                    spec == null ? null : spec.name(),
                    need.demand(), need.onHand(), need.reserved(), need.suggested(), need.unit(),
                    converted.quantity(), converted.unit(),
                    unitCost, estimatedCost));
        }

        var result = new ArrayList<SupplierGroup>(groups.size());
        for (var entry : groups.entrySet()) {
            var name = entry.getKey() == null ? NO_SUPPLIER
                    : supplierName.getOrDefault(entry.getKey(), NO_SUPPLIER);
            var total = includeCosts ? entry.getValue().stream()
                    .map(Item::estimatedCost).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            result.add(new SupplierGroup(entry.getKey(), name, List.copyOf(entry.getValue()), total));
        }
        return result;
    }
}
