package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.application.port.inbound.MaterialRequirementUseCase;
import br.com.brew.brassia.planning.domain.MaterialExplosion;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.util.List;
import java.util.Objects;

/**
 * Explode uma receita publicada em necessidade de materiais (PLN-002). Cálculo
 * puro (não reserva estoque, não persiste). A disponibilidade/faltas contra
 * estoque ficam para o módulo de inventário (Sprint 06).
 */
public final class MaterialRequirementHandler implements MaterialRequirementUseCase {

    private final RecipeLookup recipes;

    public MaterialRequirementHandler(RecipeLookup recipes) {
        this.recipes = Objects.requireNonNull(recipes);
    }

    @Override
    public List<Line> handle(Query query) {
        var composition = recipes.findPublishedComposition(query.breweryId(), query.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita não publicada ou inexistente"));

        var components = composition.items().stream()
                .map(i -> new MaterialExplosion.Component(i.ingredientId(), i.quantity(), i.unit()))
                .toList();

        return MaterialExplosion.explode(components, composition.batchVolumeLiters(),
                        query.targetVolumeLiters(), query.lossPercent()).stream()
                .map(r -> new Line(r.ingredientId(), r.requiredQuantity(), r.unit()))
                .toList();
    }
}
