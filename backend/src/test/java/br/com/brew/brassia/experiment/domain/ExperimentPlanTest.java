package br.com.brew.brassia.experiment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * O lote dividido (EXP-001).
 *
 * <p>O que estes testes fixam: uma variável isolada é condição de <em>existência</em> do plano, não aviso;
 * e uma conclusão sem limitações não é algo que se possa escrever.
 */
class ExperimentPlanTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID CONTROLE = UUID.randomUUID();
    private static final UUID VARIANTE = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T12:00:00Z");

    private static ExperimentFactor igual(String nome, String valor) {
        return new ExperimentFactor(nome, valor, valor);
    }

    private static ExperimentFactor diferente(String nome, String controle, String variante) {
        return new ExperimentFactor(nome, controle, variante);
    }

    private static ExperimentPlan plano(List<ExperimentFactor> fatores, Set<String> medicoes,
            boolean sensorial, boolean cego) {
        return ExperimentPlan.plan(UUID.randomUUID(), CERVEJARIA, RECEITA,
                "Dry hopping a frio preserva mais aroma cítrico", CONTROLE, VARIANTE, fatores,
                medicoes, sensorial, cego, AUTOR, AGORA);
    }

    private static ExperimentPlan planoPadrao() {
        return plano(List.of(
                diferente("Temperatura de dry hopping", "20 C", "4 C"),
                igual("Levedura", "US-05"),
                igual("Água", "Perfil A")),
                Set.of("AROMA_INTENSITY", "IBU"), true, true);
    }

    @Nested
    @DisplayName("uma variável isolada")
    class VariavelIsolada {

        @Test
        @DisplayName("plano com exatamente um fator diferente é aceito")
        void umFatorDiferente() {
            var plano = planoPadrao();

            assertThat(plano.isolatedVariable().name()).isEqualTo("Temperatura de dry hopping");
            assertThat(plano.status()).isEqualTo(ExperimentStatus.PLANNED);
        }

        @Test
        @DisplayName("DOIS FATORES DIFERENTES NÃO CRIAM PLANO — e o erro diz quais são")
        void doisFatoresRecusados() {
            // Com dois fatores, todo resultado tem duas explicações e nenhuma pode ser descartada. Aceitar
            // e avisar depois seria pior: o aviso se perde e o número fica.
            assertThatExceptionOfType(ConfoundedExperimentException.class)
                    .isThrownBy(() -> plano(List.of(
                            diferente("Temperatura", "20 C", "4 C"),
                            diferente("Levedura", "US-05", "S-04")),
                            Set.of("IBU"), true, true))
                    .satisfies(e -> assertThat(e.differingFactors())
                            .containsExactly("Temperatura", "Levedura"));
        }

        @Test
        @DisplayName("nenhum fator diferente também é recusado")
        void nenhumFatorRecusado() {
            // Dois lotes idênticos não testam hipótese nenhuma; o plano ficaria parecendo um experimento
            // à espera de um resultado.
            assertThatExceptionOfType(ConfoundedExperimentException.class)
                    .isThrownBy(() -> plano(List.of(igual("Levedura", "US-05")),
                            Set.of("IBU"), true, true))
                    .satisfies(e -> assertThat(e.differingFactors()).isEmpty());
        }

        @Test
        @DisplayName("controle e variante não podem ser o mesmo lote")
        void mesmoLoteRecusado() {
            assertThatIllegalArgumentException().isThrownBy(() -> ExperimentPlan.plan(
                    UUID.randomUUID(), CERVEJARIA, RECEITA, "h", CONTROLE, CONTROLE,
                    List.of(diferente("T", "20", "4")), Set.of("IBU"), true, true, AUTOR, AGORA));
        }

        @Test
        @DisplayName("hipótese vazia é recusada")
        void hipoteseVaziaRecusada() {
            // Sem hipótese antes, qualquer diferença vira "o que a gente queria descobrir".
            assertThatIllegalArgumentException().isThrownBy(() -> ExperimentPlan.plan(
                    UUID.randomUUID(), CERVEJARIA, RECEITA, "   ", CONTROLE, VARIANTE,
                    List.of(diferente("T", "20", "4")), Set.of("IBU"), true, true, AUTOR, AGORA));
        }
    }

    @Nested
    @DisplayName("limitações")
    class Limitacoes {

        @Test
        @DisplayName("SINGLE_PAIR está SEMPRE presente, no melhor desenho possível")
        void parUnicoSempre() {
            // Sensorial cego, duas grandezas: o melhor que um lote dividido consegue ser. Ainda é n=1.
            var plano = plano(List.of(diferente("T", "20 C", "4 C")),
                    Set.of("AROMA", "IBU"), true, true);

            assertThat(plano.limitations()).containsExactly(Limitation.SINGLE_PAIR);
        }

        @Test
        @DisplayName("sensorial não cego é limitação declarada")
        void sensorialNaoCego() {
            var plano = plano(List.of(diferente("T", "20 C", "4 C")),
                    Set.of("AROMA", "IBU"), true, false);

            assertThat(plano.limitations())
                    .containsExactlyInAnyOrder(Limitation.SINGLE_PAIR, Limitation.SENSORY_NOT_BLIND);
        }

        @Test
        @DisplayName("sem sensorial e sem medição planejada acumula as duas")
        void semSensorialSemMedicao() {
            var plano = plano(List.of(diferente("T", "20 C", "4 C")), Set.of(), false, false);

            assertThat(plano.limitations()).containsExactlyInAnyOrder(
                    Limitation.SINGLE_PAIR, Limitation.NO_SENSORY, Limitation.NO_PLANNED_MEASUREMENT);
        }

        @Test
        @DisplayName("uma grandeza só avisa que efeito lateral passaria despercebido")
        void grandezaUnica() {
            var plano = plano(List.of(diferente("T", "20 C", "4 C")), Set.of("IBU"), true, true);

            assertThat(plano.limitations())
                    .containsExactlyInAnyOrder(Limitation.SINGLE_PAIR, Limitation.SINGLE_METRIC);
        }

        @Test
        @DisplayName("toda limitação tem descrição que diz o que não se pode afirmar")
        void descricoesUteis() {
            for (var limitation : Limitation.values()) {
                assertThat(limitation.description()).isNotBlank().hasSizeGreaterThan(40);
            }
        }
    }

    @Nested
    @DisplayName("ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("A CONCLUSÃO CARREGA AS LIMITAÇÕES SEM NINGUÉM AS INFORMAR")
        void conclusaoCarregaLimitacoes() {
            // Limitação que depende de alguém lembrar de escrevê-la some justamente quando o resultado
            // agrada. Estas vêm do plano.
            var plano = plano(List.of(diferente("T", "20 C", "4 C")), Set.of("IBU"), true, false);
            plano.start();

            plano.conclude(true, "Variante com aroma mais intenso na mesa de prova", AUTOR, AGORA);

            var conclusao = plano.conclusion().orElseThrow();
            assertThat(conclusao.limitations()).containsExactlyInAnyOrder(
                    Limitation.SINGLE_PAIR, Limitation.SENSORY_NOT_BLIND, Limitation.SINGLE_METRIC);
            assertThat(plano.status()).isEqualTo(ExperimentStatus.CONCLUDED);
        }

        @Test
        @DisplayName("conclusão nunca é 'provado': o campo se chama supported")
        void suportadoNaoProvado() {
            var plano = planoPadrao();
            plano.start();

            plano.conclude(false, "Sem diferença perceptível", AUTOR, AGORA);

            assertThat(plano.conclusion().orElseThrow().supported()).isFalse();
        }

        @Test
        @DisplayName("observação vazia é recusada")
        void observacaoVaziaRecusada() {
            var plano = planoPadrao();
            plano.start();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> plano.conclude(true, "  ", AUTOR, AGORA));
        }

        @Test
        @DisplayName("não se conclui o que não começou")
        void concluirSemComecar() {
            var plano = planoPadrao();

            assertThatExceptionOfType(ExperimentPlan.IllegalExperimentTransitionException.class)
                    .isThrownBy(() -> plano.conclude(true, "algo", AUTOR, AGORA))
                    .satisfies(e -> assertThat(e.current()).isEqualTo(ExperimentStatus.PLANNED));
        }

        @Test
        @DisplayName("não se começa duas vezes")
        void comecarDuasVezes() {
            var plano = planoPadrao();
            plano.start();

            assertThatExceptionOfType(ExperimentPlan.IllegalExperimentTransitionException.class)
                    .isThrownBy(plano::start);
        }

        @Test
        @DisplayName("concluído não se abandona")
        void concluidoNaoAbandona() {
            // Abandonar depois de concluir apagaria uma conclusão registrada pela porta dos fundos.
            var plano = planoPadrao();
            plano.start();
            plano.conclude(true, "algo", AUTOR, AGORA);

            assertThatExceptionOfType(ExperimentPlan.IllegalExperimentTransitionException.class)
                    .isThrownBy(plano::abandon);
        }

        @Test
        @DisplayName("planejado pode ser abandonado e continua no histórico")
        void abandonoPreservado() {
            var plano = planoPadrao();

            plano.abandon();

            assertThat(plano.status()).isEqualTo(ExperimentStatus.ABANDONED);
            assertThat(plano.hypothesis()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("fatores")
    class Fatores {

        @Test
        @DisplayName("os fatores iguais ficam registrados junto com o que difere")
        void fatoresIguaisRegistrados() {
            // "O resto ficou igual" é a afirmação sobre a qual toda a conclusão se apoia; sem os iguais
            // declarados, ninguém pode conferi-la depois.
            var plano = planoPadrao();

            assertThat(plano.factors()).hasSize(3);
            assertThat(plano.factors().stream().filter(f -> !f.differs()).toList()).hasSize(2);
        }

        @Test
        @DisplayName("valor em branco é recusado")
        void valorEmBrancoRecusado() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ExperimentFactor("Levedura", " ", "S-04"));
        }
    }
}
