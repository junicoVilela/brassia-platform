package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRecipeJpaRepository extends JpaRepository<RecipeJpaEntity, UUID> {
    boolean existsByBreweryIdAndNormalizedName(UUID breweryId, String normalizedName);
}
