package br.com.brew.brassia.foodsafety.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A distinção que estrutura a história: declarado isento ≠ não declarado (FDS-001). */
class AllergenProfileTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final AllergenCode GLUTEN = AllergenCode.of("GLUTEN");

    @Test
    @DisplayName("declarado isento é afirmação: perfil vazio e completo")
    void declaradoIsentoEhCompleto() {
        var id = UUID.randomUUID();
        var profile = AllergenProfile.of(List.of(new AllergenProfile.Contribution(id, "lúpulo",
                AllergenDeclaration.declare(id, Set.of(), UUID.randomUUID(), NOW))));

        assertThat(profile.allergens()).isEmpty();
        assertThat(profile.complete()).isTrue();
    }

    @Test
    @DisplayName("não declarado também dá conjunto vazio, mas o perfil não é completo")
    void naoDeclaradoNaoEhCompleto() {
        var id = UUID.randomUUID();
        var profile = AllergenProfile.of(List.of(
                new AllergenProfile.Contribution(id, "malte", AllergenDeclaration.missing(id))));

        assertThat(profile.allergens()).isEmpty();
        assertThat(profile.complete()).isFalse();
        assertThat(profile.gaps()).singleElement()
                .satisfies(gap -> assertThat(gap.label()).isEqualTo("malte"));
    }

    @Test
    @DisplayName("um ingrediente declarado não cobre o vizinho sem declaração")
    void lacunaSobreviveAoIngredienteDeclarado() {
        var declaredId = UUID.randomUUID();
        var missingId = UUID.randomUUID();
        var profile = AllergenProfile.of(List.of(
                new AllergenProfile.Contribution(declaredId, "malte",
                        AllergenDeclaration.declare(declaredId, Set.of(GLUTEN), UUID.randomUUID(), NOW)),
                new AllergenProfile.Contribution(missingId, "adjunto", AllergenDeclaration.missing(missingId))));

        assertThat(profile.allergens()).containsExactly(GLUTEN);
        assertThat(profile.complete()).isFalse();
    }

    @Test
    @DisplayName("composição indisponível é ignorância, não isenção")
    void composicaoDesconhecidaNaoAfirmaIsencao() {
        var profile = AllergenProfile.unknown("lote inexistente");

        assertThat(profile.complete()).isFalse();
        assertThat(profile.gaps()).singleElement()
                .satisfies(gap -> assertThat(gap.ingredientId()).isNull());
    }

    @Test
    @DisplayName("alergênico sem declaração que responda por ele é estado impossível")
    void alergenicoSemDeclaracaoEhRecusado() {
        assertThatThrownBy(() -> AllergenDeclaration.declare(UUID.randomUUID(), Set.of(GLUTEN), null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("o código normaliza a caixa: GLUTEN e gluten não podem ser dois alergênicos")
    void codigoNormaliza() {
        assertThat(AllergenCode.of(" gluten ")).isEqualTo(GLUTEN);
        assertThatThrownBy(() -> AllergenCode.of("glúten com espaço"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
