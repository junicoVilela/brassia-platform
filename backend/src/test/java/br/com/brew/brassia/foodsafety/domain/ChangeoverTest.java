package br.com.brew.brassia.foodsafety.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A regra da troca de produto (FDS-001), sem banco: é uma função dos três eixos da matriz. */
class ChangeoverTest {

    private static final Instant USE = Instant.parse("2026-08-10T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final AllergenCode GLUTEN = AllergenCode.of("GLUTEN");
    private static final AllergenCode LACTOSE = AllergenCode.of("LACTOSE");

    @Test
    @DisplayName("equipamento sem uso anterior não tem o que trocar")
    void semUsoAnteriorLibera() {
        var verdict = Changeover.assess(profile(GLUTEN), null, null, null, null, NOW);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.outcome()).isEqualTo(ChangeoverVerdict.Outcome.CLEAR);
    }

    @Test
    @DisplayName("mesmo alergênico dos dois lados não exige limpeza — POP exigido sem motivo se aprende a ignorar")
    void mesmoPerfilNaoExigeTroca() {
        var verdict = Changeover.assess(profile(GLUTEN), profile(GLUTEN), USE, null, null, NOW);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.outcome()).isEqualTo(ChangeoverVerdict.Outcome.CLEAR);
    }

    @Test
    @DisplayName("carga residual sem limpeza nenhuma bloqueia e diz o que precisa sair")
    void cargaResidualSemLimpezaBloqueia() {
        var verdict = Changeover.assess(profile(), profile(GLUTEN), USE, null, null, NOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("allergen_changeover_required");
        assertThat(verdict.allergens()).containsExactly(GLUTEN);
    }

    @Test
    @DisplayName("POP liberado que remove o alergênico certo libera a troca")
    void popCompativelLibera() {
        var evidence = new Changeover.CleaningEvidence("CIP-ALERG", USE.plusSeconds(3600), Set.of(GLUTEN));

        var verdict = Changeover.assess(profile(), profile(GLUTEN), USE, null, evidence, NOW);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.outcome()).isEqualTo(ChangeoverVerdict.Outcome.CLEANED);
    }

    @Test
    @DisplayName("POP que remove outro alergênico não serve, e o que falta vem no veredito")
    void popIncompativelBloqueiaDizendoOQueFalta() {
        var evidence = new Changeover.CleaningEvidence("CIP-LAC", USE.plusSeconds(3600), Set.of(LACTOSE));

        var verdict = Changeover.assess(profile(), profile(GLUTEN, LACTOSE), USE, null, evidence, NOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.allergens()).containsExactly(GLUTEN);
        assertThat(verdict.detail()).contains("CIP-LAC").contains("GLUTEN");
    }

    @Test
    @DisplayName("limpeza anterior ao uso que sujou não comprova a troca")
    void limpezaAnteriorAoUsoNaoServe() {
        var evidence = new Changeover.CleaningEvidence("CIP-ALERG", USE.minusSeconds(3600), Set.of(GLUTEN));

        var verdict = Changeover.assess(profile(), profile(GLUTEN), USE, null, evidence, NOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("allergen_changeover_required");
    }

    @Test
    @DisplayName("limpeza liberada depois do início planejado ainda não aconteceu")
    void limpezaPosteriorAoInicioNaoServe() {
        var evidence = new Changeover.CleaningEvidence("CIP-ALERG", NOW.plusSeconds(3600), Set.of(GLUTEN));

        var verdict = Changeover.assess(profile(), profile(GLUTEN), USE, null, evidence, NOW);

        assertThat(verdict.allowed()).isFalse();
    }

    @Test
    @DisplayName("lacuna de declaração bloqueia quando há troca a avaliar: 'não sei' não vale 'não tem'")
    void lacunaBloqueiaTroca() {
        var verdict = Changeover.assess(incomplete(), profile(GLUTEN), USE, null, null, NOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("allergen_undeclared");
        assertThat(verdict.gaps()).hasSize(1);
    }

    @Test
    @DisplayName("lacuna não bloqueia onde não há troca: a ignorância só pesa onde mudaria a resposta")
    void lacunaNaoBloqueiaSemUsoAnterior() {
        var verdict = Changeover.assess(incomplete(), null, null, null, null, NOW);

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    @DisplayName("linha dedicada que comporta o produto dispensa limpeza de troca")
    void dedicacaoCompativelLibera() {
        var dedication = EquipmentDedication.of(UUID.randomUUID(), Set.of(GLUTEN));

        var verdict = Changeover.assess(profile(GLUTEN), profile(LACTOSE), USE, dedication, null, NOW);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.outcome()).isEqualTo(ChangeoverVerdict.Outcome.DEDICATED);
    }

    @Test
    @DisplayName("linha livre de alergênicos não se resolve com POP: a garantia é o alergênico nunca ter entrado")
    void dedicacaoLivreRecusaAlergenicoMesmoComLimpeza() {
        var dedication = EquipmentDedication.of(UUID.randomUUID(), Set.of());
        var evidence = new Changeover.CleaningEvidence("CIP-ALERG", NOW, Set.of(GLUTEN));

        var verdict = Changeover.assess(profile(GLUTEN), profile(), USE, dedication, evidence, NOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("allergen_dedication_violated");
        assertThat(verdict.allergens()).containsExactly(GLUTEN);
    }

    private static AllergenProfile profile(AllergenCode... codes) {
        var ingredientId = UUID.randomUUID();
        return AllergenProfile.of(List.of(new AllergenProfile.Contribution(ingredientId, "malte",
                AllergenDeclaration.declare(ingredientId, Set.of(codes), UUID.randomUUID(), NOW))));
    }

    private static AllergenProfile incomplete() {
        var ingredientId = UUID.randomUUID();
        return AllergenProfile.of(List.of(new AllergenProfile.Contribution(ingredientId, "malte novo",
                AllergenDeclaration.missing(ingredientId))));
    }
}
