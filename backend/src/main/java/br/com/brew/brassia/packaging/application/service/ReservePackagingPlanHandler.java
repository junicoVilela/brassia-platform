package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.equipment.EquipmentAvailabilityLookup;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import br.com.brew.brassia.packaging.application.port.inbound.ReservePackagingPlanUseCase;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.LineCleanliness;
import br.com.brew.brassia.packaging.domain.PackagingBlockedException;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.packaging.domain.PackagingPlanStatus;
import br.com.brew.brassia.packaging.domain.PackagingStockShortfallException;
import br.com.brew.brassia.sanitation.CleaningPolicyLookup;
import br.com.brew.brassia.sanitation.CleaningReleaseLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compromete o plano de envase (PKG-001): reúne <strong>todos</strong> os bloqueios antes de
 * tocar no estoque, para o operador ver de uma vez o que falta em vez de descobrir um
 * impedimento por tentativa. Só depois reserva a embalagem, all-or-nothing.
 *
 * <p>Tudo acontece num commit: bloqueio ou falta de embalagem não deixa reserva nem transição
 * pela metade. A limpeza vem do ciclo de sanitização liberado (evidência), não de um "ok" digitado.
 */
public final class ReservePackagingPlanHandler implements ReservePackagingPlanUseCase {

    private final PackagingPlanRepository plans;
    private final EquipmentAvailabilityLookup lines;
    private final CleaningReleaseLookup cleanings;
    private final CleaningPolicyLookup cleaningPolicy;
    private final IngredientSpecLookup ingredients;
    private final PackagingStockGateway stock;
    private final AuditTrail audit;

    public ReservePackagingPlanHandler(PackagingPlanRepository plans, EquipmentAvailabilityLookup lines,
            CleaningReleaseLookup cleanings, CleaningPolicyLookup cleaningPolicy,
            IngredientSpecLookup ingredients, PackagingStockGateway stock, AuditTrail audit) {
        this.plans = Objects.requireNonNull(plans);
        this.lines = Objects.requireNonNull(lines);
        this.cleanings = Objects.requireNonNull(cleanings);
        this.cleaningPolicy = Objects.requireNonNull(cleaningPolicy);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.stock = Objects.requireNonNull(stock);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var plan = plans.findForUpdate(command.breweryId(), command.planId())
                .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
        if (plan.status() == PackagingPlanStatus.RESERVED) {
            throw new IllegalStateException("plano já reservado");
        }

        var blockers = new ArrayList<>(plan.ownBlockers());
        blockers.addAll(lineBlockers(plan));
        if (!blockers.isEmpty()) {
            throw new PackagingBlockedException(List.copyOf(blockers));
        }

        var units = BigDecimal.valueOf(plan.plannedUnits());
        var unit = ingredients.find(plan.breweryId(), plan.containerId())
                .map(IngredientSpecLookup.Spec::useUnit)
                .orElseThrow(() -> new IllegalStateException("embalagem saiu do catálogo"));
        var outcome = stock.reserve(plan.breweryId(), plan.id(), command.actorId(), plan.containerId(), units, unit);
        if (!outcome.reserved()) {
            throw new PackagingStockShortfallException(
                    plan.containerId(), units, outcome.available(), outcome.unit());
        }

        var version = plan.version();
        plan.reserve(command.actorId(), Instant.now());
        if (!plans.updateStatus(plan, version)) {
            throw new IllegalStateException("plano alterado por outra operação; tente novamente");
        }

        audit.record(AuditEvent.success(plan.breweryId(), command.actorId(), "packaging.plan.reserve",
                "packaging.plan", plan.id().toString(),
                Map.of("code", plan.code(), "containerId", plan.containerId().toString(),
                        "units", String.valueOf(plan.plannedUnits()),
                        "lineEquipmentId", plan.lineEquipmentId().toString())));

        return new Result(plan.id(), units, unit);
    }

    /** Disponibilidade, agenda e limpeza da linha — os três motivos de recusa que o envase controla. */
    private List<PackagingBlockedException.Blocker> lineBlockers(PackagingPlan plan) {
        var blockers = new ArrayList<PackagingBlockedException.Blocker>();

        var availability = lines.check(
                plan.breweryId(), plan.lineEquipmentId(), plan.plannedStart(), plan.plannedEnd());
        switch (availability) {
            case UNKNOWN -> blockers.add(new PackagingBlockedException.Blocker(
                    "line_unknown", "A linha de envase não existe mais no cadastro."));
            case INACTIVE -> blockers.add(new PackagingBlockedException.Blocker(
                    "line_inactive", "A linha de envase está desativada."));
            case UNDER_MAINTENANCE -> blockers.add(new PackagingBlockedException.Blocker(
                    "line_under_maintenance", "Há manutenção agendada na linha durante a janela planejada."));
            case AVAILABLE -> { }
        }

        if (plans.hasLineConflict(plan.breweryId(), plan.lineEquipmentId(), plan.plannedStart(), plan.plannedEnd(),
                plan.id())) {
            blockers.add(new PackagingBlockedException.Blocker(
                    "line_conflict", "Outro envase já ocupa esta linha na janela planejada."));
        }

        var releasedAt = cleanings.lastRelease(plan.breweryId(), plan.lineEquipmentId())
                .map(CleaningReleaseLookup.Release::releasedAt)
                .orElse(null);
        var lastUse = plans.lastLineUse(plan.breweryId(), plan.lineEquipmentId(), plan.plannedStart(), plan.id())
                .orElse(null);
        // A validade por tempo é parâmetro da cervejaria (PRM-001) e vive na sanitização; sem
        // prazo configurado, `covers` responde verdadeiro e o comportamento é o de antes.
        var stillCovered = releasedAt == null
                || cleaningPolicy.covers(plan.breweryId(), releasedAt, plan.plannedStart());
        LineCleanliness.check(releasedAt, lastUse, plan.plannedStart(), stillCovered)
                .ifPresent(blockers::add);

        return blockers;
    }
}
