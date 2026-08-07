package br.com.brew.brassia.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.shared.reporting.IndicatorGroup;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import br.com.brew.brassia.shared.reporting.OperationalIndicator.DrillDown;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Como o painel junta fontes que não se conhecem (RPT-002). */
class DashboardQueryHandlerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-07-08T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    @DisplayName("os indicadores saem agrupados na ordem do painel, e não na de registro das fontes")
    void agrupaNaOrdemDoPainel() {
        var handler = new DashboardQueryHandler(List.of(
                source(indicator("custo.medio", IndicatorGroup.COST)),
                source(indicator("producao.lotes", IndicatorGroup.PRODUCTION)),
                source(indicator("qualidade.desvios", IndicatorGroup.QUALITY))));

        var groups = handler.dashboard(BREWERY, FROM, TO).indicators().stream()
                .map(OperationalIndicator::group)
                .toList();

        assertThat(groups).containsExactly(IndicatorGroup.PRODUCTION, IndicatorGroup.QUALITY,
                IndicatorGroup.COST);
    }

    @Test
    @DisplayName("indicadores do mesmo grupo saem em ordem estável de código")
    void ordemEstavelDentroDoGrupo() {
        var handler = new DashboardQueryHandler(List.of(source(
                indicator("producao.litros", IndicatorGroup.PRODUCTION),
                indicator("producao.lotes", IndicatorGroup.PRODUCTION))));

        var codes = handler.dashboard(BREWERY, FROM, TO).indicators().stream()
                .map(OperationalIndicator::code)
                .toList();

        // Ordem estável importa: painel que embaralha cartões a cada visita cansa quem o usa todo dia.
        assertThat(codes).containsExactly("producao.litros", "producao.lotes");
    }

    @Test
    @DisplayName("a contagem de fontes vai na resposta: painel que encolheu tem de dar para notar")
    void contaAsFontes() {
        var handler = new DashboardQueryHandler(List.of(
                source(indicator("producao.lotes", IndicatorGroup.PRODUCTION)),
                source(indicator("custo.medio", IndicatorGroup.COST))));

        assertThat(handler.dashboard(BREWERY, FROM, TO).sources()).isEqualTo(2);
    }

    @Test
    @DisplayName("sem fonte nenhuma o painel vem vazio, e não quebra")
    void semFonteVemVazio() {
        var dashboard = new DashboardQueryHandler(List.of()).dashboard(BREWERY, FROM, TO);

        assertThat(dashboard.indicators()).isEmpty();
        assertThat(dashboard.sources()).isZero();
    }

    @Test
    @DisplayName("fonte que falha derruba o painel: erro visível é melhor que painel pela metade")
    void fonteQueFalhaDerrubaOPainel() {
        IndicatorSource quebrada = (brewery, from, to) -> {
            throw new IllegalStateException("consulta falhou");
        };
        var handler = new DashboardQueryHandler(List.of(
                source(indicator("producao.lotes", IndicatorGroup.PRODUCTION)), quebrada));

        assertThatThrownBy(() -> handler.dashboard(BREWERY, FROM, TO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("período invertido é erro de quem perguntou, não painel vazio")
    void recusaPeriodoInvertido() {
        var handler = new DashboardQueryHandler(List.of());

        assertThatThrownBy(() -> handler.dashboard(BREWERY, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static IndicatorSource source(OperationalIndicator... indicators) {
        return (breweryId, from, to) -> List.of(indicators);
    }

    private static OperationalIndicator indicator(String code, IndicatorGroup group) {
        return OperationalIndicator.inPeriod(code, group, "Rótulo", "Definição do indicador.",
                BigDecimal.ONE, "un", FROM, TO, DrillDown.of("recurso"));
    }
}
