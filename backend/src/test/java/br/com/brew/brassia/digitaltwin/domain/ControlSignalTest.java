package br.com.brew.brassia.digitaltwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O que o histórico diz sobre o processo (SPC-001).
 *
 * <p>O que estes testes fixam: os dois sinais que a inspeção ponto a ponto <strong>não pega</strong> —
 * deslocamento e tendência —, que são justamente os que chegam antes do problema.
 */
class ControlSignalTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    /** Série estável em torno de 10, com sigma suficiente para os limites não ficarem colados. */
    private static List<BigDecimal> estavel() {
        return IntStream.range(0, 20).mapToObj(i -> v(i % 2 == 0 ? "9" : "11")).toList();
    }

    private static ControlLimits limitesEstaveis() {
        return ControlLimits.from(estavel());
    }

    @Test
    @DisplayName("processo estável não gera sinal nenhum")
    void estavelNaoGeraSinal() {
        assertThat(ControlSignal.detect(estavel(), limitesEstaveis())).isEmpty();
    }

    @Test
    @DisplayName("ponto além de 3σ é sinalizado com a posição e o valor")
    void pontoForaDosLimites() {
        var serie = new ArrayList<>(estavel());
        serie.add(v("30"));

        var sinais = ControlSignal.detect(serie, limitesEstaveis());

        assertThat(sinais).anyMatch(s -> s.kind() == ControlSignal.Kind.BEYOND_LIMIT);
        assertThat(sinais.stream().filter(s -> s.kind() == ControlSignal.Kind.BEYOND_LIMIT)
                .findFirst().orElseThrow().description()).contains("30");
    }

    @Test
    @DisplayName("DESLOCAMENTO: sete pontos do mesmo lado, NENHUM perto de um limite")
    void deslocamentoSemPontoForaDoLimite() {
        // É o caso que a inspeção ponto a ponto não pega: o processo mudou de patamar e continua estável
        // nele. Todos os pontos passam na conferência individual.
        var limits = limitesEstaveis();
        var serie = new ArrayList<>(estavel());
        serie.add(v("9"));
        for (int i = 0; i < 7; i++) {
            serie.add(v("10.5"));
        }

        var sinais = ControlSignal.detect(serie, limits);

        assertThat(serie.subList(21, 28)).allMatch(limits::contains);
        assertThat(sinais).anyMatch(s -> s.kind() == ControlSignal.Kind.RUN_ON_ONE_SIDE);
        assertThat(sinais).noneMatch(s -> s.kind() == ControlSignal.Kind.BEYOND_LIMIT);
    }

    @Test
    @DisplayName("seis pontos do mesmo lado ainda não é sinal")
    void seisPontosNaoBastam() {
        // Sete é a convenção porque a chance de acaso é ~1 em 128. Menos que isso alarmaria coincidência.
        //
        // O `9` fecha a série estável ABAIXO da central antes da sequência começar. Sem ele o último ponto
        // de `estavel()` é 11 — já acima —, e os seis viram sete. O primeiro teste que escrevi tinha esse
        // defeito, e foi a contagem do próprio detector que o denunciou.
        var serie = new ArrayList<>(estavel());
        serie.add(v("9"));
        for (int i = 0; i < 6; i++) {
            serie.add(v("10.5"));
        }

        assertThat(ControlSignal.detect(serie, limitesEstaveis()))
                .noneMatch(s -> s.kind() == ControlSignal.Kind.RUN_ON_ONE_SIDE);
    }

    @Test
    @DisplayName("TENDÊNCIA: sete pontos seguidos subindo, todos dentro dos limites")
    void tendenciaDentroDosLimites() {
        // O aviso mais antecipado: descreve algo mudando agora — desgaste, saturação, sujeira acumulando —
        // antes de qualquer ponto sair da faixa.
        var limits = limitesEstaveis();
        var serie = new ArrayList<>(estavel());
        for (int i = 0; i < 7; i++) {
            serie.add(v("10." + i));
        }

        var sinais = ControlSignal.detect(serie, limits);

        assertThat(sinais).anyMatch(s -> s.kind() == ControlSignal.Kind.TREND);
        assertThat(sinais.stream().filter(s -> s.kind() == ControlSignal.Kind.TREND)
                .findFirst().orElseThrow().description()).contains("subindo");
    }

    @Test
    @DisplayName("tendência descendente também é sinalizada")
    void tendenciaDescendente() {
        var serie = new ArrayList<>(estavel());
        for (int i = 7; i >= 1; i--) {
            serie.add(v("10." + i));
        }

        assertThat(ControlSignal.detect(serie, limitesEstaveis()))
                .anyMatch(s -> s.kind() == ControlSignal.Kind.TREND
                        && s.description().contains("descendo"));
    }

    @Test
    @DisplayName("empate interrompe a tendência: processo parado não vai a lugar nenhum")
    void empateInterrompeTendencia() {
        var serie = new ArrayList<>(estavel());
        serie.addAll(List.of(v("10.1"), v("10.2"), v("10.3"), v("10.4"), v("10.5"), v("10.6"), v("10.6")));

        assertThat(ControlSignal.detect(serie, limitesEstaveis()))
                .noneMatch(s -> s.kind() == ControlSignal.Kind.TREND);
    }

    @Test
    @DisplayName("ponto exatamente na linha central não pertence a lado nenhum")
    void pontoNaLinhaCentralNaoContaParaSequencia() {
        // Contá-lo como continuação inventaria um deslocamento que ele não sustenta.
        var limits = limitesEstaveis();
        var serie = new ArrayList<>(estavel());
        serie.add(v("9"));
        for (int i = 0; i < 7; i++) {
            serie.add(v("10.5"));
        }
        serie.add(limits.centerLine());

        assertThat(ControlSignal.detect(serie, limits))
                .noneMatch(s -> s.kind() == ControlSignal.Kind.RUN_ON_ONE_SIDE);
    }

    @Test
    @DisplayName("a sequência olhada é a MAIS RECENTE: uma que terminou há trinta pontos é história")
    void sequenciaERecente() {
        var serie = new ArrayList<BigDecimal>();
        // Deslocamento antigo…
        for (int i = 0; i < 8; i++) {
            serie.add(v("10.5"));
        }
        // …seguido de operação estável e alternada.
        serie.addAll(estavel());

        assertThat(ControlSignal.detect(serie, limitesEstaveis()))
                .noneMatch(s -> s.kind() == ControlSignal.Kind.RUN_ON_ONE_SIDE);
    }

    @Test
    @DisplayName("um sinal descreve o que os números fazem, nunca o porquê")
    void sinalNaoAfirmaCausa() {
        // Mesma fronteira de DTW-001: o sistema mostra a variação; a causa é investigada por quem conhece
        // o processo.
        var serie = new ArrayList<>(estavel());
        serie.add(v("30"));

        for (var sinal : ControlSignal.detect(serie, limitesEstaveis())) {
            assertThat(sinal.description()).doesNotContainIgnoringCase("porque");
            assertThat(sinal.description()).doesNotContainIgnoringCase("causa");
            assertThat(sinal.description()).doesNotContainIgnoringCase("devido");
        }
    }
}
