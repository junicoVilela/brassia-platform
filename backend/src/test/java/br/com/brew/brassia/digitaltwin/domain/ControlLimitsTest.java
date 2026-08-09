package br.com.brew.brassia.digitaltwin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os limites que o processo tem (SPC-001).
 *
 * <p>O que estes testes fixam é o critério da história: <strong>limite de controle não é
 * especificação</strong>. A diferença não é de fórmula, é de origem — especificação se escolhe, controle se
 * calcula.
 */
class ControlLimitsTest {

    /** Vinte pontos oscilando em torno de 10, que é o mínimo para calcular. */
    private static List<BigDecimal> historico() {
        return IntStream.range(0, 20)
                .mapToObj(i -> new BigDecimal(i % 2 == 0 ? "9" : "11"))
                .toList();
    }

    @Test
    @DisplayName("os limites são calculados do histórico, três sigmas em volta da média")
    void calculaTresSigmas() {
        var limits = ControlLimits.from(historico());

        assertThat(limits.centerLine()).isEqualByComparingTo("10");
        // σ ≈ 1,026 sobre esta série; 3σ ≈ 3,08.
        assertThat(limits.lowerControlLimit()).isLessThan(new BigDecimal("7.1"));
        assertThat(limits.upperControlLimit()).isGreaterThan(new BigDecimal("12.9"));
        assertThat(limits.sampleSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("NÃO HÁ COMO CONSTRUIR COM UM LIMITE ESCOLHIDO: só existe fábrica que calcula")
    void naoHaComoInjetarEspecificacao() {
        // A fronteira contra "usar a especificação como limite de controle" está na ausência de um caminho
        // que a aceite. O construtor canônico do record existe, mas nenhuma API do módulo o expõe para
        // receber números de fora — o caminho público é `from(observações)`.
        var metodos = java.util.Arrays.stream(ControlLimits.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(metodos).containsExactly("from");
    }

    @Test
    @DisplayName("histórico curto é RECUSADO, não vira limite frouxo")
    void historicoCurtoERecusado() {
        // Limites sobre cinco pontos passam qualquer coisa, e um controle que nunca dispara parece um
        // processo saudável.
        var poucos = IntStream.range(0, 5).mapToObj(i -> new BigDecimal("10")).toList();

        assertThatThrownBy(() -> ControlLimits.from(poucos))
                .isInstanceOf(ControlLimits.InsufficientHistoryException.class)
                .satisfies(e -> {
                    var ex = (ControlLimits.InsufficientHistoryException) e;
                    assertThat(ex.available()).isEqualTo(5);
                    assertThat(ex.required()).isEqualTo(20);
                });
    }

    @Test
    @DisplayName("exatamente vinte pontos já calcula")
    void vintePontosCalcula() {
        assertThat(ControlLimits.from(historico()).sampleSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("o valor no limite está dentro: limite é o último aceitável, não o primeiro recusado")
    void limiteEInclusivo() {
        var limits = ControlLimits.from(historico());

        assertThat(limits.contains(limits.lowerControlLimit())).isTrue();
        assertThat(limits.contains(limits.upperControlLimit())).isTrue();
        assertThat(limits.contains(limits.upperControlLimit().add(new BigDecimal("0.0001")))).isFalse();
    }

    @Test
    @DisplayName("processo estável e ESTAVELMENTE ERRADO: sob controle, fora de especificação")
    void sobControleEForaDeEspecificacao() {
        // A combinação que a confusão entre os dois limites esconde. O processo produz consistentemente
        // FG 1.020 quando o estilo pede no máximo 1.014: nenhum ponto dispara alarme de controle, e toda
        // a cerveja está fora do que se prometeu. Ajustar ponto a ponto não resolve — o processo inteiro
        // precisa mudar.
        var consistente = IntStream.range(0, 20)
                .mapToObj(i -> new BigDecimal(i % 2 == 0 ? "1.0199" : "1.0201"))
                .toList();
        var limits = ControlLimits.from(consistente);

        // Sob controle: todos os pontos dentro dos limites calculados.
        assertThat(consistente).allMatch(limits::contains);
        // E a linha central está acima do teto de especificação do estilo (1.014).
        assertThat(limits.centerLine()).isGreaterThan(new BigDecimal("1.014"));
    }

    @Test
    @DisplayName("acima e abaixo da linha central são decididos por ela, não pelos limites")
    void ladoEDecididoPelaLinhaCentral() {
        var limits = ControlLimits.from(historico());

        assertThat(limits.above(new BigDecimal("10.5"))).isTrue();
        assertThat(limits.above(new BigDecimal("9.5"))).isFalse();
        // Exatamente na linha não está acima.
        assertThat(limits.above(limits.centerLine())).isFalse();
    }
}
