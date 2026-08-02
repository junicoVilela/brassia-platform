package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.equipment.EquipmentAvailabilityLookup;
import br.com.brew.brassia.packaging.application.port.inbound.PlanPackagingUseCase;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Map;
import java.util.Objects;

/**
 * Abre o plano de envase (PKG-001). Aqui ficam as validações que dependem de outros módulos:
 * o lote existe e está fermentando (envasar durante a brassagem não é envase), a embalagem é
 * mesmo uma embalagem do catálogo com volume declarado, e a linha existe nesta cervejaria.
 *
 * <p>Manutenção, limpeza e reserva não entram na abertura: o plano é intenção e pode ser
 * montado com antecedência. Quem compromete recursos é a reserva.
 */
public final class PlanPackagingHandler implements PlanPackagingUseCase {

    private static final String PACKAGING_TYPE = "PACKAGING";

    private final PackagingPlanRepository plans;
    private final BatchLookup batches;
    private final IngredientSpecLookup ingredients;
    private final EquipmentAvailabilityLookup lines;
    private final AuditTrail audit;

    public PlanPackagingHandler(PackagingPlanRepository plans, BatchLookup batches,
            IngredientSpecLookup ingredients, EquipmentAvailabilityLookup lines, AuditTrail audit) {
        this.plans = Objects.requireNonNull(plans);
        this.batches = Objects.requireNonNull(batches);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.lines = Objects.requireNonNull(lines);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var batch = batches.find(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente: " + command.batchId()));
        if (!"FERMENTING".equals(batch.status())) {
            throw new IllegalStateException("lote não está em fermentação: " + batch.status());
        }
        if (plans.existsByCode(command.breweryId(), command.code())) {
            throw new IllegalStateException("já existe plano de envase com o código " + command.code());
        }

        var container = ingredients.find(command.breweryId(), command.containerId())
                .orElseThrow(() -> new IllegalArgumentException("embalagem inexistente no catálogo"));
        if (!PACKAGING_TYPE.equals(container.type())) {
            throw new IllegalArgumentException("ingrediente não é uma embalagem");
        }
        if (container.volumeMl() == null) {
            throw new IllegalArgumentException("embalagem sem volume declarado (atributo volumeMl)");
        }

        // Linha inexistente é erro de cadastro e não faz sentido nem como intenção.
        if (lines.check(command.breweryId(), command.lineEquipmentId(), command.plannedStart(), command.plannedEnd())
                == EquipmentAvailabilityLookup.Availability.UNKNOWN) {
            throw new IllegalArgumentException("linha de envase inexistente");
        }

        // O teto é a cerveja que está no tanque, não o volume planejado da ordem: a transferência
        // tem perdas, e planejar contra o planejado inventaria cerveja que não existe.
        var plan = PackagingPlan.plan(command.breweryId(), command.code(), command.batchId(), command.containerId(),
                container.volumeMl(), command.plannedUnits(), command.lineEquipmentId(), command.plannedStart(),
                command.plannedEnd(), batch.packageableVolumeLiters());
        plans.insert(plan);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "packaging.plan.create",
                "packaging.plan", plan.id().toString(),
                Map.of("code", plan.code(), "batchId", plan.batchId().toString(),
                        "units", String.valueOf(plan.plannedUnits()),
                        "volumeLiters", plan.plannedVolumeLiters().toPlainString())));

        return new Result(plan.id(), plan.plannedVolumeLiters());
    }
}
