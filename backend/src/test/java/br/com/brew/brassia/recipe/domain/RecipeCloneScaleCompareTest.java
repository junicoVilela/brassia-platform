package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeCloneScaleCompareTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final UUID MALT = UUID.randomUUID();
    private static final BigDecimal CAPACITY = new BigDecimal("500");

    private Recipe recipe(String batch, String maltKg) {
        var items = List.of(
                new RecipeItem(MALT, RecipeStage.MASH, new BigDecimal(maltKg), RecipeUnit.KG, null, null),
                new RecipeItem(UUID.randomUUID(), RecipeStage.BOIL, new BigDecimal("30"), RecipeUnit.G, 60, null));
        return Recipe.draft(BREWERY, "Base", EQUIPMENT, new BigDecimal(batch), CAPACITY, RecipeTargets.none(),
                60, items);
    }

    @Test
    void clonesIndependentDraft() {
        var base = recipe("400", "20");
        var clone = base.cloneAs(new RecipeName("Clone"));

        assertThat(clone.status()).isEqualTo(RecipeStatus.DRAFT);
        assertThat(clone.name().value()).isEqualTo("Clone");
        assertThat(clone.version()).isEqualTo(1);
        assertThat(clone.previousRecipeId()).isNull();
        assertThat(clone.id().value()).isNotEqualTo(base.id().value());
        assertThat(clone.batchVolumeLiters()).isEqualByComparingTo("400");
    }

    @Test
    void scalesQuantitiesByVolumeRatio() {
        var base = recipe("400", "20");
        var scaled = base.scaleTo(new RecipeName("Big"), new BigDecimal("500"), CAPACITY);

        assertThat(scaled.batchVolumeLiters()).isEqualByComparingTo("500");
        // fator 500/400 = 1.25 → malte 20 → 25; lúpulo 30 → 37.5
        assertThat(scaled.items().get(0).quantity()).isEqualByComparingTo("25.0000");
        assertThat(scaled.items().get(1).quantity()).isEqualByComparingTo("37.5000");
    }

    @Test
    void scaleRejectsVolumeAboveCapacity() {
        var base = recipe("400", "20");
        assertThatThrownBy(() -> base.scaleTo(new RecipeName("TooBig"), new BigDecimal("600"), CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacidade");
    }

    @Test
    void comparesFieldByField() {
        var a = recipe("400", "20");
        var b = a.scaleTo(new RecipeName("Big"), new BigDecimal("500"), CAPACITY);

        var result = RecipeComparison.compare(a, b);
        var fields = result.differences().stream().map(RecipeComparison.Difference::field).toList();
        assertThat(fields).contains("name", "batchVolumeLiters");
        // o item de mostura mudou de quantidade → aparece uma diferença item[MASH:...]
        assertThat(fields).anyMatch(f -> f.startsWith("item[MASH:"));
    }

    @Test
    void identicalRecipesHaveNoDifferences() {
        var a = recipe("400", "20");
        var clone = a.cloneAs(a.name());
        var result = RecipeComparison.compare(a, clone);
        // mesmo nome/volume/itens → sem diferenças escalares nem de item
        assertThat(result.differences()).isEmpty();
    }
}
