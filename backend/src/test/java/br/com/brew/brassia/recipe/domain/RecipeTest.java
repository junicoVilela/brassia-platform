package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final BigDecimal CAPACITY = new BigDecimal("500");

    private RecipeItem malt(String pct) {
        return new RecipeItem(UUID.randomUUID(), RecipeStage.MASH, new BigDecimal("5"), RecipeUnit.KG, null,
                pct == null ? null : new BigDecimal(pct));
    }

    private RecipeItem hop() {
        return new RecipeItem(UUID.randomUUID(), RecipeStage.BOIL, new BigDecimal("30"), RecipeUnit.G, 60, null);
    }

    private Recipe draft(BigDecimal batch, List<RecipeItem> items) {
        return Recipe.draft(BREWERY, "Hoppy Lager", EQUIPMENT, batch, CAPACITY, RecipeTargets.none(), 60, items);
    }

    @Test
    void createsDraftWithComposition() {
        var recipe = draft(new BigDecimal("400"), List.of(malt("70"), malt("30"), hop()));

        assertThat(recipe.status()).isEqualTo(RecipeStatus.DRAFT);
        assertThat(recipe.name().value()).isEqualTo("Hoppy Lager");
        assertThat(recipe.items()).hasSize(3);
        assertThat(recipe.version()).isZero();
    }

    @Test
    void rejectsBatchAboveCapacity() {
        assertThatThrownBy(() -> draft(new BigDecimal("600"), List.of(malt(null), hop())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacidade");
    }

    @Test
    void rejectsMashPercentagesThatDoNotSumTo100() {
        assertThatThrownBy(() -> draft(new BigDecimal("400"), List.of(malt("70"), malt("20"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("somar 100");
    }

    @Test
    void rejectsPartialMashPercentages() {
        assertThatThrownBy(() -> draft(new BigDecimal("400"), List.of(malt("100"), malt(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precisam de percentual");
    }

    @Test
    void allowsMashWithoutAnyPercentage() {
        var recipe = draft(new BigDecimal("400"), List.of(malt(null), malt(null), hop()));
        assertThat(recipe.items()).hasSize(3);
    }

    @Test
    void rejectsEmptyItemsAndNonPositiveBatch() {
        assertThatThrownBy(() -> draft(new BigDecimal("400"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft(BigDecimal.ZERO, List.of(malt(null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidItemQuantityStageUnit() {
        assertThatThrownBy(() -> new RecipeItem(UUID.randomUUID(), RecipeStage.BOIL, BigDecimal.ZERO,
                RecipeUnit.G, 60, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeStage.of("STEEP")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecipeUnit.of("OZ")).isInstanceOf(IllegalArgumentException.class);
    }
}
