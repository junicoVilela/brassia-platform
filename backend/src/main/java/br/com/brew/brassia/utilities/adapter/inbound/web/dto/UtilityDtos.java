package br.com.brew.brassia.utilities.adapter.inbound.web.dto;

import br.com.brew.brassia.utilities.UtilityReadingSource.Coverage;
import br.com.brew.brassia.utilities.application.port.inbound.UtilityQueries;
import br.com.brew.brassia.utilities.domain.UtilityIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos do indicador de utilidades (UTL-001). */
public final class UtilityDtos {

    private UtilityDtos() {
    }

    /**
     * @param perLiter        nulo quando nada foi envasado no período: dizer "0 L/L" faria uma
     *                        fábrica que limpou tanque sem produzir cerveja parecer eficiente
     * @param measuredPerLiter só a parte lida de instrumento — é a que se leva a auditoria
     * @param fullyMeasured   falso também quando ninguém declarou cobertura: não saber quanto foi
     *                        medido não é o mesmo que ter medido tudo
     */
    public record IndicatorView(String type, String unit, BigDecimal measured, BigDecimal estimated,
            BigDecimal total, BigDecimal perLiter, BigDecimal measuredPerLiter, boolean fullyMeasured,
            List<CoverageView> coverage, List<String> sources) {

        static IndicatorView from(UtilityIndicator indicator) {
            return new IndicatorView(indicator.type().name(), indicator.unit(), indicator.measured(),
                    indicator.estimated(), indicator.total(), indicator.perLiter(),
                    indicator.measuredPerLiter(), indicator.fullyMeasured(),
                    indicator.coverage().stream().map(CoverageView::from).toList(), indicator.sources());
        }
    }

    public record CoverageView(String what, int reported, int expected, boolean complete) {

        static CoverageView from(Coverage coverage) {
            return new CoverageView(coverage.what(), coverage.reported(), coverage.expected(),
                    coverage.complete());
        }
    }

    public record ReportView(Instant from, Instant to, BigDecimal packagedLiters,
            List<IndicatorView> indicators) {

        public static ReportView from(UtilityQueries.Report report) {
            return new ReportView(report.from(), report.to(), report.packagedLiters(),
                    report.indicators().stream().map(IndicatorView::from).toList());
        }
    }
}
