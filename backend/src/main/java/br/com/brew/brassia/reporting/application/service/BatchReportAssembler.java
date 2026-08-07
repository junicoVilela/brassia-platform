package br.com.brew.brassia.reporting.application.service;

import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.reporting.domain.BatchReport;
import br.com.brew.brassia.reporting.domain.UnknownBatchReportException;
import br.com.brew.brassia.traceability.BatchLineageLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Monta o relatório do lote perguntando a quem sabe (RPT-001).
 *
 * <p>Seis consultas publicadas e nenhuma tabela própria. O relatório é o único módulo da plataforma
 * que depende de quase todos os outros, e é o único que pode: ninguém depende dele. Fosse o
 * contrário — um módulo consultando o relatório —, o relatório viraria dependência de produção e a
 * plataforma inteira passaria a girar em torno de um documento.
 *
 * <p><strong>Cada seção ausente vira lacuna nomeada.</strong> O relatório é o documento que sai da
 * casa; silêncio nele é lido como "não houve", e "não houve" é uma afirmação forte demais para se
 * fazer por omissão.
 */
public final class BatchReportAssembler implements BatchReportQueries {

    private final BatchLookup batches;
    private final BatchOutcomeLookup outcomes;
    private final OrderPlanLookup plans;
    private final PackagingOutcomeLookup packaging;
    private final BatchQualityLookup quality;
    private final BatchCostLookup costs;
    private final BatchLineageLookup lineage;
    private final Clock clock;

    public BatchReportAssembler(BatchLookup batches, BatchOutcomeLookup outcomes, OrderPlanLookup plans,
            PackagingOutcomeLookup packaging, BatchQualityLookup quality, BatchCostLookup costs,
            BatchLineageLookup lineage, Clock clock) {
        this.batches = Objects.requireNonNull(batches);
        this.outcomes = Objects.requireNonNull(outcomes);
        this.plans = Objects.requireNonNull(plans);
        this.packaging = Objects.requireNonNull(packaging);
        this.quality = Objects.requireNonNull(quality);
        this.costs = Objects.requireNonNull(costs);
        this.lineage = Objects.requireNonNull(lineage);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public BatchReport ofBatch(UUID breweryId, UUID batchId) {
        var batch = batches.find(breweryId, batchId)
                .orElseThrow(() -> new UnknownBatchReportException(batchId));
        var gaps = new ArrayList<String>();

        var plan = plan(breweryId, batch.orderId(), batch.volumeLiters(), gaps);
        var execution = execution(breweryId, batchId, gaps);
        var batchQuality = quality.ofBatch(breweryId, batchId);
        if (batchQuality.unmeasured()) {
            // Zero medição e zero desvio são a mesma linha na tela e coisas opostas na prática.
            gaps.add("qualidade: nenhuma medição foi registrada para este lote — o que não é o mesmo "
                    + "que o lote ter passado no controle");
        }
        var cost = costs.ofBatch(breweryId, batchId).orElse(null);
        if (cost == null) {
            gaps.add("custo: não foi possível apurar o custo deste lote");
        } else if (cost.incomplete()) {
            gaps.add("custo: o total é menor que a verdade — " + String.join("; ", cost.gaps()));
        }
        var batchLineage = lineage.ofBatch(breweryId, batchId);
        if (!batchLineage.complete()) {
            gaps.add("genealogia: a cadeia deste lote tem elo faltando ou foi truncada — o relatório "
                    + "não prova rastreabilidade completa");
        }

        return new BatchReport(batchId, batch.code(), batch.recipeName(), batch.recipeVersion(),
                batch.status(), clock.instant(), plan, execution, batchQuality, cost, batchLineage,
                List.copyOf(gaps));
    }

    private BatchReport.Plan plan(UUID breweryId, UUID orderId, BigDecimal volumeLiters,
            List<String> gaps) {
        var plan = plans.planOf(breweryId, orderId).orElse(null);
        if (plan == null || plan.plannedRecipeVersion() == null) {
            gaps.add("plano: a receita desta ordem não está mais publicada — não há plano a mostrar");
            return new BatchReport.Plan(volumeLiters, List.of());
        }
        if (!plan.baselineMatchesOrder()) {
            gaps.add("plano: a receita foi republicada depois desta ordem (versão "
                    + plan.orderRecipeVersion() + " na ordem, " + plan.plannedRecipeVersion()
                    + " publicada) — o plano de hoje não é o que foi brassado");
            return new BatchReport.Plan(plan.plannedVolumeLiters(), List.of());
        }
        return new BatchReport.Plan(plan.plannedVolumeLiters(), plan.materials());
    }

    private BatchReport.Execution execution(UUID breweryId, UUID batchId, List<String> gaps) {
        var outcome = outcomes.outcomeOf(breweryId, batchId).orElse(null);
        var runs = packaging.outcomesOfBatch(breweryId, batchId);
        if (outcome == null || !outcome.transferred()) {
            gaps.add("execução: o lote ainda não foi transferido — não há volume real de fermentador");
        }
        if (runs.isEmpty()) {
            gaps.add("execução: o lote ainda não foi envasado");
        }
        return new BatchReport.Execution(
                outcome == null ? null : outcome.transferredVolumeLiters(),
                outcome == null ? null : outcome.transferLossesLiters(), runs);
    }
}
