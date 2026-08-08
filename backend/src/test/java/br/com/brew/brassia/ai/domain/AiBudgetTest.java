package br.com.brew.brassia.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O teto de gasto com IA (AIA-001).
 *
 * <p>O que estes testes fixam é o risco desta sprint que nenhum teste funcional mostra: custo
 * imprevisível. A regra é sempre a mesma — o teto é verificado <em>antes</em> de gastar, e o limite é
 * sobre o pior caso, não sobre o caso esperado.
 */
class AiBudgetTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    @DisplayName("cabe no mês quando o gasto somado ao pior caso não passa do teto")
    void cabeQuandoSobraEspaco() {
        var budget = budget("10.00", "4.00");

        assertThatCode(() -> budget.requireHeadroom(new BigDecimal("6.00"))).doesNotThrowAnyException();
        assertThat(budget.remaining()).isEqualByComparingTo("6.00");
        assertThat(budget.exhausted()).isFalse();
    }

    @Test
    @DisplayName("gastar exatamente o teto ainda cabe; um centavo além não")
    void oLimiteEInclusivo() {
        var budget = budget("10.00", "9.99");

        assertThatCode(() -> budget.requireHeadroom(new BigDecimal("0.01"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> budget.requireHeadroom(new BigDecimal("0.02")))
                .isInstanceOf(AiBudgetExceededException.class);
    }

    @Test
    @DisplayName("estourado, a recusa carrega teto e gasto — quem lê sabe de quanto precisa")
    void recusaDizOsNumeros() {
        var budget = budget("10.00", "10.00");

        assertThatThrownBy(() -> budget.requireHeadroom(new BigDecimal("0.01")))
                .isInstanceOf(AiBudgetExceededException.class)
                .satisfies(thrown -> {
                    var exceeded = (AiBudgetExceededException) thrown;
                    assertThat(exceeded.monthlyLimit()).isEqualByComparingTo("10.00");
                    assertThat(exceeded.spent()).isEqualByComparingTo("10.00");
                });
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    @DisplayName("gasto acima do teto não devolve saldo negativo: devolve zero")
    void saldoNuncaEhNegativo() {
        // Acontece de verdade: baixar o teto abaixo do que já foi gasto é um freio legítimo, e um saldo
        // negativo na interface só confundiria quem está tentando parar o gasto.
        var budget = budget("10.00", "12.50");

        assertThat(budget.remaining()).isEqualByComparingTo("0");
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    @DisplayName("redefinir o teto preserva o gasto do mês: o passado não se apaga mudando o limite")
    void redefinirNaoZeraOGasto() {
        var budget = budget("10.00", "4.00");

        var redefined = budget.redefine(new BigDecimal("30.00"), ACTOR, Instant.now());

        assertThat(redefined.monthlyLimit()).isEqualByComparingTo("30.00");
        assertThat(redefined.spentThisMonth()).isEqualByComparingTo("4.00");
        assertThat(redefined.updatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("baixar o teto abaixo do que já foi gasto é permitido e para as chamadas")
    void baixarOTetoFreiaNaHora() {
        var budget = budget("100.00", "40.00");

        var tightened = budget.redefine(new BigDecimal("10.00"), ACTOR, Instant.now());

        assertThat(tightened.exhausted()).isTrue();
        assertThatThrownBy(() -> tightened.requireHeadroom(new BigDecimal("0.01")))
                .isInstanceOf(AiBudgetExceededException.class);
    }

    @Test
    @DisplayName("teto negativo não existe")
    void tetoNegativoRecusado() {
        var budget = budget("10.00", "0.00");

        assertThatThrownBy(() -> budget.redefine(new BigDecimal("-1.00"), ACTOR, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("alteração sem autor não existe: teto de gasto é decisão de alguém")
    void alteracaoExigeAutor() {
        var budget = budget("10.00", "0.00");

        assertThatThrownBy(() -> budget.redefine(new BigDecimal("20.00"), null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    private static AiBudget budget(String limit, String spent) {
        return AiBudget.defaultOf(BREWERY, new BigDecimal(limit), "USD", new BigDecimal(spent));
    }
}
