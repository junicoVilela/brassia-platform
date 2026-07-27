package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.planning.BrewOrderCancelled;
import br.com.brew.brassia.planning.application.port.inbound.CancelBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderEventPublisher;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cancela uma OP com motivo (BOP-003). Idempotente: recancelar uma OP já
 * cancelada é no-op (sem novo efeito). Ordem iniciada/encerrada não pode ser
 * cancelada (409). Publica {@link BrewOrderCancelled} para o estoque liberar as
 * reservas associadas (STK-003-B).
 */
public final class CancelBrewOrderHandler implements CancelBrewOrderUseCase {

    private final BrewOrderRepository repository;
    private final BrewOrderEventPublisher events;
    private final AuditTrail audit;

    public CancelBrewOrderHandler(BrewOrderRepository repository, BrewOrderEventPublisher events, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var order = repository.findById(command.breweryId(), command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));

        // Idempotência: já cancelada → no-op.
        if (order.cancelled()) {
            return new Result(order.id().value(), "CANCELLED");
        }
        if (!order.cancellable()) {
            throw new IllegalStateException("ordem iniciada ou encerrada não pode ser cancelada");
        }

        var at = Instant.now();
        order.cancel(command.reason(), at); // valida motivo/estado no domínio

        if (!repository.markCancelled(command.breweryId(), command.orderId(), command.reason(), at)) {
            // Concorrência: estado mudou entre a leitura e a escrita.
            var current = repository.findById(command.breweryId(), command.orderId())
                    .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));
            if (current.cancelled()) {
                return new Result(current.id().value(), "CANCELLED");
            }
            throw new IllegalStateException("ordem iniciada ou encerrada não pode ser cancelada");
        }

        // Estoque libera as reservas da OP ao consumir o evento (STK-003-B), no mesmo commit.
        events.publish(new BrewOrderCancelled(command.breweryId(), order.id().value(), order.code(),
                command.actorId(), command.reason(), at));

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.cancel",
                "planning.order", order.id().value().toString(),
                Map.of("code", order.code(), "reason", command.reason())));

        return new Result(order.id().value(), "CANCELLED");
    }
}
