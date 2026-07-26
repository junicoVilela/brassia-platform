package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.planning.BrewOrderReleased;
import br.com.brew.brassia.planning.application.port.inbound.ReleaseBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderEventPublisher;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.BrewOrder;
import br.com.brew.brassia.planning.domain.ReleaseBlocker;
import br.com.brew.brassia.planning.domain.ReleaseBlockedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Libera uma OP (BOP-002): reúne todos os bloqueios (estado, responsável,
 * equipamento) e, se houver algum, falha listando-os (409). Sem bloqueios,
 * transita DRAFT → RELEASED de forma atômica, emite {@code BrewOrderReleased} e
 * registra auditoria. Estoque e sanitização entram nas Sprints 06/08.
 */
public final class ReleaseBrewOrderHandler implements ReleaseBrewOrderUseCase {

    private final BrewOrderRepository repository;
    private final EquipmentProfileLookup equipment;
    private final BrewOrderEventPublisher events;
    private final AuditTrail audit;

    public ReleaseBrewOrderHandler(BrewOrderRepository repository, EquipmentProfileLookup equipment,
            BrewOrderEventPublisher events, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.equipment = Objects.requireNonNull(equipment);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var order = repository.findById(command.breweryId(), command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));

        var blockers = collectBlockers(order, command.assignedUserId());
        if (!blockers.isEmpty()) {
            throw new ReleaseBlockedException(blockers);
        }

        var releasedAt = Instant.now();
        order.release(command.assignedUserId(), releasedAt); // valida a transição no domínio

        // Guarda de concorrência: só uma liberação vence.
        if (!repository.markReleased(command.breweryId(), command.orderId(), command.assignedUserId(), releasedAt)) {
            throw new ReleaseBlockedException(List.of(ReleaseBlocker.notDraft()));
        }

        events.publish(new BrewOrderReleased(command.breweryId(), order.id().value(), order.code(),
                order.recipeId(), command.assignedUserId(), releasedAt));

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.release",
                "planning.order", order.id().value().toString(),
                Map.of("code", order.code(), "assignedUserId", command.assignedUserId().toString())));

        return new Result(order.id().value(), "RELEASED");
    }

    private List<ReleaseBlocker> collectBlockers(BrewOrder order, java.util.UUID assignedUserId) {
        var blockers = new ArrayList<ReleaseBlocker>();
        if (!order.releasable()) {
            blockers.add(ReleaseBlocker.notDraft());
        }
        if (assignedUserId == null) {
            blockers.add(ReleaseBlocker.missingResponsible());
        }
        if (equipment.find(order.breweryId(), order.snapshot().equipment().id()).isEmpty()) {
            blockers.add(ReleaseBlocker.equipmentMissing());
        }
        return blockers;
    }
}
