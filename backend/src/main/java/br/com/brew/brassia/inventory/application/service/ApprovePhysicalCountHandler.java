package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.application.port.inbound.ApprovePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.PhysicalCountRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.CountLine;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Aprova a contagem (STK-004): reconcilia cada lote ao contado, gerando um
 * ajuste (ADJUSTMENT_IN/OUT) do delta = contado − on-hand vivo (lote travado).
 * A contagem original permanece registrada. Idempotência garantida pelo estado
 * (só aprova de OPEN).
 */
public final class ApprovePhysicalCountHandler implements ApprovePhysicalCountUseCase {

    private final PhysicalCountRepository counts;
    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final AuditTrail audit;

    public ApprovePhysicalCountHandler(PhysicalCountRepository counts, StockLotRepository lots,
            StockLedgerRepository ledger, AuditTrail audit) {
        this.counts = Objects.requireNonNull(counts);
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var count = counts.findById(command.breweryId(), command.countId())
                .orElseThrow(() -> new IllegalArgumentException("contagem inexistente"));
        if (!count.approvable()) {
            throw new IllegalStateException("contagem não está aberta");
        }

        var now = Instant.now();
        int adjustments = 0;
        for (CountLine line : count.lines()) {
            lots.lockForUpdate(command.breweryId(), line.lotId())
                    .orElseThrow(() -> new IllegalArgumentException("lote da contagem inexistente"));
            var currentOnHand = ledger.balance(command.breweryId(), line.lotId()).onHand();
            var delta = line.countedQuantity().subtract(currentOnHand);
            if (delta.signum() == 0) {
                continue;
            }
            var type = delta.signum() > 0 ? StockMovementType.ADJUSTMENT_IN : StockMovementType.ADJUSTMENT_OUT;
            ledger.append(StockMovement.record(command.breweryId(), line.lotId(), line.ingredientId(), type,
                    delta.abs(), count.id().value(), "inventário físico", now, command.actorId()));
            adjustments++;
        }

        if (!counts.markApproved(command.breweryId(), command.countId(), now, command.actorId())) {
            throw new IllegalStateException("contagem não está aberta");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.count.approve",
                "inventory.count", count.id().value().toString(),
                Map.of("adjustments", Integer.toString(adjustments))));

        return new Result(count.id().value(), "APPROVED", adjustments);
    }
}
