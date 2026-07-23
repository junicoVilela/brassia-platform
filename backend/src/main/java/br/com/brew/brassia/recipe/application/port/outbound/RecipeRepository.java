package br.com.brew.brassia.recipe.application.port.outbound;

import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository {
    boolean existsByName(UUID breweryId, String normalizedName);

    /** Persiste a receita e seus itens no mesmo commit. */
    void insert(Recipe recipe);

    /** Marca a receita como publicada (rascunho → publicada). Falha se já não for rascunho. */
    boolean markPublished(UUID breweryId, UUID recipeId);

    Optional<Recipe> findById(UUID breweryId, UUID id);

    List<Recipe> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);
}
