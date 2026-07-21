package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.domain.Recipe;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "recipe")
class RecipeJpaEntity {
    @Id private UUID id;
    private UUID breweryId;
    private String name;
    private String normalizedName;
    private String status;
    @Version private long version;

    protected RecipeJpaEntity() {}

    static RecipeJpaEntity from(Recipe recipe) {
        var entity = new RecipeJpaEntity();
        entity.id = recipe.id().value();
        entity.breweryId = recipe.breweryId();
        entity.name = recipe.name().value();
        entity.normalizedName = recipe.name().value().trim().toLowerCase(java.util.Locale.ROOT);
        entity.status = recipe.status().name();
        return entity;
    }
}
