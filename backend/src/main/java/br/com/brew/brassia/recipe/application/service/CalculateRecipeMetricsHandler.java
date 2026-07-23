package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeMetricsRepository;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.BrewingMetrics;
import br.com.brew.brassia.recipe.domain.Recipe;
import br.com.brew.brassia.recipe.domain.RecipeItem;
import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import br.com.brew.brassia.recipe.domain.RecipeTargets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class CalculateRecipeMetricsHandler implements CalculateRecipeMetricsUseCase {
    // Tolerâncias explícitas por meta (REC-003).
    private static final BigDecimal OG_TOLERANCE = new BigDecimal("3.0");
    private static final BigDecimal IBU_TOLERANCE = new BigDecimal("5.0");
    private static final BigDecimal COLOR_TOLERANCE = new BigDecimal("5.0");
    private static final BigDecimal ABV_TOLERANCE = new BigDecimal("0.3");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal G_PER_KG = new BigDecimal("1000");
    private static final BigDecimal MG_PER_KG = new BigDecimal("1000000");

    private final RecipeRepository recipes;
    private final EquipmentProfileLookup equipment;
    private final IngredientSpecLookup ingredients;
    private final RecipeMetricsRepository metricsRepository;
    private final AuditTrail audit;

    public CalculateRecipeMetricsHandler(RecipeRepository recipes, EquipmentProfileLookup equipment,
            IngredientSpecLookup ingredients, RecipeMetricsRepository metricsRepository, AuditTrail audit) {
        this.recipes = Objects.requireNonNull(recipes);
        this.equipment = Objects.requireNonNull(equipment);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.metricsRepository = Objects.requireNonNull(metricsRepository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var recipe = recipes.findById(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var profile = equipment.find(command.breweryId(), recipe.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));
        var efficiency = profile.mashEfficiencyPercent().divide(HUNDRED);

        var fermentables = new ArrayList<BrewingMetrics.Fermentable>();
        var hops = new ArrayList<BrewingMetrics.Hop>();
        BigDecimal yeastAttenuation = null;
        for (var item : recipe.items()) {
            var spec = ingredients.find(command.breweryId(), item.ingredientId()).orElse(null);
            if (spec == null) {
                continue;
            }
            if (spec.potentialSg() != null) {
                fermentables.add(new BrewingMetrics.Fermentable(massKg(item), spec.potentialSg(), spec.colorEbc()));
            }
            if (spec.alphaAcidPercent() != null) {
                var minutes = item.timingMinutes() == null ? 0 : item.timingMinutes();
                hops.add(new BrewingMetrics.Hop(massGrams(item), spec.alphaAcidPercent(), minutes));
            }
            if (spec.attenuationPercent() != null && yeastAttenuation == null) {
                yeastAttenuation = spec.attenuationPercent();
            }
        }

        var m = BrewingMetrics.compute(recipe.batchVolumeLiters(), efficiency, fermentables, hops, yeastAttenuation);

        var stored = new RecipeMetrics(recipe.id().value(), recipe.breweryId(), m.ogPoints(), m.ogSg(),
                m.fgPoints(), m.fgSg(), m.abv(), m.ibu(), m.colorEbc(), m.attenuationPercent(), m.method(),
                m.version());
        metricsRepository.upsert(stored);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.metrics.calculate",
                "recipe", recipe.id().value().toString(),
                Map.of("method", m.method(), "version", Integer.toString(m.version()))));

        var t = recipe.targets();
        return new Result(recipe.id().value(), m.method(), m.version(), m.ogPoints(), m.ogSg(), m.fgPoints(),
                m.fgSg(), m.abv(), m.ibu(), m.colorEbc(), m.attenuationPercent(),
                check(m.ogPoints(), t.ogPoints(), OG_TOLERANCE),
                check(m.ibu(), t.ibu(), IBU_TOLERANCE),
                check(m.colorEbc(), t.colorEbc(), COLOR_TOLERANCE),
                check(m.abv(), t.abv(), ABV_TOLERANCE));
    }

    private static Check check(BigDecimal value, BigDecimal target, BigDecimal tolerance) {
        if (target == null) {
            return new Check(value, null, tolerance, null, null);
        }
        var deviation = value.subtract(target);
        var within = deviation.abs().compareTo(tolerance) <= 0;
        return new Check(value, target, tolerance, deviation, within);
    }

    private static BigDecimal massKg(RecipeItem item) {
        return switch (item.unit()) {
            case KG -> item.quantity();
            case G -> item.quantity().divide(G_PER_KG);
            case MG -> item.quantity().divide(MG_PER_KG);
            case L, ML, UNIT -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal massGrams(RecipeItem item) {
        return switch (item.unit()) {
            case KG -> item.quantity().multiply(G_PER_KG);
            case G -> item.quantity();
            case MG -> item.quantity().divide(G_PER_KG);
            case L, ML, UNIT -> BigDecimal.ZERO;
        };
    }
}
