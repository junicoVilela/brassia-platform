package br.com.brew.brassia.shared.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.shared.reporting.OperationalIndicator.DrillDown;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os três requisitos da RPT-002 como invariante: definição, período e drill-down.
 *
 * <p>O que estes testes fixam não é comportamento, é impossibilidade — não dá para construir o
 * indicador errado, então "indicador sem definição" deixa de ser um risco que depende de disciplina.
 */
class OperationalIndicatorTest {

    private static final Instant FROM = Instant.parse("2026-07-08T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    @DisplayName("indicador sem definição não existe")
    void semDefinicaoNaoExiste() {
        assertThatThrownBy(() -> indicator("producao.lotes", "  ", FROM, TO, DrillDown.of("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definição");
    }

    @Test
    @DisplayName("indicador sem período não existe")
    void semPeriodoNaoExiste() {
        assertThatThrownBy(() -> indicator("producao.lotes", "lotes iniciados", FROM, null,
                DrillDown.of("x")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("período");
    }

    @Test
    @DisplayName("indicador sem drill-down não existe: número que não se abre não se confere")
    void semDrillDownNaoExiste() {
        assertThatThrownBy(() -> indicator("producao.lotes", "lotes iniciados", FROM, TO, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("drill-down");
    }

    @Test
    @DisplayName("período invertido é recusado na construção")
    void periodoInvertidoEhRecusado() {
        assertThatThrownBy(() -> indicator("producao.lotes", "lotes", TO, FROM, DrillDown.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("posição e acumulado se distinguem pelo início vazio, não por convenção")
    void posicaoSeDistingueDoAcumulado() {
        var acumulado = OperationalIndicator.inPeriod("producao.lotes", IndicatorGroup.PRODUCTION,
                "Lotes", "Lotes iniciados no período.", BigDecimal.TEN, "lotes", FROM, TO,
                DrillDown.of("production.batches"));
        var posicao = OperationalIndicator.snapshot("producao.em_andamento",
                IndicatorGroup.PRODUCTION, "Em andamento", "Lotes na panela agora.", BigDecimal.ONE,
                "lotes", TO, DrillDown.of("production.batches"));

        assertThat(acumulado.positional()).isFalse();
        assertThat(posicao.positional()).isTrue();
        // Foto não tem começo, mas tem instante: o período nunca é ausente.
        assertThat(posicao.to()).isEqualTo(TO);
    }

    @Test
    @DisplayName("a ressalva é acrescentada sem refazer o indicador")
    void aRessalvaEhAcrescentada() {
        var indicator = OperationalIndicator
                .inPeriod("qualidade.conformidade", IndicatorGroup.QUALITY, "Conformidade",
                        "Percentual dentro da faixa.", BigDecimal.ZERO, "%", FROM, TO,
                        DrillDown.of("quality.controlPlans"))
                .withGap("não houve medição no período");

        assertThat(indicator.gap()).contains("não houve medição");
        assertThat(indicator.code()).isEqualTo("qualidade.conformidade");
    }

    @Test
    @DisplayName("o drill-down é recurso e filtro, não rota — a rota é da interface")
    void oDrillDownNaoEhRota() {
        var drillDown = DrillDown.of("production.batches", "status", "IN_PROGRESS");

        assertThat(drillDown.resource()).isEqualTo("production.batches");
        assertThat(drillDown.filter()).containsEntry("status", "IN_PROGRESS");
    }

    private static OperationalIndicator indicator(String code, String definition, Instant from,
            Instant to, DrillDown drillDown) {
        return new OperationalIndicator(code, IndicatorGroup.PRODUCTION, "Rótulo", definition,
                BigDecimal.ONE, "lotes", from, to, drillDown, null);
    }
}
