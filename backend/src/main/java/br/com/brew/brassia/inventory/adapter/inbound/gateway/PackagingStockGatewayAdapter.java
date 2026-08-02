package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
