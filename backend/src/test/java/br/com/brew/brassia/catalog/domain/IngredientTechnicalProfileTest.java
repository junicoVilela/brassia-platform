package br.com.brew.brassia.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngredientTechnicalProfileTest {

    private static IngredientTechnicalProfile draft() {
        Map<String, PropertyRange> ranges = new LinkedHashMap<>();
        ranges.put("alphaAcid", new PropertyRange(new BigDecimal("5.5"), new BigDecimal("7.5"), "%"));
        ranges.put("cohumulone", PropertyRange.none()); // vazia: deve ser descartada
        return IngredientTechnicalProfile.draft(UUID.randomUUID(), UUID.randomUUID(), "Yakima", "US", "PELLET",
                "BITTERING", null, null, ranges, List.of("cítrico", " ", "resinoso"), UUID.randomUUID(),
                "Yakima Chief");
    }

    @Test
    void draftsWithProvenanceAndSanitizedData() {
        var profile = draft();
        assertThat(profile.status()).isEqualTo(TechnicalProfileStatus.DRAFT);
        assertThat(profile.sourceName()).isEqualTo("Yakima Chief");
        // Faixa vazia descartada; descritor em branco removido.
        assertThat(profile.ranges()).containsOnlyKeys("alphaAcid");
        assertThat(profile.descriptors()).containsExactly("cítrico", "resinoso");
    }

    @Test
    void publishesThenBlocksRepublish() {
        var profile = draft();
        profile.publish();
        assertThat(profile.isPublished()).isTrue();
        assertThatThrownBy(profile::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já publicado");
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> new PropertyRange(new BigDecimal("8"), new BigDecimal("5"), "%"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
