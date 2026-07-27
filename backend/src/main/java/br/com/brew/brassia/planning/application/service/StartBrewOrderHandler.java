package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.planning.BrewOrderStarted;
import br.com.brew.brassia.planning.application.port.inbound.StartBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderEventPublisher;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Inicia a produção de uma OP liberada (PRD-001): RELEASED → IN_PRODUCTION,
 * transição única guardada pelo estado. Publica {@link BrewOrderStarted} para a
 * produção criar o lote (Batch) no mesmo commit.
 */
public final class StartBrewOrderHandler implements StartBrewOrderUseCase {

    private final BrewOrderRepository repository;
    private final BrewOrderEventPublisher events;
    private final AuditTrail audit;

    public StartBrewOrderHandler(BrewOrderRepository repository, BrewOrderEventPublisher events, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var order = repository.findById(command.breweryId(), command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));

        order.start(Instant.now()); // valida a transição no domínio (RELEASED → IN_PRODUCTION)

        var at = Instant.now();
        if (!repository.markStarted(command.breweryId(), command.orderId(), at)) {
            // Concorrência: estado mudou entre a leitura e a escrita.
            throw new IllegalStateException("ordem não está liberada");
        }

        events.publish(new BrewOrderStarted(command.breweryId(), order.id().value(), order.code(),
                order.recipeId(), order.recipeVersion(), order.snapshot().recipe().name(), order.volumeLiters(),
                command.actorId(), at));

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.start",
                "planning.order", order.id().value().toString(), Map.of("code", order.code())));

        return new Result(order.id().value(), "IN_PRODUCTION");
    }
}
