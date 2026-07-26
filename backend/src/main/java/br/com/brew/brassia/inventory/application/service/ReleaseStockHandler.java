package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Libera as reservas de uma referência (ex.: OP cancelada): lança RELEASE
 * compensando o reservado líquido de cada lote. Idempotente por natureza — sem
 * reservas para a referência, não há efeito.
 */
public final class ReleaseStockHandler implements ReleaseStockUseCase {

    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final AuditTrail audit;

    public ReleaseStockHandler(StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit) {
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var reserved = ledger.reservedByReference(command.breweryId(), command.reference());
        int released = 0;
        for (var entry : reserved) {
            var lot = lots.lockForUpdate(command.breweryId(), entry.lotId());
            if (lot.isEmpty() || entry.reserved().signum() <= 0) {
                continue;
            }
            ledger.append(StockMovement.record(command.breweryId(), entry.lotId(), entry.ingredientId(),
                    StockMovementType.RELEASE, entry.reserved(), command.reference(), null, Instant.now(),
                    command.actorId()));
            released++;
        }
        if (released > 0) {
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.stock.release",
                    "inventory.reservation", command.reference().toString(),
                    Map.of("lots", Integer.toString(released))));
        }
        return new Result(command.reference(), released);
    }
}
