package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.WaterSourceName;
import java.util.Map;
import java.util.Objects;

public final class UpdateWaterSourceHandler implements UpdateWaterSourceUseCase {
    private final WaterSourceRepository repository;
    private final AuditTrail audit;

    public UpdateWaterSourceHandler(WaterSourceRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = repository.findById(command.breweryId(), command.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente"));
        source.rename(new WaterSourceName(command.name()));
        if (!repository.update(source, command.version())) {
            throw new IllegalStateException("versão da fonte divergiu");
        }
        var refreshed = repository.findById(command.breweryId(), command.sourceId()).orElseThrow();
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.source.update",
                "water_source", refreshed.id().value().toString(), Map.of("code", refreshed.code().value())));

        return new Result(refreshed.id().value(), refreshed.code().value(), refreshed.name().value(),
                refreshed.active(), refreshed.version());
    }
}
