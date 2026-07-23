package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.WaterSource;
import br.com.brew.brassia.water.domain.WaterSourceCode;
import br.com.brew.brassia.water.domain.WaterSourceName;
import java.util.Map;
import java.util.Objects;

public final class RegisterWaterSourceHandler implements RegisterWaterSourceUseCase {
    private final WaterSourceRepository repository;
    private final AuditTrail audit;

    public RegisterWaterSourceHandler(WaterSourceRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var code = new WaterSourceCode(command.code());
        var name = new WaterSourceName(command.name());

        if (repository.existsByCode(command.breweryId(), code.value())) {
            throw new IllegalStateException("código de fonte já existe nesta cervejaria");
        }

        var source = WaterSource.register(command.breweryId(), code, name);
        repository.insert(source);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.source.register",
                "water_source", source.id().value().toString(), Map.of("code", code.value())));

        return new Result(source.id().value(), code.value(), name.value(), source.active(), source.version());
    }
}
