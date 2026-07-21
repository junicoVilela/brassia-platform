package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeTest {
    @Test
    void shouldCreateDraftWithNormalizedName() {
        var recipe = Recipe.draft(UUID.randomUUID(), "  Hoppy Lager  ");
        assertThat(recipe.name().value()).isEqualTo("Hoppy Lager");
        assertThat(recipe.status()).isEqualTo(RecipeStatus.DRAFT);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Recipe.draft(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
