package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.planning.application.port.inbound.CancelBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cancela uma OP com motivo (BOP-003). Idempotente: recancelar uma OP já
 * cancelada é no-op (sem novo efeito). Ordem iniciada/encerrada não pode ser
 * cancelada (409). A liberação de reservas de estoque entra na Sprint 06.
 */
public final class CancelBrewOrderHandler implements CancelBrewOrderUseCase {

    private final BrewOrderRepository repository;
    private final AuditTrail audit;

    public CancelBrewOrderHandler(BrewOrderRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
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

        // Liberação de reservas de estoque: no-op até o módulo de inventário (Sprint 06).

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.cancel",
                "planning.order", order.id().value().toString(),
                Map.of("code", order.code(), "reason", command.reason())));

        return new Result(order.id().value(), "CANCELLED");
    }
}
