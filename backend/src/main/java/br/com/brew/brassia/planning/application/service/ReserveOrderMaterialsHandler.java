package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.planning.StockReservationGateway;
import br.com.brew.brassia.planning.application.port.inbound.ReserveOrderMaterialsUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.BrewOrderStatus;
import br.com.brew.brassia.planning.domain.InsufficientStockForOrderException;
import br.com.brew.brassia.planning.domain.MaterialExplosion;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reserva os materiais de uma OP liberada (STK-003-A): explode a receita
 * publicada pelo volume da ordem e delega ao estoque a reserva atômica
 * (all-or-nothing). Só OPs RELEASED; falta em qualquer item → 409 com as faltas.
 */
public final class ReserveOrderMaterialsHandler implements ReserveOrderMaterialsUseCase {

    private final BrewOrderRepository orders;
    private final RecipeLookup recipes;
    private final StockReservationGateway stock;
    private final AuditTrail audit;

    public ReserveOrderMaterialsHandler(BrewOrderRepository orders, RecipeLookup recipes,
            StockReservationGateway stock, AuditTrail audit) {
        this.orders = Objects.requireNonNull(orders);
        this.recipes = Objects.requireNonNull(recipes);
        this.stock = Objects.requireNonNull(stock);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var order = orders.findById(command.breweryId(), command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));
        if (order.status() != BrewOrderStatus.RELEASED) {
            throw new IllegalStateException("só é possível reservar estoque de uma ordem liberada");
        }

        var composition = recipes.findPublishedComposition(command.breweryId(), order.recipeId())
                .orElseThrow(() -> new IllegalStateException("receita publicada indisponível para a ordem"));
        var components = composition.items().stream()
                .map(i -> new MaterialExplosion.Component(i.ingredientId(), i.quantity(), i.unit()))
                .toList();
        var lines = MaterialExplosion.explode(components, composition.batchVolumeLiters(), order.volumeLiters(),
                        BigDecimal.ZERO).stream()
                .map(r -> new StockReservationGateway.MaterialLine(r.ingredientId(), r.requiredQuantity(), r.unit()))
                .toList();

        var outcome = stock.reserveForOrder(command.breweryId(), order.id().value(), command.actorId(), lines);
        if (!outcome.reserved()) {
            throw new InsufficientStockForOrderException(outcome.shortfalls().stream()
                    .map(s -> new InsufficientStockForOrderException.Shortfall(
                            s.ingredientId(), s.requested(), s.available(), s.unit()))
                    .toList());
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.reserve",
                "planning.order", order.id().value().toString(),
                Map.of("code", order.code(), "items", String.valueOf(lines.size()))));

        return new Result(order.id().value(), lines.size());
    }
}
