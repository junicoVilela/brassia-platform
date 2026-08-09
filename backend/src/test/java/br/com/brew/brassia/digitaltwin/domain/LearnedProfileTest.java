package br.com.brew.brassia.digitaltwin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O perfil aprendido de uma receita (DTW-001).
 *
 * <p>O que estes testes fixam: o perfil é um <strong>resumo datado de uma amostra nomeada</strong>, e não
 * uma verdade sobre a cervejaria. Ele registra quais lotes leu — o que o torna auditável e reproduzível — e
 * nunca afirma causa.
 */
class LearnedProfileTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private static List<BigDecimal> valores(String... v) {
        return java.util.Arrays.stream(v).map(BigDecimal::new).toList();
    }

    @Test
    @DisplayName("calcula estimativa por métrica e guarda quais lotes foram observados")
    void calculaEGuardaAmostra() {
        var lotes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        var profile = LearnedProfile.compute(CERVEJARIA, RECEITA, 1, Map.of(
                ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94", "90"),
                ProfileMetric.TRANSFER_LOSS_LITERS, valores("3.2", "2.8", "3.5")),
                lotes, OPERADOR, AGORA);

        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).mean())
                .isEqualByComparingTo("92");
        // A amostra fica registrada: é o que permite refazer a conta ou apontar um lote que não deveria
        // estar ali.
        assertThat(profile.observedBatchIds()).isEqualTo(lotes);
        assertThat(profile.computedAt()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("métrica sem observação suficiente NÃO desaparece do perfil")
    void metricaInsuficienteNaoDesaparece() {
        // Ausência declarada é informação; ausência silenciosa é um buraco. Quem lê precisa saber que a
        // perda não foi estimada, e não concluir que ela é zero.
        var profile = LearnedProfile.compute(CERVEJARIA, RECEITA, 1,
                Map.of(ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94", "90")),
                List.of(UUID.randomUUID()), OPERADOR, AGORA);

        var perda = profile.estimateOf(ProfileMetric.TRANSFER_LOSS_LITERS);

        assertThat(perda).isNotNull();
        assertThat(perda.confidence()).isEqualTo(Confidence.INSUFFICIENT);
        assertThat(perda.mean()).isNull();
    }

    @Test
    @DisplayName("todas as métricas conhecidas aparecem, mesmo sem observação nenhuma")
    void todasAsMetricasAparecem() {
        var profile = LearnedProfile.compute(CERVEJARIA, RECEITA, 1, Map.of(),
                List.of(UUID.randomUUID()), OPERADOR, AGORA);

        assertThat(profile.estimates()).containsOnlyKeys(ProfileMetric.values());
        assertThat(profile.hasAnyUsableEstimate()).isFalse();
    }

    @Test
    @DisplayName("um perfil em que nada é estimável ainda é um perfil")
    void perfilSemEstimativaAindaEPerfil() {
        // Ele registra que a tentativa foi feita, sobre quais lotes, e que não deu — a resposta certa para
        // "por que não tenho perfil desta receita?".
        var lote = UUID.randomUUID();

        var profile = LearnedProfile.compute(CERVEJARIA, RECEITA, 1,
                Map.of(ProfileMetric.VOLUME_YIELD_PERCENT, valores("92")),
                List.of(lote), OPERADOR, AGORA);

        assertThat(profile.hasAnyUsableEstimate()).isFalse();
        assertThat(profile.observedBatchIds()).containsExactly(lote);
    }

    @Test
    @DisplayName("perfil sem lote observado é recusado")
    void exigeAmostra() {
        // Um perfil sem amostra não é reproduzível: não há contra o que conferir o número.
        assertThatThrownBy(() -> LearnedProfile.compute(CERVEJARIA, RECEITA, 1, Map.of(),
                List.of(), OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos um lote");
    }

    @Test
    @DisplayName("a versão começa em 1")
    void versaoComecaEmUm() {
        assertThatThrownBy(() -> LearnedProfile.compute(CERVEJARIA, RECEITA, 0, Map.of(),
                List.of(UUID.randomUUID()), OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a amostra gravada é imutável: alterar a lista de fora não muda o perfil")
    void amostraImutavel() {
        // O perfil é evidência do que foi lido. Se a lista pudesse mudar depois, a reprodutibilidade
        // deixaria de valer sem que nada no registro mudasse.
        var mutavel = new java.util.ArrayList<>(List.of(UUID.randomUUID()));
        var profile = LearnedProfile.compute(CERVEJARIA, RECEITA, 1, Map.of(), mutavel, OPERADOR, AGORA);

        mutavel.add(UUID.randomUUID());

        assertThat(profile.observedBatchIds()).hasSize(1);
    }

    @Test
    @DisplayName("as métricas descrevem grandezas observadas, nunca causas")
    void metricasNaoAfirmamCausa() {
        // A fronteira contra correlação-vira-causa está no tipo: não existe métrica que nomeie um porquê.
        for (var metric : ProfileMetric.values()) {
            assertThat(metric.label()).doesNotContainIgnoringCase("porque");
            assertThat(metric.label()).doesNotContainIgnoringCase("causa");
            assertThat(metric.label()).doesNotContainIgnoringCase("devido");
        }
        // E o rendimento não se chama "eficiência": quem lê "eficiência 74%" pensa em extração de açúcar,
        // que é outra grandeza.
        assertThat(ProfileMetric.VOLUME_YIELD_PERCENT.label()).contains("Rendimento");
    }
}
