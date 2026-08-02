package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import br.com.brew.brassia.packaging.application.port.inbound.CancelPackagingPlanUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ConfirmChecklistItemUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.PackagingPlanQueries;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.ChecklistItem;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.packaging.domain.PackagingPlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Comandos e consultas menores do plano de envase (PKG-001). */
public final class PackagingPlanHandlers {

    private PackagingPlanHandlers() {
    }

    /**
     * Confirma um item do checklist. A confirmação é guardada pelo estado no banco: só plano
     * em PLANNED aceita, e repetir preserva a primeira evidência (quem conferiu e quando).
     */
    public static final class ConfirmItem implements ConfirmChecklistItemUseCase {

        private final PackagingPlanRepository plans;
        private final AuditTrail audit;

        public ConfirmItem(PackagingPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var item = ChecklistItem.of(command.item());
            var plan = plans.findById(command.breweryId(), command.planId())
                    .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
            if (plan.status() != PackagingPlanStatus.PLANNED) {
                throw new IllegalStateException("checklist não aceita alteração no estado " + plan.status());
            }

            var confirmed = plans.confirmChecklistItem(
                    command.breweryId(), command.planId(), item, command.actorId(), Instant.now());
            if (confirmed) {
                audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                        "packaging.plan.checklist.confirm", "packaging.plan", plan.id().toString(),
                        Map.of("code", plan.code(), "item", item.name())));
            }
        }
    }

    /**
     * Cancela o plano e devolve a embalagem. A devolução vem antes da transição: se o estoque
     * falhar, o commit inteiro cai e o plano continua reservado — nunca cancelado segurando estoque.
     */
    public static final class Cancel implements CancelPackagingPlanUseCase {

        private final PackagingPlanRepository plans;
        private final PackagingStockGateway stock;
        private final AuditTrail audit;

        public Cancel(PackagingPlanRepository plans, PackagingStockGateway stock, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.stock = Objects.requireNonNull(stock);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var plan = plans.findForUpdate(command.breweryId(), command.planId())
                    .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));

            var version = plan.version();
            var wasReserved = plan.status() == PackagingPlanStatus.RESERVED;
            plan.cancel(command.reason(), Instant.now());
            if (wasReserved) {
                stock.release(plan.breweryId(), plan.id(), command.actorId());
            }
            if (!plans.updateStatus(plan, version)) {
                throw new IllegalStateException("plano alterado por outra operação; tente novamente");
            }

            audit.record(AuditEvent.success(plan.breweryId(), command.actorId(), "packaging.plan.cancel",
                    "packaging.plan", plan.id().toString(),
                    Map.of("code", plan.code(), "reason", plan.cancelReason(),
                            "releasedStock", String.valueOf(wasReserved))));
        }
    }

    /** Consultas de leitura, sem efeito colateral. */
    public static final class Queries implements PackagingPlanQueries {

        private final PackagingPlanRepository plans;

        public Queries(PackagingPlanRepository plans) {
            this.plans = Objects.requireNonNull(plans);
        }

        @Override
        public List<PackagingPlan> list(UUID breweryId, UUID batchId) {
            return plans.findAll(breweryId, batchId);
        }

        @Override
        public Optional<PackagingPlan> find(UUID breweryId, UUID planId) {
            return plans.findById(breweryId, planId);
        }
    }
}
