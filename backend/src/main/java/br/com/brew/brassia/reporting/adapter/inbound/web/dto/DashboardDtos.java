package br.com.brew.brassia.reporting.adapter.inbound.web.dto;

import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contratos do painel operacional (RPT-002). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardView(Instant from, Instant to, int sources,
            List<IndicatorView> indicators) {

        public static DashboardView from(DashboardQueries.Dashboard dashboard) {
            return new DashboardView(dashboard.from(), dashboard.to(), dashboard.sources(),
                    dashboard.indicators().stream().map(IndicatorView::from).toList());
        }
    }

    /**
     * @param definition  o que o número quer dizer. Nunca vazio — o contrato não tem como produzir
     *                    indicador sem definição, porque o domínio recusa construir um
     * @param from        nulo quando o indicador é posição, não acumulado
     * @param positional  facilita a leitura do que o {@code from} nulo já diz
     * @param drillDown   recurso e filtro onde o número se abre; a rota é da interface
     * @param gap         o que este número não cobre; nulo quando não há o que ressalvar
     */
    public record IndicatorView(String code, String group, String label, String definition,
            BigDecimal value, String unit, Instant from, Instant to, boolean positional,
            DrillDownView drillDown, String gap) {

        static IndicatorView from(OperationalIndicator indicator) {
            return new IndicatorView(indicator.code(), indicator.group().name(), indicator.label(),
                    indicator.definition(), indicator.value(), indicator.unit(), indicator.from(),
                    indicator.to(), indicator.positional(),
                    new DrillDownView(indicator.drillDown().resource(), indicator.drillDown().filter()),
                    indicator.gap());
        }
    }

    public record DrillDownView(String resource, Map<String, String> filter) {}
}
