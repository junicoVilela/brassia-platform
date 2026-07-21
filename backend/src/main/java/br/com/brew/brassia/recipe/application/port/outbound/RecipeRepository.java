package br.com.brew.brassia.recipe.application.port.outbound;

import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.UUID;

public interface RecipeRepository {
    boolean existsByName(UUID breweryId, String normalizedName);
    void save(Recipe recipe);
}
