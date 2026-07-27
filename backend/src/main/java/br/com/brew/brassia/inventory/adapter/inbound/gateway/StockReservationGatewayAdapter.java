package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.planning.StockReservationGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserva o estoque de uma OP inteira (STK-003-A), implementando a porta do
 * planejamento. Tudo num commit: libera as reservas atuais da OP (idempotência),
 * verifica a disponibilidade reservável (FEFO: APROVADO e não vencido) de todos
 * os itens e só então reserva. Falta em qualquer item → reverte tudo (all-or-nothing).
 */
@Component
class StockReservationGatewayAdapter implements StockReservationGateway {

    private final ReserveStockUseCase reserveStock;
    private final ReleaseStockUseCase releaseStock;
    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final TransactionTemplate transaction;

    StockReservationGatewayAdapter(ReserveStockUseCase reserveStock, ReleaseStockUseCase releaseStock,
            StockLotRepository lots, StockLedgerRepository ledger, PlatformTransactionManager transactionManager) {
        this.reserveStock = Objects.requireNonNull(reserveStock);
        this.releaseStock = Objects.requireNonNull(releaseStock);
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    @Override
    public Outcome reserveForOrder(UUID breweryId, UUID orderId, UUID actorId, List<MaterialLine> lines) {
        return transaction.execute(status -> {
            // Re-sincroniza: libera o que a OP já tinha reservado (evita duplicar).
            releaseStock.handle(new ReleaseStockUseCase.Command(actorId, breweryId, orderId));

            var today = LocalDate.now(ZoneOffset.UTC);
            var shortfalls = new ArrayList<Shortfall>();
            for (var line : lines) {
                var requestedUnit = StockUnit.of(line.unit());
                var requestedCanonical = requestedUnit.toCanonical(line.quantity());
                var available = reservableCanonical(breweryId, line.ingredientId(), requestedUnit, today);
                if (available.compareTo(requestedCanonical) < 0) {
                    shortfalls.add(new Shortfall(line.ingredientId(), line.quantity(), available, line.unit()));
                }
            }
            if (!shortfalls.isEmpty()) {
                status.setRollbackOnly(); // nada reservado; reservas anteriores da OP permanecem
                return new Outcome(false, List.copyOf(shortfalls));
            }

            // Disponibilidade garantida (locks retidos): reserva cada item.
            for (var line : lines) {
                reserveStock.handle(new ReserveStockUseCase.Command(actorId, breweryId, line.ingredientId(),
                        line.quantity(), line.unit(), orderId));
            }
            return new Outcome(true, List.of());
        });
    }

    /** Disponível reservável do ingrediente (lotes candidatos FEFO travados), em unidade canônica. */
    private BigDecimal reservableCanonical(UUID breweryId, UUID ingredientId, StockUnit requestedUnit,
            LocalDate today) {
        var available = BigDecimal.ZERO;
        for (StockLot lot : lots.lockCandidateLots(breweryId, ingredientId, today)) {
            if (!lot.unit().sameDimension(requestedUnit)) {
                continue;
            }
            var balance = ledger.balance(breweryId, lot.id().value());
            var free = lot.unit().toCanonical(balance.onHand().subtract(balance.reserved()));
            if (free.signum() > 0) {
                available = available.add(free);
            }
        }
        return available;
    }
}
