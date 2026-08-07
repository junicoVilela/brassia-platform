package br.com.brew.brassia.reporting.adapter.inbound.web.dto;

import br.com.brew.brassia.quality.BatchQualityLookup.BatchQuality;
import br.com.brew.brassia.reporting.domain.BatchReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos do relatório do lote (RPT-001). */
public final class BatchReportDtos {

    private BatchReportDtos() {
    }

    /**
     * @param generatedAt quando este documento foi montado. Vai no corpo porque o relatório é
     *                    derivado: o mesmo lote responde diferente amanhã, e um PDF impresso sem
     *                    data seria indefensável em auditoria
     * @param gaps        o que não pôde ser afirmado. Silêncio num documento que sai da casa é
     *                    lido como "não houve"
     */
    public record BatchReportView(UUID batchId, String batchCode, String recipeName, int recipeVersion,
            String status, Instant generatedAt, boolean incomplete, PlanView plan,
            ExecutionView execution, QualityView quality, CostView cost, LineageView lineage,
            List<String> gaps) {

        public static BatchReportView from(BatchReport report) {
            return new BatchReportView(report.batchId(), report.batchCode(), report.recipeName(),
                    report.recipeVersion(), report.status(), report.generatedAt(), report.incomplete(),
                    PlanView.from(report.plan()), ExecutionView.from(report.execution()),
                    QualityView.from(report.quality()), CostView.from(report.cost()),
                    LineageView.from(report.lineage()), report.gaps());
        }
    }

    public record PlanView(BigDecimal volumeLiters, List<MaterialView> materials) {

        static PlanView from(BatchReport.Plan plan) {
            return new PlanView(plan.volumeLiters(), plan.materials().stream()
                    .map(material -> new MaterialView(material.ingredientId(), material.quantity(),
                            material.unit()))
                    .toList());
        }
    }

    public record MaterialView(UUID ingredientId, BigDecimal quantity, String unit) {}

    public record ExecutionView(BigDecimal transferredVolumeLiters, BigDecimal transferLossesLiters,
            boolean transferred, boolean packaged, List<PackagingView> packaging) {

        static ExecutionView from(BatchReport.Execution execution) {
            return new ExecutionView(execution.transferredVolumeLiters(),
                    execution.transferLossesLiters(), execution.transferred(), execution.packaged(),
                    execution.packaging().stream()
                            .map(run -> new PackagingView(run.planCode(), run.plannedVolumeLiters(),
                                    run.packagedVolumeLiters(), run.rejectedVolumeLiters(),
                                    run.lossesLiters()))
                            .toList());
        }
    }

    public record PackagingView(String planCode, BigDecimal plannedVolumeLiters,
            BigDecimal packagedVolumeLiters, BigDecimal rejectedVolumeLiters, BigDecimal lossesLiters) {}

    /**
     * @param unmeasured verdadeiro quando ninguém mediu nada — que não é o mesmo que aprovado
     */
    public record QualityView(int measurements, int withinSpec, boolean unmeasured,
            List<MeasurementView> outOfSpec, List<DeviationView> deviations,
            List<NonConformityView> nonConformities) {

        static QualityView from(BatchQuality quality) {
            return new QualityView(quality.measurements(), quality.withinSpec(), quality.unmeasured(),
                    quality.outOfSpec().stream()
                            .map(m -> new MeasurementView(m.parameter(), m.value(), m.unit(),
                                    m.measuredAt()))
                            .toList(),
                    quality.deviations().stream()
                            .map(d -> new DeviationView(d.parameter(), d.severity(), d.status(),
                                    d.limitValue(), d.measuredValue(), d.unit(), d.openedAt()))
                            .toList(),
                    quality.nonConformities().stream()
                            .map(n -> new NonConformityView(n.code(), n.title(), n.severity(),
                                    n.status()))
                            .toList());
        }
    }

    public record MeasurementView(String parameter, BigDecimal value, String unit, Instant measuredAt) {}

    public record DeviationView(String parameter, String severity, String status, BigDecimal limitValue,
            BigDecimal measuredValue, String unit, Instant openedAt) {}

    public record NonConformityView(String code, String title, String severity, String status) {}

    /** Nulo quando o custo não pôde ser apurado; a lacuna correspondente diz por quê. */
    public record CostView(BigDecimal total, BigDecimal costPerLiter, BigDecimal volumeLiters,
            boolean closed, boolean incomplete, List<String> gaps) {

        static CostView from(br.com.brew.brassia.costing.BatchCostLookup.CostSummary cost) {
            return cost == null ? null
                    : new CostView(cost.total(), cost.costPerLiter(), cost.volumeLiters(), cost.closed(),
                            cost.incomplete(), cost.gaps());
        }
    }

    public record LineageView(List<EntryView> origins, List<EntryView> destinations, List<String> gaps,
            boolean truncated, boolean complete) {

        static LineageView from(br.com.brew.brassia.traceability.BatchLineageLookup.BatchLineage lineage) {
            return new LineageView(
                    lineage.origins().stream().map(e -> new EntryView(e.type(), e.label())).toList(),
                    lineage.destinations().stream().map(e -> new EntryView(e.type(), e.label())).toList(),
                    lineage.gaps(), lineage.truncated(), lineage.complete());
        }
    }

    public record EntryView(String type, String label) {}
}
