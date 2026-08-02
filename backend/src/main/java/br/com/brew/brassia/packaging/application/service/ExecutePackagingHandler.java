package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import br.com.brew.brassia.packaging.application.port.inbound.ExecutePackagingUseCase;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingRunRepository;
import br.com.brew.brassia.packaging.domain.BatchVolumeExceededException;
import br.com.brew.brassia.packaging.domain.PackagingPlanStatus;
import br.com.brew.brassia.packaging.domain.PackagingRun;
import br.com.brew.brassia.packaging.domain.PackagingStockShortfallException;
import br.com.brew.brassia.production.BatchLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Executa o envase (PKG-003). Tudo num commit: o balanço de volume, o consumo da embalagem e a
 * transição do plano acontecem juntos, então não existe embalagem consumida sem envase registrado
 * nem envase registrado sem embalagem baixada.
 *
 * <p>Só plano reservado é executado — a reserva é o que garante linha verificada, limpa e
 * embalagem separada. Executar é terminal: o plano não volta a ser cancelável, porque desfazer
 * produção não é cancelar plano.
 */
public final class ExecutePackagingHandler implements ExecutePackagingUseCase {

    private final PackagingPlanRepository plans;
    private final PackagingRunRepository runs;
    private final BatchLookup batches;
    private final IngredientSpecLookup ingredients;
    private final PackagingStockGateway stock;
    private final AuditTrail audit;

    public ExecutePackagingHandler(PackagingPlanRepository plans, PackagingRunRepository runs, BatchLookup batches,
            IngredientSpecLookup ingredients, PackagingStockGateway stock, AuditTrail audit) {
        this.plans = Objects.requireNonNull(plans);
        this.runs = Objects.requireNonNull(runs);
        this.batches = Objects.requireNonNull(batches);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.stock = Objects.requireNonNull(stock);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var plan = plans.findForUpdate(command.breweryId(), command.planId())
                .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
        if (plan.status() != PackagingPlanStatus.RESERVED) {
            throw new IllegalStateException("só plano reservado é executado: " + plan.status());
        }

        var batch = batches.find(plan.breweryId(), plan.batchId())
                .orElseThrow(() -> new IllegalStateException("lote do plano não encontrado"));
        // Um lote pode ser dividido em vários envases, mas a soma não inventa cerveja.
        var alreadyPackaged = runs.totalInputVolumeOfBatch(plan.breweryId(), plan.batchId());
        var totalInput = alreadyPackaged.add(command.inputVolumeLiters());
        if (totalInput.compareTo(batch.packageableVolumeLiters()) > 0) {
            throw new BatchVolumeExceededException(batch.packageableVolumeLiters(), alreadyPackaged,
                    command.inputVolumeLiters());
        }

        var run = PackagingRun.execute(plan.id(), plan.breweryId(), plan.batchId(), plan.containerVolumeMl(),
                command.inputVolumeLiters(), command.producedUnits(), command.rejectedUnits(), command.note(),
                Instant.now(), command.actorId());

        var containers = BigDecimal.valueOf(run.containersConsumed());
        var unit = ingredients.find(plan.breweryId(), plan.containerId())
                .map(IngredientSpecLookup.Spec::useUnit)
                .orElseThrow(() -> new IllegalStateException("embalagem saiu do catálogo"));
        var outcome = stock.consume(plan.breweryId(), plan.id(), command.actorId(), plan.containerId(),
                containers, unit);
        if (!outcome.reserved()) {
            throw new PackagingStockShortfallException(
                    plan.containerId(), containers, outcome.available(), outcome.unit());
        }

        var version = plan.version();
        plan.execute(run.executedAt());
        if (!plans.updateStatus(plan, version)) {
            throw new IllegalStateException("plano alterado por outra operação; tente novamente");
        }
        runs.insert(run);

        audit.record(AuditEvent.success(plan.breweryId(), command.actorId(), "packaging.plan.execute",
                "packaging.plan", plan.id().toString(),
                Map.of("code", plan.code(),
                        "producedUnits", String.valueOf(run.producedUnits()),
                        "rejectedUnits", String.valueOf(run.rejectedUnits()),
                        "packagedVolumeLiters", run.packagedVolumeLiters().toPlainString(),
                        "lossesLiters", run.lossesLiters().toPlainString(),
                        "containersConsumed", String.valueOf(run.containersConsumed()))));

        return new Result(run.id(), run.packagedVolumeLiters(), run.lossesLiters(), run.containersConsumed());
    }
}
