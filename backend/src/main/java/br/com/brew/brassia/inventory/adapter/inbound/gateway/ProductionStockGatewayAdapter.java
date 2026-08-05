package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.production.ProductionStockGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Converte a reserva da OP em consumo do dia de brassa (TRC-001-C), implementando a porta da
 * produção.
 *
 * <p><strong>Libera antes de consumir, lote a lote.</strong> Sem a liberação, o mesmo estoque
 * contaria duas vezes — uma como reservado e outra como consumido —, que é o mesmo cuidado que o
 * envase já tomava. Depois de registrar o consumo, o que sobrou da reserva é devolvido: OP
 * brassada não fica segurando insumo que não usou.
 *
 * <p>O movimento sai com {@code reason = 'brassagem'}, e não é detalhe. A coluna {@code reference}
 * do ledger é um UUID sem tipo; é o motivo que diz se aquele consumo foi da linha de envase ou do
 * dia de brassa, e é por ele que a genealogia decide qual aresta desenhar.
 */
@Component
class ProductionStockGatewayAdapter implements ProductionStockGateway {

    static final String REASON = "brassagem";

    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    ProductionStockGatewayAdapter(StockLotRepository lots, StockLedgerRepository ledger, JdbcClient jdbc,
            PlatformTransactionManager transactionManager) {
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    @Override
    public List<ReservedLot> reservedFor(UUID breweryId, UUID orderId) {
        var reserved = new ArrayList<ReservedLot>();
        for (var entry : ledger.reservedByReference(breweryId, orderId)) {
            if (entry.reserved().signum() <= 0) {
                continue;
            }
            lots.findById(breweryId, entry.lotId()).ifPresent(lot -> reserved.add(new ReservedLot(
                    entry.lotId(), entry.ingredientId(), ingredientName(breweryId, entry.ingredientId()),
                    lot.supplierLotCode(), entry.reserved(), lot.unit().name())));
        }
        return reserved;
    }

    @Override
    public boolean alreadyConsumed(UUID breweryId, UUID orderId) {
        return jdbc.sql("""
                SELECT 1 FROM stock_movement
                WHERE brewery_id = :brewery AND reference = :ref AND type = 'CONSUMPTION'
                  AND reason = :reason LIMIT 1
                """)
                .param("brewery", breweryId).param("ref", orderId).param("reason", REASON)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public Outcome consume(UUID breweryId, UUID orderId, UUID actorId, List<ConsumedLot> lines) {
        return transaction.execute(status -> {
            var shortfalls = new ArrayList<Shortfall>();
            var prepared = new ArrayList<Prepared>();

            for (ConsumedLot line : lines) {
                var lot = lots.lockForUpdate(breweryId, line.lotId()).orElse(null);
                if (lot == null) {
                    throw new IllegalArgumentException("lote de insumo inexistente nesta cervejaria");
                }
                var lotUnit = lot.unit();
                var declaredUnit = StockUnit.of(line.unit());
                if (!lotUnit.sameDimension(declaredUnit)) {
                    throw new IllegalArgumentException(
                            "unidade incompatível com o lote: " + line.unit());
                }
                var quantity = lotUnit.fromCanonical(declaredUnit.toCanonical(line.quantity()));
                var balance = ledger.balance(breweryId, line.lotId());
                // Confere contra o que existe de fato no lote — reservado ou não. Quem brassou pode
                // ter usado um lote que a OP não segurava, e isso é o registro sendo honesto.
                if (balance.onHand().compareTo(quantity) < 0) {
                    shortfalls.add(new Shortfall(line.lotId(), quantity, balance.onHand(), lotUnit.name()));
                    continue;
                }
                var reservedHere = reservedOf(breweryId, orderId, line.lotId());
                prepared.add(new Prepared(lot.id().value(), lot.ingredientId(), quantity,
                        reservedHere.min(quantity)));
            }

            if (!shortfalls.isEmpty()) {
                status.setRollbackOnly();
                return new Outcome(false, List.copyOf(shortfalls));
            }

            var at = Instant.now();
            for (Prepared line : prepared) {
                if (line.fromReservation().signum() > 0) {
                    ledger.append(StockMovement.record(breweryId, line.lotId(), line.ingredientId(),
                            StockMovementType.RELEASE, line.fromReservation(), orderId, null, at, actorId));
                }
                ledger.append(StockMovement.record(breweryId, line.lotId(), line.ingredientId(),
                        StockMovementType.CONSUMPTION, line.quantity(), orderId, REASON, at, actorId));
            }
            return new Outcome(true, List.of());
        });
    }

    /** Quanto a OP ainda segura deste lote — é o que pode virar consumo sem liberar a mais. */
    private BigDecimal reservedOf(UUID breweryId, UUID orderId, UUID lotId) {
        return ledger.reservedByReference(breweryId, orderId).stream()
                .filter(entry -> entry.lotId().equals(lotId))
                .map(StockLedgerRepository.ReservedLot::reserved)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String ingredientName(UUID breweryId, UUID ingredientId) {
        return jdbc.sql("SELECT name FROM catalog_ingredient WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", ingredientId)
                .query(String.class).optional().orElse(null);
    }

    private record Prepared(UUID lotId, UUID ingredientId, BigDecimal quantity, BigDecimal fromReservation) {}
}
