package br.com.brew.brassia.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.costing.BatchCostLookup.CostSummary;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup.PackagingOutcome;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.planning.OrderPlanLookup.OrderPlan;
import br.com.brew.brassia.planning.OrderPlanLookup.PlannedMaterial;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup.BatchOutcome;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.quality.BatchQualityLookup.BatchQuality;
import br.com.brew.brassia.reporting.domain.BatchReport;
import br.com.brew.brassia.reporting.domain.UnknownBatchReportException;
import br.com.brew.brassia.traceability.BatchLineageLookup;
import br.com.brew.brassia.traceability.BatchLineageLookup.BatchLineage;
import br.com.brew.brassia.traceability.BatchLineageLookup.LineageEntry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que o dossiê junta e o que ele se recusa a deixar em silêncio (RPT-001). */
class BatchReportAssemblerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    @DisplayName("o lote completo sai sem lacuna, com as cinco seções preenchidas")
    void loteCompletoSaiSemLacuna() {
        var report = new Scenario().assemble();

        assertThat(report.incomplete()).isFalse();
        assertThat(report.gaps()).isEmpty();
        assertThat(report.plan().materials()).hasSize(1);
        assertThat(report.execution().transferred()).isTrue();
        assertThat(report.execution().packaged()).isTrue();
        assertThat(report.quality().measurements()).isEqualTo(4);
        assertThat(report.cost().total()).isEqualByComparingTo("195");
        assertThat(report.lineage().origins()).hasSize(1);
        // A data de geração vai no documento: o relatório é derivado e responde diferente amanhã.
        assertThat(report.generatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("lote sem medição declara que não foi medido, e não que passou")
    void semMedicaoNaoEhAprovacao() {
        var report = new Scenario().quality(BatchQuality.empty()).assemble();

        assertThat(report.quality().unmeasured()).isTrue();
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("nenhuma medição"));
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("não é o mesmo"));
    }

    @Test
    @DisplayName("lote ainda não transferido e não envasado declara as duas ausências")
    void execucaoIncompletaDeclaraAsDuas() {
        var report = new Scenario()
                .outcome(new BatchOutcome(new BigDecimal("400"), null, null))
                .packaging(List.of())
                .assemble();

        assertThat(report.execution().transferred()).isFalse();
        assertThat(report.execution().packaged()).isFalse();
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("ainda não foi transferido"));
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("ainda não foi envasado"));
    }

    @Test
    @DisplayName("custo incompleto carrega os motivos para dentro do relatório")
    void custoIncompletoCarregaOsMotivos() {
        var report = new Scenario()
                .cost(new CostSummary(new BigDecimal("195"), new BigDecimal("0.5"),
                        new BigDecimal("390"), false, true, List.of("LABOR: não há hora trabalhada")))
                .assemble();

        assertThat(report.gaps()).anyMatch(gap -> gap.contains("menor que a verdade"));
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("hora trabalhada"));
    }

    @Test
    @DisplayName("genealogia com elo faltando não deixa o relatório afirmar rastreabilidade")
    void genealogiaIncompletaNaoProvaRastreabilidade() {
        var report = new Scenario()
                .lineage(new BatchLineage(List.of(), List.of(), List.of("consumo: não confirmado"), false))
                .assemble();

        assertThat(report.lineage().complete()).isFalse();
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("não prova rastreabilidade"));
    }

    @Test
    @DisplayName("genealogia truncada também não prova: há mais grafo além do que se vê")
    void genealogiaTruncadaTambemNaoProva() {
        var report = new Scenario()
                .lineage(new BatchLineage(List.of(), List.of(), List.of(), true))
                .assemble();

        assertThat(report.gaps()).anyMatch(gap -> gap.contains("truncada"));
    }

    @Test
    @DisplayName("receita republicada depois da ordem não vira plano do relatório")
    void receitaRepublicadaNaoViraPlano() {
        var report = new Scenario()
                .plan(new OrderPlan(new BigDecimal("400"), 2, 3,
                        List.of(new PlannedMaterial(UUID.randomUUID(), new BigDecimal("30"), "KG"))))
                .assemble();

        assertThat(report.plan().materials()).isEmpty();
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("republicada"));
    }

    @Test
    @DisplayName("custo que não pôde ser apurado é lacuna, não zero")
    void custoAusenteEhLacuna() {
        var report = new Scenario().cost(null).assemble();

        assertThat(report.cost()).isNull();
        assertThat(report.gaps()).anyMatch(gap -> gap.contains("não foi possível apurar"));
    }

    @Test
    @DisplayName("lote inexistente é recusado em vez de devolver dossiê vazio")
    void loteInexistenteEhRecusado() {
        var scenario = new Scenario().unknownBatch();

        assertThatThrownBy(scenario::assemble).isInstanceOf(UnknownBatchReportException.class);
    }

    // --- cenário ---

    private static final class Scenario {
        private boolean known = true;
        private OrderPlan plan = new OrderPlan(new BigDecimal("400"), 2, 2,
                List.of(new PlannedMaterial(UUID.randomUUID(), new BigDecimal("20"), "KG")));
        private BatchOutcome outcome =
                new BatchOutcome(new BigDecimal("400"), new BigDecimal("390"), new BigDecimal("8"));
        private List<PackagingOutcome> packaging = List.of(new PackagingOutcome("ENV-1",
                new BigDecimal("142"), new BigDecimal("138.45"), new BigDecimal("1.77"),
                new BigDecimal("1.78")));
        private BatchQuality quality = new BatchQuality(4, 4, List.of(), List.of(), List.of());
        private CostSummary cost = new CostSummary(new BigDecimal("195"), new BigDecimal("0.5"),
                new BigDecimal("390"), true, false, List.of());
        private BatchLineage lineage = new BatchLineage(
                List.of(new LineageEntry("STOCK_LOT", "Malte F-1234")),
                List.of(new LineageEntry("SHIPMENT", "Expedição 42")), List.of(), false);

        Scenario unknownBatch() {
            known = false;
            return this;
        }

        Scenario plan(OrderPlan value) {
            plan = value;
            return this;
        }

        Scenario outcome(BatchOutcome value) {
            outcome = value;
            return this;
        }

        Scenario packaging(List<PackagingOutcome> value) {
            packaging = value;
            return this;
        }

        Scenario quality(BatchQuality value) {
            quality = value;
            return this;
        }

        Scenario cost(CostSummary value) {
            cost = value;
            return this;
        }

        Scenario lineage(BatchLineage value) {
            lineage = value;
            return this;
        }

        BatchReport assemble() {
            BatchLookup batches = (breweryId, batchId) -> known
                    ? Optional.of(new BatchLookup.Snapshot(BATCH, ORDER, "LOTE-100",
                            new BigDecimal("400"), new BigDecimal("390"), "COMPLETED",
                            UUID.randomUUID(), 2, "IPA"))
                    : Optional.empty();
            BatchOutcomeLookup outcomes = (breweryId, batchId) -> Optional.ofNullable(outcome);
            OrderPlanLookup plans = (breweryId, orderId) -> Optional.ofNullable(plan);
            PackagingOutcomeLookup runs = (breweryId, batchId) -> packaging;
            BatchQualityLookup qualities = (breweryId, batchId) -> quality;
            BatchCostLookup costs = (breweryId, batchId) -> Optional.ofNullable(cost);
            BatchLineageLookup lineages = (breweryId, batchId) -> lineage;
            return new BatchReportAssembler(batches, outcomes, plans, runs, qualities, costs, lineages,
                    Clock.fixed(NOW, ZoneOffset.UTC)).ofBatch(BREWERY, BATCH);
        }
    }
}
