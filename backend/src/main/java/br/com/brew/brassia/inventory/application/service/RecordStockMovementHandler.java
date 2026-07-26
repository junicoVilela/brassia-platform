package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.application.port.inbound.RecordStockMovementUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registra um movimento manual no ledger (STK-002). Só tipos manuais (consumo,
 * devolução, perda, ajuste) — reserva/liberação são do fluxo FEFO (STK-003).
 * Nas saídas, trava o lote (lock pessimista) e rejeita saldo negativo (409).
 */
public final class RecordStockMovementHandler implements RecordStockMovementUseCase {

    private static final Set<StockMovementType> MANUAL = Set.of(
            StockMovementType.CONSUMPTION, StockMovementType.RETURN, StockMovementType.LOSS,
            StockMovementType.ADJUSTMENT_IN, StockMovementType.ADJUSTMENT_OUT);

    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final AuditTrail audit;

    public RecordStockMovementHandler(StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit) {
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var type = parseType(command.type());

        // Lock pessimista serializa saídas concorrentes do mesmo lote (evita double spend).
        StockLot lot = lots.lockForUpdate(command.breweryId(), command.lotId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));

        var balance = ledger.balance(command.breweryId(), command.lotId());
        if (type.isOutflow() && balance.onHand().subtract(command.quantity()).signum() < 0) {
            throw new IllegalStateException("saldo insuficiente para a saída");
        }

        var movement = StockMovement.record(command.breweryId(), lot.id().value(), lot.ingredientId(), type,
                command.quantity(), null, command.reason(), Instant.now(), command.actorId());
        ledger.append(movement);

        var updated = ledger.balance(command.breweryId(), command.lotId());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.movement.record",
                "inventory.lot", lot.id().value().toString(),
                Map.of("type", type.name(), "quantity", command.quantity().toPlainString())));

        return new Result(movement.id(), updated.onHand(), updated.available());
    }

    private static StockMovementType parseType(String value) {
        StockMovementType type;
        try {
            type = StockMovementType.valueOf(value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("tipo de movimento inválido: " + value);
        }
        if (!MANUAL.contains(type)) {
            throw new IllegalArgumentException("tipo de movimento não permitido manualmente: " + type);
        }
        return type;
    }
}
