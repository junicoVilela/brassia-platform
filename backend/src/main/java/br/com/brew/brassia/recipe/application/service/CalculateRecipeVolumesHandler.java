package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeVolumesUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import br.com.brew.brassia.recipe.domain.RecipeItem;
import br.com.brew.brassia.recipe.domain.RecipeStage;
import br.com.brew.brassia.recipe.domain.RecipeUnit;
import br.com.brew.brassia.recipe.domain.VolumeBalance;
import java.math.BigDecimal;
import java.util.Objects;

public final class CalculateRecipeVolumesHandler implements CalculateRecipeVolumesUseCase {
    private static final BigDecimal G_PER_KG = new BigDecimal("1000");
    private static final BigDecimal MG_PER_KG = new BigDecimal("1000000");

    private final RecipeRepository recipes;
    private final EquipmentProfileLookup equipment;

    public CalculateRecipeVolumesHandler(RecipeRepository recipes, EquipmentProfileLookup equipment) {
        this.recipes = Objects.requireNonNull(recipes);
        this.equipment = Objects.requireNonNull(equipment);
    }

    @Override
    public Result handle(Query query) {
        var recipe = recipes.findById(query.breweryId(), query.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var profile = equipment.find(query.breweryId(), recipe.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var grainMassKg = grainMassKg(recipe);
        var balance = VolumeBalance.compute(recipe.batchVolumeLiters(), grainMassKg, recipe.boilTimeMinutes(),
                profile.deadSpaceLiters(), profile.boilOffLitersPerHour());

        return new Result(recipe.id().value(), grainMassKg, balance.finalVolumeLiters(),
                balance.grainAbsorptionLiters(), balance.evaporationLiters(), balance.lossesLiters(),
                balance.preBoilVolumeLiters(), balance.totalWaterLiters(), balance.method());
    }

    /** Massa total (kg) dos itens de mostura; unidades de volume não contribuem. */
    private static BigDecimal grainMassKg(Recipe recipe) {
        return recipe.items().stream()
                .filter(i -> i.stage() == RecipeStage.MASH)
                .map(CalculateRecipeVolumesHandler::massKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal massKg(RecipeItem item) {
        return switch (item.unit()) {
            case KG -> item.quantity();
            case G -> item.quantity().divide(G_PER_KG);
            case MG -> item.quantity().divide(MG_PER_KG);
            case L, ML, UNIT -> BigDecimal.ZERO;
        };
    }
}
