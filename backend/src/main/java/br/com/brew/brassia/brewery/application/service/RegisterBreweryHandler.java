package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.domain.Brewery;
import br.com.brew.brassia.brewery.domain.BreweryCode;
import br.com.brew.brassia.brewery.domain.BreweryName;
import br.com.brew.brassia.brewery.domain.Timezone;
import java.util.Map;
import java.util.Objects;

public final class RegisterBreweryHandler implements RegisterBreweryUseCase {
    private final BreweryRepository repository;
    private final AuditTrail audit;

    public RegisterBreweryHandler(BreweryRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var code = new BreweryCode(command.code());
        var name = new BreweryName(command.name());
        var timezone = new Timezone(command.timezone());

        if (repository.existsByCode(code.value())) {
            throw new IllegalStateException("brewery code already exists");
        }

        var brewery = Brewery.register(code, name, timezone);
        repository.save(brewery);

        audit.record(AuditEvent.success(
                brewery.id().value(),
                command.actorId(),
                "brewery.register",
                "brewery",
                brewery.id().value().toString(),
                Map.of("code", code.value(), "name", name.value())));

        return new Result(brewery.id().value(), code.value(), name.value(), timezone.value());
    }
}
