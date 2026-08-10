package br.com.brew.brassia.optimization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A corrida do otimizador (OPT-001).
 *
 * <p>O que estes testes fixam: método e versão viajam com o resultado, a IA não tem por onde mexer no
 * score, e inviabilidade é uma resposta com conteúdo — não um erro.
 */
class OptimizationRunTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T12:00:00Z");
    private static final String CATALOGO = "catalog@2026-08-01";

    private static Candidate candidata(String label, String score) {
        return new Candidate(label, List.of(), new BigDecimal("3.20"), new BigDecimal("32"),
                new BigDecimal("12"), new BigDecimal(score), List.of());
    }

    private static OptimizationRun resolvida(Candidate... candidatas) {
        return OptimizationRun.solved(UUID.randomUUID(), CERVEJARIA, RECEITA, 3, Objective.COST,
                List.of(), SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, CATALOGO, null,
                List.of(candidatas), ATOR, AGORA);
    }

    @Nested
    @DisplayName("reprodutibilidade")
    class Reprodutibilidade {

        @Test
        @DisplayName("método e versão do catálogo viajam com o resultado")
        void metodoEVersao() {
            // Sem eles, seis meses depois ninguém diz se o número mudou porque o catálogo mudou ou
            // porque o solver mudou.
            var run = resolvida(candidata("A", "10"));

            assertThat(run.method()).isEqualTo(SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION);
            assertThat(run.catalogVersion()).isEqualTo(CATALOGO);
            assertThat(run.recipeVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("MÉTODO DETERMINÍSTICO NÃO ACEITA SEMENTE")
        void deterministicoSemSemente() {
            // Semente num método que não a usa sugeriria variação inexistente — o registro mentiria.
            assertThatIllegalArgumentException().isThrownBy(() -> OptimizationRun.solved(
                    UUID.randomUUID(), CERVEJARIA, RECEITA, 3, Objective.COST, List.of(),
                    SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, CATALOGO, 42L,
                    List.of(candidata("A", "10")), ATOR, AGORA));
        }

        @Test
        @DisplayName("a ausência de semente é registrada, não omitida")
        void ausenciaDeSementeRegistrada() {
            var run = resolvida(candidata("A", "10"));

            assertThat(run.seed()).isEmpty();
            assertThat(run.method().usesSeed()).isFalse();
        }
    }

    @Nested
    @DisplayName("a IA explica, não decide")
    class FronteiraDaIa {

        @Test
        @DisplayName("A EXPLICAÇÃO NÃO TEM POR ONDE ALTERAR O SCORE")
        void explicacaoNaoAlteraScore() {
            // Se pudesse, a explicação deixaria de explicar o cálculo e passaria a ser parte dele.
            var run = resolvida(candidata("A", "10"), candidata("B", "8"));
            var antes = run.candidates().stream().map(Candidate::score).toList();

            run.explain("A troca do malte reduz o custo mantendo a cor dentro da faixa.");

            assertThat(run.candidates().stream().map(Candidate::score)).isEqualTo(antes);
            assertThat(run.explanation()).isPresent();
        }

        @Test
        @DisplayName("explain só recebe texto — nenhum número entra por ela")
        void explainSoRecebeTexto() throws Exception {
            // A garantia é estrutural, e o teste vigia a estrutura: um parâmetro numérico aqui seria o
            // caminho por onde a IA passaria a influenciar o resultado.
            var metodo = OptimizationRun.class.getMethod("explain", String.class);

            assertThat(metodo.getParameterCount()).isEqualTo(1);
            assertThat(metodo.getReturnType()).isEqualTo(void.class);
        }

        @Test
        @DisplayName("explicação vazia é recusada")
        void explicacaoVazia() {
            var run = resolvida(candidata("A", "10"));

            assertThatIllegalArgumentException().isThrownBy(() -> run.explain("   "));
        }
    }

    @Nested
    @DisplayName("inviabilidade é resposta")
    class Inviabilidade {

        private OptimizationRun inviavel() {
            return OptimizationRun.infeasible(UUID.randomUUID(), CERVEJARIA, RECEITA, 3,
                    Objective.COST, List.of(), SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, CATALOGO,
                    null, new Infeasible(List.of("MAX_COST_PER_LITER", "IBU_RANGE"),
                            "Nenhuma combinação respeita o teto de custo mantendo o IBU na faixa."),
                    ATOR, AGORA);
        }

        @Test
        @DisplayName("diz QUAIS restrições se contradizem")
        void dizQuaisSeContradizem() {
            // "Inviável" sozinho manda a pessoa afrouxar tudo ao acaso.
            var run = inviavel();

            assertThat(run.feasible()).isFalse();
            assertThat(run.infeasible().orElseThrow().conflictingConstraints())
                    .containsExactly("MAX_COST_PER_LITER", "IBU_RANGE");
            assertThat(run.best()).isEmpty();
        }

        @Test
        @DisplayName("corrida resolvida sem alternativa é recusada")
        void resolvidaSemAlternativa() {
            // Seria inviabilidade sem o nome — e sem a explicação que a torna acionável.
            assertThatIllegalArgumentException().isThrownBy(() -> OptimizationRun.solved(
                    UUID.randomUUID(), CERVEJARIA, RECEITA, 3, Objective.COST, List.of(),
                    SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, CATALOGO, null, List.of(),
                    ATOR, AGORA));
        }

        @Test
        @DisplayName("não se aplica alternativa de corrida inviável")
        void naoAplicaInviavel() {
            var run = inviavel();

            assertThatIllegalStateException().isThrownBy(() -> run.markApplied(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("aplicação exige nova versão de receita")
    class Aplicacao {

        @Test
        @DisplayName("A CORRIDA REGISTRA QUE ALGUÉM APLICOU — ela não aplica")
        void registraNaoAplica() {
            // Quem cria a versão é o módulo de receita, sob revisão humana. Se o otimizador pudesse
            // escrever na receita, "revisado" viraria um campo que alguém marca.
            var run = resolvida(candidata("A", "10"));
            var novaVersao = UUID.randomUUID();

            run.markApplied(novaVersao);

            assertThat(run.appliedRecipeVersionId()).contains(novaVersao);
        }

        @Test
        @DisplayName("não se aplica duas vezes")
        void naoAplicaDuasVezes() {
            var run = resolvida(candidata("A", "10"));
            run.markApplied(UUID.randomUUID());

            assertThatIllegalStateException().isThrownBy(() -> run.markApplied(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("restrições e trade-offs")
    class Restricoes {

        @Test
        @DisplayName("faixa invertida é recusada")
        void faixaInvertida() {
            assertThatIllegalArgumentException().isThrownBy(() -> OptimizationConstraint.range(
                    ConstraintKind.IBU_RANGE, new BigDecimal("40"), new BigDecimal("30")));
        }

        @Test
        @DisplayName("restrição de ingrediente precisa dizer qual")
        void ingredienteObrigatorio() {
            assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                    () -> new OptimizationConstraint(ConstraintKind.KEEP_INGREDIENT, null, null, null));
        }

        @Test
        @DisplayName("admits decide dentro/fora — a candidata é descartada, não penalizada")
        void admiteOuDescarta() {
            // Restrição não entra no score: um peso alto o bastante sempre acabaria comprando a violação.
            var faixa = OptimizationConstraint.range(ConstraintKind.IBU_RANGE,
                    new BigDecimal("30"), new BigDecimal("40"));

            assertThat(faixa.admits(new BigDecimal("35"))).isTrue();
            assertThat(faixa.admits(new BigDecimal("30"))).isTrue();
            assertThat(faixa.admits(new BigDecimal("41"))).isFalse();
            assertThat(faixa.admits(null)).isFalse();
        }

        @Test
        @DisplayName("o trade-off carrega o valor original e o da candidata")
        void tradeOffComparavel() {
            // "8% mais barata" sem dizer que a cor mudou 4 EBC faz escolher sem saber o que se troca.
            var trade = new Candidate.TradeOff("Cor (EBC)", "A cerveja fica mais escura",
                    new BigDecimal("12"), new BigDecimal("16"));

            assertThat(trade.originalValue()).isEqualByComparingTo("12");
            assertThat(trade.candidateValue()).isEqualByComparingTo("16");
        }
    }
}
