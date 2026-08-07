package br.com.brew.brassia.costing.application.service;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.costing.MaterialActualSource;
import br.com.brew.brassia.costing.MaterialActualSource.MaterialFact;
import br.com.brew.brassia.costing.domain.BatchVariance;
import br.com.brew.brassia.costing.domain.BatchVariance.MaterialVariance;
import br.com.brew.brassia.costing.domain.BatchVariance.VarianceGap;
import br.com.brew.brassia.costing.domain.BatchVariance.VolumeKind;
import br.com.brew.brassia.costing.domain.BatchVariance.VolumeVariance;
import br.com.brew.brassia.costing.domain.UnknownBatchCostException;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Monta o planejado versus real de um lote (CST-002).
 *
 * <p>Nada aqui é guardado. A variação é sobre o presente — muda quando o envase acontece, quando um
 * consumo é corrigido — e congelá-la criaria uma segunda verdade ao lado do custo fechado. O que se
 * congela é o custo (CST-001); a explicação dele se refaz.
 *
 * <p>O trabalho é de casamento: o plano vem por ingrediente, o fato vem por ingrediente, e o que
 * aparece só de um lado é informação e não erro — insumo planejado e não usado é uma variação de
 * consumo de 100%, insumo usado e não planejado também.
 */
public final class BatchVarianceAssembler {

    private final BatchLookup batches;
    private final BatchOutcomeLookup outcomes;
    private final OrderPlanLookup plans;
    private final MaterialActualSource actuals;
    private final PackagingOutcomeLookup packaging;
    private final IngredientPurchaseLookup ingredients;

    public BatchVarianceAssembler(BatchLookup batches, BatchOutcomeLookup outcomes, OrderPlanLookup plans,
            MaterialActualSource actuals, PackagingOutcomeLookup packaging,
            IngredientPurchaseLookup ingredients) {
        this.batches = Objects.requireNonNull(batches);
        this.outcomes = Objects.requireNonNull(outcomes);
        this.plans = Objects.requireNonNull(plans);
        this.actuals = Objects.requireNonNull(actuals);
        this.packaging = Objects.requireNonNull(packaging);
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    public BatchVariance assemble(UUID breweryId, UUID batchId) {
        var batch = batches.find(breweryId, batchId)
                .orElseThrow(() -> new UnknownBatchCostException(batchId));
        var gaps = new ArrayList<VarianceGap>();
        var facts = actuals.actualsFor(breweryId, batch.orderId());
        var plan = plans.planOf(breweryId, batch.orderId()).orElse(null);

        var materials = materials(breweryId, plan, facts, gaps);
        var volumes = volumes(breweryId, batch, gaps);
        return new BatchVariance(batchId, batch.code(), materials, volumes, List.copyOf(gaps));
    }

    // --- material ---

    private List<MaterialVariance> materials(UUID breweryId, OrderPlanLookup.OrderPlan plan,
            MaterialActualSource.Actuals facts, List<VarianceGap> gaps) {
        var planned = plannedQuantities(plan, gaps);
        var hasPlan = !planned.isEmpty();
        var reserved = byIngredient(facts.reserved());
        var consumed = byIngredient(facts.consumed());
        var hasConsumption = !consumed.isEmpty();

        if (!hasConsumption) {
            gaps.add(new VarianceGap("consumo",
                    "o consumo do dia de brassa ainda não foi confirmado: não há real a comparar"));
        }

        var names = names(breweryId, facts);
        var result = new ArrayList<MaterialVariance>();
        for (UUID ingredientId : union(planned.keySet(), consumed.keySet())) {
            var actual = consumed.get(ingredientId);
            var base = reserved.get(ingredientId);
            var plannedMaterial = planned.get(ingredientId);
            var unit = unitOf(actual, base, plannedMaterial);
            var name = names.getOrDefault(ingredientId, "insumo " + shortId(ingredientId));

            if (base == null || base.unitCost() == null) {
                gaps.add(new VarianceGap(name,
                        "a ordem não separou lote deste insumo: sem preço planejado, a diferença não se "
                                + "divide entre preço e consumo"));
            }
            // Zero só quando se sabe que era zero: com plano confiável, insumo ausente dele foi
            // consumo extra; com consumo confirmado, insumo ausente dele não foi usado.
            var plannedQuantity = plannedMaterial != null ? plannedMaterial.quantity()
                    : hasPlan ? BigDecimal.ZERO : null;
            var actualQuantity = actual != null ? actual.quantity()
                    : hasConsumption ? BigDecimal.ZERO : null;
            result.add(new MaterialVariance(ingredientId, name, unit, plannedQuantity, actualQuantity,
                    base == null ? null : base.unitCost(),
                    actual != null ? actual.unitCost() : hasConsumption ? baseCostOf(base) : null));
        }
        return result;
    }

    /**
     * Preço real de um insumo planejado e não consumido.
     *
     * <p>Ele não tem preço real — não saiu lote nenhum. Usar o preço da base deixa a variação de
     * preço em zero e joga a diferença inteira para o consumo, que é onde ela de fato está: o
     * desvio foi não ter usado, não ter pago diferente.
     */
    private static BigDecimal baseCostOf(MaterialFact reserved) {
        return reserved == null ? null : reserved.unitCost();
    }

    /**
     * O plano por ingrediente — vazio quando não há base em que confiar.
     *
     * <p>Receita republicada depois da ordem é o caso que obriga a declarar em vez de comparar:
     * a explosão sairia de uma receita que ninguém brassou, e a variação de consumo seria a
     * diferença entre duas receitas, não entre plano e execução.
     */
    private Map<UUID, OrderPlanLookup.PlannedMaterial> plannedQuantities(OrderPlanLookup.OrderPlan plan,
            List<VarianceGap> gaps) {
        if (plan == null || plan.plannedRecipeVersion() == null) {
            gaps.add(new VarianceGap("plano de material",
                    "a receita desta ordem não está mais publicada: não há plano contra o qual comparar "
                            + "o consumo"));
            return Map.of();
        }
        if (!plan.baselineMatchesOrder()) {
            gaps.add(new VarianceGap("plano de material",
                    "a receita foi republicada depois desta ordem (versão " + plan.orderRecipeVersion()
                            + " na ordem, " + plan.plannedRecipeVersion() + " publicada): comparar o "
                            + "consumo com ela seria comparar com uma receita que ninguém brassou"));
            return Map.of();
        }
        var planned = new LinkedHashMap<UUID, OrderPlanLookup.PlannedMaterial>();
        for (var material : plan.materials()) {
            planned.merge(material.ingredientId(), material, BatchVarianceAssembler::add);
        }
        return planned;
    }

    private static OrderPlanLookup.PlannedMaterial add(OrderPlanLookup.PlannedMaterial current,
            OrderPlanLookup.PlannedMaterial next) {
        return new OrderPlanLookup.PlannedMaterial(current.ingredientId(),
                current.quantity().add(next.quantity()), current.unit());
    }

    // --- volume ---

    private List<VolumeVariance> volumes(UUID breweryId, BatchLookup.Snapshot batch,
            List<VarianceGap> gaps) {
        var volumes = new ArrayList<VolumeVariance>();
        var outcome = outcomes.outcomeOf(breweryId, batch.batchId()).orElse(null);

        if (outcome == null || !outcome.transferred()) {
            gaps.add(new VarianceGap("rendimento",
                    "o lote ainda não foi transferido: o volume real do fermentador não existe"));
        } else {
            volumes.add(new VolumeVariance(VolumeKind.YIELD, "volume transferido ao fermentador",
                    outcome.plannedVolumeLiters(), outcome.transferredVolumeLiters()));
            if (outcome.transferLossesLiters() != null) {
                // Sem perda esperada cadastrada, o número é fato e não desvio — CST-002-A.
                volumes.add(new VolumeVariance(VolumeKind.LOSS, "perda na transferência", null,
                        outcome.transferLossesLiters()));
            }
        }

        var runs = packaging.outcomesOfBatch(breweryId, batch.batchId());
        if (runs.isEmpty()) {
            gaps.add(new VarianceGap("envase", "este lote ainda não foi envasado: não há volume envasado "
                    + "a comparar com o planejado"));
        }
        for (var run : runs) {
            volumes.add(new VolumeVariance(VolumeKind.YIELD, "volume envasado no plano " + run.planCode(),
                    run.plannedVolumeLiters(), run.packagedVolumeLiters()));
            volumes.add(new VolumeVariance(VolumeKind.LOSS, "rejeito no plano " + run.planCode(), null,
                    run.rejectedVolumeLiters()));
            volumes.add(new VolumeVariance(VolumeKind.LOSS, "perda de linha no plano " + run.planCode(),
                    null, run.lossesLiters()));
        }

        if (volumes.stream().anyMatch(volume -> volume.kind() == VolumeKind.LOSS && !volume.comparable())) {
            gaps.add(new VarianceGap("perda esperada",
                    "não há perda esperada cadastrada para transferência nem para linha de envase: a "
                            + "perda aparece como fato, sem desvio (CST-002-A)"));
        }
        return volumes;
    }

    // --- apoio ---

    private Map<UUID, String> names(UUID breweryId, MaterialActualSource.Actuals facts) {
        var names = new HashMap<UUID, String>();
        // O catálogo primeiro, o estoque depois: o nome do lote é o mesmo, e o catálogo cobre o
        // insumo que foi planejado e nunca chegou a ter movimento.
        for (var spec : ingredients.findAll(breweryId)) {
            names.put(spec.ingredientId(), spec.name());
        }
        for (var fact : facts.consumed()) {
            if (fact.name() != null) {
                names.put(fact.ingredientId(), fact.name());
            }
        }
        return names;
    }

    private static Map<UUID, MaterialFact> byIngredient(List<MaterialFact> facts) {
        var totals = new LinkedHashMap<UUID, MaterialFact>();
        for (MaterialFact fact : facts) {
            totals.merge(fact.ingredientId(), fact, (current, next) -> new MaterialFact(
                    current.ingredientId(), current.name(), current.quantity().add(next.quantity()),
                    current.unit(), current.totalCost().add(next.totalCost())));
        }
        return totals;
    }

    private static List<UUID> union(java.util.Collection<UUID> planned,
            java.util.Collection<UUID> consumed) {
        var all = new ArrayList<UUID>(planned);
        for (UUID id : consumed) {
            if (!all.contains(id)) {
                all.add(id);
            }
        }
        return all;
    }

    private static String unitOf(MaterialFact actual, MaterialFact reserved,
            OrderPlanLookup.PlannedMaterial planned) {
        if (actual != null) {
            return actual.unit();
        }
        if (reserved != null) {
            return reserved.unit();
        }
        return planned == null ? "" : planned.unit();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
