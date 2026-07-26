package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.StockReserved;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockEventPublisher;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import br.com.brew.brassia.inventory.domain.StockUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reserva FEFO (STK-003): aloca a quantidade pedida sobre os lotes disponíveis do
 * ingrediente, do que vence primeiro ao que vence depois, pulando vencidos e
 * bloqueados. Os lotes são travados (lock pessimista) — duas OPs não consomem a
 * mesma disponibilidade. Insuficiência falha inteira (409), sem reserva parcial.
 */
public final class ReserveStockHandler implements ReserveStockUseCase {

    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final StockEventPublisher events;
    private final AuditTrail audit;

    public ReserveStockHandler(StockLotRepository lots, StockLedgerRepository ledger,
            StockEventPublisher events, AuditTrail audit) {
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var requestedUnit = StockUnit.of(command.unit());
        if (command.quantity() == null || command.quantity().signum() <= 0) {
            throw new IllegalArgumentException("quantidade a reservar deve ser positiva");
        }
        var remaining = requestedUnit.toCanonical(command.quantity());
        var today = LocalDate.now(ZoneOffset.UTC);

        var candidates = lots.lockCandidateLots(command.breweryId(), command.ingredientId(), today);
        var allocations = new ArrayList<Allocation>();

        for (StockLot lot : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (!lot.unit().sameDimension(requestedUnit)) {
                continue; // dimensão incompatível (ex.: massa x volume)
            }
            var balance = ledger.balance(command.breweryId(), lot.id().value());
            var availableCanonical = lot.unit().toCanonical(balance.onHand().subtract(balance.reserved()));
            if (availableCanonical.signum() <= 0) {
                continue;
            }
            var takeCanonical = remaining.min(availableCanonical);
            var takeInLotUnit = lot.unit().fromCanonical(takeCanonical);
            if (takeInLotUnit.signum() <= 0) {
                continue;
            }
            ledger.append(StockMovement.record(command.breweryId(), lot.id().value(), lot.ingredientId(),
                    StockMovementType.RESERVATION, takeInLotUnit, command.reference(), null, Instant.now(),
                    command.actorId()));
            allocations.add(new Allocation(lot.id().value(), takeInLotUnit, lot.unit().name()));
            remaining = remaining.subtract(takeCanonical);
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException("disponível insuficiente para reservar o ingrediente");
        }

        events.publish(new StockReserved(command.breweryId(), command.reference(), command.ingredientId(),
                command.quantity(), requestedUnit.name(), Instant.now()));
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.stock.reserve",
                "inventory.ingredient", command.ingredientId().toString(),
                Map.of("quantity", command.quantity().toPlainString(), "unit", requestedUnit.name(),
                        "reference", String.valueOf(command.reference()))));

        return new Result(command.ingredientId(), command.quantity(), requestedUnit.name(), List.copyOf(allocations));
    }
}
