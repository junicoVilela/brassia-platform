package br.com.brew.brassia.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeLookup {
    Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId);

    /**
     * Composição da receita publicada (itens + volume da batelada), para outros
     * módulos explodirem em necessidade de materiais (PLN-002) sem acessar a
     * tabela de receitas.
     */
    Optional<PublishedComposition> findPublishedComposition(UUID breweryId, UUID recipeId);

    record PublishedRecipe(UUID id, int version, String name) {}

    record PublishedComposition(UUID id, int version, BigDecimal batchVolumeLiters, List<CompositionItem> items) {}

    record CompositionItem(UUID ingredientId, String stage, BigDecimal quantity, String unit) {}
}
