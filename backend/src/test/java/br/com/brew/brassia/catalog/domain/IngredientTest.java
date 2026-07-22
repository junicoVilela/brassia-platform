package br.com.brew.brassia.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngredientTest {

    private static final UUID BREWERY = UUID.randomUUID();

    private Ingredient malt(Map<String, String> attributes) {
        return Ingredient.register(BREWERY, IngredientType.MALT, new IngredientCode("pilsen"),
                new IngredientName("Malte Pilsen"), MeasurementUnit.KG, MeasurementUnit.KG, attributes);
    }

    @Test
    void registersWithAllowedAttributesNormalizingCode() {
        var ingredient = malt(Map.of("colorEbc", "3.5", "potentialSg", "1.037"));

        assertThat(ingredient.code().value()).isEqualTo("PILSEN");
        assertThat(ingredient.type()).isEqualTo(IngredientType.MALT);
        assertThat(ingredient.active()).isTrue();
        assertThat(ingredient.version()).isZero();
        assertThat(ingredient.attributes()).containsEntry("colorEbc", "3.5");
    }

    @Test
    void rejectsAttributeNotAllowedForType() {
        assertThatThrownBy(() -> malt(Map.of("alphaAcid", "12")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    void rejectsBlankAttributeValue() {
        assertThatThrownBy(() -> malt(Map.of("colorEbc", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem valor");
    }

    @Test
    void acceptsNullAttributesAsEmpty() {
        var ingredient = malt(null);
        assertThat(ingredient.attributes()).isEmpty();
    }

    @Test
    void rejectsInvalidUnitAndType() {
        assertThatThrownBy(() -> MeasurementUnit.of("TON")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngredientType.of("WOOD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateKeepsTypeAndRevalidatesAttributes() {
        var ingredient = malt(Map.of("colorEbc", "3.5"));

        ingredient.update(new IngredientName("Malte Pilsen DE"), MeasurementUnit.G, MeasurementUnit.KG,
                Map.of("potentialSg", "1.038"));
        assertThat(ingredient.name().value()).isEqualTo("Malte Pilsen DE");
        assertThat(ingredient.useUnit()).isEqualTo(MeasurementUnit.G);
        assertThat(ingredient.attributes()).containsOnlyKeys("potentialSg");

        assertThatThrownBy(() -> ingredient.update(new IngredientName("X"), MeasurementUnit.KG,
                MeasurementUnit.KG, Map.of("alphaAcid", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attributesAreImmutableFromOutside() {
        var ingredient = malt(Map.of("colorEbc", "3.5"));
        assertThatThrownBy(() -> ingredient.attributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
