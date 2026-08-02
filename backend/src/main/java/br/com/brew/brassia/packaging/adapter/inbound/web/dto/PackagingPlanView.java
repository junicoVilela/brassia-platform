package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.ChecklistItem;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Plano de envase como a interface precisa dele: o checklist vem completo (itens pendentes
 * incluídos, com {@code confirmed} falso), para a tela não precisar conhecer o enum do domínio.
 */
public record PackagingPlanView(UUID id, String code, UUID batchId, UUID containerId, BigDecimal containerVolumeMl,
        int plannedUnits, BigDecimal plannedVolumeLiters, UUID lineEquipmentId, Instant plannedStart,
        Instant plannedEnd, String status, List<ChecklistItemView> checklist, boolean checklistComplete,
        Instant reservedAt, String cancelReason) {

    public static PackagingPlanView from(PackagingPlan plan) {
        var confirmations = plan.checklist();
        var checklist = Arrays.stream(ChecklistItem.values())
                .map(item -> ChecklistItemView.of(item, confirmations.get(item)))
                .toList();
        return new PackagingPlanView(plan.id(), plan.code(), plan.batchId(), plan.containerId(),
                plan.containerVolumeMl(), plan.plannedUnits(), plan.plannedVolumeLiters(), plan.lineEquipmentId(),
                plan.plannedStart(), plan.plannedEnd(), plan.status().name(), checklist,
                plan.pendingChecklist().isEmpty(), plan.reservedAt(), plan.cancelReason());
    }

    public record ChecklistItemView(String item, boolean confirmed, UUID confirmedBy, Instant confirmedAt) {

        static ChecklistItemView of(ChecklistItem item, PackagingPlan.Confirmation confirmation) {
            return confirmation == null
                    ? new ChecklistItemView(item.name(), false, null, null)
                    : new ChecklistItemView(item.name(), true, confirmation.actorId(), confirmation.at());
        }
    }
}
