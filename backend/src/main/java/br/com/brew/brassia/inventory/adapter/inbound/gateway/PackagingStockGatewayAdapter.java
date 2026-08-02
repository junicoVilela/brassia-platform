package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reserva a embalagem de um plano de envase (PKG-001), implementando a porta do módulo de
 * envase. Idempotente por plano: libera o que a referência já segurava antes de reservar,
 * então repetir o comando não duplica o compromisso.
 *
 * <p>Roda dentro da transação do caso de uso que chamou — a verificação de disponibilidade e a
 * reserva compartilham os locks dos lotes candidatos, e uma falta reverte tudo com o commit.
 */
@Component
class PackagingStockGatewayAdapter implements PackagingStockGateway {

    private final ReserveStockUseCase reserveStock;
    private final ReleaseStockUseCase releaseStock;
    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;

    PackagingStockGatewayAdapter(ReserveStockUseCase reserveStock, ReleaseStockUseCase releaseStock,
            StockLotRepository lots, StockLedgerRepository ledger) {
        this.reserveStock = Objects.requireNonNull(reserveStock);
        this.releaseStock = Objects.requireNonNull(releaseStock);
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public Outcome reserve(UUID breweryId, UUID planId, UUID actorId, UUID containerId, BigDecimal units,
            String unit) {
        releaseStock.handle(new ReleaseStockUseCase.Command(actorId, breweryId, planId));

        var requestedUnit = StockUnit.of(unit);
        var available = reservable(breweryId, containerId, requestedUnit);
        if (available.compareTo(requestedUnit.toCanonical(units)) < 0) {
            return new Outcome(false, requestedUnit.fromCanonical(available), unit);
        }

        reserveStock.handle(new ReserveStockUseCase.Command(actorId, breweryId, containerId, units, unit, planId));
        return new Outcome(true, requestedUnit.fromCanonical(available), unit);
    }

    @Override
    public void release(UUID breweryId, UUID planId, UUID actorId) {
        releaseStock.handle(new ReleaseStockUseCase.Command(actorId, breweryId, planId));
    }

    @Override
    public Outcome consume(UUID breweryId, UUID planId, UUID actorId, UUID containerId, BigDecimal units,
            String unit) {
        // Todo o cálculo roda em unidade canônica: lote e pedido podem estar em unidades diferentes.
        var requestedUnit = StockUnit.of(unit);
        var reservedLots = reservedLotsOf(breweryId, planId, containerId);
        var reservedCanonical = reservedLots.stream()
                .map(ReservedLot::canonicalQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var available = reservedCanonical.add(reservable(breweryId, containerId, requestedUnit));
        var remaining = requestedUnit.toCanonical(units);
        if (available.compareTo(remaining) < 0) {
            return new Outcome(false, requestedUnit.fromCanonical(available), unit);
        }

        // Primeiro o que o plano já segurava: é a embalagem que foi de fato para a linha.
        for (var reserved : reservedLots) {
            if (remaining.signum() <= 0) {
                break;
            }
            var takeCanonical = reserved.canonicalQuantity().min(remaining);
            consumeFromLot(breweryId, planId, actorId, reserved.lot(), containerId,
                    reserved.lot().unit().fromCanonical(takeCanonical), true);
            remaining = remaining.subtract(takeCanonical);
        }

        // O que o envase gastou além do reservado sai do saldo livre, em ordem FEFO.
        for (StockLot lot : lots.lockCandidateLots(breweryId, containerId, LocalDate.now(ZoneOffset.UTC))) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (!lot.unit().sameDimension(requestedUnit)) {
                continue;
            }
            var balance = ledger.balance(breweryId, lot.id().value());
            var freeCanonical = lot.unit().toCanonical(balance.onHand().subtract(balance.reserved()));
            if (freeCanonical.signum() <= 0) {
                continue;
            }
            var takeCanonical = freeCanonical.min(remaining);
            consumeFromLot(breweryId, planId, actorId, lot, containerId,
                    lot.unit().fromCanonical(takeCanonical), false);
            remaining = remaining.subtract(takeCanonical);
        }

        // Plano executado não fica segurando estoque: a sobra da reserva volta.
        releaseStock.handle(new ReleaseStockUseCase.Command(actorId, breweryId, planId));
        return new Outcome(true, requestedUnit.fromCanonical(available), unit);
    }

    /**
     * Libera a reserva do lote antes de consumir: sem isso o mesmo estoque contaria duas vezes,
     * uma como reservado e outra como consumido.
     */
    private void consumeFromLot(UUID breweryId, UUID planId, UUID actorId, StockLot lot, UUID containerId,
            BigDecimal quantity, boolean fromReservation) {
        if (quantity.signum() <= 0) {
            return;
        }
        var at = Instant.now();
        if (fromReservation) {
            ledger.append(StockMovement.record(breweryId, lot.id().value(), containerId,
                    StockMovementType.RELEASE, quantity, planId, null, at, actorId));
        }
        ledger.append(StockMovement.record(breweryId, lot.id().value(), containerId,
                StockMovementType.CONSUMPTION, quantity, planId, "envase", at, actorId));
    }

    /** Lotes que o plano ainda segura para esta embalagem, travados e já convertidos. */
    private List<ReservedLot> reservedLotsOf(UUID breweryId, UUID planId, UUID containerId) {
        var reserved = new ArrayList<ReservedLot>();
        for (var entry : ledger.reservedByReference(breweryId, planId)) {
            if (!containerId.equals(entry.ingredientId()) || entry.reserved().signum() <= 0) {
                continue;
            }
            lots.lockForUpdate(breweryId, entry.lotId())
                    .ifPresent(lot -> reserved.add(new ReservedLot(lot, entry.reserved())));
        }
        return reserved;
    }

    /** Reserva viva de um lote, com a conversão para a unidade canônica já resolvida. */
    private record ReservedLot(StockLot lot, BigDecimal quantity) {

        BigDecimal canonicalQuantity() {
            return lot.unit().toCanonical(quantity);
        }
    }

    /** Disponível reservável da embalagem (lotes candidatos FEFO travados), em unidade canônica. */
    private BigDecimal reservable(UUID breweryId, UUID containerId, StockUnit requestedUnit) {
        var today = LocalDate.now(ZoneOffset.UTC);
        var available = BigDecimal.ZERO;
        for (StockLot lot : lots.lockCandidateLots(breweryId, containerId, today)) {
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
