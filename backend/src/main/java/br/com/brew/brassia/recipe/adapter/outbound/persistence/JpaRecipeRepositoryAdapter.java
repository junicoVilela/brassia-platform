package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
final class JpaRecipeRepositoryAdapter implements RecipeRepository {
    private final SpringDataRecipeJpaRepository repository;

    JpaRecipeRepositoryAdapter(SpringDataRecipeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByName(UUID breweryId, String normalizedName) {
        return repository.existsByBreweryIdAndNormalizedName(breweryId, normalizedName);
    }

    @Override
    public void save(Recipe recipe) {
        repository.save(RecipeJpaEntity.from(recipe));
    }
}
